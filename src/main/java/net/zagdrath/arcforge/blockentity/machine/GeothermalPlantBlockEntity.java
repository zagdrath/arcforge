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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.GeothermalHeat;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.GeothermalPlantMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Turns lava into heat (HU); it makes no FE itself. Lava drains from the tank at 1 mB/t for lavaHeat
// HU/t, and touching lava source blocks and magma blocks add passive heat, tank or no tank. Production
// pauses while the heat buffer is full. At 600°C it's a cooler source than a Firebox.
public class GeothermalPlantBlockEntity extends MachineBlockEntity implements FluidInteractable {
    // Lava buckets go in the input slot and are poured into the tank; the empty buckets come out below.
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.HEAT);

    private static final int SCAN_INTERVAL = 20;
    private static final FluidResource LAVA = FluidResource.of(Fluids.LAVA);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.HEAT);

    private final HeatBuffer heat;
    private final HeatHandler heatOutput;
    private final FilteredFluidTank lavaTank;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ResourceHandler<FluidResource> fluidInput;
    private final ContainerData data;

    // Lava taken from the tank and still draining (1 mB/t), out of what was taken.
    private double lavaBurning;
    private int lavaBurnTotal;
    private GeothermalHeat.@Nullable Surroundings surroundings;
    private int heatPerTick;

    public GeothermalPlantBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.GEOTHERMAL_PLANT.get(), pos, state, MACHINE_SLOTS, GeothermalPlantBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.HEAT, SideMode.NONE),
                SIDE_MODES);
        this.heat = new HeatBuffer(
                ArcforgeConfig.GEOTHERMAL_HEAT_CAPACITY.getAsInt(),
                ArcforgeConfig.GEOTHERMAL_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatOutput = heat.output();
        this.lavaTank = new FilteredFluidTank(
                ArcforgeConfig.GEOTHERMAL_LAVA_TANK_CAPACITY.getAsInt(),
                resource -> resource.getFluid() == Fluids.LAVA,
                this::setChanged);
        // With no output face mode, input faces also hand back the empty buckets.
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
        this.fluidInput = new AutomationResourceHandler<>(lavaTank, index -> true, index -> false);
        this.data = new WideIntContainerData(GeothermalPlantMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                GeothermalHeat.Surroundings around = surroundings();
                return switch (index) {
                    case GeothermalPlantMenu.DATA_HEAT -> heat.getStored();
                    case GeothermalPlantMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case GeothermalPlantMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case GeothermalPlantMenu.DATA_LAVA -> lavaTank.getAmount();
                    case GeothermalPlantMenu.DATA_LAVA_CAPACITY -> lavaTank.getCapacity();
                    case GeothermalPlantMenu.DATA_BURN_TIME -> (int) Math.ceil(lavaBurning);
                    case GeothermalPlantMenu.DATA_BURN_TOTAL -> lavaBurnTotal;
                    case GeothermalPlantMenu.DATA_HEAT_PER_TICK -> heatPerTick;
                    case GeothermalPlantMenu.DATA_LAVA_SOURCES -> around.lavaSources();
                    case GeothermalPlantMenu.DATA_MAGMA_BLOCKS -> around.magmaBlocks();
                    case GeothermalPlantMenu.DATA_STATUS -> status.ordinal();
                    case GeothermalPlantMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case GeothermalPlantMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(int slot, ItemResource resource) {
        return slot == SLOT_INPUT && resource.is(Items.LAVA_BUCKET);
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, GeothermalPlantBlockEntity::isItemValid, UPGRADES, () -> {});
    }

    private GeothermalHeat.Surroundings surroundings() {
        return surroundings != null ? surroundings : GeothermalHeat.Surroundings.NONE;
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, GeothermalPlantBlockEntity plant) {
        plant.tick(level, pos);
    }

    private void tick(ServerLevel level, BlockPos pos) {
        if (surroundings == null || level.getGameTime() % SCAN_INTERVAL == 0) {
            surroundings = GeothermalHeat.scan(level, pos);
        }
        drainLavaBucket();

        // A redstone-disabled plant makes nothing but still gives away the heat it holds.
        boolean enabled = redstoneMode.canRun(level.hasNeighborSignal(pos));
        heatPerTick = 0;
        boolean drained = false;
        if (enabled && !heat.isFull()) {
            if (lavaBurning <= 0) {
                takeLava();
            }
            // Speed upgrades drain the lava faster; Heat upgrades get more heat from it and from the surroundings.
            double made = surroundings.passiveHeat();
            if (lavaBurning > 0) {
                double drain = Math.min(lavaBurning, speedMultiplier());
                lavaBurning -= drain;
                drained = true;
                made += GeothermalHeat.lavaHeat() * drain;
            }
            heatPerTick = heat.add((int) Math.round(made * UpgradeType.outputMultiplier(upgrades(UpgradeType.HEAT))));
        }
        outputs.pushHeat(level, pos, getFacing(), sideConfig, heat, ArcforgeConfig.HEAT_CONTACT_RATE.getAsInt());

        if (!enabled) {
            status = MachineStatus.DISABLED;
        } else if (heat.isFull()) {
            status = MachineStatus.FULL;
        } else if (heatPerTick > 0) {
            status = MachineStatus.RUNNING;
        } else {
            status = MachineStatus.NO_LAVA;
        }
        setLit(heatPerTick > 0);
        if (drained) {
            setChanged();
        }
    }

    // Takes the next lavaPerBurn mB (or whatever is left) from the tank to drain at 1 mB/t.
    private void takeLava() {
        int amount = Math.min(lavaTank.getAmount(), ArcforgeConfig.GEOTHERMAL_LAVA_PER_BURN.getAsInt());
        if (amount <= 0) {
            return;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int taken = lavaTank.extract(0, LAVA, amount, tx);
            tx.commit();
            lavaBurning = taken;
            lavaBurnTotal = taken;
        }
    }

    // Empties a lava bucket from the input slot into the tank, placing the empty bucket in the output slot.
    private void drainLavaBucket() {
        ItemStack input = items.getStack(SLOT_INPUT);
        if (!input.is(Items.LAVA_BUCKET) || lavaTank.getSpace() < FluidType.BUCKET_VOLUME) {
            return;
        }

        ItemStack output = items.getStack(SLOT_OUTPUT);
        if (!output.isEmpty() && (!output.is(Items.BUCKET) || output.getCount() >= output.getMaxStackSize())) {
            return;
        }

        try (Transaction tx = Transaction.openRoot()) {
            if (lavaTank.insert(0, LAVA, FluidType.BUCKET_VOLUME, tx) != FluidType.BUCKET_VOLUME) {
                return;
            }
            tx.commit();
        }

        items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
        items.setStack(SLOT_OUTPUT, output.isEmpty() ? new ItemStack(Items.BUCKET) : output.copyWithCount(output.getCount() + 1));
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    // Input faces take lava buckets and give back the empty ones.
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? itemAutomation : null;
    }

    // Input faces take lava from pipes and fluid conduits.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? fluidInput : null;
    }

    // Heat can be drawn out of heat faces (and unsided queries); nothing can put heat in.
    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.HEAT ? heatOutput : null;
    }

    // Held buckets fill the tank from any face, regardless of the side configuration.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidInput;
    }

    // Auto conduits follow the side configuration: items and lava in on input faces, heat out of heat faces.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM, FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        lavaTank.deserialize(input.childOrEmpty("lava"));
        heat.deserialize(input);
        lavaBurning = input.getDoubleOr("lava_burning", 0.0);
        lavaBurnTotal = input.getIntOr("lava_burn_total", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        lavaTank.serialize(output.child("lava"));
        heat.serialize(output);
        output.putDouble("lava_burning", lavaBurning);
        output.putInt("lava_burn_total", lavaBurnTotal);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.geothermal_plant");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GeothermalPlantMenu(containerId, inventory, worldPosition, items, data);
    }
}
