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
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.GrainDryerMenu;
import net.zagdrath.arcforge.recipe.DryingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Dries Hop Cones into Dried Hops, and wheat and sorghum into Dried Grain and Dried Sorghum (which ferment faster), with
// heat and no FE (arcforge:drying recipes). It only works while its heat buffer is above grainDryer.minTemperature
// (60°C): below that it pauses, keeping its progress. Heat flows in from hotter blocks (a Firebox, a Geothermal Plant, a
// warm Heat Cell) through heat faces. Speed upgrades make it faster (drawing heat just as much faster), Heat upgrades
// cut the heat per item. It also dries fluids from its tank (Latex into Raw Rubber sheets): the tank fills from pipes and
// conduits on input faces, or a held bucket, and when the input slot has nothing to dry, a fluid recipe runs from the
// tank, taking the recipe's amount when it's done.
public class GrainDryerBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.HEAT);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.HEAT);

    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> fluidInput;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int heatUsage;

    public GrainDryerBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: top input, bottom output, back heat.
        super(ModBlockEntityTypes.GRAIN_DRYER.get(), pos, state, MACHINE_SLOTS, GrainDryerBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.HEAT, SideMode.NONE),
                SIDE_MODES);
        this.heat = new HeatBuffer(ArcforgeConfig.GRAIN_DRYER_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.GRAIN_DRYER_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        this.tank = new FilteredFluidTank(ArcforgeConfig.GRAIN_DRYER_TANK_CAPACITY.getAsInt(),
                resource -> MachineRecipes.isDryerFluid(level, resource), this::setChanged);
        this.fluidInput = new AutomationResourceHandler<>(tank, index -> true, index -> false);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(GrainDryerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case GrainDryerMenu.DATA_HEAT -> heat.getStored();
                    case GrainDryerMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case GrainDryerMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case GrainDryerMenu.DATA_MIN_TEMPERATURE -> minTemperature();
                    case GrainDryerMenu.DATA_PROGRESS -> progress;
                    case GrainDryerMenu.DATA_TOTAL -> total;
                    case GrainDryerMenu.DATA_HEAT_USAGE -> heatUsage;
                    case GrainDryerMenu.DATA_STATUS -> status.ordinal();
                    case GrainDryerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case GrainDryerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    case GrainDryerMenu.DATA_FLUID -> tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
                    case GrainDryerMenu.DATA_FLUID_AMOUNT -> tank.getAmount();
                    case GrainDryerMenu.DATA_FLUID_CAPACITY -> tank.getCapacity();
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && MachineRecipes.isDryerInput(level, resource.toStack(1));
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    public static int minTemperature() {
        return ArcforgeConfig.GRAIN_DRYER_MIN_TEMPERATURE.getAsInt();
    }

    // HU/t while drying: Speed draws it faster, Heat cuts it the way Energy cuts FE.
    private int heatPerTick(DryingRecipe recipe) {
        return (int) Math.ceil(recipe.huPerTick() * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.HEAT)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        heatUsage = 0;
        if (!canRun(level)) {
            status = stoppedStatus();
        } else {
            status = work(level);
        }
        setLit(status == MachineStatus.DRYING);
        autoEject(level, itemOutput);
    }

    private MachineStatus work(ServerLevel level) {
        ItemStack input = items.getStack(SLOT_INPUT);
        RecipeHolder<DryingRecipe> holder = input.isEmpty() ? null : MachineRecipes.drying(level, input).orElse(null);
        // Nothing to dry in the slot: what's in the tank, if there's enough of it.
        boolean fromTank = false;
        if (holder == null && tank.getAmount() > 0) {
            holder = MachineRecipes.dryingFluid(level, tank.getResource(0))
                    .filter(found -> tank.getAmount() >= found.value().fluid().map(fluid -> fluid.amount()).orElse(Integer.MAX_VALUE))
                    .orElse(null);
            fromTank = holder != null;
        }
        if (holder == null) {
            progress = 0;
            total = 0;
            return MachineStatus.IDLE;
        }
        DryingRecipe recipe = holder.value();
        total = UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED));
        ItemStack result = recipe.result().create();
        if (!fits(items.getStack(SLOT_OUTPUT), result)) {
            return MachineStatus.OUTPUT_FULL;
        }
        int hu = heatPerTick(recipe);
        // Too cool, or too little heat left for this tick: wait for more, keeping the progress.
        if (heat.getTemperature() <= minTemperature() || heat.getStored() < hu) {
            return MachineStatus.TOO_COLD;
        }
        heat.remove(hu);
        heatUsage = hu;
        progress++;
        if (progress >= total) {
            if (fromTank) {
                try (Transaction tx = Transaction.openRoot()) {
                    tank.extract(0, tank.getResource(0), recipe.fluid().get().amount(), tx);
                    tx.commit();
                }
            } else {
                items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
            }
            ItemStack current = items.getStack(SLOT_OUTPUT);
            items.setStack(SLOT_OUTPUT, current.isEmpty() ? result : current.copyWithCount(current.getCount() + result.getCount()));
            if (fromTank) {
                controlState.completed(List.of(result.copy()), List.of(), 0, recipe.fluid().get().amount());
            } else {
                controlState.completed(result.copy(), 1);
            }
            progress = 0;
        }
        setChanged();
        return MachineStatus.DRYING;
    }

    private static boolean fits(ItemStack slot, ItemStack result) {
        if (slot.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(slot, result) && slot.getCount() + result.getCount() <= slot.getMaxStackSize();
    }

    public int getHeatUsage() {
        return heatUsage;
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    // Input faces fill the tank from pipes and fluid conduits; nothing drains it back out. Unsided, the tank (for Jade).
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? fluidInput : null;
    }

    // Held buckets fill the tank from any face.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidInput;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
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
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY, GAS -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heat.deserialize(input);
        tank.deserialize(input.childOrEmpty("tank"));
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
        tank.serialize(output.child("tank"));
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.grain_dryer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GrainDryerMenu(containerId, inventory, worldPosition, items, data);
    }
}
