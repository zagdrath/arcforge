/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.FiberizerMenu;
import net.zagdrath.arcforge.recipe.FiberizingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Melts slag and basalt and spins them into mineral wool (arcforge:fiberizing recipes). It needs both FE
// and heat every tick it works (30 FE/t and 20 HU/t for the stock recipes), and only works while its
// heat buffer is at least the recipe's minimum temperature (800°C): below that it pauses, keeping its
// progress. Heat only flows in from hotter blocks, so it needs a Firebox, a Geothermal Plant or a hot
// Heat Cell. Speed upgrades make it faster (drawing FE and heat just as much faster), Energy upgrades cut
// the FE and Heat upgrades the heat per operation.
public class FiberizerBlockEntity extends MachineBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY, UpgradeType.HEAT);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY, SideMode.HEAT);

    private final ConsumerEnergyHandler energy;
    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int minTemp = FiberizingRecipe.DEFAULT_MIN_TEMP;
    private int feUsage;
    private int heatUsage;

    public FiberizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FIBERIZER.get(), pos, state, MACHINE_SLOTS, FiberizerBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.HEAT, SideMode.OUTPUT, SideMode.INPUT, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.FIBERIZER_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.FIBERIZER_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.heat = new HeatBuffer(
                ArcforgeConfig.FIBERIZER_HEAT_CAPACITY.getAsInt(),
                ArcforgeConfig.FIBERIZER_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(FiberizerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case FiberizerMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case FiberizerMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case FiberizerMenu.DATA_HEAT -> heat.getStored();
                    case FiberizerMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case FiberizerMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case FiberizerMenu.DATA_MIN_TEMPERATURE -> minTemp;
                    case FiberizerMenu.DATA_PROGRESS -> progress;
                    case FiberizerMenu.DATA_TOTAL -> total;
                    case FiberizerMenu.DATA_FE_USAGE -> feUsage;
                    case FiberizerMenu.DATA_HEAT_USAGE -> heatUsage;
                    case FiberizerMenu.DATA_STATUS -> status.ordinal();
                    case FiberizerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case FiberizerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // The input slot takes anything with a fiberizing recipe. The client passes a null level and checks
    // against the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && MachineRecipes.isFiberizerInput(level, resource.toStack(1));
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // FE/t while working: Speed draws it faster, Energy cuts it, so an operation costs base x 0.8^n.
    private int energyPerTick(FiberizingRecipe recipe) {
        return (int) Math.ceil(recipe.fePerTick() * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // HU/t while working: Speed draws it faster, Heat cuts it the same way Energy cuts FE.
    private int heatPerTick(FiberizingRecipe recipe) {
        return (int) Math.ceil(recipe.huPerTick() * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.HEAT)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        feUsage = 0;
        heatUsage = 0;
        if (!canRun(level)) {
            status = stoppedStatus();
        } else {
            status = work(level);
        }
        setLit(status == MachineStatus.FIBERIZING);
        autoEject(level, itemOutput);
    }

    private MachineStatus work(ServerLevel level) {
        ItemStack input = items.getStack(SLOT_INPUT);
        RecipeHolder<FiberizingRecipe> holder = input.isEmpty() ? null : MachineRecipes.fiberizing(level, input).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            minTemp = FiberizingRecipe.DEFAULT_MIN_TEMP;
            return MachineStatus.IDLE;
        }
        FiberizingRecipe recipe = holder.value();
        total = UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED));
        minTemp = recipe.minTemp();
        ItemStack result = recipe.assemble(new SingleRecipeInput(input));
        if (!fits(items.getStack(SLOT_OUTPUT), result)) {
            return MachineStatus.OUTPUT_FULL;
        }
        int hu = heatPerTick(recipe);
        // Too cold, or too little heat left for this tick: wait for more, keeping the progress.
        if (heat.getTemperature() < recipe.minTemp() || heat.getStored() < hu) {
            return MachineStatus.TOO_COLD;
        }
        int fe = energyPerTick(recipe);
        if (!energy.consume(fe)) {
            return MachineStatus.NO_POWER;
        }
        heat.remove(hu);
        feUsage = fe;
        heatUsage = hu;
        progress++;
        if (progress >= total) {
            items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
            ItemStack current = items.getStack(SLOT_OUTPUT);
            items.setStack(SLOT_OUTPUT, current.isEmpty() ? result : current.copyWithCount(current.getCount() + result.getCount()));
            controlState.completed(result.copy(), 1);
            progress = 0;
        }
        setChanged();
        return MachineStatus.FIBERIZING;
    }

    private static boolean fits(ItemStack slot, ItemStack result) {
        if (slot.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(slot, result) && slot.getCount() + result.getCount() <= slot.getMaxStackSize();
    }

    public int getTotal() {
        return total;
    }

    public int getUsage() {
        return feUsage;
    }

    public int getHeatUsage() {
        return heatUsage;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public int getProgress() {
        return progress;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> itemInput;
            case OUTPUT -> itemOutput;
            default -> null;
        };
    }

    // FE goes in on energy faces.
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    // Heat goes in on heat faces; nothing can draw it back out.
    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.HEAT ? heatInput : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case FLUID, GAS -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        heat.deserialize(input);
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        heat.serialize(output);
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.fiberizer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FiberizerMenu(containerId, inventory, worldPosition, items, data);
    }
}
