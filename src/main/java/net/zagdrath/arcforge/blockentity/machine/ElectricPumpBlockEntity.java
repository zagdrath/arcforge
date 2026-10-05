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
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.machine.ClimateHelper;
import net.zagdrath.arcforge.machine.BucketSlots;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ElectricPumpMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Pumps the fluid source block directly below it with FE: a bucket (1,000 mB) every 20 ticks for
// 10 FE/t. The source is removed, except water that is an infinite source (two or more water sources
// beside it), which is left in place. Any fluid with a source block works; flowing fluid doesn't. Water in an ocean or
// beach biome comes up as Seawater. It
// pushes up to 1,000 mB/t out of its top every tick, and out of output faces with auto-eject; the
// bucket slots fill empty buckets from the tank. Speed upgrades pump faster (drawing FE just as much
// faster), Energy upgrades cut the FE per bucket.
public class ElectricPumpBlockEntity extends MachineBlockEntity {
    public static final int SLOT_BUCKET_IN = 0;
    public static final int SLOT_BUCKET_OUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> fluidOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;
    // What's below, for the GUI: the source fluid's id (-1 for none) and whether it's an infinite pool.
    private int sourceFluid = -1;
    private boolean infiniteSource;

    public ElectricPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ELECTRIC_PUMP.get(), pos, state, MACHINE_SLOTS, ElectricPumpBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.PUMP_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.PUMP_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.tank = new FilteredFluidTank(ArcforgeConfig.PUMP_TANK_CAPACITY.getAsInt(), resource -> true, this::setChanged);
        this.fluidOutput = new AutomationResourceHandler<>(tank, index -> false, index -> true);
        // Automation puts empty buckets in and takes the full ones out.
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_BUCKET_IN, slot -> slot == SLOT_BUCKET_OUT);
        this.data = new WideIntContainerData(ElectricPumpMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case ElectricPumpMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case ElectricPumpMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case ElectricPumpMenu.DATA_PROGRESS -> progress;
                    case ElectricPumpMenu.DATA_TOTAL -> cycleTicks();
                    case ElectricPumpMenu.DATA_USAGE -> usage;
                    case ElectricPumpMenu.DATA_FLUID -> tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
                    case ElectricPumpMenu.DATA_FLUID_AMOUNT -> tank.getAmount();
                    case ElectricPumpMenu.DATA_FLUID_CAPACITY -> tank.getCapacity();
                    case ElectricPumpMenu.DATA_SOURCE -> sourceFluid;
                    case ElectricPumpMenu.DATA_INFINITE -> infiniteSource ? 1 : 0;
                    case ElectricPumpMenu.DATA_STATUS -> status.ordinal();
                    case ElectricPumpMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ElectricPumpMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // The bucket slot takes empty buckets, to fill from the tank.
    public static boolean isItemValid(int slot, ItemResource resource) {
        return slot == SLOT_BUCKET_IN && resource.is(Items.BUCKET);
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, ElectricPumpBlockEntity::isItemValid, UPGRADES, () -> {});
    }

    // Ticks per bucket, after Speed upgrades.
    public int cycleTicks() {
        return Math.max(1, (int) Math.round(ArcforgeConfig.PUMP_CYCLE_TICKS.getAsInt() / speedMultiplier()));
    }

    // FE/t while pumping: Speed draws it faster, Energy cuts it, so a bucket costs base x 0.8^n.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.PUMP_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        BucketSlots.fill(items, SLOT_BUCKET_IN, SLOT_BUCKET_OUT, tank);
        usage = 0;

        BlockPos below = pos.below();
        FluidState source = level.getFluidState(below);
        boolean hasSource = source.isSource() && level.getBlockState(below).getBlock() instanceof BucketPickup;
        Fluid pumped = hasSource ? pumpedFluid(level, below, source) : null;
        sourceFluid = pumped != null ? BuiltInRegistries.FLUID.getId(pumped) : -1;
        infiniteSource = hasSource && isInfiniteWater(level, below, source);
        total = cycleTicks();

        if (!canRun(level)) {
            status = stoppedStatus();
        } else if (!hasSource) {
            status = MachineStatus.NO_SOURCE;
            progress = 0;
        } else if (!fits(FluidResource.of(pumped))) {
            status = MachineStatus.TANK_FULL;
        } else if (!energy.consume(energyPerTick())) {
            status = MachineStatus.NO_POWER;
        } else {
            status = MachineStatus.PUMPING;
            usage = energyPerTick();
            if (++progress >= total) {
                progress = 0;
                pump(level, below, source, pumped);
            }
            setChanged();
        }
        setLit(status == MachineStatus.PUMPING);

        // The top always pushes; output faces too with auto-eject.
        int rate = ArcforgeConfig.PUMP_OUTPUT_RATE.getAsInt();
        int pushed = outputs.pushFluid(level, pos, Direction.UP, fluidOutput, rate);
        if (isAutoEject()) {
            outputs.pushFluid(level, pos, getFacing(), sideConfig, fluidOutput, rate - pushed, Direction.UP);
        }
    }

    private boolean fits(FluidResource fluid) {
        return tank.getSpace() >= FluidType.BUCKET_VOLUME && (tank.getAmount() == 0 || tank.getResource(0).equals(fluid));
    }

    // What a bucket of the source below fills the tank with: Seawater from water in ocean and beach biomes
    // (electricPump.seawaterBiomeTags), otherwise the source's own fluid.
    public static Fluid pumpedFluid(ServerLevel level, BlockPos pos, FluidState source) {
        if (source.getType() == Fluids.WATER && ClimateHelper.inAny(level.getBiome(pos), ArcforgeConfig.PUMP_SEAWATER_BIOME_TAGS.get())) {
            return ModFluids.SEAWATER.get();
        }
        return source.getType();
    }

    // Takes a bucket of the source below, leaving an infinite water source in place.
    private void pump(ServerLevel level, BlockPos below, FluidState source, Fluid pumped) {
        FluidResource fluid = FluidResource.of(pumped);
        if (!isInfiniteWater(level, below, source) || !ArcforgeConfig.PUMP_INFINITE_WATER.getAsBoolean()) {
            BlockState state = level.getBlockState(below);
            if (!(state.getBlock() instanceof BucketPickup pickup) || pickup.pickupBlock(null, level, below, state).isEmpty()) {
                return;
            }
        }
        int pumpedAmount;
        try (Transaction tx = Transaction.openRoot()) {
            pumpedAmount = tank.insert(0, fluid, FluidType.BUCKET_VOLUME, tx);
            tx.commit();
        }
        // One bucket is one operation.
        controlState.completed(List.of(), List.of(new FluidStack(pumped, pumpedAmount)), 0, 0);
    }

    // Water with two or more water sources beside it refills itself, as with a bucket.
    public static boolean isInfiniteWater(ServerLevel level, BlockPos pos, FluidState fluid) {
        if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) {
            return false;
        }
        int sources = 0;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            FluidState beside = level.getFluidState(pos.relative(side));
            if (beside.is(FluidTags.WATER) && beside.isSource()) {
                sources++;
            }
        }
        return sources >= 2;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    // FE used last tick.
    public int getUsage() {
        return usage;
    }

    // What a bucket of the source below fills the tank with, or null for none (as of the last tick).
    public @Nullable Fluid getSourceFluid() {
        return sourceFluid < 0 ? null : BuiltInRegistries.FLUID.byId(sourceFluid);
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.OUTPUT ? itemAutomation : null;
    }

    // Fluid can be drawn out of the top and of output faces; nothing goes in.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.OUTPUT || side == Direction.UP ? fluidOutput : null;
    }

    // FE goes in on energy faces.
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case FLUID -> mode == SideMode.OUTPUT || side == Direction.UP ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ITEM -> mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        tank.deserialize(input.childOrEmpty("tank"));
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        tank.serialize(output.child("tank"));
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.electric_pump");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ElectricPumpMenu(containerId, inventory, worldPosition, items, data);
    }
}
