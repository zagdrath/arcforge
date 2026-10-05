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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.HydrothermalCarbonizerMenu;
import net.zagdrath.arcforge.recipe.HydrothermalCarbonizingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Cooks biomass (#arcforge:biomass) in water under heat into Bio-Coal (arcforge:hydrothermal_carbonizing recipes), with
// no FE. It only works at hydrothermalCarbonizer.minTemperature (200°C) or hotter: below that it pauses, keeping its
// progress. Each operation takes the recipe's biomass (biomassPerBioCoal, 8) from the input slot and its water from the
// water tank, uses its heat (heatPerOperation, 6,000 HU) spread over its time, and gives the result and the recipe's
// water_return into the returned-water tank (what doesn't fit is lost as steam, as on the Thermal Evaporator Array). Heat
// flows in through heat faces from hotter blocks. Speed upgrades make it faster (drawing heat just as much faster), Heat
// upgrades cut the heat per operation.
public class HydrothermalCarbonizerBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.HEAT);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.HEAT);

    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final FilteredFluidTank water;
    private final FilteredFluidTank returned;
    // Set in the GUI: the water each batch gives back is thrown away instead of kept, and the tank is kept empty.
    private boolean discardWater;
    private final ResourceHandler<FluidResource> waterInput;
    private final ResourceHandler<FluidResource> waterOutput;
    private final ResourceHandler<FluidResource> fluidAutomation;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int heatUsage;
    // HU not yet paid for this operation's ticks (its heat isn't a whole number per tick).
    private double heatOwed;

    public HydrothermalCarbonizerBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: top input (biomass and water), bottom output (Bio-Coal and the returned water), back heat.
        super(ModBlockEntityTypes.HYDROTHERMAL_CARBONIZER.get(), pos, state, MACHINE_SLOTS, HydrothermalCarbonizerBlockEntity::isItemValid,
                UPGRADES, new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.HEAT, SideMode.NONE),
                SIDE_MODES);
        this.heat = new HeatBuffer(ArcforgeConfig.HYDROTHERMAL_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.HYDROTHERMAL_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        int capacity = ArcforgeConfig.HYDROTHERMAL_TANK_CAPACITY.getAsInt();
        this.water = new FilteredFluidTank(capacity, resource -> resource.getFluid() == Fluids.WATER, this::setChanged);
        this.returned = new FilteredFluidTank(capacity, resource -> resource.getFluid() == Fluids.WATER, this::setChanged);
        this.waterInput = new AutomationResourceHandler<>(water, index -> true, index -> false);
        this.waterOutput = new AutomationResourceHandler<>(returned, index -> false, index -> true);
        this.fluidAutomation = new CombinedResourceHandler<>(waterInput, waterOutput);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(HydrothermalCarbonizerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case HydrothermalCarbonizerMenu.DATA_HEAT -> heat.getStored();
                    case HydrothermalCarbonizerMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case HydrothermalCarbonizerMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case HydrothermalCarbonizerMenu.DATA_MIN_TEMPERATURE -> minTemperature();
                    case HydrothermalCarbonizerMenu.DATA_PROGRESS -> progress;
                    case HydrothermalCarbonizerMenu.DATA_TOTAL -> total;
                    case HydrothermalCarbonizerMenu.DATA_HEAT_USAGE -> heatUsage;
                    case HydrothermalCarbonizerMenu.DATA_STATUS -> status.ordinal();
                    case HydrothermalCarbonizerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case HydrothermalCarbonizerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    case HydrothermalCarbonizerMenu.DATA_WATER -> water.getAmount();
                    case HydrothermalCarbonizerMenu.DATA_RETURNED -> returned.getAmount();
                    case HydrothermalCarbonizerMenu.DATA_TANK_CAPACITY -> water.getCapacity();
                    case HydrothermalCarbonizerMenu.DATA_DISCARD -> discardWater ? 1 : 0;
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && MachineRecipes.isHydrothermalInput(level, resource.toStack(1));
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    public static int minTemperature() {
        return ArcforgeConfig.HYDROTHERMAL_MIN_TEMPERATURE.getAsInt();
    }

    // HU per operation after Heat upgrades (they cut it the way Energy upgrades cut FE).
    public int heatPerOperation(HydrothermalCarbonizingRecipe recipe) {
        return (int) Math.ceil(recipe.heatPerOperation() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.HEAT)));
    }

    public int ticksFor(HydrothermalCarbonizingRecipe recipe) {
        return UpgradeType.time(recipe.ticks(), upgrades(UpgradeType.SPEED));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        heatUsage = 0;
        if (discardWater && returned.getAmount() > 0) {
            returned.set(0, FluidResource.EMPTY, 0);
        }
        if (!canRun(level)) {
            status = stoppedStatus();
        } else {
            status = work(level);
        }
        setLit(status == MachineStatus.CARBONIZING);
        outputs.pushFluid(level, pos, getFacing(), sideConfig, waterOutput, ArcforgeConfig.HYDROTHERMAL_OUTPUT_RATE.getAsInt(), null);
        autoEject(level, itemOutput);
    }

    private MachineStatus work(ServerLevel level) {
        ItemStack input = items.getStack(SLOT_INPUT);
        RecipeHolder<HydrothermalCarbonizingRecipe> holder = input.isEmpty() ? null : MachineRecipes.hydrothermalCarbonizing(level, input).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            heatOwed = 0;
            return MachineStatus.IDLE;
        }
        HydrothermalCarbonizingRecipe recipe = holder.value();
        total = ticksFor(recipe);
        if (input.getCount() < recipe.inputCount()) {
            return MachineStatus.MISSING_ITEMS;
        }
        if (water.getAmount() < recipe.water()) {
            return MachineStatus.NO_WATER;
        }
        ItemStack result = recipe.result().create();
        if (!fits(items.getStack(SLOT_OUTPUT), result)) {
            return MachineStatus.OUTPUT_FULL;
        }
        // This tick's share of the operation's heat (whole HU, the rest carried to the next tick).
        double perTick = heatPerOperation(recipe) / (double) Math.max(1, total);
        int hu = (int) Math.ceil(perTick + heatOwed - 1.0E-9);
        // Too cool, or too little heat for this tick: wait for more, keeping the progress.
        if (heat.getTemperature() < minTemperature() || heat.getStored() < hu) {
            return MachineStatus.TOO_COLD;
        }
        heat.remove(hu);
        heatOwed += perTick - hu;
        heatUsage = hu;
        progress++;
        if (progress >= total) {
            items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - recipe.inputCount()));
            int used;
            int kept = 0;
            try (Transaction tx = Transaction.openRoot()) {
                used = water.extract(0, FluidResource.of(Fluids.WATER), recipe.water(), tx);
                // Water that doesn't fit is lost as steam, and all of it while discarding.
                int back = discardWater ? 0 : Math.min(recipe.waterReturn(), returned.getSpace());
                if (back > 0) {
                    kept = returned.insert(0, FluidResource.of(Fluids.WATER), back, tx);
                }
                tx.commit();
            }
            ItemStack current = items.getStack(SLOT_OUTPUT);
            items.setStack(SLOT_OUTPUT, current.isEmpty() ? result.copy() : current.copyWithCount(current.getCount() + result.getCount()));
            controlState.completed(List.of(result), kept > 0 ? List.of(new FluidStack(Fluids.WATER, kept)) : List.of(), recipe.inputCount(), used);
            ArcforgeAdvancements.produced(this, result, null, "hydrothermal_carbonizing");
            progress = 0;
            heatOwed = 0;
        }
        setChanged();
        return MachineStatus.CARBONIZING;
    }

    private static boolean fits(ItemStack slot, ItemStack result) {
        if (slot.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(slot, result) && slot.getCount() + result.getCount() <= slot.getMaxStackSize();
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    public FilteredFluidTank getReturned() {
        return returned;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    public int getHeatUsage() {
        return heatUsage;
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

    // Input faces fill the water tank; output faces drain the returned water.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return fluidAutomation;
        }
        return switch (mode) {
            case INPUT -> waterInput;
            case OUTPUT -> waterOutput;
            default -> null;
        };
    }

    // Held buckets fill the water tank, and empty buckets take the returned water.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidAutomation;
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
            case ITEM, FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY, GAS -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        heat.deserialize(input);
        water.deserialize(input.childOrEmpty("water"));
        returned.deserialize(input.childOrEmpty("returned"));
        progress = input.getIntOr("progress", 0);
        heatOwed = input.getDoubleOr("heat_owed", 0.0);
        discardWater = input.getBooleanOr("discard_water", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        heat.serialize(output);
        water.serialize(output.child("water"));
        returned.serialize(output.child("returned"));
        output.putInt("progress", progress);
        output.putDouble("heat_owed", heatOwed);
        output.putBoolean("discard_water", discardWater);
    }

    public boolean isDiscardingWater() {
        return discardWater;
    }

    public void setDiscardingWater(boolean on) {
        discardWater = on;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.hydrothermal_carbonizer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new HydrothermalCarbonizerMenu(containerId, inventory, worldPosition, items, data);
    }
}
