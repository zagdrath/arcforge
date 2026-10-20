/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import net.zagdrath.arcforge.item.nuclear.FuelRodItem;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.FissionReactorMenu;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.multiblock.VesselStructures;
import net.zagdrath.arcforge.nuclear.Enrichment;
import net.zagdrath.arcforge.nuclear.FuelRod;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.nuclear.RadiationSource;
import net.zagdrath.arcforge.nuclear.reactor.CoreLattice;
import net.zagdrath.arcforge.nuclear.reactor.ReactorParams;
import net.zagdrath.arcforge.nuclear.reactor.ReactorSim;
import net.zagdrath.arcforge.nuclear.safety.Containment;
import net.zagdrath.arcforge.nuclear.safety.Containments;
import net.zagdrath.arcforge.nuclear.safety.Fallout;
import net.zagdrath.arcforge.nuclear.safety.ReactorAlarm;
import net.zagdrath.arcforge.nuclear.safety.ReactorExplosions;
import net.zagdrath.arcforge.blockentity.nuclear.DrainTankBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.EccsAccumulatorBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.EccsPumpBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.FreezePlugBlockEntity;
import net.zagdrath.arcforge.blockentity.nuclear.SaltProcessingUnitBlockEntity;
import net.zagdrath.arcforge.registry.ModSounds;
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
//  - Advanced cores: Lead-Bismuth through the intake makes a fast reactor (no moderator; 15–20% fuel or MOX, a blanket
//    of Thorium Blanket Rods breeding U-233), pumped out of its Hot Leg as Hot Lead-Bismuth at about 900 °C; Fuel Salt
//    makes a molten salt reactor: every open cell (air or an empty Fuel Channel) between its graphite holds the salt,
//    which is its fuel (its make-up is the core's own, mixed with fresh salt as it comes in), pumped out as Hot Fuel
//    Salt at about 700 °C. Its Salt Processing Units (against the vessel) take the fission products out online, and
//    its Freeze Plug (under the vessel's floor, a Drain Tank under that) drains the salt away when it overheats.
//  - Safety (see the Safety page): it finds its containment every SCAN_INTERVAL ticks (see Containment), and the
//    Relief Valves, ECCS Accumulators and Pumps, Salt Processing Units round its vessel and the Freeze Plug under it.
//    Failures each come with warnings first (ReactorAlarm: lit on the Safety page, listed by Jade, the klaxon sounding
//    until silenced): cladding failure (Hydrogen and fission products into the coolant and the containment's air), a
//    Hydrogen burn when the air's Hydrogen reaches nuclear.failures.hydrogenIgnition with something to light it, a
//    meltdown (the core becomes Corium), a burst vessel and a prompt-critical excursion (explosions sized by what they
//    let go; see ReactorExplosions). Outside a sealed containment they let fallout out (see Fallout).
public class FissionReactorBlockEntity extends VesselControllerBlockEntity implements RadiationSource {
    public static final int INPUT_SLOTS = 9, OUTPUT_SLOTS = 9, MACHINE_SLOTS = INPUT_SLOTS + OUTPUT_SLOTS;
    // The intake and Hot Leg tanks' size; the steam tank holds four times as much.
    public static final int TANK = 64_000, STEAM_TANK = 4 * TANK;
    public static final int HISTORY = 60;
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.HOT_LEG, SideMode.STEAM);
    private static final Set<SideMode> PUSHED = EnumSet.of(SideMode.HOT_LEG, SideMode.STEAM);
    private static final int MOVE_INTERVAL = 5;
    private static final int SCAN_INTERVAL = 100;
    private static final int KLAXON_INTERVAL = 40;

    // The rods in the channels, by the channel's position.
    private final Map<BlockPos, ItemStack> rods = new HashMap<>();
    private @Nullable ReactorSim sim;
    private BlockPos[] cellPos = new BlockPos[0];
    // The cells in the order fresh rods fill them: nearest the core's middle first (see moveRods).
    private int[] loadOrder = new int[0];
    // Rods the player asked to come out at the next shutdown, from the GUI's core map (see togglePull).
    private final Set<BlockPos> pulls = new LinkedHashSet<>();
    // The core map for the GUI (see FissionReactorMenu.DATA_MAP), worked out once a tick while a menu reads it.
    private final int[] coreMap = new int[FissionReactorMenu.MAP_CELLS];
    private long coreMapTime = Long.MIN_VALUE;
    private long layoutKey;
    // Fuel Channel blocks in the vessel, loaded or not (the core counts only loaded ones).
    private int fuelChannelBlocks;
    // Control Rod Channel blocks in the vessel, loaded or not.
    private int controlChannelBlocks;
    private boolean layoutDirty = true;
    // The Cherenkov glow's step (see glowOf), synced to the client.
    private static final int GLOW_STEPS = 16;
    private int glow;
    // How full of coolant the vessel is, in WATER_STEPS (see setWater), synced to the client for the water its windows
    // show.
    private static final int WATER_STEPS = 32;
    private int water;
    // Set by a failure that destroyed the fuel this tick, so the damage isn't cleared at once.
    private boolean failedThisTick;
    // The core's state, kept for when the vessel is rebuilt (or unformed and formed again) and for saving.
    private final Saved saved = new Saved();
    private ReactorSim.Mode mode = ReactorSim.Mode.NONE;
    private boolean redstoneRods;
    private boolean autoUnload = true;
    private boolean unloadAll;
    private boolean damageMarked;
    private boolean wasCritical;
    // The containment (its air's Hydrogen and radioactivity are kept here and saved), and what's round the vessel.
    private final Containment containment;
    private final List<BlockPos> reliefValves = new ArrayList<>();
    private final List<BlockPos> accumulators = new ArrayList<>();
    private final List<BlockPos> pumps = new ArrayList<>();
    private final List<BlockPos> processors = new ArrayList<>();
    private @Nullable BlockPos freezePlug;
    private @Nullable BlockPos drainTank;
    private boolean scanned;
    private boolean everSealed;
    // The alarms up now, and those silenced (a new one sounds again).
    private int alarms;
    private int silenced;
    // The molten salt core's salt, shared by every salt cell.
    private ReactorSim.@Nullable Rod salt;

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
        this.intake = new FilteredFluidTank(TANK, FissionReactorBlockEntity::isCoolant, this::setChanged);
        this.hotLeg = new FilteredFluidTank(TANK, resource -> resource.is(ModFluids.HOT_COOLANT.get()) || resource.is(ModFluids.HOT_HEAVY_WATER.get())
                || resource.is(ModFluids.HOT_LEAD_BISMUTH.get()) || resource.is(ModFluids.HOT_FUEL_SALT.get()), this::setChanged);
        this.containment = new Containment(pos);
        this.steam = new FilteredFluidTank(STEAM_TANK, resource -> resource.is(ModFluids.CONTAMINATED_STEAM.get()), this::setChanged);
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
        // Failures, and what was done about them.
        double oxidised, coolantActivity;
        boolean ruptured;
        // The salt's make-up: its fissile uranium (fraction), burnup, bred U-233 and fission products.
        double saltEnrichment, saltBurnup, saltBred, saltFission;
    }

    public static boolean isCoolant(FluidResource resource) {
        return resource.is(net.minecraft.world.level.material.Fluids.WATER) || resource.is(ModFluids.HEAVY_WATER.get())
                || resource.is(ModFluids.LEAD_BISMUTH.get()) || resource.is(ModFluids.FUEL_SALT.get());
    }

    // What fills the core for a coolant fluid.
    public static CoreLattice.Coolant coolantOf(FluidResource resource) {
        if (resource.is(ModFluids.HEAVY_WATER.get())) {
            return CoreLattice.Coolant.HEAVY_WATER;
        }
        if (resource.is(ModFluids.LEAD_BISMUTH.get())) {
            return CoreLattice.Coolant.LEAD_BISMUTH;
        }
        if (resource.is(ModFluids.FUEL_SALT.get())) {
            return CoreLattice.Coolant.FUEL_SALT;
        }
        return CoreLattice.Coolant.LIGHT_WATER;
    }

    // The fluid each coolant leaves the hot leg as.
    public static Fluid hotFluid(CoreLattice.Coolant coolant) {
        return switch (coolant) {
            case HEAVY_WATER -> ModFluids.HOT_HEAVY_WATER.get();
            case LEAD_BISMUTH -> ModFluids.HOT_LEAD_BISMUTH.get();
            case FUEL_SALT -> ModFluids.HOT_FUEL_SALT.get();
            default -> ModFluids.HOT_COOLANT.get();
        };
    }

    // What goes in its input slots: Fuel Rods (not damaged ones) and Control Rods.
    public static boolean isLoadable(ItemStack stack) {
        if (stack.is(ModItems.CONTROL_ROD.get())) {
            return true;
        }
        FuelRod rod = stack.get(ModDataComponents.FUEL_ROD.get());
        return FuelRodItem.isFuelRod(stack) && (rod == null || !rod.damaged());
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
        p.zrHydrogen = ArcforgeConfig.ZIRCONIUM_HYDROGEN.getAsDouble();
        p.burstFactor = ArcforgeConfig.VESSEL_BURST_FACTOR.getAsDouble();
        p.excursionPower = ArcforgeConfig.EXCURSION_POWER.getAsDouble();
        p.reliefPerValve = ArcforgeConfig.RELIEF_PER_VALVE.getAsDouble();
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

    // What each cell inside is, and its bank; and whether it has its rod. In a molten salt core every open cell (air,
    // or a Fuel Channel, whatever's in it) holds the salt: a fuel cell.
    private CoreLattice.Cell cellOf(BlockState state, BlockPos pos, boolean saltCore) {
        if (saltCore) {
            if (state.is(ModBlocks.CONTROL_ROD_CHANNEL.get())) {
                ItemStack rod = rods.get(pos);
                return rod != null && rod.is(ModItems.CONTROL_ROD.get()) ? CoreLattice.Cell.CONTROL : CoreLattice.Cell.FUEL;
            }
            if (state.is(ModBlocks.GRAPHITE_MODERATOR.get())) {
                return CoreLattice.Cell.GRAPHITE;
            }
            return state.is(ModBlocks.STEEL_REFLECTOR.get()) ? CoreLattice.Cell.STEEL : CoreLattice.Cell.FUEL;
        }
        if (state.is(ModBlocks.FUEL_CHANNEL.get())) {
            ItemStack rod = rods.get(pos);
            return rod != null && FuelRodItem.isFuelRod(rod) && !FuelRod.of(rod).damaged() ? CoreLattice.Cell.FUEL : CoreLattice.Cell.COOLANT;
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
            boolean fits = FuelRodItem.isFuelRod(entry.getValue()) ? state.is(ModBlocks.FUEL_CHANNEL.get())
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
        boolean saltCore = (sim != null ? sim.coolantType : saved.coolantType) == CoreLattice.Coolant.FUEL_SALT;
        lattice.saltCore = saltCore;
        BlockPos[] positions = new BlockPos[lattice.size];
        int channelBlocks = 0, controlBlocks = 0;
        long key = 1469598103934665603L;
        for (int y = 0; y < ny; y++) {
            for (int z = 0; z < nz; z++) {
                for (int x = 0; x < nx; x++) {
                    BlockPos pos = box.min().offset(x + 1, y + 1, z + 1);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(ModBlocks.FUEL_CHANNEL.get())) {
                        channelBlocks++;
                    } else if (state.is(ModBlocks.CONTROL_ROD_CHANNEL.get())) {
                        controlBlocks++;
                    }
                    CoreLattice.Cell cell = cellOf(state, pos, saltCore);
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
        fuelChannelBlocks = channelBlocks;
        controlChannelBlocks = controlBlocks;
        key ^= box.min().asLong() * 31 + box.max().asLong() + (saltCore ? 7 : 0);
        if (sim != null && key == layoutKey) {
            return;
        }
        if (sim != null) {
            keep(level.getGameTime());
        }
        layoutKey = key;
        cellPos = positions;
        loadOrder = centreOut(positions, box.min(), box.max());
        ReactorParams p = params();
        p.solidFuel = !saltCore;
        ReactorSim next = new ReactorSim(p, lattice);
        if (saltCore) {
            salt = saltRod();
            for (int cell : next.fuelCells()) {
                next.setRod(cell, salt);
            }
        } else {
            salt = null;
            for (int cell : next.fuelCells()) {
                next.setRod(cell, FuelRod.of(rods.get(positions[cell])).toSim());
            }
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
        saved.oxidised = sim.oxidised;
        saved.coolantActivity = sim.coolantActivity;
        saved.ruptured = sim.ruptured;
        if (salt != null) {
            saved.saltEnrichment = salt.enrichment;
            saved.saltBurnup = salt.burnup;
            saved.saltBred = salt.plutonium;
            saved.saltFission = salt.fissionProducts;
        }
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
        next.oxidised = saved.oxidised;
        next.coolantActivity = saved.coolantActivity;
        next.ruptured = saved.ruptured;
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

    // The salt's make-up as one Rod, shared by every salt cell (its fertile thorium breeding U-233).
    private ReactorSim.Rod saltRod() {
        ReactorSim.Rod rod = new ReactorSim.Rod(saved.saltEnrichment);
        rod.burnup = saved.saltBurnup;
        rod.plutonium = saved.saltBred;
        rod.fissionProducts = saved.saltFission;
        rod.thorium = true;
        return rod;
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

    // The model's rods' burnup back into the rod items (a salt core's rods take no part).
    private void writeBack(long now) {
        if (sim == null || sim.lattice.saltCore) {
            return;
        }
        for (int cell : sim.fuelCells()) {
            ReactorSim.Rod rod = sim.rod(cell);
            ItemStack stack = rods.get(cellPos[cell]);
            if (rod != null && stack != null && FuelRodItem.isFuelRod(stack)) {
                FuelRod was = FuelRod.of(stack);
                stack.set(ModDataComponents.FUEL_ROD.get(), FuelRod.fromSim(was, rod, was.dischargeHeat(), was.discharged(), sim.coreDamaged));
            }
        }
    }

    // --- Running ---

    @Override
    protected void idle(ServerLevel level) {
        super.idle(level);
        setGlow(0);
        ensureCore(level);
        status = MachineStatus.NOT_FORMED;
    }

    @Override
    protected void work(ServerLevel level) {
        ensureCore(level);
        ReactorSim core = sim;
        if (core == null) {
            setGlow(0);
            setWater(0);
            return;
        }
        long now = level.getGameTime();
        if (now % 20 == 0) {
            mode = modeFromPorts(level);
        }
        if (!scanned || now % SCAN_INTERVAL == 0) {
            scan(level);
        }
        // A lead-bismuth or salt core doesn't boil: without a Hot Leg it only heats.
        core.mode = !core.coolantType.isWater() && mode == ReactorSim.Mode.BOILING ? ReactorSim.Mode.NONE : mode;
        core.reliefCapacity = core.p.reliefCapacity + reliefValves.size() * core.p.reliefPerValve;
        control(core);
        takeCoolant(core);
        Fluid hotFluid = hotFluid(core.coolantType);
        double hotRoom = core.ruptured ? 0.0 : hotLeg.getAmount() > 0 && !hotLeg.getResource(0).is(hotFluid) ? 0.0 : hotLeg.getSpace();
        failedThisTick = false;
        double oxidisedBefore = core.oxidised;
        core.tick(hotRoom, core.coolantType.isWater() ? steam.getSpace() : 0.0);
        add(hotLeg, FluidResource.of(hotFluid), core.hotLeg);
        add(steam, FluidResource.of(ModFluids.CONTAMINATED_STEAM.get()), core.steamMade);
        MultiblockAutomation.pushFluids(level, this, PUSHED, 64_000);
        MultiblockAutomation.pushOutputs(level, this);
        safety(level, core, oxidisedBefore);
        if (sim != core || isRemoved()) {
            // The vessel was blown apart this tick.
            return;
        }
        if (core.coreDamaged && !damageMarked) {
            damageMarked = true;
            writeBack(now);
            for (ItemStack stack : rods.values()) {
                if (FuelRodItem.isFuelRod(stack)) {
                    FuelRod rod = FuelRod.of(stack);
                    stack.set(ModDataComponents.FUEL_ROD.get(), rod.damage());
                }
            }
        }
        // With every fuel rod out (or a salt core drained), a damaged core can be started again (not on the tick it
        // failed). Unloaded, its cladding's oxidation goes with the rods; drained, the coolant's activity with it.
        if (!core.lattice.saltCore && loadedFuel() == 0) {
            core.oxidised = 0.0;
        }
        if (core.coolant < 1.0) {
            core.coolantActivity = 0.0;
        }
        if (core.coreDamaged && !failedThisTick && (core.lattice.saltCore ? core.coolant < 1.0 : loadedFuel() == 0)) {
            core.clearDamage();
            saved.ruptured = false;
            damageMarked = false;
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
        setGlow(glowOf(core.power));
        setWater(waterOf(core.level()));
        if (critical && !wasCritical) {
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.HOT_COOLANT.get(), "critical");
        }
        if (critical && now % 20 == 0) {
            if (core.coolantType == CoreLattice.Coolant.LEAD_BISMUTH) {
                ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.HOT_LEAD_BISMUTH.get(), "fast_reactor");
            } else if (core.coolantType == CoreLattice.Coolant.FUEL_SALT) {
                ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.HOT_FUEL_SALT.get(), "molten_salt_reactor");
            }
        }
        if (core.power >= 0.95 && now % 20 == 0) {
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.HOT_COOLANT.get(), "full_power");
        }
        wasCritical = critical;
    }

    // --- Safety: the containment, Emergency Core Cooling, the failures and the alarms ---

    // Finds the containment round the vessel and what stands against the vessel (and under it).
    private void scan(ServerLevel level) {
        scanned = true;
        reliefValves.clear();
        accumulators.clear();
        pumps.clear();
        processors.clear();
        freezePlug = null;
        drainTank = null;
        if (box == null) {
            return;
        }
        containment.scan(level, box.min(), box.max(), ArcforgeConfig.CONTAINMENT_MAX_VOLUME.getAsInt());
        Containments.put(level, containment);
        if (containment.sealed() && !everSealed) {
            everSealed = true;
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, null, "containment");
        }
        BlockPos min = box.min(), max = box.max();
        for (BlockPos pos : BlockPos.betweenClosed(min.offset(-1, -1, -1), max.offset(1, 1, 1))) {
            if (box.contains(pos)) {
                continue;
            }
            // Against a face of the vessel (not round its edges).
            int outside = (pos.getX() < min.getX() || pos.getX() > max.getX() ? 1 : 0) + (pos.getY() < min.getY() || pos.getY() > max.getY() ? 1 : 0)
                    + (pos.getZ() < min.getZ() || pos.getZ() > max.getZ() ? 1 : 0);
            if (outside != 1) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            BlockEntity there = level.getBlockEntity(pos);
            if (state.is(ModBlocks.RELIEF_VALVE.get())) {
                reliefValves.add(pos.immutable());
            } else if (there instanceof EccsAccumulatorBlockEntity) {
                accumulators.add(pos.immutable());
            } else if (there instanceof EccsPumpBlockEntity) {
                pumps.add(pos.immutable());
            } else if (there instanceof SaltProcessingUnitBlockEntity) {
                processors.add(pos.immutable());
            } else if (there instanceof FreezePlugBlockEntity && pos.getY() < min.getY() && freezePlug == null) {
                freezePlug = pos.immutable();
                if (level.getBlockEntity(pos.below()) instanceof DrainTankBlockEntity) {
                    drainTank = pos.below().immutable();
                }
            }
        }
    }

    // One tick of everything that keeps it safe, and of what happens when that isn't enough.
    private void safety(ServerLevel level, ReactorSim core, double oxidisedBefore) {
        long now = level.getGameTime();
        Vec3 centre = box != null ? box.aabb().getCenter() : Vec3.atCenterOf(worldPosition);
        int channels = Math.max(1, core.fuelChannels());
        double perChannel = ArcforgeConfig.FALLOUT_PER_CHANNEL.getAsDouble();
        // Emergency Core Cooling: the accumulators, then the pumps, while a water core's coolant is low.
        if (core.coolantType.isWater() && !core.ruptured && core.level() < ArcforgeConfig.ECCS_LEVEL.getAsDouble()) {
            double room = core.coolantCapacity - core.coolant;
            for (BlockPos pos : accumulators) {
                if (room > 1.0 && level.getBlockEntity(pos) instanceof EccsAccumulatorBlockEntity accumulator) {
                    room -= core.inject(accumulator.discharge((int) room), 20.0);
                }
            }
            for (BlockPos pos : pumps) {
                if (room > 1.0 && level.getBlockEntity(pos) instanceof EccsPumpBlockEntity pump) {
                    room -= core.inject(pump.pump(level, (int) room, nearestWall(pos), containment), 20.0);
                }
            }
        }
        // Cladding failure: Hydrogen into the containment's air, fission products into the coolant and (a share) the air.
        if (core.hydrogenMade > 0.0 && containment.sealed()) {
            containment.hydrogen += core.hydrogenMade;
        }
        if (core.oxidised > oxidisedBefore) {
            release(level, centre, (core.oxidised - oxidisedBefore) * channels * perChannel * 0.05);
        }
        // What the Relief Valves vent goes to the Suppression Pool (warming it; once it boils, into the air), the vessel's
        // own relief into the containment's air, or outside with no containment, carrying the coolant's activity.
        if (core.vented > 0.0) {
            double heat = core.vented * core.p.boilingHeat;
            boolean pool = !reliefValves.isEmpty() && containment.sealed() && containment.poolBlocks() > 0;
            if (pool) {
                double capacity = containment.poolBlocks() * Containment.POOL_HEAT_PER_BLOCK;
                containment.poolTemp = Math.min(100.0, containment.poolTemp + heat / capacity);
            }
            if (!pool || containment.poolTemp >= 100.0) {
                release(level, centre, core.vented * core.coolantActivity * perChannel * 2.0E-4);
            }
        }
        if (containment.poolBlocks() > 0) {
            containment.poolTemp -= (containment.poolTemp - 20.0) * 0.0005;
        }
        // The air: Hydrogen taken out by the recombiners; radioactivity decaying, and leaking out of an open containment.
        if (containment.hydrogen > 0.0) {
            if (!containment.sealed()) {
                containment.hydrogen = 0.0;
            } else {
                containment.hydrogen = Math.max(0.0, containment.hydrogen - containment.recombiners().size() * ArcforgeConfig.RECOMBINER_RATE.getAsDouble());
            }
        }
        if (containment.activity > 0.0) {
            double ticksPerHour = ArcforgeConfig.RADIATION_TICKS_PER_HOUR.getAsInt();
            containment.activity *= Math.pow(0.5, 1.0 / (ArcforgeConfig.AIR_ACTIVITY_HALF_LIFE.getAsDouble() * ticksPerHour));
            if (!containment.sealed() && now % 20 == 0) {
                double leak = containment.activity * 0.05;
                containment.activity -= leak;
                Fallout.settle(level, centre, leak * Math.max(64, containment.airVolume()) / 64.0);
            }
            if (containment.activity < 1.0E-3) {
                containment.activity = 0.0;
            }
        }
        // A Hydrogen burn: enough in the air and something to light it (a flame, a spark, Corium, glowing fuel).
        boolean hotFuel = core.hottestCladding() > 900.0 && (core.ruptured || core.level() < 0.5);
        if (containment.sealed() && containment.hydrogenPercent() >= ArcforgeConfig.HYDROGEN_IGNITION.getAsDouble()
                && (containment.hasIgniter() || hotFuel)) {
            double radius = ReactorExplosions.hydrogenRadius(containment.hydrogen);
            containment.hydrogen = 0.0;
            Vec3 at = box != null ? new Vec3(centre.x, box.max().getY() + 1.5, centre.z) : centre;
            if (!ReactorExplosions.explode(level, at, radius, containment)) {
                Fallout.settle(level, at, containment.activity * Math.max(64, containment.airVolume()) / 64.0);
                containment.activity = 0.0;
            }
            scanned = false;
        }
        // The molten salt core: its online processing, and its Freeze Plug draining it into the Drain Tank.
        if (core.lattice.saltCore && salt != null) {
            for (BlockPos pos : processors) {
                if (level.getBlockEntity(pos) instanceof SaltProcessingUnitBlockEntity unit) {
                    salt.fissionProducts = Math.max(0.0, salt.fissionProducts - unit.process(salt.fissionProducts));
                }
            }
        }
        if (freezePlug != null && core.coolantType == CoreLattice.Coolant.FUEL_SALT && level.getBlockEntity(freezePlug) instanceof FreezePlugBlockEntity plug) {
            plug.watch(level, core.coolant >= 1.0 ? core.coolantTemp : -1.0);
            if (plug.isMelted() && core.coolant >= 1.0) {
                int moved = (int) Math.min(core.coolant, 4_000.0);
                if (drainTank != null && level.getBlockEntity(drainTank) instanceof DrainTankBlockEntity tank) {
                    moved = tank.fill(moved);
                }
                core.coolant -= moved;
                if (moved > 0 && now % 40 == 0) {
                    ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.FUEL_SALT.get(), "freeze_plug");
                }
            }
        }
        // The failures.
        if (core.melted) {
            core.melted = false;
            meltdown(level, core, centre, channels, perChannel);
        }
        if (core.ruptured && !saved.ruptured) {
            saved.ruptured = true;
            rupture(level, core, centre, channels, perChannel);
        }
        if (core.excursion) {
            core.excursion = false;
            // A salt core's passive safety: the power spike overheats the salt, its Freeze Plug melts and the salt
            // drains away from the moderator before the vessel can go. Without a plug it goes like any core.
            if (core.lattice.saltCore && freezePlug != null && level.getBlockEntity(freezePlug) instanceof FreezePlugBlockEntity plug) {
                plug.melt(level);
                core.scram(ReactorSim.Trip.HIGH_POWER);
            } else {
                excursion(level, core, centre, channels, perChannel);
                return;
            }
        }
        alarms(level, core, now);
    }

    // The vessel wall block nearest pos (what a pump against it must not draw from).
    private @Nullable BlockPos nearestWall(BlockPos pos) {
        if (box == null) {
            return null;
        }
        BlockPos min = box.min(), max = box.max();
        return new BlockPos(Mth.clamp(pos.getX(), min.getX(), max.getX()), Mth.clamp(pos.getY(), min.getY(), max.getY()),
                Mth.clamp(pos.getZ(), min.getZ(), max.getZ()));
    }

    // Fission products let out of the vessel: into a sealed containment's air (the vessel itself isn't one of its
    // cells), or onto the ground round it.
    private void release(ServerLevel level, Vec3 centre, double amount) {
        if (amount <= 0.0) {
            return;
        }
        if (containment.sealed()) {
            containment.activity += Fallout.airDose(containment, amount);
        } else {
            Fallout.release(level, centre, amount);
        }
    }

    // The fuel melted: it slumps to the vessel's floor as Corium (with block damage from meltdowns on; else the rods are
    // only ruined), letting fission products out.
    private void meltdown(ServerLevel level, ReactorSim core, Vec3 centre, int channels, double perChannel) {
        release(level, centre, channels * perChannel * 0.15);
        if (box == null || !ArcforgeConfig.MELTDOWN_MELTS_BLOCKS.getAsBoolean() || core.lattice.saltCore) {
            return;
        }
        int fuel = loadedFuel();
        writeBack(level.getGameTime());
        rods.entrySet().removeIf(entry -> FuelRodItem.isFuelRod(entry.getValue()));
        pourCorium(level, Math.max(1, (fuel + 1) / 2), ModBlocks.CORIUM.get().defaultBlockState());
        layoutDirty = true;
        failedThisTick = true;
        setChanged();
    }

    // The bottom layer inside the vessel, from its middle out, as Corium (or what's given).
    private void pourCorium(ServerLevel level, int count, BlockState state) {
        if (box == null) {
            return;
        }
        BlockPos min = box.min(), max = box.max();
        int y = min.getY() + 1;
        List<BlockPos> floor = new ArrayList<>();
        for (int x = min.getX() + 1; x < max.getX(); x++) {
            for (int z = min.getZ() + 1; z < max.getZ(); z++) {
                floor.add(new BlockPos(x, y, z));
            }
        }
        BlockPos middle = new BlockPos((min.getX() + max.getX()) / 2, y, (min.getZ() + max.getZ()) / 2);
        floor.sort(java.util.Comparator.comparingDouble(pos -> pos.distSqr(middle)));
        for (int i = 0; i < Math.min(count, floor.size()); i++) {
            level.setBlock(floor.get(i), state, Block.UPDATE_ALL);
        }
    }

    // The vessel burst: a steam explosion sized by what it held, its coolant gone, its rods ruined.
    private void rupture(ServerLevel level, ReactorSim core, Vec3 centre, int channels, double perChannel) {
        double radius = ReactorExplosions.vesselRadius(core.storedEnergy());
        boolean held = ReactorExplosions.explode(level, centre, radius, containment);
        double released = channels * perChannel * (0.1 + 0.3 * core.oxidised) + core.coolant * core.coolantActivity * perChannel * 2.0E-4;
        if (held) {
            containment.activity += Fallout.airDose(containment, released);
        } else {
            Fallout.settle(level, centre, released);
        }
        core.coolant = 0.0;
        core.coreDamaged = true;
        core.scram(ReactorSim.Trip.HIGH_PRESSURE);
        scanned = false;
        setChanged();
    }

    // A prompt-critical excursion: the core's power spikes and blows the vessel apart (with block damage from
    // explosions on, every block of it goes), its fuel scattered as Corium on the floor where it stood.
    private void excursion(ServerLevel level, ReactorSim core, Vec3 centre, int channels, double perChannel) {
        double radius = ReactorExplosions.vesselRadius(core.storedEnergy());
        boolean held = ReactorExplosions.explode(level, centre, radius, containment);
        double released = channels * perChannel;
        if (held) {
            containment.activity += Fallout.airDose(containment, released);
        } else {
            Fallout.settle(level, centre, released);
        }
        core.coreDamaged = true;
        core.ruptured = true;
        saved.ruptured = true;
        core.coolant = 0.0;
        core.scram(ReactorSim.Trip.HIGH_POWER);
        int fuel = loadedFuel();
        rods.entrySet().removeIf(entry -> FuelRodItem.isFuelRod(entry.getValue()));
        layoutDirty = true;
        failedThisTick = true;
        if (box == null || !ArcforgeConfig.EXPLOSIONS_BREAK_BLOCKS.getAsBoolean()) {
            damageMarked = false;
            setChanged();
            return;
        }
        VesselStructure.Box was = box;
        BlockState debris = ArcforgeConfig.MELTDOWN_MELTS_BLOCKS.getAsBoolean() ? ModBlocks.CORIUM.get().defaultBlockState()
                : ModBlocks.SOLIDIFIED_CORIUM.get().defaultBlockState();
        for (BlockPos pos : was.positions()) {
            if (!pos.equals(worldPosition)) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        List<BlockPos> floor = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(was.min(), new BlockPos(was.max().getX(), was.min().getY(), was.max().getZ()))) {
            floor.add(pos.immutable());
        }
        java.util.Collections.shuffle(floor, new java.util.Random(level.getRandom().nextLong()));
        for (int i = 0; i < Math.min(floor.size(), Math.max(1, (fuel + 1) / 2)); i++) {
            level.setBlock(floor.get(i), debris, Block.UPDATE_ALL);
        }
        // Last of all the controller itself (and this block entity with it).
        Containments.remove(level, worldPosition);
        level.setBlock(worldPosition, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    // Each warning, worked out from the core and the containment; the klaxon while any unsilenced one is up.
    private void alarms(ServerLevel level, ReactorSim core, long now) {
        int flags = 0;
        double cladFail = core.p.cladFail, melt = core.p.fuelMelt;
        double clad = core.hottestCladding(), fuel = core.hottestFuel();
        if (core.lattice.saltCore) {
            if (freezePlug != null && level.getBlockEntity(freezePlug) instanceof FreezePlugBlockEntity plug && plug.isMelted()) {
                flags |= ReactorAlarm.SALT_DRAINED.bit();
            }
            if (core.coolantTemp > ArcforgeConfig.FREEZE_PLUG_MELT.getAsDouble() - 50.0) {
                flags |= ReactorAlarm.FUEL_HOT.bit();
            }
        } else {
            if (clad > cladFail - 200.0) {
                flags |= ReactorAlarm.CLADDING_HOT.bit();
            }
            if (core.oxidised > 0.0) {
                flags |= ReactorAlarm.CLADDING_FAILED.bit();
            }
            if (fuel > melt - 400.0) {
                flags |= ReactorAlarm.FUEL_HOT.bit();
            }
            if (core.coreDamaged && core.p.solidFuel && fuel > melt - 100.0) {
                flags |= ReactorAlarm.CORE_MELT.bit();
            }
        }
        if (core.pressure > core.reliefPressure()) {
            flags |= ReactorAlarm.HIGH_PRESSURE.bit();
        }
        if (core.pressure > core.burstPressure() * 0.9) {
            flags |= ReactorAlarm.OVERPRESSURE.bit();
        }
        if (core.power > 0.05 && core.period > 0.0 && core.period < 5.0) {
            flags |= ReactorAlarm.POWER_RISE.bit();
        }
        if (core.power > Math.min(2.0, core.p.excursionPower * 0.25) || core.rho > core.p.beta) {
            flags |= ReactorAlarm.EXCURSION.bit();
        }
        if (core.coolantType.isWater() && core.loadedRods() > 0 && core.level() < ArcforgeConfig.ECCS_LEVEL.getAsDouble()) {
            flags |= ReactorAlarm.LOW_COOLANT.bit();
        }
        if (core.coolantActivity > 0.0) {
            flags |= ReactorAlarm.COOLANT_ACTIVITY.bit();
        }
        if (containment.sealed() && containment.hydrogenPercent() >= ArcforgeConfig.HYDROGEN_IGNITION.getAsDouble() * 0.5) {
            flags |= ReactorAlarm.HYDROGEN.bit();
        }
        if (containment.activity > 0.01) {
            flags |= ReactorAlarm.AIR_ACTIVITY.bit();
        }
        if (!containment.sealed()) {
            flags |= ReactorAlarm.NO_CONTAINMENT.bit();
        }
        if (core.ruptured) {
            flags |= ReactorAlarm.RUPTURED.bit();
        }
        alarms = flags;
        silenced &= flags;
        int sounding = flags & ReactorAlarm.klaxonMask() & ~silenced;
        if (sounding != 0 && now % KLAXON_INTERVAL == 0) {
            level.playSound(null, worldPosition, ModSounds.REACTOR_ALARM.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
        }
    }

    // Silences the alarms up now (a new one sounds again).
    public void silence() {
        silenced = alarms;
        setChanged();
    }

    public int getAlarms() {
        return alarms;
    }

    public int getSilenced() {
        return silenced;
    }

    public Containment getContainment() {
        return containment;
    }

    public int reliefValves() {
        return reliefValves.size();
    }

    public int eccsUnits() {
        return accumulators.size() + pumps.size();
    }

    // Water ready in the ECCS Accumulators (mB).
    public int eccsWater() {
        int total = 0;
        if (level != null) {
            for (BlockPos pos : accumulators) {
                if (level.getBlockEntity(pos) instanceof EccsAccumulatorBlockEntity accumulator) {
                    total += accumulator.getTank().getAmount();
                }
            }
        }
        return total;
    }

    // The Freeze Plug: 0 none, 1 frozen, 2 melted.
    public int freezePlugState() {
        if (freezePlug == null || level == null || !(level.getBlockEntity(freezePlug) instanceof FreezePlugBlockEntity plug)) {
            return 0;
        }
        return plug.isMelted() ? 2 : 1;
    }

    public ReactorSim.@Nullable Rod salt() {
        return salt;
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            Containments.remove(level, worldPosition);
        }
        super.setRemoved();
    }

    private MachineStatus statusOf(ReactorSim core) {
        if (core.ruptured) {
            return MachineStatus.DESTROYED;
        }
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
        // Critical once k-effective reaches 1, even while the power is still climbing from the source level.
        return core.power > 0.01 || core.k >= 1.0 ? MachineStatus.CRITICAL : MachineStatus.SUBCRITICAL;
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

    // Coolant from the intake into the vessel: one kind at a time (it changes only once the vessel has drained; a burst
    // vessel holds none). Changing to or from Fuel Salt rebuilds the core. Fresh Fuel Salt (carrying an assay) mixes
    // its uranium into the salt already in the core; salt without one is the core's own coming back round its loop.
    private void takeCoolant(ReactorSim core) {
        if (intake.getAmount() <= 0 || core.ruptured) {
            return;
        }
        FluidResource resource = intake.getResource(0);
        CoreLattice.Coolant kind = coolantOf(resource);
        if (core.coolant >= 1.0 && kind != core.coolantType) {
            return;
        }
        if (kind != core.coolantType) {
            boolean rebuild = kind == CoreLattice.Coolant.FUEL_SALT || core.coolantType == CoreLattice.Coolant.FUEL_SALT;
            core.coolantType = kind;
            saved.coolantType = kind;
            // The client's windows show the new coolant.
            sync();
            if (rebuild) {
                if (kind == CoreLattice.Coolant.FUEL_SALT) {
                    saved.saltEnrichment = saved.saltBurnup = saved.saltBred = saved.saltFission = 0.0;
                }
                layoutDirty = true;
                return;
            }
        }
        int room = (int) Math.floor(core.coolantCapacity - core.coolant);
        int moved = Math.min(room, intake.getAmount());
        if (moved > 0) {
            Integer fresh = kind == CoreLattice.Coolant.FUEL_SALT ? Enrichment.saltAssay(resource) : null;
            if (fresh != null && salt != null) {
                double had = Math.max(0.0, core.coolant), total = had + moved;
                salt.enrichment = (salt.enrichment * (1.0 - core.p.u235Use * salt.burnup) * had + fresh / 100_000.0 * moved) / total;
                salt.plutonium = salt.plutonium * had / total;
                salt.fissionProducts = salt.fissionProducts * had / total;
                salt.burnup = 0.0;
            }
            core.coolant += moved;
            int left = intake.getAmount() - moved;
            intake.set(0, left > 0 ? resource : FluidResource.EMPTY, left);
        }
    }

    private static void add(FilteredFluidTank tank, FluidResource resource, double amount) {
        int whole = (int) Math.floor(amount);
        if (whole > 0) {
            tank.set(0, resource, tank.getAmount() + whole);
        }
    }

    // Loading and unloading rods, a rod each time, only while shut down. A salt core takes Control Rods only.
    private void moveRods(ServerLevel level, ReactorSim core) {
        if (!canRefuel()) {
            return;
        }
        boolean saltCore = core.lattice.saltCore;
        long now = level.getGameTime();
        // Out first: spent and damaged rods (with auto-unload), or every rod when asked.
        for (int cell : saltCore ? new int[0] : core.fuelCells()) {
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
                if (FuelRodItem.isFuelRod(stack) && FuelRod.of(stack).damaged()) {
                    if (unload(entry.getKey(), stack, null, core, now)) {
                        return;
                    }
                    break;
                }
            }
        }
        // Rods asked for from the core map, one at a time.
        for (BlockPos pos : List.copyOf(pulls)) {
            ItemStack stack = rods.get(pos);
            if (stack == null) {
                pulls.remove(pos);
                continue;
            }
            ReactorSim.Rod live = null;
            for (int cell : core.fuelCells()) {
                if (pos.equals(cellPos[cell])) {
                    live = core.rod(cell);
                }
            }
            if (unload(pos, stack, live, core, now)) {
                pulls.remove(pos);
            }
            return;
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
        // In: the first rod waiting goes into the first empty channel for it. Fuel fills from the core's middle out (see
        // centreOut), so the rods loaded first sit where the flux is highest; control rods fill from the top down (the
        // cells run from the bottom layer up), as the banks enter from the top, so a part-loaded set still covers the top
        // of the core.
        for (int slot = 0; slot < INPUT_SLOTS; slot++) {
            ItemStack waiting = items.getStack(slot);
            if (waiting.isEmpty() || !isLoadable(waiting)) {
                continue;
            }
            boolean fuel = FuelRodItem.isFuelRod(waiting);
            if (fuel && saltCore) {
                continue;
            }
            Block channel = fuel ? ModBlocks.FUEL_CHANNEL.get() : ModBlocks.CONTROL_ROD_CHANNEL.get();
            for (int c = 0; c < cellPos.length; c++) {
                int cell = fuel ? (c < loadOrder.length ? loadOrder[c] : c) : cellPos.length - 1 - c;
                BlockPos pos = cellPos[cell];
                if (pos != null && !rods.containsKey(pos) && level.getBlockState(pos).is(channel)) {
                    ItemStack one = waiting.copyWithCount(1);
                    if (fuel && !one.has(ModDataComponents.FUEL_ROD.get())) {
                        one.set(ModDataComponents.FUEL_ROD.get(), FuelRod.of(one));
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
        if (live != null && FuelRodItem.isFuelRod(stack)) {
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

    // From the core map: a fuel rod to bring out at the next shutdown, or not after all.
    public void togglePull(int cell) {
        if (cell < 0 || cell >= cellPos.length || cellPos[cell] == null || salt != null) {
            return;
        }
        BlockPos pos = cellPos[cell];
        if (!pulls.remove(pos) && rods.get(pos) != null && FuelRodItem.isFuelRod(rods.get(pos))) {
            pulls.add(pos);
        }
        setChanged();
    }

    // From the core map: the most-burned third of the fuel rods (the batch a refuelling replaces), to come out at the
    // next shutdown.
    public void pullBurnedThird() {
        ReactorSim core = sim;
        if (core == null || core.lattice.saltCore) {
            return;
        }
        for (int cell : burnedThird(core)) {
            pulls.add(cellPos[cell]);
        }
        setChanged();
    }

    // How many rods a third of count is (rounded up, so one rod is its own third).
    public static int third(int count) {
        return (count + 2) / 3;
    }

    // The cells of the most-burned third of the loaded fuel rods, most burned first.
    private static List<Integer> burnedThird(ReactorSim core) {
        List<Integer> loaded = new ArrayList<>();
        for (int cell : core.fuelCells()) {
            if (core.rod(cell) != null) {
                loaded.add(cell);
            }
        }
        loaded.sort((a, b) -> Double.compare(core.rod(b).burnup, core.rod(a).burnup));
        return loaded.subList(0, third(loaded.size()));
    }

    // The cells nearest the core's middle first: across first (a channel's distance from the vessel's axis), then up
    // and down from its middle layer, so partly burned rods loaded first go in the middle and fresh ones round them.
    private static int[] centreOut(BlockPos[] positions, BlockPos min, BlockPos max) {
        double cx = (min.getX() + max.getX()) / 2.0, cy = (min.getY() + max.getY()) / 2.0, cz = (min.getZ() + max.getZ()) / 2.0;
        Integer[] order = new Integer[positions.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        java.util.function.ToDoubleFunction<Integer> distance = i -> {
            BlockPos pos = positions[i];
            if (pos == null) {
                return Double.MAX_VALUE;
            }
            double dx = pos.getX() - cx, dz = pos.getZ() - cz;
            return (dx * dx + dz * dz) * 1_000.0 + Math.abs(pos.getY() - cy);
        };
        java.util.Arrays.sort(order, java.util.Comparator.comparingDouble(distance).thenComparingInt(i -> i));
        int[] out = new int[order.length];
        for (int i = 0; i < out.length; i++) {
            out[i] = order[i];
        }
        return out;
    }

    // Fuel Channel blocks in the vessel, loaded or not.
    public int fuelChannelBlocks() {
        return fuelChannelBlocks;
    }

    // Control Rod Channel blocks in the vessel, loaded or not.
    public int controlChannelBlocks() {
        return controlChannelBlocks;
    }

    public int loadedFuel() {
        int count = 0;
        for (ItemStack stack : rods.values()) {
            count += FuelRodItem.isFuelRod(stack) ? 1 : 0;
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
            if (FuelRodItem.isFuelRod(stack)) {
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
        // Coolant carrying fission products from failed cladding.
        if (sim != null && sim.coolantActivity > 0.0) {
            rate += sim.coolant / 1_000.0 * sim.coolantActivity * ArcforgeConfig.FALLOUT_PER_CHANNEL.getAsDouble() * 0.5;
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
        if (index >= FissionReactorMenu.DATA_MAP && index < FissionReactorMenu.DATA_MAP + FissionReactorMenu.MAP_CELLS) {
            return coreMap()[index - FissionReactorMenu.DATA_MAP];
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
            case FissionReactorMenu.DATA_CHANNELS -> fuelChannelBlocks;
            case FissionReactorMenu.DATA_CONTROL_CHANNELS -> controlChannelBlocks;
            case FissionReactorMenu.DATA_MAP_SIZE -> core != null ? FissionReactorMenu.packMapSize(core.lattice.nx, core.lattice.ny, core.lattice.nz) : 0;
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
            case FissionReactorMenu.DATA_ALARMS -> alarms;
            case FissionReactorMenu.DATA_SILENCED -> silenced;
            case FissionReactorMenu.DATA_CONTAINMENT -> box != null && scanned ? containment.status().ordinal() + 1 : 0;
            case FissionReactorMenu.DATA_HYDROGEN -> (int) Math.round(containment.hydrogenPercent() * 100.0);
            case FissionReactorMenu.DATA_AIR_ACTIVITY -> (int) Math.min(Integer.MAX_VALUE, Math.round(containment.activity * 10.0));
            case FissionReactorMenu.DATA_POOL -> containment.poolBlocks();
            case FissionReactorMenu.DATA_POOL_TEMP -> (int) Math.round(containment.poolTemp * 10.0);
            case FissionReactorMenu.DATA_VALVES -> reliefValves.size();
            case FissionReactorMenu.DATA_ECCS_UNITS -> eccsUnits();
            case FissionReactorMenu.DATA_ECCS_WATER -> eccsWater();
            case FissionReactorMenu.DATA_RECOMBINERS -> containment.recombiners().size();
            case FissionReactorMenu.DATA_OXIDISED -> core != null ? (int) Math.round(core.oxidised * 1000.0) : (int) Math.round(saved.oxidised * 1000.0);
            case FissionReactorMenu.DATA_COOLANT_ACTIVITY -> core != null ? (int) Math.round(core.coolantActivity * 1000.0) : 0;
            case FissionReactorMenu.DATA_SALT_FISSION -> salt != null ? (int) Math.round(salt.fissionProducts * 1000.0) : 0;
            case FissionReactorMenu.DATA_FREEZE_PLUG -> freezePlugState();
            case FissionReactorMenu.DATA_AIR_VOLUME -> containment.airVolume();
            case FissionReactorMenu.DATA_SALT_FISSILE -> salt != null ? (int) Math.round(salt.fissile(core != null ? core.p : params()) * 100_000.0) : 0;
            case FissionReactorMenu.DATA_BURST_PRESSURE -> core != null ? (int) Math.round(core.burstPressure() * 10.0) : 0;
            default -> 0;
        };
    }

    // Each cell of the core for the GUI's map (see FissionReactorMenu.packCell): what's in it, and a fuel rod's burnup,
    // enrichment and type, whether it's damaged and whether it's to come out.
    private int[] coreMap() {
        long now = level != null ? level.getGameTime() : 0L;
        if (now == coreMapTime) {
            return coreMap;
        }
        coreMapTime = now;
        java.util.Arrays.fill(coreMap, 0);
        ReactorSim core = sim;
        if (core == null || level == null) {
            return coreMap;
        }
        for (int i = 0; i < Math.min(cellPos.length, coreMap.length); i++) {
            BlockPos pos = cellPos[i];
            if (pos == null) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            ItemStack stack = rods.get(pos);
            int kind;
            FuelRod fuel = null;
            if (state.is(ModBlocks.FUEL_CHANNEL.get())) {
                if (core.lattice.saltCore) {
                    kind = FissionReactorMenu.CELL_SALT;
                } else if (stack != null && FuelRodItem.isFuelRod(stack)) {
                    kind = FissionReactorMenu.CELL_FUEL;
                    fuel = FuelRod.of(stack);
                } else {
                    kind = FissionReactorMenu.CELL_EMPTY_CHANNEL;
                }
            } else if (state.is(ModBlocks.CONTROL_ROD_CHANNEL.get())) {
                kind = FissionReactorMenu.CELL_CONTROL;
            } else {
                kind = switch (core.lattice.cell(i)) {
                    case GRAPHITE -> FissionReactorMenu.CELL_GRAPHITE;
                    case STEEL -> FissionReactorMenu.CELL_STEEL;
                    default -> FissionReactorMenu.CELL_COOLANT;
                };
            }
            if (fuel == null) {
                coreMap[i] = FissionReactorMenu.packCell(kind, 0.0, 0, 0, false, false);
                continue;
            }
            ReactorSim.Rod live = core.rod(i);
            double burnup = live != null ? live.burnup : fuel.burnup();
            coreMap[i] = FissionReactorMenu.packCell(kind, burnup, fuel.enrichment(), fuel.type().ordinal(), fuel.damaged(), pulls.contains(pos));
        }
        return coreMap;
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
        saved.coolantType = CoreLattice.Coolant.values()[Mth.clamp(in.getIntOr("coolant_type", 0), 0, CoreLattice.Coolant.values().length - 1)];
        saved.oxidised = in.getDoubleOr("oxidised", 0.0);
        saved.coolantActivity = in.getDoubleOr("coolant_activity", 0.0);
        saved.ruptured = in.getBooleanOr("ruptured", false);
        saved.saltEnrichment = in.getDoubleOr("salt_enrichment", 0.0);
        saved.saltBurnup = in.getDoubleOr("salt_burnup", 0.0);
        saved.saltBred = in.getDoubleOr("salt_bred", 0.0);
        saved.saltFission = in.getDoubleOr("salt_fission", 0.0);
        containment.hydrogen = in.getDoubleOr("hydrogen", 0.0);
        containment.activity = in.getDoubleOr("air_activity", 0.0);
        containment.poolTemp = in.getDoubleOr("pool_temp", 20.0);
        everSealed = in.getBooleanOr("ever_sealed", false);
        silenced = in.getIntOr("silenced", 0);
        scanned = false;
        saved.xenon.clear();
        in.read("xenon", XenonEntry.CODEC.listOf()).ifPresent(list -> list.forEach(entry -> saved.xenon.put(BlockPos.of(entry.pos()),
                new double[] { entry.iodine(), entry.xenon() })));
        mode = ReactorSim.Mode.values()[Mth.clamp(in.getIntOr("mode", 0), 0, ReactorSim.Mode.values().length - 1)];
        redstoneRods = in.getBooleanOr("redstone_rods", false);
        autoUnload = in.getBooleanOr("auto_unload", true);
        pulls.clear();
        in.read("pulls", Codec.LONG.listOf()).ifPresent(list -> list.forEach(pos -> pulls.add(BlockPos.of(pos))));
        glow = in.getIntOr("glow", 0);
        water = Mth.clamp(in.getIntOr("water", 0), 0, WATER_STEPS);
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
        out.putDouble("oxidised", saved.oxidised);
        out.putDouble("coolant_activity", saved.coolantActivity);
        out.putBoolean("ruptured", saved.ruptured);
        out.putDouble("salt_enrichment", saved.saltEnrichment);
        out.putDouble("salt_burnup", saved.saltBurnup);
        out.putDouble("salt_bred", saved.saltBred);
        out.putDouble("salt_fission", saved.saltFission);
        out.putDouble("hydrogen", containment.hydrogen);
        out.putDouble("air_activity", containment.activity);
        out.putDouble("pool_temp", containment.poolTemp);
        out.putBoolean("ever_sealed", everSealed);
        out.putInt("silenced", silenced);
        List<XenonEntry> xenonList = new ArrayList<>();
        saved.xenon.forEach((pos, values) -> xenonList.add(new XenonEntry(pos.asLong(), values[0], values[1])));
        out.store("xenon", XenonEntry.CODEC.listOf(), xenonList);
        out.putInt("mode", mode.ordinal());
        out.putBoolean("redstone_rods", redstoneRods);
        out.putBoolean("auto_unload", autoUnload);
        out.store("pulls", Codec.LONG.listOf(), pulls.stream().map(BlockPos::asLong).toList());
        out.putInt("glow", glow);
        out.putInt("water", water);
        intake.serialize(out.child("intake"));
        hotLeg.serialize(out.child("hot_leg"));
        steam.serialize(out.child("steam"));
    }

    // The Cherenkov glow's step, 0 (dark, under 0.1% power) or 1..GLOW_STEPS on a log scale up to full power.
    private static int glowOf(double power) {
        if (power < 0.001) {
            return 0;
        }
        double t = Math.min(1.0, (Math.log10(power) + 3.0) / 3.0);
        return 1 + (int) Math.round(t * (GLOW_STEPS - 1));
    }

    // Lights the controller (the glow and the pumps' hum) and sends the client a new step when it changes.
    private void setGlow(int step) {
        setLit(step > 0);
        if (step != glow) {
            glow = step;
            sync();
        }
    }

    // From faint at 0.1% power to full at 100%.
    @Override
    public float glowLevel() {
        return glow <= 0 ? 0.0F : 0.25F + 0.75F * (glow - 1) / (GLOW_STEPS - 1.0F);
    }

    // The vessel's coolant level (0..1) in WATER_STEPS: none only when it's empty, full only when it's full.
    private static int waterOf(double level) {
        if (level <= 0.0) {
            return 0;
        }
        return level >= 1.0 ? WATER_STEPS : Mth.clamp((int) Math.round(level * WATER_STEPS), 1, WATER_STEPS - 1);
    }

    // Sends the client a new water step when it changes.
    private void setWater(int step) {
        if (step != water) {
            water = step;
            sync();
        }
    }

    @Override
    public float waterLevel() {
        return water / (float) WATER_STEPS;
    }

    // Water (light or heavy) shows as water; lead-bismuth as its opaque molten metal; fuel salt as its pale green salt.
    @Override
    public @Nullable Identifier coolantTexture() {
        return switch (saved.coolantType) {
            case LEAD_BISMUTH -> Identifier.fromNamespaceAndPath(net.zagdrath.arcforge.Arcforge.MODID, "block/fluid/lead_bismuth_still");
            case FUEL_SALT -> Identifier.fromNamespaceAndPath(net.zagdrath.arcforge.Arcforge.MODID, "block/fluid/fuel_salt_still");
            default -> null;
        };
    }

    @Override
    public int coolantColor() {
        return switch (saved.coolantType) {
            case LEAD_BISMUTH -> 0xFFFFFFFF;
            case FUEL_SALT -> 0xD0FFFFFF;
            default -> super.coolantColor();
        };
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
