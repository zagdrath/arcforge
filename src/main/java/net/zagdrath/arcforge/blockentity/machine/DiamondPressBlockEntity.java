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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.DiamondPressMenu;
import net.zagdrath.arcforge.recipe.DiamondPressingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Diamond Press (Hardened): presses Graphite into a Diamond between tungsten anvils (arcforge:diamond_pressing), with
// a lot of FE and heat. It only works at the recipe's temperature (diamondPress.minTemperature, 1,400°C) or hotter, so its
// heat has to come from something hotter still (oxy-fuel, a hot Firebox Array); below that it pauses, keeping its
// progress. Each operation's FE and heat (energyPerDiamond, heatPerDiamond) are spread over its time and drawn every tick.
// Speed upgrades make it faster (drawing FE and heat just as much faster), Energy upgrades cut the FE and Heat upgrades the
// HU per diamond.
public class DiamondPressBlockEntity extends MachineBlockEntity {
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
    private int usage;
    private int heatUsage;
    private int needed = ArcforgeConfig.DIAMOND_PRESS_MIN_TEMPERATURE.getAsInt();
    // FE and HU not yet paid for this operation's ticks (neither is a whole number per tick).
    private double energyOwed;
    private double heatOwed;

    public DiamondPressBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: Graphite in the top, Diamonds out of the bottom, heat into the left, FE into the back.
        super(ModBlockEntityTypes.DIAMOND_PRESS.get(), pos, state, MACHINE_SLOTS, DiamondPressBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.HEAT, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.DIAMOND_PRESS_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.DIAMOND_PRESS_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.heat = new HeatBuffer(ArcforgeConfig.DIAMOND_PRESS_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.DIAMOND_PRESS_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(DiamondPressMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case DiamondPressMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case DiamondPressMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case DiamondPressMenu.DATA_USAGE -> usage;
                    case DiamondPressMenu.DATA_HEAT -> heat.getStored();
                    case DiamondPressMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case DiamondPressMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case DiamondPressMenu.DATA_MIN_TEMPERATURE -> needed;
                    case DiamondPressMenu.DATA_HEAT_USAGE -> heatUsage;
                    case DiamondPressMenu.DATA_PROGRESS -> progress;
                    case DiamondPressMenu.DATA_TOTAL -> total;
                    case DiamondPressMenu.DATA_STATUS -> status.ordinal();
                    case DiamondPressMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case DiamondPressMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && MachineRecipes.isDiamondPressInput(level, resource.toStack(1));
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    public int ticksFor(DiamondPressingRecipe recipe) {
        return UpgradeType.time(recipe.ticks(), upgrades(UpgradeType.SPEED));
    }

    // FE per diamond after Energy upgrades.
    public long energyFor(DiamondPressingRecipe recipe) {
        return (long) Math.ceil(recipe.energyPerOperation() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // HU per diamond after Heat upgrades (they cut it the way Energy upgrades cut FE).
    public long heatFor(DiamondPressingRecipe recipe) {
        return (long) Math.ceil(recipe.heatPerOperation() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.HEAT)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        heatUsage = 0;
        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
        } else {
            status = work(level);
        }
        setLit(status == MachineStatus.PRESSING);
        autoEject(level, itemOutput);
    }

    private MachineStatus work(ServerLevel level) {
        ItemStack input = items.getStack(SLOT_INPUT);
        RecipeHolder<DiamondPressingRecipe> holder = input.isEmpty() ? null : MachineRecipes.diamondPressing(level, input).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            energyOwed = 0;
            heatOwed = 0;
            needed = ArcforgeConfig.DIAMOND_PRESS_MIN_TEMPERATURE.getAsInt();
            return MachineStatus.IDLE;
        }
        DiamondPressingRecipe recipe = holder.value();
        total = ticksFor(recipe);
        needed = recipe.minTemperatureOrDefault();
        if (input.getCount() < recipe.inputCount()) {
            return MachineStatus.MISSING_ITEMS;
        }
        ItemStack result = recipe.result().create();
        ItemStack slot = items.getStack(SLOT_OUTPUT);
        if (!slot.isEmpty() && !(ItemStack.isSameItemSameComponents(slot, result) && slot.getCount() + result.getCount() <= slot.getMaxStackSize())) {
            return MachineStatus.OUTPUT_FULL;
        }
        // This tick's share of the operation's FE and heat (whole units, the rest carried to the next tick).
        double fePerTick = energyFor(recipe) / (double) Math.max(1, total);
        double huPerTick = heatFor(recipe) / (double) Math.max(1, total);
        int fe = (int) Math.ceil(fePerTick + energyOwed - 1.0E-9);
        int hu = (int) Math.ceil(huPerTick + heatOwed - 1.0E-9);
        if (heat.getTemperature() < needed || heat.getStored() < hu) {
            return MachineStatus.TOO_COLD;
        }
        if (fe > 0 && !energy.consume(fe)) {
            return MachineStatus.NO_POWER;
        }
        heat.remove(hu);
        energyOwed += fePerTick - fe;
        heatOwed += huPerTick - hu;
        usage = fe;
        heatUsage = hu;
        if (++progress >= total) {
            items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - recipe.inputCount()));
            items.setStack(SLOT_OUTPUT, slot.isEmpty() ? result : slot.copyWithCount(slot.getCount() + result.getCount()));
            ArcforgeAdvancements.produced(this, result, null, "diamond_pressing");
            progress = 0;
            energyOwed = 0;
            heatOwed = 0;
        }
        setChanged();
        return MachineStatus.PRESSING;
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

    public int getTotal() {
        return total;
    }

    public int getNeededTemperature() {
        return needed;
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
        energyOwed = input.getDoubleOr("energy_owed", 0.0);
        heatOwed = input.getDoubleOr("heat_owed", 0.0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        heat.serialize(output);
        output.putInt("progress", progress);
        output.putDouble("energy_owed", energyOwed);
        output.putDouble("heat_owed", heatOwed);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.diamond_press");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new DiamondPressMenu(containerId, inventory, worldPosition, items, data);
    }
}
