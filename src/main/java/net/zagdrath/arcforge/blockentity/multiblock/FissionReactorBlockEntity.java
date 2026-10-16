/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.nuclear.ControlRodChannelBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.FissionReactorMenu;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.multiblock.VesselStructures;
import net.zagdrath.arcforge.nuclear.FuelRod;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.nuclear.RadiationSource;
import net.zagdrath.arcforge.nuclear.reactor.CoreLattice;
import net.zagdrath.arcforge.nuclear.reactor.ReactorParams;
import net.zagdrath.arcforge.nuclear.reactor.ReactorSim;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Fission Reactor (see VesselStructures.REACTOR), run by its controller. Its physics is ReactorSim's; this block
// entity builds the core from the vessel (one lattice cell per block inside), keeps the rods that are in its channels,
// and does the Minecraft side:
//  - Coolant: Water or Heavy Water goes in through Input ports into the intake tank and fills the vessel. A core with a
//    Hot Leg port is pressurized: it pumps its coolant out of the Hot Leg as Hot Coolant (or Hot Heavy Water) for a
//    Steam Generator, which sends it back cooled. Without a Hot Leg but with Steam ports it boils: Contaminated Steam
//    out of the Steam ports, straight to turbines; it needs its coolant made up as it boils off.
//  - Rods: Fuel Rods and Control Rods put in through Input ports (or the GUI) wait in its nine input slots and load
//    into empty Fuel Channels and Control Rod Channels, only while it's shut down (every rod in, the power under 1%).
//    Spent and damaged rods come out into its nine output slots then (Output ports give them out), hot and radioactive,
//    as can every rod on request. A channel broken out of the vessel drops its rod.
//  - Control: the four rod banks are moved from the GUI, by redstone (a signal pushes every bank in at least that far,
//    when Redstone Rods is on), and through the machine control API (bank_a to bank_d, scram). The Redstone tab's mode
//    SCRAMs it when it says to stop; switching it off through the API drives every rod in.
//  - Protection (ReactorSim.Trips) is set in the GUI; a SCRAM button trips it by hand, Reset clears a trip.
//  - It's a radiation source while it runs and after (see RadiationSource): its walls and what's round them shield.
public class FissionReactorBlockEntity extends VesselControllerBlockEntity implements RadiationSource {
    public static final int INPUT_SLOTS = 9, OUTPUT_SLOTS = 9, MACHINE_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS;
    public static final int HISTORY = 60;
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.HOT_LEG, SideMode.STEAM);
    private static final Set<SideMode> PUSHED = EnumSet.of(SideMode.HOT_LEG, SideMode.STEAM);
    private static final int MOVE_INTERVAL = 5;

    // The rods in the channels, by the channel's position.
    private final Map<BlockPos, ItemStack> rods = new HashMap<>();
    private @Nullable ReactorSim sim;
    private BlockPos[] cellPos = new BlockPos[0];
    private long layoutKey;
    private boolean layoutDirty = true;
    // The core's state, kept for when the vessel is rebuilt (or unformed and formed again) and for saving.
    private final Saved saved = new Saved();
    private ReactorSim.Mode mode = ReactorSim.Mode.NONE;
    private boolean redstoneRods;
    private boolean autoUnload = true;
    private boolean unloadAll;
    private boolean damageMarked;
    private boolean wasCritical;

    private final FilteredFluidTank intake;
    private final FilteredFluidTank hotLeg;
    private final FilteredFluidTank steam;
    private final ResourceHandler<FluidResource> intakeInput;
    private final ResourceHandler<FluidResource> hotLegOutput;
    private final ResourceHandler<FluidResource> steamOutput;
    private final ResourceHandler<FluidResource> allFluids;
    private final ResourceHandler<ItemResource> rodInput;
    private final ResourceHandler<ItemResource> rodOutput;
    private final ContainerData data;
    // The trend graphs: a reading a second (power as log10 of the fraction, coolant °C, k), in a ring.
    private final int[][] history = new int[3][HISTORY];
    private int historyHead;

    public FissionReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FISSION_REACTOR.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> slot < INPUT_SLOTS && isLoadable(resource.toStack(1)),
                SIDE_MODES);
        int tank = 64_000;
        this.intake = new FilteredFluidTank(tank, FissionReactorBlockEntity::isCoolant, this::setChanged);
        this.hotLeg = new FilteredFluidTank(tank, resource -> resource.is(ModFluids.HOT_COOLANT.get()) || resource.is(ModFluids.HOT_HEAVY_WATER.get()),
                this::setChanged);
        this.steam = new FilteredFluidTank(tank * 4, resource -> resource.is(ModFluids.CONTAMINATED_STEAM.get()), this::setChanged);
        this.intakeInput = new SidedResourceHandler<>(intake, true, false, Integer.MAX_VALUE);
        this.hotLegOutput = new SidedResourceHandler<>(hotLeg, false, true, Integer.MAX_VALUE);
        this.steamOutput = new SidedResourceHandler<>(steam, false, true, Integer.MAX_VALUE);
        this.allFluids = new CombinedResourceHandler<>(intakeInput, hotLegOutput, steamOutput);
        this.rodInput = new AutomationResourceHandler<>(items, slot -> slot < INPUT_SLOTS, slot -> false);
        this.rodOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot >= INPUT_SLOTS && slot < MACHINE_SLOTS);
        this.data = new WideIntContainerData(FissionReactorMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return dataValue(index);
            }
        };
    }

    // What the core's state was, when there's no model running (unformed, or between rebuilds) and in the save.
    private static final class Saved {
        double power, precursors, fuel = ReactorSim.AMBIENT, clad = ReactorSim.AMBIENT, coolantTemp = ReactorSim.AMBIENT, coolant;
        final double[] decay = new double[3];
        final double[] insertion = { 1.0, 1.0, 1.0, 1.0 };
        final double[] target = { 1.0, 1.0, 1.0, 1.0 };
        final ReactorSim.Trips trips = new ReactorSim.Trips();
        boolean scrammed, coreDamaged;
        ReactorSim.Trip trip = ReactorSim.Trip.NONE;
        double pump = 1.0;
        CoreLattice.Coolant coolantType = CoreLattice.Coolant.LIGHT_WATER;
        final Map<BlockPos, double[]> xenon = new HashMap<>();
    }

    public static boolean isCoolant(FluidResource resource) {
        return resource.is(net.minecraft.world.level.material.Fluids.WATER) || resource.is(ModFluids.HEAVY_WATER.get());
    }

    // What goes in its input slots: Fuel Rods (not damaged ones) and Control Rods.
    public static boolean isLoadable(ItemStack stack) {
        if (stack.is(ModItems.CONTROL_ROD.get())) {
            return true;
        }
        FuelRod rod = stack.get(ModDataComponents.FUEL_ROD.get());
        return stack.is(ModItems.FUEL_ROD.get()) && (rod == null || !rod.damaged());
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> slot < INPUT_SLOTS, Set.of(), () -> {});
    }

    @Override
    public VesselStructure structure() {
        return VesselStructures.REACTOR;
    }

    public static ReactorParams params() {
        ReactorParams p = ReactorParams.defaults();
        p.channelHeat = ArcforgeConfig.REACTOR_CHANNEL_HEAT.getAsDouble();
        p.hotCoolantHeat = ArcforgeConfig.REACTOR_HOT_COOLANT_HEAT.getAsDouble();
        p.boilingHeat = ArcforgeConfig.REACTOR_BOILING_HEAT.getAsDouble();
        p.coolantPerBlock = ArcforgeConfig.REACTOR_COOLANT_PER_BLOCK.getAsDouble();
        p.vesselCapacity = ArcforgeConfig.REACTOR_VESSEL_HEAT_CAPACITY.getAsDouble();
        p.rodLifeTicks = ArcforgeConfig.REACTOR_ROD_LIFE.getAsDouble();
        p.rodSpeed = ArcforgeConfig.REACTOR_ROD_SPEED.getAsDouble();
        p.scramTicks = ArcforgeConfig.REACTOR_SCRAM_TICKS.getAsInt();
        p.iodineHalfLife = ArcforgeConfig.REACTOR_IODINE_HALF_LIFE.getAsDouble();
        p.xenonHalfLife = ArcforgeConfig.REACTOR_XENON_HALF_LIFE.getAsDouble();
        p.decayFraction = ArcforgeConfig.REACTOR_DECAY_FRACTION.getAsDouble();
        double decay = ArcforgeConfig.REACTOR_DECAY_TIME.getAsDouble();
        p.decayTicks = new double[] { decay, decay * 10.0, decay * 60.0 };
        p.generationTime = ArcforgeConfig.REACTOR_PROMPT_GENERATION.getAsDouble();
        p.cladFail = ArcforgeConfig.REACTOR_CLAD_FAILURE.getAsDouble();
        p.fuelMelt = ArcforgeConfig.REACTOR_FUEL_MELT.getAsDouble();
        return p;
    }

    // --- The core from the vessel ---

    @Override
    protected void onShapeChanged(ServerLevel level) {
        layoutDirty = true;
    }

    @Override
    public void requestCheck() {
        super.requestCheck();
        layoutDirty = true;
    }

    // What each cell inside is, and its bank; and whether it has its rod.
    private CoreLattice.Cell cellOf(BlockState state, BlockPos pos) {
        if (state.is(ModBlocks.FUEL_CHANNEL.get())) {
            ItemStack rod = rods.get(pos);
            return rod != null && rod.is(ModItems.FUEL_ROD.get()) && !FuelRod.of(rod).damaged() ? CoreLattice.Cell.FUEL : CoreLattice.Cell.COOLANT;
        }
        if (state.is(ModBlocks.CONTROL_ROD_CHANNEL.get())) {
            ItemStack rod = rods.get(pos);
            return rod != null && rod.is(ModItems.CONTROL_ROD.get()) ? CoreLattice.Cell.CONTROL : CoreLattice.Cell.COOLANT;
        }
        if (state.is(ModBlocks.GRAPHITE_MODERATOR.get())) {
            return CoreLattice.Cell.GRAPHITE;
        }
        if (state.is(ModBlocks.STEEL_REFLECTOR.get())) {
            return CoreLattice.Cell.STEEL;
        }
        return CoreLattice.Cell.COOLANT;
    }

    // Rods whose channel has gone (broken out, or the vessel rebuilt round something else) drop where it was.
    private void dropOrphans(ServerLevel level) {
        List<BlockPos> gone = new ArrayList<>();
        for (Map.Entry<BlockPos, ItemStack> entry : rods.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            boolean fits = entry.getValue().is(ModItems.FUEL_ROD.get()) ? state.is(ModBlocks.FUEL_CHANNEL.get())
                    : state.is(ModBlocks.CONTROL_ROD_CHANNEL.get());
            if (!fits) {
                gone.add(pos);
            }
        }
        if (!gone.isEmpty()) {
            writeBack(level.getGameTime());
            for (BlockPos pos : gone) {
                Block.popResource(level, pos, rods.remove(pos));
            }
            layoutDirty = true;
            setChanged();
        }
    }

    // Builds the core again when the vessel or what's in it changed, carrying its state over.
    private void ensureCore(ServerLevel level) {
        if (layoutDirty || level.getGameTime() % 20 == 0) {
            dropOrphans(level);
        }
        if (box == null) {
            if (sim != null) {
                keep(level.getGameTime());
                sim = null;
            }
            return;
        }
        if (!layoutDirty && sim != null) {
            return;
        }
        layoutDirty = false;
        int nx = box.size(Direction.Axis.X) - 2, ny = box.size(Direction.Axis.Y) - 2, nz = box.size(Direction.Axis.Z) - 2;
        CoreLattice lattice = new CoreLattice(nx, ny, nz);
        BlockPos[] positions = new BlockPos[lattice.size];
        long key = 1469598103934665603L;
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    BlockPos pos = box.min().offset(x + 1, y + 1, z + 1);
                    BlockState state = level.getBlockState(pos);
                    CoreLattice.Cell cell = cellOf(state, pos);
                    int bank = state.is(ModBlocks.CONTROL_ROD_CHANNEL.get()) ? state.getValue(ControlRodChannelBlock.BANK) : -1;
                    int i = lattice.index(x, y, z);
                    lattice.set(i, cell, bank);
                    positions[i] = pos;
                    key = (key ^ (cell.ordinal() * 8 + bank + 1)) * 1099511628211L;
                    ItemStack rod = rods.get(pos);
                    key = (key ^ (rod != null ? System.identityHashCode(rod) : 0)) * 1099511628211L;
                }
            }
        }
        key ^= box.min().asLong() * 31 + box.max().asLong();
        if (sim != null && key == layoutKey) {
            return;
        }
        if (sim != null) {
            keep(level.getGameTime());
        }
        layoutKey = key;
        cellPos = positions;
        ReactorSim next = new ReactorSim(params(), lattice);
        for (int cell : next.fuelCells()) {
            next.setRod(cell, FuelRod.of(rods.get(positions[cell])).toSim());
        }
        restore(next);
        sim = next;
    }

    // Copies the running model's state into saved (and its rods' progress into the rod items).
    private void keep(long now) {
        if (sim == null) {
            return;
        }
        writeBack(now);
        saved.power = sim.power;
        saved.precursors = sim.precursors();
        saved.fuel = sim.fuelTemp;
        saved.clad = sim.cladTemp;
        saved.coolantTemp = sim.coolantTemp;
        saved.coolant = sim.coolant;
        System.arraycopy(sim.decay, 0, saved.decay, 0, 3);
        System.arraycopy(sim.insertion, 0, saved.insertion, 0, 4);
        System.arraycopy(sim.target, 0, saved.target, 0, 4);
        copyTrips(sim.trips, saved.trips);
        saved.scrammed = sim.scrammed;
        saved.trip = sim.trip;
        saved.coreDamaged = sim.coreDamaged;
        saved.pump = sim.pumpSetting;
        saved.coolantType = sim.coolantType;
        saved.xenon.clear();
        for (int cell : sim.fuelCells()) {
            if (sim.rod(cell) != null) {
                saved.xenon.put(cellPos[cell], new double[] { sim.iodine(cell), sim.lattice.xenon(cell) });
            }
        }
    }

    private void restore(ReactorSim next) {
        next.restore(saved.power, saved.precursors, saved.fuel, saved.clad, saved.coolantTemp, saved.coolant, saved.decay);
        System.arraycopy(saved.insertion, 0, next.insertion, 0, 4);
        System.arraycopy(saved.target, 0, next.target, 0, 4);
        copyTrips(saved.trips, next.trips);
        next.scrammed = saved.scrammed;
        next.trip = saved.trip;
        next.coreDamaged = saved.coreDamaged;
        next.pumpSetting = saved.pump;
        next.coolantType = saved.coolantType;
        next.mode = mode;
        for (int cell : next.fuelCells()) {
            double[] xe = saved.xenon.get(cellPos[cell]);
            if (xe != null) {
                next.restoreCell(cell, xe[0], xe[1]);
            }
        }
        if (next.precursors() <= 0.0 && next.power > 0.0) {
            next.settlePrecursors();
        }
    }

    private static void copyTrips(ReactorSim.Trips from, ReactorSim.Trips to) {
        to.highPower = from.highPower;
        to.highPowerPercent = from.highPowerPercent;
        to.shortPeriod = from.shortPeriod;
        to.shortPeriodSeconds = from.shortPeriodSeconds;
        to.highFuelTemperature = from.highFuelTemperature;
        to.highFuelCelsius = from.highFuelCelsius;
        to.highPressure = from.highPressure;
        to.highPressureMargin = from.highPressureMargin;
        to.lowFlow = from.lowFlow;
        to.lowFlowPercent = from.lowFlowPercent;
    }

    // The model's rods' burnup back into the rod items.
    private void writeBack(long now) {
        if (sim == null) {
            return;
        }
        for (int cell : sim.fuelCells()) {
            ReactorSim.Rod rod = sim.rod(cell);
            ItemStack stack = rods.get(cellPos[cell]);
            if (rod != null && stack != null && stack.is(ModItems.FUEL_ROD.get())) {
                FuelRod was = FuelRod.of(stack);
                stack.set(ModDataComponents.FUEL_ROD.get(), FuelRod.fromSim(was, rod, was.dischargeHeat(), was.discharged(), sim.coreDamaged));
            }
        }
    }

    // --- Running ---

    @Override
    protected void idle(ServerLevel level) {
        super.idle(level);
        ensureCore(level);
        status = MachineStatus.NOT_FORMED;
    }

    @Override
    protected void work(ServerLevel level) {
        ensureCore(level);
        ReactorSim core = sim;
        if (core == null) {
            return;
        }
        long now = level.getGameTime();
        if (now % 20 == 0) {
            mode = modeFromPorts(level);
        }
        core.mode = mode;
        control(core);
        takeCoolant(core);
        Fluid hotFluid = core.coolantType == CoreLattice.Coolant.HEAVY_WATER ? ModFluids.HOT_HEAVY_WATER.get() : ModFluids.HOT_COOLANT.get();
        double hotRoom = hotLeg.getAmount() > 0 && !hotLeg.getResource(0).is(hotFluid) ? 0.0 : hotLeg.getSpace();
        core.tick(hotRoom, steam.getSpace());
        add(hotLeg, FluidResource.of(hotFluid), core.hotLeg);
        add(steam, FluidResource.of(ModFluids.CONTAMINATED_STEAM.get()), core.steamMade);
        MultiblockAutomation.pushFluids(level, this, PUSHED, 64_000);
        MultiblockAutomation.pushOutputs(level, this);
        if (core.coreDamaged && !damageMarked) {
            damageMarked = true;
            writeBack(now);
            for (ItemStack stack : rods.values()) {
                if (stack.is(ModItems.FUEL_ROD.get())) {
                    FuelRod rod = FuelRod.of(stack);
                    stack.set(ModDataComponents.FUEL_ROD.get(), new FuelRod(rod.type(), rod.enrichment(), rod.burnup(), rod.plutonium(), rod.fissionProducts(),
                            rod.dischargeHeat(), rod.discharged(), true));
                }
            }
        }
        if (now % MOVE_INTERVAL == 0) {
            moveRods(level, core);
        }
        if (now % 20 == 0) {
            record(core);
            setChanged();
        }
        status = statusOf(core);
        boolean critical = core.power > 0.01;
        setLit(critical);
        if (critical && !wasCritical) {
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.HOT_COOLANT.get(), "critical");
        }
        if (core.power >= 0.95 && now % 20 == 0) {
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.HOT_COOLANT.get(), "full_power");
        }
        wasCritical = critical;
    }

    private MachineStatus statusOf(ReactorSim core) {
        if (core.coreDamaged) {
            return MachineStatus.CORE_DAMAGED;
        }
        if (core.scrammed) {
            return MachineStatus.SCRAMMED;
        }
        if (core.loadedRods() == 0) {
            return MachineStatus.NO_FUEL;
        }
        if (core.level() < 0.5) {
            return MachineStatus.NO_WATER;
        }
        return core.power > 0.01 ? MachineStatus.CRITICAL : MachineStatus.SUBCRITICAL;
    }

    // Pressurized with a Hot Leg port, boiling with only Steam ports.
    private ReactorSim.Mode modeFromPorts(ServerLevel level) {
        boolean hot = false, boiling = false;
        for (MultiblockPorts.Port port : MultiblockPorts.list(level, this)) {
            hot |= port.mode() == SideMode.HOT_LEG;
            boiling |= port.mode() == SideMode.STEAM;
        }
        return hot ? ReactorSim.Mode.PRESSURIZED : boiling ? ReactorSim.Mode.BOILING : ReactorSim.Mode.NONE;
    }

    // Redstone and the control API: the Redstone tab's stop SCRAMs it, switching it off drives the rods in, and with
    // Redstone Rods a signal holds every bank at least that far in.
    private void control(ReactorSim core) {
        if (level != null && !redstoneMode.canRun(redstone > 0) && !core.scrammed) {
            core.scram(ReactorSim.Trip.REDSTONE);
        }
        if (!controlState.isEnabled()) {
            for (int b = 0; b < ReactorSim.BANKS; b++) {
                core.target[b] = 1.0;
            }
        }
        core.redstoneFloor = redstoneRods ? redstone / 15.0 : 0.0;
    }

    // Coolant from the intake into the vessel: one kind at a time (it changes only once the vessel has drained).
    private void takeCoolant(ReactorSim core) {
        if (intake.getAmount() <= 0) {
            return;
        }
        CoreLattice.Coolant kind = intake.getResource(0).is(ModFluids.HEAVY_WATER.get()) ? CoreLattice.Coolant.HEAVY_WATER : CoreLattice.Coolant.LIGHT_WATER;
        if (core.coolant >= 1.0 && kind != core.coolantType) {
            return;
        }
        core.coolantType = kind;
        int room = (int) Math.floor(core.coolantCapacity - core.coolant);
        int moved = Math.min(room, intake.getAmount());
        if (moved > 0) {
            core.coolant += moved;
            int left = intake.getAmount() - moved;
            intake.set(0, left > 0 ? intake.getResource(0) : FluidResource.EMPTY, left);
        }
    }

    private static void add(FilteredFluidTank tank, FluidResource resource, double amount) {
        int whole = (int) Math.floor(amount);
        if (whole > 0) {
            tank.set(0, resource, tank.getAmount() + whole);
        }
    }

    // Loading and unloading rods, a rod each time, only while shut down.
    private void moveRods(ServerLevel level, ReactorSim core) {
        if (!canRefuel()) {
            return;
        }
        long now = level.getGameTime();
        // Out first: spent and damaged rods (with auto-unload), or every rod when asked.
        for (int cell : core.fuelCells()) {
            ItemStack stack = rods.get(cellPos[cell]);
            if (stack == null) {
                continue;
            }
            FuelRod rod = FuelRod.of(stack);
            ReactorSim.Rod live = core.rod(cell);
            boolean spent = rod.damaged() || live != null && live.isSpent() || rod.isSpent();
            if (unloadAll || autoUnload && spent) {
                if (unload(cellPos[cell], stack, live, core, now)) {
                    return;
                }
                break;
            }
        }
        // A damaged rod is no longer part of the core (its channel counts as coolant), so it's found by its channel.
        if (autoUnload) {
            for (Map.Entry<BlockPos, ItemStack> entry : List.copyOf(rods.entrySet())) {
                ItemStack stack = entry.getValue();
                if (stack.is(ModItems.FUEL_ROD.get()) && FuelRod.of(stack).damaged()) {
                    if (unload(entry.getKey(), stack, null, core, now)) {
                        return;
                    }
                    break;
                }
            }
        }
        if (unloadAll) {
            for (int cell : core.controlCells()) {
                ItemStack stack = rods.get(cellPos[cell]);
                if (stack != null && unload(cellPos[cell], stack, null, core, now)) {
                    return;
                }
            }
            for (BlockPos pos : List.copyOf(rods.keySet())) {
                if (rods.get(pos) != null && unload(pos, rods.get(pos), null, core, now)) {
                    return;
                }
            }
            unloadAll = false;
        }
        // In: the first rod waiting goes into the first empty channel for it.
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack waiting = items.getStack(slot);
            if (waiting.isEmpty() || !isLoadable(waiting)) {
                continue;
            }
            boolean fuel = waiting.is(ModItems.FUEL_ROD.get());
            Block channel = fuel ? ModBlocks.FUEL_CHANNEL.get() : ModBlocks.CONTROL_ROD_CHANNEL.get();
            for (BlockPos pos : cellPos) {
                if (pos != null && !rods.containsKey(pos) && level.getBlockState(pos).is(channel)) {
                    ItemStack one = waiting.copyWithCount(1);
                    if (fuel && !one.has(ModDataComponents.FUEL_ROD.get())) {
                        one.set(ModDataComponents.FUEL_ROD.get(), FuelRod.fresh(net.zagdrath.arcforge.nuclear.Enrichment.NATURAL_PCM));
                    }
                    rods.put(pos, one);
                    items.setStack(slot, waiting.copyWithCount(waiting.getCount() - 1));
                    layoutDirty = true;
                    setChanged();
                    return;
                }
            }
        }
    }

    // A rod out of its channel into the first free output slot, with its share of the decay heat if it's fuel.
    private boolean unload(BlockPos pos, ItemStack stack, ReactorSim.@Nullable Rod live, ReactorSim core, long now) {
        int slot = freeOutput(stack);
        if (slot < 0) {
            return false;
        }
        ItemStack out = stack.copy();
        if (live != null && stack.is(ModItems.FUEL_ROD.get())) {
            double total = 0.0;
            for (int cell : core.fuelCells()) {
                ReactorSim.Rod other = core.rod(cell);
                total += other != null ? other.power + 1.0E-6 : 0.0;
            }
            double share = total > 0.0 ? (live.power + 1.0E-6) / total : 0.0;
            FuelRod was = FuelRod.of(stack);
            out.set(ModDataComponents.FUEL_ROD.get(), FuelRod.fromSim(was, live, core.decayPower * share, now, core.coreDamaged));
        }
        rods.remove(pos);
        ItemStack current = items.getStack(slot);
        items.setStack(slot, current.isEmpty() ? out : current.copyWithCount(current.getCount() + 1));
        layoutDirty = true;
        setChanged();
        return true;
    }

    private int freeOutput(ItemStack stack) {
        for (int slot = INPUT_SLOTS; slot < MACHINE_SLOTS; slot++) {
            ItemStack current = items.getStack(slot);
            if (current.isEmpty() || ItemStack.isSameItemSameComponents(current, stack) && current.getCount() < current.getMaxStackSize()) {
                return slot;
            }
        }
        return -1;
    }

    // Rods move only with the core shut down: every rod in and the power under 1% (decay heat aside).
    public boolean canRefuel() {
        return sim == null || sim.isShutDown();
    }

    private void record(ReactorSim core) {
        historyHead = (historyHead + 1) % HISTORY;
        history[0][historyHead] = (int) Math.round(Math.log10(Math.max(core.power, 1.0E-9)) * 1000.0);
        history[1][historyHead] = (int) Math.round(core.coolantTemp * 10.0);
        history[2][historyHead] = (int) Math.round(core.k * 100_000.0);
    }

    // --- Commands (the GUI, redstone, the control API) ---

    public @Nullable ReactorSim sim() {
        return sim;
    }

    public void moveBank(int bank, double by) {
        if (sim != null && bank >= 0 && bank < ReactorSim.BANKS) {
            sim.target[bank] = Mth.clamp(sim.target[bank] + by, 0.0, 1.0);
            setChanged();
        } else if (bank >= 0 && bank < ReactorSim.BANKS) {
            saved.target[bank] = Mth.clamp(saved.target[bank] + by, 0.0, 1.0);
        }
    }

    // A bank's target insertion, 0 out to 100 in.
    public int getBankTarget(int bank) {
        double[] targets = sim != null ? sim.target : saved.target;
        return (int) Math.round(targets[bank] * 100.0);
    }

    public void setBankTarget(int bank, int percent) {
        if (sim != null) {
            sim.target[bank] = Mth.clamp(percent / 100.0, 0.0, 1.0);
        } else {
            saved.target[bank] = Mth.clamp(percent / 100.0, 0.0, 1.0);
        }
        setChanged();
    }

    public int getBankInsertion(int bank) {
        double[] insertion = sim != null ? sim.insertion : saved.insertion;
        return (int) Math.round(insertion[bank] * 100.0);
    }

    public void scram() {
        if (sim != null) {
            sim.scram(ReactorSim.Trip.MANUAL);
        } else {
            saved.scrammed = true;
            saved.trip = ReactorSim.Trip.MANUAL;
            java.util.Arrays.fill(saved.target, 1.0);
        }
        setChanged();
    }

    public boolean isScrammed() {
        return sim != null ? sim.scrammed : saved.scrammed;
    }

    public void resetTrip() {
        if (sim != null) {
            sim.reset();
        } else if (!saved.coreDamaged) {
            saved.scrammed = false;
            saved.trip = ReactorSim.Trip.NONE;
        }
        setChanged();
    }

    public ReactorSim.Trips trips() {
        return sim != null ? sim.trips : saved.trips;
    }

    public void toggleTrip(int which) {
        ReactorSim.Trips trips = trips();
        switch (which) {
            case 0 -> trips.highPower = !trips.highPower;
            case 1 -> trips.shortPeriod = !trips.shortPeriod;
            case 2 -> trips.highFuelTemperature = !trips.highFuelTemperature;
            case 3 -> trips.highPressure = !trips.highPressure;
            default -> trips.lowFlow = !trips.lowFlow;
        }
        setChanged();
    }

    public void stepTrip(int which, boolean up) {
        ReactorSim.Trips trips = trips();
        int sign = up ? 1 : -1;
        switch (which) {
            case 0 -> trips.highPowerPercent = Mth.clamp(trips.highPowerPercent + 5 * sign, 5, 150);
            case 1 -> trips.shortPeriodSeconds = Mth.clamp(trips.shortPeriodSeconds + sign, 1, 60);
            case 2 -> trips.highFuelCelsius = Mth.clamp(trips.highFuelCelsius + 100 * sign, 300, 2_400);
            case 3 -> trips.highPressureMargin = Mth.clamp(trips.highPressureMargin + sign, 1, 40);
            default -> trips.lowFlowPercent = Mth.clamp(trips.lowFlowPercent + 5 * sign, 5, 95);
        }
        setChanged();
    }

    public void stepPump(boolean up) {
        double pump = sim != null ? sim.pumpSetting : saved.pump;
        pump = Mth.clamp(pump + (up ? 0.1 : -0.1), 0.1, 1.0);
        if (sim != null) {
            sim.pumpSetting = pump;
        }
        saved.pump = pump;
        setChanged();
    }

    public void toggleRedstoneRods() {
        redstoneRods = !redstoneRods;
        setChanged();
    }

    public void toggleAutoUnload() {
        autoUnload = !autoUnload;
        setChanged();
    }

    public void unloadAll() {
        unloadAll = true;
        setChanged();
    }

    public int loadedFuel() {
        int count = 0;
        for (ItemStack stack : rods.values()) {
            count += stack.is(ModItems.FUEL_ROD.get()) ? 1 : 0;
        }
        return count;
    }

    public Map<BlockPos, ItemStack> rods() {
        return rods;
    }

    public FilteredFluidTank getIntake() {
        return intake;
    }

    public FilteredFluidTank getHotLeg() {
        return hotLeg;
    }

    public FilteredFluidTank getSteam() {
        return steam;
    }

    public ReactorSim.Mode getMode() {
        return mode;
    }

    // Thermal power (HU/t) now: fission and decay heat.
    public double thermalPower() {
        return sim != null ? sim.thermalPower : 0.0;
    }

    // --- Radiation ---

    @Override
    public double radiationRate() {
        double rate = 0.0;
        if (sim != null) {
            double rated = Math.max(1.0, sim.ratedPower());
            rate += ArcforgeConfig.REACTOR_RUNNING_DOSE.getAsDouble() * (sim.power + sim.decayPower / rated);
        }
        for (ItemStack stack : rods.values()) {
            if (stack.is(ModItems.FUEL_ROD.get())) {
                rate += FuelRod.of(stack).doseRate();
            }
        }
        // What waits in its slots (spent rods above all) and its tanks, as any other container's would count.
        for (int slot = 0; slot < MACHINE_SLOTS; slot++) {
            rate += RadiationField.rate(items.getStack(slot));
        }
        for (FilteredFluidTank tank : List.of(intake, hotLeg, steam)) {
            if (tank.getAmount() > 0) {
                rate += RadiationField.rate(tank.getResource(0).toStack(tank.getAmount()));
            }
        }
        return rate;
    }

    @Override
    public Vec3 radiationCentre() {
        return box != null ? box.aabb().getCenter() : Vec3.atCenterOf(worldPosition);
    }

    // --- Capabilities ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        if (box == null) {
            return null;
        }
        if (mode == null) {
            return new CombinedResourceHandler<>(rodInput, rodOutput);
        }
        return switch (mode) {
            case INPUT -> rodInput;
            case OUTPUT -> rodOutput;
            default -> null;
        };
    }

    // Input faces take coolant in; Hot Leg faces give Hot Coolant, Steam faces Contaminated Steam.
    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (box == null) {
            return null;
        }
        if (mode == null) {
            return allFluids;
        }
        return switch (mode) {
            case INPUT -> intakeInput;
            case HOT_LEG -> hotLegOutput;
            case STEAM -> steamOutput;
            default -> null;
        };
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.HOT_LEG ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case GAS -> mode == SideMode.STEAM ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            default -> ConnectionMode.NONE;
        };
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    // Breaking the controller drops every rod in the core, as well as its slots.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel serverLevel) {
            writeBack(serverLevel.getGameTime());
            for (Map.Entry<BlockPos, ItemStack> entry : rods.entrySet()) {
                Block.popResource(serverLevel, entry.getKey(), entry.getValue());
            }
            rods.clear();
        }
        super.preRemoveSideEffects(pos, state);
    }

    // --- The GUI ---

    private int dataValue(int index) {
        ReactorSim core = sim;
        if (index >= FissionReactorMenu.DATA_HISTORY && index < FissionReactorMenu.DATA_HISTORY + 3 * HISTORY) {
            int i = index - FissionReactorMenu.DATA_HISTORY;
            return history[i / HISTORY][i % HISTORY];
        }
        ReactorSim.Trips trips = trips();
        return switch (index) {
            case FissionReactorMenu.DATA_STATUS -> status.ordinal();
            case FissionReactorMenu.DATA_FORMED -> box != null ? 1 : 0;
            case FissionReactorMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
            case FissionReactorMenu.DATA_SIDE_CONFIG -> packedSides();
            case FissionReactorMenu.DATA_THERMAL -> core != null ? (int) Math.min(Integer.MAX_VALUE, core.thermalPower) : 0;
            case FissionReactorMenu.DATA_POWER_LOG -> core != null ? (int) Math.round(Math.log10(Math.max(core.power, 1.0E-9)) * 1000.0) : -9000;
            case FissionReactorMenu.DATA_K -> core != null ? (int) Math.round(core.k * 100_000.0) : 0;
            case FissionReactorMenu.DATA_RHO -> core != null ? (int) Math.round(core.rho * 1.0E6) : 0;
            case FissionReactorMenu.DATA_PERIOD -> core == null || Double.isInfinite(core.period) || Math.abs(core.period) > 100_000 ? 0
                    : (int) Math.round(core.period * 10.0);
            case FissionReactorMenu.DATA_INSERTION, FissionReactorMenu.DATA_INSERTION + 1, FissionReactorMenu.DATA_INSERTION + 2,
                    FissionReactorMenu.DATA_INSERTION + 3 -> (int) Math.round((core != null ? core.insertion : saved.insertion)[index - FissionReactorMenu.DATA_INSERTION] * 1000.0);
            case FissionReactorMenu.DATA_TARGET, FissionReactorMenu.DATA_TARGET + 1, FissionReactorMenu.DATA_TARGET + 2,
                    FissionReactorMenu.DATA_TARGET + 3 -> (int) Math.round((core != null ? core.target : saved.target)[index - FissionReactorMenu.DATA_TARGET] * 1000.0);
            case FissionReactorMenu.DATA_FUEL_TEMP -> core != null ? (int) Math.round(core.hottestFuel() * 10.0) : (int) (saved.fuel * 10.0);
            case FissionReactorMenu.DATA_CLAD_TEMP -> core != null ? (int) Math.round(core.hottestCladding() * 10.0) : (int) (saved.clad * 10.0);
            case FissionReactorMenu.DATA_COOLANT_TEMP -> core != null ? (int) Math.round(core.coolantTemp * 10.0) : (int) (saved.coolantTemp * 10.0);
            case FissionReactorMenu.DATA_PRESSURE -> core != null ? (int) Math.round(core.pressure * 10.0) : 10;
            case FissionReactorMenu.DATA_XENON -> core != null ? (int) Math.round(core.meanXenon * 1000.0) : 0;
            case FissionReactorMenu.DATA_DECAY -> core != null ? (int) Math.round(core.decayPower) : 0;
            case FissionReactorMenu.DATA_COOLANT -> core != null ? (int) core.coolant : (int) saved.coolant;
            case FissionReactorMenu.DATA_COOLANT_CAPACITY -> core != null ? (int) core.coolantCapacity : 0;
            case FissionReactorMenu.DATA_COOLANT_TYPE -> (core != null ? core.coolantType : saved.coolantType).ordinal();
            case FissionReactorMenu.DATA_MODE -> mode.ordinal();
            case FissionReactorMenu.DATA_TRIP -> (core != null ? core.trip : saved.trip).ordinal();
            case FissionReactorMenu.DATA_SCRAMMED -> isScrammed() ? 1 : 0;
            case FissionReactorMenu.DATA_FLAGS -> (trips.highPower ? 1 : 0) | (trips.shortPeriod ? 2 : 0) | (trips.highFuelTemperature ? 4 : 0)
                    | (trips.highPressure ? 8 : 0) | (trips.lowFlow ? 16 : 0) | (redstoneRods ? 32 : 0) | (autoUnload ? 64 : 0) | (unloadAll ? 128 : 0)
                    | (canRefuel() ? 256 : 0) | (core != null && core.coreDamaged ? 512 : 0);
            case FissionReactorMenu.DATA_TRIP_POWER -> (int) trips.highPowerPercent;
            case FissionReactorMenu.DATA_TRIP_PERIOD -> (int) trips.shortPeriodSeconds;
            case FissionReactorMenu.DATA_TRIP_FUEL -> (int) trips.highFuelCelsius;
            case FissionReactorMenu.DATA_TRIP_PRESSURE -> (int) Math.round(trips.highPressureMargin + (core != null ? core.operatingPressure() : 0.0));
            case FissionReactorMenu.DATA_TRIP_FLOW -> (int) trips.lowFlowPercent;
            case FissionReactorMenu.DATA_RODS -> core != null ? core.loadedRods() : loadedFuel();
            case FissionReactorMenu.DATA_CHANNELS -> core != null ? core.fuelChannels() : 0;
            case FissionReactorMenu.DATA_CONTROL_RODS -> core != null ? core.controlCells().length : 0;
            case FissionReactorMenu.DATA_BURNUP -> meanBurnup(core);
            case FissionReactorMenu.DATA_DOPPLER -> core != null ? (int) Math.round(core.dopplerCoefficient * 1.0E7) : 0;
            case FissionReactorMenu.DATA_VOID -> core != null ? (int) Math.round(core.voidCoefficient * 1.0E5) : 0;
            case FissionReactorMenu.DATA_FLOW -> core != null ? (int) Math.round(core.flowRatio * 1000.0) : 0;
            case FissionReactorMenu.DATA_PUMP -> (int) Math.round((core != null ? core.pumpSetting : saved.pump) * 100.0);
            case FissionReactorMenu.DATA_HOT_LEG -> hotLeg.getAmount();
            case FissionReactorMenu.DATA_STEAM -> steam.getAmount();
            case FissionReactorMenu.DATA_INTAKE -> intake.getAmount();
            case FissionReactorMenu.DATA_RATED -> core != null ? (int) Math.round(core.ratedPower()) : 0;
            case FissionReactorMenu.DATA_PEAKING -> core != null ? (int) Math.round(core.peaking * 100.0) : 100;
            case FissionReactorMenu.DATA_HISTORY_HEAD -> historyHead;
            case FissionReactorMenu.DATA_VOID_FRACTION -> core != null ? (int) Math.round(core.coreVoid * 1000.0) : 0;
            default -> 0;
        };
    }

    private int meanBurnup(@Nullable ReactorSim core) {
        if (core == null) {
            return 0;
        }
        double sum = 0.0;
        int count = 0;
        for (int cell : core.fuelCells()) {
            ReactorSim.Rod rod = core.rod(cell);
            if (rod != null) {
                sum += rod.burnup;
                count++;
            }
        }
        return count > 0 ? (int) Math.round(sum / count * 1000.0) : 0;
    }

    @Override
    public Component getDisplayName() {
        return box != null ? Component.translatable("container.arcforge.fission_reactor.sized", box.sizeText())
                : Component.translatable("container.arcforge.fission_reactor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FissionReactorMenu(containerId, inventory, worldPosition, items, data);
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        rods.clear();
        in.read("rods", RodEntry.CODEC.listOf()).ifPresent(list -> list.forEach(entry -> rods.put(BlockPos.of(entry.pos()), entry.item())));
        saved.power = in.getDoubleOr("power", 0.0);
        saved.precursors = in.getDoubleOr("precursors", 0.0);
        saved.fuel = in.getDoubleOr("fuel_temp", ReactorSim.AMBIENT);
        saved.clad = in.getDoubleOr("clad_temp", ReactorSim.AMBIENT);
        saved.coolantTemp = in.getDoubleOr("coolant_temp", ReactorSim.AMBIENT);
        saved.coolant = in.getDoubleOr("coolant", 0.0);
        for (int g = 0; g < 3; g++) {
            saved.decay[g] = in.getDoubleOr("decay_" + g, 0.0);
        }
        for (int b = 0; b < ReactorSim.BANKS; b++) {
            saved.insertion[b] = in.getDoubleOr("insertion_" + b, 1.0);
            saved.target[b] = in.getDoubleOr("target_" + b, 1.0);
        }
        ReactorSim.Trips trips = saved.trips;
        trips.highPower = in.getBooleanOr("trip_power", true);
        trips.highPowerPercent = in.getDoubleOr("trip_power_at", 110.0);
        trips.shortPeriod = in.getBooleanOr("trip_period", true);
        trips.shortPeriodSeconds = in.getDoubleOr("trip_period_at", 10.0);
        trips.highFuelTemperature = in.getBooleanOr("trip_fuel", true);
        trips.highFuelCelsius = in.getDoubleOr("trip_fuel_at", 1_800.0);
        trips.highPressure = in.getBooleanOr("trip_pressure", true);
        trips.highPressureMargin = in.getDoubleOr("trip_pressure_margin", 10.0);
        trips.lowFlow = in.getBooleanOr("trip_flow", true);
        trips.lowFlowPercent = in.getDoubleOr("trip_flow_at", 50.0);
        saved.scrammed = in.getBooleanOr("scrammed", false);
        saved.trip = ReactorSim.Trip.values()[Mth.clamp(in.getIntOr("trip", 0), 0, ReactorSim.Trip.values().length - 1)];
        saved.coreDamaged = in.getBooleanOr("core_damaged", false);
        saved.pump = in.getDoubleOr("pump", 1.0);
        saved.coolantType = in.getIntOr("coolant_type", 0) == 1 ? CoreLattice.Coolant.HEAVY_WATER : CoreLattice.Coolant.LIGHT_WATER;
        saved.xenon.clear();
        in.read("xenon", XenonEntry.CODEC.listOf()).ifPresent(list -> list.forEach(entry -> saved.xenon.put(BlockPos.of(entry.pos()),
                new double[] { entry.iodine(), entry.xenon() })));
        mode = ReactorSim.Mode.values()[Mth.clamp(in.getIntOr("mode", 0), 0, ReactorSim.Mode.values().length - 1)];
        redstoneRods = in.getBooleanOr("redstone_rods", false);
        autoUnload = in.getBooleanOr("auto_unload", true);
        damageMarked = saved.coreDamaged;
        intake.deserialize(in.childOrEmpty("intake"));
        hotLeg.deserialize(in.childOrEmpty("hot_leg"));
        steam.deserialize(in.childOrEmpty("steam"));
        sim = null;
        layoutDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (sim != null && level != null) {
            keep(level.getGameTime());
        }
        List<RodEntry> rodList = new ArrayList<>();
        rods.forEach((pos, stack) -> rodList.add(new RodEntry(pos.asLong(), stack)));
        out.store("rods", RodEntry.CODEC.listOf(), rodList);
        out.putDouble("power", saved.power);
        out.putDouble("precursors", saved.precursors);
        out.putDouble("fuel_temp", saved.fuel);
        out.putDouble("clad_temp", saved.clad);
        out.putDouble("coolant_temp", saved.coolantTemp);
        out.putDouble("coolant", saved.coolant);
        for (int g = 0; g < 3; g++) {
            out.putDouble("decay_" + g, saved.decay[g]);
        }
        for (int b = 0; b < ReactorSim.BANKS; b++) {
            out.putDouble("insertion_" + b, saved.insertion[b]);
            out.putDouble("target_" + b, saved.target[b]);
        }
        ReactorSim.Trips trips = saved.trips;
        out.putBoolean("trip_power", trips.highPower);
        out.putDouble("trip_power_at", trips.highPowerPercent);
        out.putBoolean("trip_period", trips.shortPeriod);
        out.putDouble("trip_period_at", trips.shortPeriodSeconds);
        out.putBoolean("trip_fuel", trips.highFuelTemperature);
        out.putDouble("trip_fuel_at", trips.highFuelCelsius);
        out.putBoolean("trip_pressure", trips.highPressure);
        out.putDouble("trip_pressure_margin", trips.highPressureMargin);
        out.putBoolean("trip_flow", trips.lowFlow);
        out.putDouble("trip_flow_at", trips.lowFlowPercent);
        out.putBoolean("scrammed", saved.scrammed);
        out.putInt("trip", saved.trip.ordinal());
        out.putBoolean("core_damaged", saved.coreDamaged);
        out.putDouble("pump", saved.pump);
        out.putInt("coolant_type", saved.coolantType.ordinal());
        List<XenonEntry> xenonList = new ArrayList<>();
        saved.xenon.forEach((pos, values) -> xenonList.add(new XenonEntry(pos.asLong(), values[0], values[1])));
        out.store("xenon", XenonEntry.CODEC.listOf(), xenonList);
        out.putInt("mode", mode.ordinal());
        out.putBoolean("redstone_rods", redstoneRods);
        out.putBoolean("auto_unload", autoUnload);
        intake.serialize(out.child("intake"));
        hotLeg.serialize(out.child("hot_leg"));
        steam.serialize(out.child("steam"));
    }

    // The controller's own client sound and glow come from its renderer; nothing more to tick there.
    @Override
    public void clientTick() {
        clientHook.accept(this);
    }

    public static Consumer<FissionReactorBlockEntity> clientHook = reactor -> {};

    // Whether the core is running (for the Cherenkov glow and the hum).
    public boolean isRunning() {
        return isLit();
    }

    // The menu's slot check for the input slots.
    public static boolean acceptsInput(int slot, ItemStack stack) {
        return slot < INPUT_SLOTS && isLoadable(stack);
    }

    // A rod in a channel, and a fuel channel's iodine and xenon, as saved.
    private record RodEntry(long pos, ItemStack item) {
        static final Codec<RodEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(RodEntry::pos),
                ItemStack.CODEC.fieldOf("item").forGetter(RodEntry::item))
                .apply(i, RodEntry::new));
    }

    private record XenonEntry(long pos, double iodine, double xenon) {
        static final Codec<XenonEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(XenonEntry::pos),
                Codec.DOUBLE.fieldOf("iodine").forGetter(XenonEntry::iodine),
                Codec.DOUBLE.fieldOf("xenon").forGetter(XenonEntry::xenon))
                .apply(i, XenonEntry::new));
    }
}
