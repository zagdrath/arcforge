/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.fusion;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.fusion.FusionModuleBlock;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.fusion.FusionAlarm;
import net.zagdrath.arcforge.fusion.FusionExhaustPool;
import net.zagdrath.arcforge.fusion.PlasmaSim;
import net.zagdrath.arcforge.fusion.TokamakLayout;
import net.zagdrath.arcforge.fusion.TokamakStructure;
import net.zagdrath.arcforge.fusion.Tritium;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.fusion.FusionReactorMenu;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.PortFaces;
import net.zagdrath.arcforge.nuclear.RadiationSource;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModSounds;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Fusion Reactor (see TokamakStructure and TokamakLayout), run by its controller. Its plasma is PlasmaSim's, stepped
// once a second; this block entity is the plant round it:
//  - Vacuum: the Cryopumps pump the vessel down from air (fusion.reactor.pumpDownSeconds for one pump on the smallest
//    vessel, longer for a bigger one); a plasma needs 10^-5 atm. What's left of the air is impurity in the plasma.
//  - Magnets: the coils are Arcite superconductor, superconducting only while cold: Liquid Helium (in through Input ports)
//    takes them to 4 K and the full field, Liquid Nitrogen alone to 77 K and a weaker field (nitrogenFieldShare). They
//    boil a little of it every second they're cold. Energising them is a big FE ramp; held, the field costs nothing. A
//    coil that warms past its critical temperature while energised quenches: its stored energy is dumped into it, which
//    damages it unless a Quench Protection Unit stands against the cryostat.
//  - The Central Solenoid (a tokamak's) is charged with FE before each pulse; it drives the plasma's current, and when
//    its flux is spent the pulse ends. A bigger reactor runs longer; a stellarator has none and runs as long as it's fed.
//  - Heating (Neutral Beam Injectors, Gyrotrons) and fuelling (Pellet Injectors first, then Gas Puff Valves at half
//    efficiency), from the modules round the vessel, to the setpoints from the GUI, redstone (a signal starts it) or the
//    machine control API. The fuel is held at the density setpoint's share of the density limit, its mix even D-T (or
//    D-He3), and thinned near the beta limit (burn control).
//  - Heat: the neutrons (80% of D-T's energy, times the blanket's gain) heat the Breeding Blanket's Lead-Lithium, which
//    goes out of Hot Leg ports as Hot Lead-Lithium for a Steam Generator (High-Pressure Steam); the rest (alpha heating,
//    the heating, radiation) lands on the First Wall and Divertor, cooled by Water out of Output ports as Hot Coolant.
//    Heat that isn't carried off warms the wall and blanket; too hot, they fail.
//  - The fuel cycle: the blanket breeds tritium (TBR, against what's burnt); the Cryopumps take the exhaust (unburnt fuel,
//    helium ash and the bred tritium, as Fusion Exhaust; see FusionExhaustPool) for a Tritium Plant to separate. Exhaust
//    with nowhere to go is vented: a tritium release.
//  - Failures (see FusionAlarm): disruptions (the density, beta and vertical limits; runaway electrons can burn through
//    the wall), quenches, coolant loss, vacuum leaks and tritium releases. No meltdown. The First Wall, Divertor and
//    blanket wear and become activated; a Remote Handling Arm replaces them while it's shut down.
//  - It's a radiation source while it runs (neutrons, see RadiationSource) and after (its activated parts).
public class FusionReactorBlockEntity extends MachineBlockEntity implements MultiblockController, RadiationSource {
    public static final int TANK = 256_000;
    public static final int HISTORY = 60;
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.HOT_LEG, SideMode.ENERGY);
    private static final Set<SideMode> PUSHED = EnumSet.of(SideMode.OUTPUT, SideMode.HOT_LEG);
    private static final int STRUCTURE_CHECK_INTERVAL = 40;
    private static final int SCAN_INTERVAL = 100;
    private static final int KLAXON_INTERVAL = 40;
    // Vacuum (atm) a plasma needs, and at which what's left is no impurity at all.
    public static final double VACUUM_READY = 1.0E-5, VACUUM_CLEAN = 1.0E-7, VACUUM_FLOOR = 1.0E-8;
    // The coils' critical temperatures (K): at full field, and at the Liquid Nitrogen share of it.
    public static final double TC_FULL = 20.0, TC_NITROGEN = 85.0, AMBIENT_K = 293.0;
    // How hot the First Wall and blanket may get (°C) before they fail.
    public static final double WALL_LIMIT = 1_300.0, BLANKET_LIMIT = 900.0;
    // Fuel puffed in to start a plasma (mB).
    private static final double START_GAS = 4.0;

    public enum FuelMode { DT, DHE3 }

    // --- The structure ---
    private TokamakStructure.@Nullable Shape shape;
    private boolean checkRequested = true;
    private int redstone;
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();
    // What the last scan found.
    private int firstWalls, divertors, blankets, toroidalCoils, poloidalCoils, solenoids, vesselPorts, windows, quenchUnits;
    private final List<BlockPos> modules = new ArrayList<>();
    private final List<BlockPos> portBlocks = new ArrayList<>();
    private final List<BlockPos> glass = new ArrayList<>();
    private final int[] moduleCounts = new int[FusionModuleBlock.Kind.values().length];
    private boolean scanned;

    // --- The plant ---
    private final ConsumerEnergyHandler energy;
    // FE spent on the Central Solenoid and the coils this second, and last second's average a tick (for the GUI).
    private long feSpent;
    private int energyUse;
    private final FilteredFluidTank water, hotCoolant, leadLithium, hotLeadLithium, liquidHelium, liquidNitrogen;
    private final ResourceHandler<FluidResource> input;
    private final ResourceHandler<FluidResource> coolantOutput;
    private final ResourceHandler<FluidResource> hotLegOutput;
    private final ResourceHandler<FluidResource> allFluids;
    // Vacuum (atm), its leak (decades a second it rises), the coils' temperature (K), the field (T), the FE paid toward
    // energising the coils and the Central Solenoid's charge (FE).
    private double vacuum = 1.0, leak;
    private double coilTemp = AMBIENT_K, field;
    private boolean energised;
    private double magnetPaid, csCharge, csNeeded;
    // Heat not yet carried off (HU), as the wall's and blanket's temperatures; this second's heat to carry (HU/t).
    private double wallHeat, blanketHeat, wallPerTick, blanketPerTick;
    // Wear (0 new to 1 worn out), coil damage, and the activated parts' activity (mSv/h at a block, decaying).
    private double fwWear, divWear, blanketWear, coilDamage, activation;
    // Neutron fluence through the wall (MW·s a block), for what Activated Components carry.
    private double fluence;

    // --- Controls ---
    private int heatingSetpoint = 50, densitySetpoint = 85, currentSetpoint = 100, fieldSetpoint = 100;
    private FuelMode fuelMode = FuelMode.DT;
    private boolean run, repeat = true, fastShutdown;

    // --- The plasma ---
    private @Nullable PlasmaSim sim;
    private PlasmaSim.Event lastEvent = PlasmaSim.Event.NONE;
    private double heatDelivered, fuelDelivered, pumping, bredRate, burnRate, neutronMegawatts, thermalPerTick;
    private int alarms, silenced;
    private final int[][] history = new int[3][HISTORY];
    private int historyHead;
    // Synced to clients: whether a plasma burns, how bright (0-15), and the field's share, for the renderer and sound.
    private int glow;
    private boolean alive;
    private final ContainerData data;

    public FusionReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FUSION_REACTOR.get(), pos, state, 0, (slot, resource) -> false, Set.<UpgradeType>of(),
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE), SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(maxEnergy(), maxEnergy(), this::setChanged);
        // The wall's coolant: Water (to Hot Coolant, for a Steam Generator) or Helium (to Hot Helium, for a Helium Gas Turbine).
        this.water = new FilteredFluidTank(TANK, resource -> resource.is(Fluids.WATER) || resource.is(ModFluids.HELIUM.get()), this::setChanged);
        this.hotCoolant = new FilteredFluidTank(TANK, resource -> resource.is(ModFluids.HOT_COOLANT.get()) || resource.is(ModFluids.HOT_HELIUM.get()),
                this::setChanged);
        this.leadLithium = new FilteredFluidTank(TANK, resource -> resource.is(ModFluids.LEAD_LITHIUM.get()), this::setChanged);
        this.hotLeadLithium = new FilteredFluidTank(TANK, resource -> resource.is(ModFluids.HOT_LEAD_LITHIUM.get()), this::setChanged);
        this.liquidHelium = new FilteredFluidTank(TANK / 4, resource -> resource.is(ModFluids.LIQUID_HELIUM.get()), this::setChanged);
        this.liquidNitrogen = new FilteredFluidTank(TANK / 4, resource -> resource.is(ModFluids.LIQUID_NITROGEN.get()), this::setChanged);
        this.input = new CombinedResourceHandler<>(new SidedResourceHandler<>(water, true, false, Integer.MAX_VALUE),
                new SidedResourceHandler<>(leadLithium, true, false, Integer.MAX_VALUE), new SidedResourceHandler<>(liquidHelium, true, false, Integer.MAX_VALUE),
                new SidedResourceHandler<>(liquidNitrogen, true, false, Integer.MAX_VALUE));
        this.coolantOutput = new SidedResourceHandler<>(hotCoolant, false, true, Integer.MAX_VALUE);
        this.hotLegOutput = new SidedResourceHandler<>(hotLeadLithium, false, true, Integer.MAX_VALUE);
        this.allFluids = new CombinedResourceHandler<>(input, coolantOutput, hotLegOutput);
        this.data = new WideIntContainerData(FusionReactorMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return dataValue(index);
            }
        };
    }

    // The FE the controller holds: enough for the biggest Central Solenoid's charge and the field coils' energising.
    public static int maxEnergy() {
        long solenoid = (long) (swing(TokamakLayout.MAX_RADIUS, maxField(TokamakLayout.MAX_RADIUS)) * ArcforgeConfig.FUSION_CS_FE_PER_WEBER.getAsInt());
        long magnets = (long) TokamakLayout.MAX_RADIUS * TokamakLayout.MAX_RADIUS * ArcforgeConfig.FUSION_MAGNET_FE.getAsInt();
        return (int) Math.min(Integer.MAX_VALUE - 1L, Math.max(10_000_000L, (solenoid + magnets) * 5 / 4));
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(0, (slot, resource) -> false, Set.of(), () -> {});
    }

    // --- Physics parameters and sizes ---

    public static PlasmaSim.Params params() {
        PlasmaSim.Params p = new PlasmaSim.Params();
        p.hFactor = ArcforgeConfig.FUSION_H_FACTOR.getAsDouble();
        p.stellaratorH = ArcforgeConfig.FUSION_STELLARATOR_H.getAsDouble();
        p.betaNLimit = ArcforgeConfig.FUSION_BETA_LIMIT.getAsDouble();
        p.greenwaldLimit = ArcforgeConfig.FUSION_GREENWALD_LIMIT.getAsDouble();
        p.dhe3Boost = ArcforgeConfig.FUSION_DHE3_BOOST.getAsDouble();
        return p;
    }

    // A tokamak's full toroidal field (T) at a major radius: the smallest strongest, stepping down to the largest.
    public static double maxField(int radius) {
        double small = ArcforgeConfig.FUSION_FIELD_SMALL.getAsDouble(), large = ArcforgeConfig.FUSION_FIELD_LARGE.getAsDouble();
        double t = (radius - TokamakLayout.MIN_RADIUS) / (double) (TokamakLayout.MAX_RADIUS - TokamakLayout.MIN_RADIUS);
        return small + (large - small) * Mth.clamp(t, 0.0, 1.0);
    }

    // The Central Solenoid's flux swing (Wb) at a field: what PlasmaSim gets.
    private static double swing(int radius, double field) {
        return new PlasmaSim(new TokamakLayout(radius).majorRadiusMetres(), false, params()).swingAt(field);
    }

    public boolean isStellarator() {
        return shape != null && shape.stellarator();
    }

    public int getRadius() {
        return shape != null ? shape.radius() : 0;
    }

    // Cold enough to start on: below Liquid Helium's critical temperature while there's Liquid Helium to cool with (so a
    // pulse doesn't light on the nitrogen-level field partway through the cool-down, then lose its flux to the field's
    // ramp to full), else below Liquid Nitrogen's.
    public boolean coldEnough() {
        return coilTemp <= (liquidHelium.getAmount() > 0 ? TC_FULL : TC_NITROGEN);
    }

    // The field the coils go to now (T): the setpoint's share of full, less for Liquid Nitrogen cooling and a stellarator,
    // and for coil damage.
    public double fieldTarget() {
        if (shape == null) {
            return 0.0;
        }
        double full = maxField(shape.radius()) * (shape.stellarator() ? ArcforgeConfig.FUSION_STELLARATOR_FIELD.getAsDouble() : 1.0);
        double cooled = coilTemp <= TC_FULL ? 1.0 : coilTemp <= TC_NITROGEN ? ArcforgeConfig.FUSION_LN2_FIELD.getAsDouble() : 0.0;
        return full * Math.min(cooled, fieldSetpoint / 100.0) * (1.0 - coilDamage);
    }

    // --- Structure ---

    @Override
    public void onLoad() {
        super.onLoad();
        TokamakStructure.register(this);
    }

    @Override
    public void setRemoved() {
        TokamakStructure.unregister(this);
        super.setRemoved();
    }

    @Override
    public boolean isFormed() {
        return shape != null;
    }

    public TokamakStructure.@Nullable Shape getShape() {
        return shape;
    }

    public void requestCheck() {
        checkRequested = true;
        scanned = false;
    }

    public void checkNow() {
        if (level instanceof ServerLevel serverLevel) {
            checkRequested = false;
            updateFormed(serverLevel);
        }
    }

    // Breaking the controller un-forms the rest of the reactor (and ends a plasma, as a disruption would).
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && shape != null) {
            if (sim != null && sim.alive) {
                disrupt(serverLevel, PlasmaSim.Event.DISRUPTION_COLLAPSE);
            }
            TokamakStructure.unform(serverLevel, shape, pos);
            shape = null;
            sim = null;
        }
    }

    private void updateFormed(ServerLevel level) {
        if (shape != null && !(level.isLoaded(shape.min()) && level.isLoaded(shape.max()))) {
            return;
        }
        TokamakStructure.Shape found = TokamakStructure.find(level, worldPosition, shape);
        if (Objects.equals(found, shape)) {
            return;
        }
        if (shape != null) {
            if (sim != null && sim.alive) {
                disrupt(level, PlasmaSim.Event.DISRUPTION_COLLAPSE);
            }
            TokamakStructure.unform(level, shape, null);
        }
        boolean changedSize = found == null || shape == null || found.radius() != shape.radius() || found.stellarator() != shape.stellarator();
        shape = found;
        if (changedSize) {
            sim = null;
            field = 0.0;
            energised = false;
            magnetPaid = 0.0;
            csCharge = 0.0;
        }
        scanned = false;
        if (found != null) {
            TokamakStructure.form(level, found, worldPosition);
            MultiblockEffects.formed(level, found.min(), found.max());
            ArcforgeAdvancements.formed(level, this, null);
            scan(level);
        } else {
            vacuum = 1.0;
        }
        setLit(false);
        setChanged();
        sync();
    }

    // Counts what the reactor is built of and finds what's round it: the modules on its Vessel Ports, its Quench
    // Protection Units, the wall blocks holding ports and its windows.
    private void scan(ServerLevel level) {
        scanned = true;
        modules.clear();
        portBlocks.clear();
        glass.clear();
        java.util.Arrays.fill(moduleCounts, 0);
        firstWalls = divertors = blankets = toroidalCoils = poloidalCoils = solenoids = vesselPorts = windows = quenchUnits = 0;
        if (shape == null) {
            return;
        }
        TokamakLayout layout = shape.layout();
        int ro = layout.outer(), top = layout.top();
        BlockPos axis = shape.axis();
        firstWalls = layout.count(TokamakLayout.Part.FIRST_WALL);
        divertors = layout.count(TokamakLayout.Part.DIVERTOR);
        blankets = layout.count(TokamakLayout.Part.BLANKET);
        toroidalCoils = layout.count(TokamakLayout.Part.TOROIDAL_COIL);
        poloidalCoils = layout.count(TokamakLayout.Part.POLOIDAL_COIL);
        solenoids = layout.count(TokamakLayout.Part.CENTRAL_SOLENOID);
        for (int dx = -ro; dx <= ro; dx++) {
            for (int dz = -ro; dz <= ro; dz++) {
                int d = TokamakLayout.distance(dx, dz);
                if (d > ro) {
                    continue;
                }
                for (int y = -top; y <= top; y++) {
                    TokamakLayout.Part part = layout.at(d, y);
                    if (part != TokamakLayout.Part.CRYOSTAT_WALL && part != TokamakLayout.Part.CRYOSTAT_LID) {
                        continue;
                    }
                    BlockPos pos = axis.offset(dx, y, dz);
                    BlockState state = level.getBlockState(pos);
                    if (!MultiblockPorts.faces(level, pos).isEmpty()) {
                        portBlocks.add(pos);
                    }
                    if (part != TokamakLayout.Part.CRYOSTAT_WALL) {
                        continue;
                    }
                    Direction out = shape.outward(pos);
                    BlockPos beside = pos.relative(out);
                    if (state.is(ModBlocks.REACTOR_GLASS.get())) {
                        windows++;
                        glass.add(pos);
                    }
                    BlockState outside = level.getBlockState(beside);
                    if (outside.is(ModBlocks.QUENCH_PROTECTION_UNIT.get())) {
                        quenchUnits++;
                    }
                    if (state.is(ModBlocks.VESSEL_PORT.get())) {
                        vesselPorts++;
                        if (level.getBlockEntity(beside) instanceof FusionModuleBlockEntity module && module.getFacing() == out.getOpposite()) {
                            modules.add(beside);
                            moduleCounts[module.kind().ordinal()]++;
                        }
                    }
                }
            }
        }
    }

    private List<FusionModuleBlockEntity> modules(ServerLevel level, FusionModuleBlock.Kind kind) {
        List<FusionModuleBlockEntity> found = new ArrayList<>();
        for (BlockPos pos : modules) {
            if (level.getBlockEntity(pos) instanceof FusionModuleBlockEntity module && module.kind() == kind) {
                found.add(module);
            }
        }
        return found;
    }

    public int moduleCount(FusionModuleBlock.Kind kind) {
        return moduleCounts[kind.ordinal()];
    }

    public boolean hasDiagnostics() {
        return moduleCount(FusionModuleBlock.Kind.DIAGNOSTICS) > 0;
    }

    // --- Ticking ---

    public void serverTick(ServerLevel level) {
        if (checkRequested || level.getGameTime() % STRUCTURE_CHECK_INTERVAL == 0) {
            checkRequested = false;
            updateFormed(level);
        }
        if (shape == null) {
            status = MachineStatus.NOT_FORMED;
            setLit(false);
            energyUse = 0;
            return;
        }
        if (!scanned || level.getGameTime() % SCAN_INTERVAL == 0) {
            scan(level);
        }
        portDefaults.tick(level, this);
        if (level.getGameTime() % 10 == 0) {
            redstone = redstone(level);
        }
        chargeSolenoid();
        carryHeat();
        pushOutputs(level);
        if (level.getGameTime() % 20 == 0) {
            energyUse = (int) (feSpent / 20);
            feSpent = 0;
            second(level);
        }
    }

    public void clientTick() {
        clientHook.accept(this);
    }

    // The client's sound (see FusionReactorSound), set by the client.
    public static java.util.function.Consumer<FusionReactorBlockEntity> clientHook = reactor -> {};

    private int redstone(ServerLevel level) {
        int best = 0;
        for (BlockPos pos : portBlocks) {
            best = Math.max(best, level.getBestNeighborSignal(pos));
        }
        return Math.max(best, level.getBestNeighborSignal(worldPosition));
    }

    // Whether the operator wants a plasma: Run in the GUI or through the API; on High, a redstone signal instead (it
    // starts the reactor and holds it running); on Low, Run unless a signal stops it.
    private boolean wantsPlasma() {
        if (!controlState.isEnabled()) {
            return false;
        }
        boolean signal = redstone > 0;
        return switch (redstoneMode) {
            case HIGH -> signal;
            case LOW -> run && !signal;
            default -> run;
        };
    }

    // The Central Solenoid charges from the controller's FE while the operator wants a plasma and none burns.
    private void chargeSolenoid() {
        if (shape == null || shape.stellarator()) {
            csNeeded = 0.0;
            csCharge = 0.0;
            return;
        }
        csNeeded = swing(shape.radius(), Math.max(field, fieldTarget())) * ArcforgeConfig.FUSION_CS_FE_PER_WEBER.getAsInt();
        if (!wantsPlasma() || sim != null && sim.alive || csCharge >= csNeeded) {
            return;
        }
        int take = (int) Math.min(energy.getAmountAsInt(), Math.ceil(csNeeded - csCharge));
        if (take > 0 && energy.consume(take)) {
            feSpent += take;
            csCharge += take;
        }
    }

    public boolean solenoidCharged() {
        return shape != null && (shape.stellarator() || csNeeded > 0.0 && csCharge >= csNeeded * 0.999);
    }

    // This second's heat (HU/t) into the Lead-Lithium and the wall's coolant, a tick at a time. Water cools into Hot
    // Coolant; Helium into Hot Helium, which also takes whatever of the blanket's heat the Lead-Lithium can't (a
    // helium-cooled blanket: the Lead-Lithium still breeds, and the gas carries the rest).
    private void carryHeat() {
        wallHeat += wallPerTick;
        blanketHeat += blanketPerTick;
        double perLead = ArcforgeConfig.FUSION_LEAD_LITHIUM_HEAT.getAsDouble();
        int heated = (int) Math.min(Math.min(leadLithium.getAmount(), hotLeadLithium.getSpace()), Math.floor(blanketHeat / perLead));
        if (heated > 0) {
            take(leadLithium, heated);
            hotLeadLithium.set(0, FluidResource.of(ModFluids.HOT_LEAD_LITHIUM.get()), hotLeadLithium.getAmount() + heated);
            blanketHeat -= heated * perLead;
        }
        boolean helium = heliumCooled();
        Fluid hot = helium ? ModFluids.HOT_HELIUM.get() : ModFluids.HOT_COOLANT.get();
        if (water.getAmount() <= 0 || hotCoolant.getAmount() > 0 && !hotCoolant.contains(hot)) {
            return; // nothing to cool with, or the hot side still holds the other loop's fluid
        }
        double per = helium ? ArcforgeConfig.FUSION_HOT_HELIUM_HEAT.getAsDouble() : ArcforgeConfig.REACTOR_HOT_COOLANT_HEAT.getAsDouble();
        double heat = wallHeat + (helium ? blanketHeat : 0.0);
        int cooled = (int) Math.min(Math.min(water.getAmount(), hotCoolant.getSpace()), Math.floor(heat / per));
        if (cooled > 0) {
            take(water, cooled);
            hotCoolant.set(0, FluidResource.of(hot), hotCoolant.getAmount() + cooled);
            double carried = cooled * per;
            double fromWall = Math.min(wallHeat, carried);
            wallHeat -= fromWall;
            blanketHeat = Math.max(0.0, blanketHeat - (carried - fromWall));
        }
    }

    // Whether the wall's coolant is Helium (the gas loop) rather than Water.
    public boolean heliumCooled() {
        return water.contains(ModFluids.HELIUM.get()) || water.getAmount() <= 0 && hotCoolant.contains(ModFluids.HOT_HELIUM.get());
    }

    private static void take(FilteredFluidTank tank, int amount) {
        int left = tank.getAmount() - amount;
        tank.set(0, left > 0 ? tank.getResource(0) : FluidResource.EMPTY, Math.max(0, left));
    }

    // The wall's and blanket's temperatures (°C) from the heat they hold.
    public double wallTemperature() {
        return 150.0 + wallHeat / Math.max(1.0, (firstWalls + divertors) * 400.0);
    }

    public double blanketTemperature() {
        return 300.0 + blanketHeat / Math.max(1.0, blankets * 600.0);
    }

    // Hot fluids out of their ports, every tick, through the ports on the walls.
    private void pushOutputs(ServerLevel level) {
        if (hotCoolant.getAmount() <= 0 && hotLeadLithium.getAmount() <= 0) {
            return;
        }
        for (BlockPos pos : portBlocks) {
            PortFaces faces = MultiblockPorts.faces(level, pos);
            for (Direction side : Direction.values()) {
                SideMode mode = faces.get(side);
                if (!PUSHED.contains(mode) || isInside(pos.relative(side))) {
                    continue;
                }
                ResourceHandler<FluidResource> from = getFluidHandler(mode);
                ResourceHandler<FluidResource> into = from != null ? level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(side), side.getOpposite()) : null;
                if (into != null) {
                    ResourceHandlerUtil.move(from, into, resource -> true, TANK, null);
                }
            }
        }
    }

    // --- One second of the plant ---

    private void second(ServerLevel level) {
        TokamakLayout layout = shape.layout();
        PlasmaSim plasma = sim();
        boolean wanted = wantsPlasma();
        List<FusionModuleBlockEntity> cryopumps = modules(level, FusionModuleBlock.Kind.CRYOPUMP);
        // Vacuum: the running Cryopumps bring it down a decade at a time; a leak or no pumps let it back up.
        int pumps = 0;
        int pumpSpace = 0;
        for (FusionModuleBlockEntity pump : cryopumps) {
            if (pump.pump()) {
                pumps++;
                FilteredFluidTank exhaust = pump.getExhaust();
                pumpSpace += exhaust != null ? exhaust.getSpace() : 0;
            }
        }
        double decade = Math.log10(Math.max(VACUUM_FLOOR, vacuum));
        if (pumps > 0) {
            double size = Math.pow(shape.radius() / 4.0, 3.0);
            decade -= 7.0 * pumps / (ArcforgeConfig.FUSION_PUMPDOWN.getAsInt() * size);
        } else {
            decade += 0.02;
        }
        decade += leak;
        vacuum = Math.pow(10.0, Mth.clamp(decade, Math.log10(VACUUM_FLOOR), 0.0));
        if (leak > 0.0 && !plasma.alive) {
            // An idle vessel's seals bed back in slowly.
            leak = Math.max(0.0, leak - 0.0005);
        }
        magnets(level, wanted, plasma.alive);
        // Heating: share the setpoint across the heaters, gyrotrons first (they start the plasma).
        double ech = 0.0, nbi = 0.0;
        double heatWanted = wanted ? heatingSetpoint : 0.0;
        boolean ready = vacuum <= VACUUM_READY && coldEnough() && field > 0.2 && field >= fieldTarget() * 0.95 && solenoidCharged() && fwWear < 1.0
                && divWear < 1.0;
        if (!plasma.alive && !ready) {
            heatWanted = 0.0;
        }
        for (FusionModuleBlockEntity gyrotron : modules(level, FusionModuleBlock.Kind.GYROTRON)) {
            double got = gyrotron.heat(Math.min(heatWanted - ech - nbi, gyrotron.available()));
            ech += got;
        }
        for (FusionModuleBlockEntity injector : modules(level, FusionModuleBlock.Kind.NEUTRAL_BEAM_INJECTOR)) {
            double got = injector.heat(Math.min(heatWanted - ech - nbi, injector.available()));
            nbi += got;
        }
        heatDelivered = ech + nbi;
        // Fuel: to the density setpoint, its mix even.
        double electronsPerIon = fuelMode == FuelMode.DHE3 ? 1.5 : 1.0;
        double demand = plasma.alive ? plasma.fuelDemand(wanted ? densitySetpoint / 100.0 : 0.0, electronsPerIon) : wanted && ready ? START_GAS : 0.0;
        double[] fuel = fuel(level, demand, plasma);
        fuelDelivered = fuel[0] + fuel[1] + fuel[2];
        plasma.srcD = fuel[0];
        plasma.srcT = fuel[1];
        plasma.srcHe3 = fuel[2];
        plasma.pEch = ech;
        plasma.pNbi = nbi;
        plasma.bField = field;
        plasma.ipTarget = !shape.stellarator() && wanted ? plasma.maxCurrent() * currentSetpoint / 100.0 : 0.0;
        plasma.pfControl = pfControl();
        plasma.impurity = impurity();
        pumping = pumps > 0 && pumpSpace > 0 ? Math.min(1.0, pumps * ArcforgeConfig.FUSION_CRYOPUMP_SPEED.getAsDouble()) : 0.0;
        plasma.pumping = pumping;
        plasma.ready = ready;
        plasma.fastShutdown = fastShutdown;
        fastShutdown = false;
        boolean wasAlive = plasma.alive;
        PlasmaSim.Event event = plasma.step();
        // Switched off: a tokamak ramps its current down and goes out quietly once it's low; a stellarator just goes out.
        if (plasma.alive && !wanted && (shape.stellarator() || plasma.ip <= 0.2)) {
            plasma.quench();
            event = PlasmaSim.Event.SHUTDOWN;
        }
        if (event != PlasmaSim.Event.NONE) {
            lastEvent = event;
            onEvent(level, event, wasAlive);
        }
        // The Central Solenoid's charge follows its flux through the pulse.
        if (!shape.stellarator() && plasma.alive && plasma.fluxSwing > 0.0) {
            csCharge = csNeeded * plasma.flux / plasma.fluxSwing;
        } else if (event == PlasmaSim.Event.PULSE_END || event.name().startsWith("DISRUPTION") || event == PlasmaSim.Event.SHUTDOWN) {
            csCharge = 0.0;
            if (event == PlasmaSim.Event.PULSE_END && !repeat) {
                run = false;
            }
        }
        output(level, plasma, cryopumps, layout);
        wear(level, plasma, layout);
        alive = plasma.alive;
        status = !controlState.isEnabled() ? MachineStatus.DISABLED : plasma.alive ? MachineStatus.FUSING : wanted ? MachineStatus.STARTING
                : MachineStatus.IDLE;
        setLit(plasma.alive);
        record(plasma);
        alarms(level, plasma);
        int newGlow = plasma.alive ? Mth.clamp((int) Math.round(Math.sqrt(Math.max(0.0, plasma.t)) * 3.5), 1, 15) : 0;
        if (newGlow != glow || event != PlasmaSim.Event.NONE) {
            glow = newGlow;
            sync();
        }
        if (plasma.alive && level.getGameTime() % 200 == 0) {
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, null, plasma.ignited() ? "fusion_ignition" : "fusion_plasma");
            if (plasma.pFusion >= plasma.pHeating && plasma.pHeating > 0.0) {
                ArcforgeAdvancements.produced(this, ItemStack.EMPTY, null, "fusion_breakeven");
            }
            if (fuelMode == FuelMode.DHE3) {
                ArcforgeAdvancements.produced(this, ItemStack.EMPTY, null, "fusion_dhe3");
            }
        }
        setChanged();
    }

    // The coils: cooled by Liquid Helium (else Liquid Nitrogen) while they're wanted, energised up to the field target,
    // and quenched if they warm past their critical temperature while energised.
    private void magnets(ServerLevel level, boolean wanted, boolean burning) {
        boolean cooling = wanted || burning || field > 0.0;
        double target = AMBIENT_K;
        if (cooling) {
            int helium = (int) Math.ceil(ArcforgeConfig.FUSION_MAGNET_LHE.getAsInt() * shape.radius() * (1.0 + Math.max(0.0, coilTemp - 4.5) / 30.0));
            int nitrogen = (int) Math.ceil(ArcforgeConfig.FUSION_MAGNET_LN2.getAsInt() * shape.radius() * (1.0 + Math.max(0.0, coilTemp - 77.0) / 60.0));
            if (helium > 0 && liquidHelium.getAmount() >= helium) {
                take(liquidHelium, helium);
                target = 4.5;
            } else if (nitrogen > 0 && liquidNitrogen.getAmount() >= nitrogen) {
                take(liquidNitrogen, nitrogen);
                target = 77.0;
            }
        }
        coilTemp += (target - coilTemp) * (target < coilTemp ? 0.02 : 0.004);
        double critical = field <= maxField(shape.radius()) * ArcforgeConfig.FUSION_LN2_FIELD.getAsDouble() + 0.01 ? TC_NITROGEN : TC_FULL;
        if (field > 0.1 && coilTemp > critical) {
            quench(level);
            return;
        }
        double goal = cooling && coilTemp <= TC_NITROGEN && coilDamage < 1.0 ? fieldTarget() : 0.0;
        if (field < goal) {
            // Energising costs FE in proportion to the field's energy (B²), paid as it ramps.
            double full = maxField(shape.radius());
            double cost = (double) shape.radius() * shape.radius() * ArcforgeConfig.FUSION_MAGNET_FE.getAsInt();
            double next = Math.min(goal, field + 0.25);
            double price = cost * (next * next - field * field) / (full * full);
            int pay = (int) Math.min(energy.getAmountAsInt(), Math.ceil(price));
            if (pay > 0 && energy.consume(pay)) {
                feSpent += pay;
                field = pay >= price ? next : Math.sqrt(field * field + full * full * pay / cost);
            }
            energised = field > 0.0;
        } else if (field > goal) {
            field = Math.max(goal, field - 0.5);
            energised = field > 0.0;
        }
    }

    // A coil went normal while energised: its stored energy dumps into it. Without a Quench Protection Unit it's damaged.
    private void quench(ServerLevel level) {
        double share = field / Math.max(0.1, maxField(shape.radius()));
        field = 0.0;
        energised = false;
        alarms |= FusionAlarm.QUENCH.bit;
        if (quenchUnits <= 0 && ArcforgeConfig.FUSION_QUENCH_DAMAGE.getAsBoolean()) {
            coilDamage = Math.min(1.0, coilDamage + 0.25 * share);
        }
        if (sim != null && sim.alive) {
            disrupt(level, PlasmaSim.Event.DISRUPTION_COLLAPSE);
        }
        Vec3 at = Vec3.atCenterOf(shape.axis());
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5F, 0.5F);
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + shape.layout().top(), at.z, 40, shape.radius(), 0.5, shape.radius(), 0.05);
    }

    // Fuel this second (mB): {deuterium, tritium, helium-3}, from the Pellet Injectors (cold fuel pellets, Liquid Helium
    // to freeze them) then the Gas Puff Valves (half of their gas reaches the plasma; the rest goes to the exhaust).
    private double[] fuel(ServerLevel level, double demand, PlasmaSim plasma) {
        double[] got = new double[3];
        if (demand <= 0.0) {
            return got;
        }
        // The mix: tritium (or helium-3) to bring the plasma's share back to even.
        double partner = fuelMode == FuelMode.DHE3 ? plasma.nHe3 : plasma.nT;
        double share = plasma.nD + partner > 1.0E-6 ? partner / (plasma.nD + partner) : 0.5;
        double partnerShare = Mth.clamp(0.5 + (0.5 - share) * 2.0, 0.0, 1.0);
        double want = demand;
        for (FusionModuleBlockEntity injector : modules(level, FusionModuleBlock.Kind.PELLET_INJECTOR)) {
            if (want <= 0.0) {
                break;
            }
            double shot = Math.min(want, ArcforgeConfig.FUSION_PELLET_RATE.getAsInt());
            int helium = (int) Math.ceil(shot / 20.0);
            if (injector.amount(ModFluids.LIQUID_HELIUM.get()) < helium) {
                continue;
            }
            double delivered = draw(injector, shot, partnerShare, got, 1.0);
            if (delivered > 0.0) {
                injector.take(ModFluids.LIQUID_HELIUM.get(), helium);
            }
            want -= delivered;
        }
        for (FusionModuleBlockEntity valve : modules(level, FusionModuleBlock.Kind.GAS_PUFF_VALVE)) {
            if (want <= 0.0) {
                break;
            }
            // Twice the gas, for half to reach the plasma.
            double puff = Math.min(want * 2.0, ArcforgeConfig.FUSION_PUFF_RATE.getAsInt());
            want -= draw(valve, puff, partnerShare, got, 0.5);
        }
        return got;
    }

    // Takes amount mB from a module in the mix, deuterium first then its partner (either alone if that's all it has);
    // adds efficiency of it to got and the rest to the exhaust. Gives what reached the plasma.
    private double draw(FusionModuleBlockEntity module, double amount, double partnerShare, double[] got, double efficiency) {
        net.minecraft.world.level.material.Fluid partnerFluid = fuelMode == FuelMode.DHE3 ? ModFluids.HELIUM_3.get() : ModFluids.TRITIUM.get();
        int partnerIndex = fuelMode == FuelMode.DHE3 ? 2 : 1;
        int wantPartner = (int) Math.round(amount * partnerShare);
        int wantD = (int) Math.round(amount) - wantPartner;
        int d = module.take(ModFluids.DEUTERIUM.get(), wantD);
        int p = module.take(partnerFluid, wantPartner + (wantD - d));
        if (d < wantD && p < wantPartner + (wantD - d)) {
            d += module.take(ModFluids.DEUTERIUM.get(), wantPartner - p);
        }
        got[0] += d * efficiency;
        got[partnerIndex] += p * efficiency;
        double lost = (d + p) * (1.0 - efficiency);
        if (lost > 0.0) {
            puffLostD += d * (1.0 - efficiency);
            if (partnerIndex == 1) {
                puffLostT += p * (1.0 - efficiency);
            } else {
                puffLostHe3 += p * (1.0 - efficiency);
            }
        }
        return (d + p) * efficiency;
    }

    private double puffLostD, puffLostT, puffLostHe3;

    // The Poloidal Field coils' hold on the plasma's position against what it needs (1: just enough). Energised with the
    // field; damaged coils hold less.
    private double pfControl() {
        if (shape == null || field <= 0.0) {
            return 0.0;
        }
        int needed = Math.max(1, shape.layout().count(TokamakLayout.Part.POLOIDAL_COIL));
        return 1.3 * Math.min(1.0, poloidalCoils / (double) needed) * (1.0 - coilDamage);
    }

    // The plasma's impurity: a clean plasma's, what's left of the air, and what the worn wall gives off.
    public double impurity() {
        double air = Mth.clamp((Math.log10(Math.max(VACUUM_FLOOR, vacuum)) - Math.log10(VACUUM_CLEAN)) / 2.0, 0.0, 1.0) * 0.02;
        double worn = Math.max(fwWear, divWear);
        return ArcforgeConfig.FUSION_BASE_IMPURITY.getAsDouble() + air + 0.01 * worn * worn;
    }

    // The blanket's tritium breeding ratio: config's for a whole blanket, less a little for each port through it, less for
    // wear; none without Lead-Lithium in it.
    public double tbr() {
        if (shape == null || leadLithium.getAmount() <= 0 && blanketHeat <= 0.0) {
            return 0.0;
        }
        double coverage = Math.max(0.0, 1.0 - 0.01 * vesselPorts);
        return ArcforgeConfig.FUSION_TBR.getAsDouble() * coverage * (1.0 - 0.5 * blanketWear);
    }

    private PlasmaSim sim() {
        if (sim == null) {
            sim = new PlasmaSim(shape.layout().majorRadiusMetres(), shape.stellarator(), params());
        }
        return sim;
    }

    public @Nullable PlasmaSim getSim() {
        return sim;
    }

    // --- What a second's plasma gives ---

    private void output(ServerLevel level, PlasmaSim plasma, List<FusionModuleBlockEntity> cryopumps, TokamakLayout layout) {
        double huPerMw = ArcforgeConfig.FUSION_HU_PER_MW.getAsInt();
        double gain = ArcforgeConfig.FUSION_BLANKET_GAIN.getAsDouble();
        double tbr = tbr();
        double caught = tbr > 0.0 ? Math.max(0.0, 1.0 - 0.01 * vesselPorts) : 0.0;
        double blanketMw = plasma.pNeutron * caught * gain;
        double wallMw = plasma.pTransport + plasma.pBrems + plasma.pImpurity + plasma.pNeutron * (1.0 - caught) * 0.5;
        // Direct Energy Converters take the charged particles streaming out of the plasma (its transport loss) before they
        // reach the divertor, and make FE of them straight away; what they take doesn't heat the wall.
        double charged = plasma.alive ? Math.min(plasma.pTransport, wallMw) : 0.0, converted = 0.0;
        for (FusionModuleBlockEntity converter : modules(level, FusionModuleBlock.Kind.DIRECT_CONVERTER)) {
            converted += converter.convert(Math.max(0.0, charged - converted));
        }
        directMegawatts = converted;
        wallMw -= converted;
        blanketPerTick = blanketMw * huPerMw;
        wallPerTick = wallMw * huPerMw;
        thermalPerTick = blanketPerTick + wallPerTick;
        neutronMegawatts = plasma.pNeutron;
        // Heat that isn't carried away eases off the wall and blanket slowly (radiated, conducted), when nothing burns.
        if (!plasma.alive) {
            wallHeat *= 0.98;
            blanketHeat *= 0.98;
        }
        // The fuel cycle: what burnt, what the blanket bred, and the exhaust to the Cryopumps.
        burnRate = plasma.dtRate;
        bredRate = plasma.dtRate * tbr;
        double d = plasma.exhaustD + puffLostD, t = plasma.exhaustT + puffLostT + bredRate, he3 = plasma.exhaustHe3 + puffLostHe3;
        double ash = plasma.exhaustAsh;
        puffLostD = puffLostT = puffLostHe3 = 0.0;
        double total = d + t + he3 + ash;
        if (total <= 0.0) {
            return;
        }
        exhaustPending += total;
        int whole = (int) Math.floor(exhaustPending);
        int stored = 0;
        for (FusionModuleBlockEntity pump : cryopumps) {
            if (stored >= whole) {
                break;
            }
            stored += pump.putExhaust(whole - stored);
        }
        exhaustPending -= whole;
        double share = whole > 0 ? stored / (double) whole : 0.0;
        FusionExhaustPool.get(level.getServer()).add(d * share, t * share, he3 * share, ash * share);
        // What the pumps couldn't take is vented into the hall: a tritium release.
        double vented = (whole - stored) * (t / total);
        if (vented > 0.5) {
            alarms |= FusionAlarm.TRITIUM.bit;
            Tritium.release(level, Vec3.atCenterOf(worldPosition).relative(getFacing(), 1.5), vented);
        }
    }

    private double exhaustPending;
    // The MW the Direct Energy Converters took last second.
    private double directMegawatts;

    public double directMegawatts() {
        return directMegawatts;
    }

    // Wear: the wall by the heat through it, the divertor more; the blanket by its neutrons; the activated parts' glow.
    private void wear(ServerLevel level, PlasmaSim plasma, TokamakLayout layout) {
        double rate = ArcforgeConfig.FUSION_WALL_WEAR.getAsDouble();
        if (plasma.alive) {
            double area = Math.max(1.0, firstWalls + divertors);
            double flux = (plasma.pTransport + plasma.pBrems + plasma.pImpurity) / area;
            double hot = wallTemperature() > WALL_LIMIT * 0.8 ? 5.0 : 1.0;
            fwWear = Math.min(1.0, fwWear + rate * flux * 0.4 * hot);
            divWear = Math.min(1.0, divWear + rate * flux * 1.6 * hot);
            double neutrons = plasma.pNeutron / Math.max(1.0, blankets);
            blanketWear = Math.min(1.0, blanketWear + ArcforgeConfig.FUSION_BLANKET_WEAR.getAsDouble() * neutrons);
            fluence += plasma.pNeutron / Math.max(1.0, firstWalls + divertors + blankets);
            activation += plasma.pNeutron * 0.0005;
        }
        // Activated steel's short-lived glow fades (half in about a minute), leaving the long-lived part.
        activation *= 0.99;
        // Coolant loss: too hot, the wall and blanket fail, ending the plasma.
        if (wallTemperature() > WALL_LIMIT || blanketTemperature() > BLANKET_LIMIT) {
            alarms |= FusionAlarm.COOLANT.bit;
            if (plasma.alive) {
                fwWear = Math.min(1.0, fwWear + 0.02);
                divWear = Math.min(1.0, divWear + 0.04);
                if (wallTemperature() > WALL_LIMIT * 1.2 || blanketTemperature() > BLANKET_LIMIT * 1.2) {
                    disrupt(level, PlasmaSim.Event.DISRUPTION_COLLAPSE);
                    meltTiles(level, 2);
                }
            }
        }
        // Air into a hot vessel.
        if (vacuum > 1.0E-2 && wallTemperature() > 300.0) {
            fwWear = Math.min(1.0, fwWear + 0.002);
            divWear = Math.min(1.0, divWear + 0.002);
        }
    }

    // --- Events ---

    private void onEvent(ServerLevel level, PlasmaSim.Event event, boolean wasAlive) {
        Vec3 at = Vec3.atCenterOf(shape.axis());
        switch (event) {
            case BREAKDOWN -> level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.2F, 1.6F);
            case PULSE_END, SHUTDOWN -> level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
            case STELLARATOR_COLLAPSE -> level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0F, 0.8F);
            default -> {
                if (event.name().startsWith("DISRUPTION")) {
                    disrupt(level, event);
                }
            }
        }
    }

    // A disruption: the plasma's energy slams into the wall and divertor (heavy wear, maybe melted tiles), its current's
    // forces jerk the vessel (a vertical one can open a leak), and runaway electrons may burn through the wall at a spot.
    private void disrupt(ServerLevel level, PlasmaSim.Event kind) {
        PlasmaSim plasma = sim;
        double energyMj = plasma != null ? plasma.energy : 0.0;
        double current = plasma != null ? plasma.ip : 0.0;
        double volume = plasma != null ? plasma.volume : 1.0;
        double tritiumHeld = plasma != null ? plasma.nT * plasma.volume : 0.0;
        if (plasma != null && plasma.alive) {
            plasma.quench();
        }
        lastEvent = kind;
        alarms |= FusionAlarm.DISRUPTION.bit;
        csCharge = 0.0;
        double severity = Mth.clamp(energyMj / (0.4 * volume), 0.0, 1.0);
        double wear = ArcforgeConfig.FUSION_DISRUPTION_WEAR.getAsDouble() * severity;
        fwWear = Math.min(1.0, fwWear + wear);
        divWear = Math.min(1.0, divWear + wear * 1.5);
        Vec3 at = Vec3.atCenterOf(shape.axis());
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0F, 0.6F + (float) severity * 0.2F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.0F, 1.6F);
        RandomSource random = level.getRandom();
        if (kind == PlasmaSim.Event.DISRUPTION_VERTICAL) {
            leak = Math.max(leak, 0.05 * severity);
            alarms |= FusionAlarm.VACUUM.bit;
        }
        if (severity > 0.5 && random.nextDouble() < severity) {
            meltTiles(level, (int) Math.round(severity * (1 + shape.radius() / 3.0)));
        }
        // Runaway electrons: a tokamak's current, carried by a beam of them, can burn a hole in the wall.
        if (!shape.stellarator() && current > 3.0 && random.nextDouble() < 0.25 * severity) {
            burnThrough(level, random, tritiumHeld);
        }
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, shape.aabb().inflate(16.0))) {
            player.sendSystemMessage(Component.translatable("message.arcforge.fusion_reactor.disruption." + kind.name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(net.minecraft.ChatFormatting.RED));
        }
    }

    // Disruption heat melts a few First Wall or Divertor blocks out of the vessel (if blocks may be damaged).
    private void meltTiles(ServerLevel level, int count) {
        if (!ArcforgeConfig.FUSION_DISRUPTION_DAMAGE.getAsBoolean() || count <= 0 || shape == null) {
            return;
        }
        List<BlockPos> tiles = new ArrayList<>();
        for (BlockPos pos : shape.box()) {
            TokamakLayout.Part part = shape.partAt(pos);
            if (part == TokamakLayout.Part.FIRST_WALL || part == TokamakLayout.Part.DIVERTOR) {
                tiles.add(pos.immutable());
            }
        }
        RandomSource random = level.getRandom();
        for (int i = 0; i < count && !tiles.isEmpty(); i++) {
            BlockPos pos = tiles.remove(random.nextInt(tiles.size()));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            level.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0.0);
        }
        requestCheck();
    }

    // A runaway-electron beam burns through the outer wall at a spot on the midplane: the First Wall, the blanket behind
    // it; the vessel loses its vacuum and the tritium in the plasma gets out.
    private void burnThrough(ServerLevel level, RandomSource random, double tritiumHeld) {
        double angle = random.nextDouble() * Math.PI * 2.0;
        int r = shape.radius();
        BlockPos axis = shape.axis();
        for (int d = r; d <= r + 1; d++) {
            BlockPos pos = axis.offset((int) Math.round(Math.cos(angle) * d), 0, (int) Math.round(Math.sin(angle) * d));
            TokamakLayout.Part part = shape.partAt(pos);
            if ((part == TokamakLayout.Part.FIRST_WALL || part == TokamakLayout.Part.BLANKET) && ArcforgeConfig.FUSION_DISRUPTION_DAMAGE.getAsBoolean()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        fwWear = Math.min(1.0, fwWear + 0.05);
        leak = Math.max(leak, 0.2);
        alarms |= FusionAlarm.VACUUM.bit | FusionAlarm.TRITIUM.bit;
        if (tritiumHeld > 0.0) {
            Tritium.release(level, Vec3.atCenterOf(axis), tritiumHeld);
        }
        requestCheck();
    }

    // --- Alarms ---

    private void alarms(ServerLevel level, PlasmaSim plasma) {
        int flags = alarms & (FusionAlarm.DISRUPTION.bit | FusionAlarm.QUENCH.bit | FusionAlarm.TRITIUM.bit);
        if (plasma.alive) {
            if (!shape.stellarator() && plasma.greenwald > 0.9 * ArcforgeConfig.FUSION_GREENWALD_LIMIT.getAsDouble()) {
                flags |= FusionAlarm.DENSITY.bit;
            }
            if (!shape.stellarator() && plasma.betaN > 0.9 * ArcforgeConfig.FUSION_BETA_LIMIT.getAsDouble()) {
                flags |= FusionAlarm.BETA.bit;
            }
            if (Math.abs(plasma.z) > 0.05 * plasma.a) {
                flags |= FusionAlarm.VERTICAL.bit;
            }
            if (!shape.stellarator() && plasma.pulseLeft() < 30.0) {
                flags |= FusionAlarm.PULSE.bit;
            }
        }
        if (leak > 0.0 || energised && vacuum > VACUUM_READY) {
            flags |= FusionAlarm.VACUUM.bit;
        }
        if (wallTemperature() > WALL_LIMIT * 0.8 || blanketTemperature() > BLANKET_LIMIT * 0.8) {
            flags |= FusionAlarm.COOLANT.bit;
        }
        if (energised && coilTemp > TC_FULL * 0.8 && coilTemp <= TC_FULL || coilDamage > 0.0) {
            flags |= FusionAlarm.MAGNET.bit;
        }
        if (Math.max(fwWear, divWear) > 0.8 || blanketWear > 0.8) {
            flags |= FusionAlarm.WEAR.bit;
        }
        alarms = flags;
        silenced &= flags;
        int sounding = flags & FusionAlarm.klaxonMask() & ~silenced;
        if (sounding != 0 && level.getGameTime() % KLAXON_INTERVAL == 0) {
            level.playSound(null, worldPosition, ModSounds.REACTOR_ALARM.get(), SoundSource.BLOCKS, 2.0F, 1.2F);
        }
    }

    public void silence() {
        silenced = alarms;
        // The latched alarms (a disruption, a quench, a release) clear when silenced.
        alarms &= ~(FusionAlarm.DISRUPTION.bit | FusionAlarm.QUENCH.bit | FusionAlarm.TRITIUM.bit);
        setChanged();
    }

    public int getAlarms() {
        return alarms;
    }

    // A reading a second for the trend graphs: fusion power (MW), temperature (keV x 10) and density (x 100).
    private void record(PlasmaSim plasma) {
        history[0][historyHead] = (int) Math.round(plasma.pFusion);
        history[1][historyHead] = (int) Math.round(plasma.t * 10.0);
        history[2][historyHead] = (int) Math.round(plasma.electrons() * 100.0);
        historyHead = (historyHead + 1) % HISTORY;
    }

    // --- Controls (GUI, redstone, the machine control API) ---

    public void setRun(boolean run) {
        this.run = run;
        setChanged();
    }

    public boolean isRunning() {
        return run;
    }

    public void fastShutdown() {
        run = false;
        if (sim != null && sim.alive) {
            fastShutdown = true;
            // Massive gas injection: a burst of deuterium from the puff valves, into the exhaust.
            puffLostD += 50.0;
        }
        setChanged();
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
        setChanged();
    }

    public boolean isRepeat() {
        return repeat;
    }

    public int getHeatingSetpoint() {
        return heatingSetpoint;
    }

    public void setHeatingSetpoint(int megawatts) {
        heatingSetpoint = Mth.clamp(megawatts, 0, 1_000);
        setChanged();
    }

    public int getDensitySetpoint() {
        return densitySetpoint;
    }

    public void setDensitySetpoint(int percent) {
        densitySetpoint = Mth.clamp(percent, 0, 120);
        setChanged();
    }

    public int getCurrentSetpoint() {
        return currentSetpoint;
    }

    public void setCurrentSetpoint(int percent) {
        currentSetpoint = Mth.clamp(percent, 0, 120);
        setChanged();
    }

    public int getFieldSetpoint() {
        return fieldSetpoint;
    }

    public void setFieldSetpoint(int percent) {
        fieldSetpoint = Mth.clamp(percent, 0, 100);
        setChanged();
    }

    public FuelMode getFuelMode() {
        return fuelMode;
    }

    public void setFuelMode(FuelMode mode) {
        fuelMode = mode;
        setChanged();
    }

    // --- Service (the Remote Handling Arm) ---

    // Shut down: no plasma, the field off, so the arm can work through a port.
    public boolean isShutDown() {
        return shape != null && (sim == null || !sim.alive) && field <= 0.0;
    }

    public double wear(TokamakLayout.Part part) {
        return switch (part) {
            case FIRST_WALL -> fwWear;
            case DIVERTOR -> divWear;
            case BLANKET -> blanketWear;
            case TOROIDAL_COIL, POLOIDAL_COIL, CENTRAL_SOLENOID -> coilDamage;
            default -> 0.0;
        };
    }

    public double leak() {
        return leak;
    }

    // One block's worth of a part replaced (its share of the wear taken off); gives the activity (mSv/h at a block) the
    // removed part carries, or -1 if there was nothing to replace.
    public double replace(TokamakLayout.Part part) {
        double blocks = Math.max(1.0, switch (part) {
            case FIRST_WALL -> firstWalls;
            case DIVERTOR -> divertors;
            case BLANKET -> blankets;
            default -> toroidalCoils + poloidalCoils + solenoids;
        });
        double before = wear(part);
        if (before <= 0.0) {
            return -1.0;
        }
        double after = Math.max(0.0, before - 1.0 / blocks);
        switch (part) {
            case FIRST_WALL -> fwWear = after;
            case DIVERTOR -> divWear = after;
            case BLANKET -> blanketWear = after;
            default -> coilDamage = after;
        }
        setChanged();
        boolean coil = part == TokamakLayout.Part.TOROIDAL_COIL || part == TokamakLayout.Part.POLOIDAL_COIL || part == TokamakLayout.Part.CENTRAL_SOLENOID;
        return coil ? 0.0 : Math.min(500.0, fluence * 0.02 * (part == TokamakLayout.Part.BLANKET ? 0.5 : 1.0));
    }

    // Reseals a leak (the arm's work with a First Wall block).
    public boolean reseal() {
        if (leak <= 0.0) {
            return false;
        }
        leak = 0.0;
        setChanged();
        return true;
    }

    // --- Radiation ---

    // Gamma from the activated parts (their glow after shutdown too).
    @Override
    public double radiationRate() {
        return shape != null ? activation : 0.0;
    }

    // Neutrons while it burns: per MW leaving the plasma, before the cryostat and what's round it shield them.
    @Override
    public double neutronRate() {
        return shape != null && alive ? neutronMegawatts * ArcforgeConfig.FUSION_NEUTRON_DOSE.getAsDouble() * shape.radius() * shape.radius() : 0.0;
    }

    @Override
    public Vec3 radiationCentre() {
        return shape != null ? Vec3.atCenterOf(shape.axis()) : Vec3.atCenterOf(worldPosition);
    }

    // --- For the client: the renderer and sound ---

    public boolean isAlive() {
        return alive;
    }

    public int getGlow() {
        return glow;
    }

    public List<BlockPos> getGlass() {
        return glass;
    }

    public AABB getRenderBox() {
        return shape != null ? shape.aabb().inflate(2.0) : new AABB(worldPosition);
    }

    // --- MultiblockController ---

    @Override
    public Direction getStructureFacing() {
        return getFacing();
    }

    @Override
    public BlockPos getMinCorner() {
        return shape != null ? shape.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return shape != null ? shape.max() : worldPosition;
    }

    // The cryostat (walls and lids) and the controller: what can hold ports.
    @Override
    public boolean isPart(BlockPos pos) {
        if (shape == null) {
            return false;
        }
        TokamakLayout.Part part = shape.partAt(pos);
        return part == TokamakLayout.Part.CRYOSTAT_WALL || part == TokamakLayout.Part.CRYOSTAT_LID;
    }

    // The reactor's inside is its cylinder, not its bounding box (so the corners round it are outside).
    @Override
    public boolean isInside(BlockPos pos) {
        return shape != null && shape.contains(pos);
    }

    @Override
    public void onPortsChanged() {
        scanned = false;
    }

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (shape == null) {
            return null;
        }
        if (mode == null) {
            return allFluids;
        }
        return switch (mode) {
            case INPUT -> input;
            case OUTPUT -> coolantOutput;
            case HOT_LEG -> hotLegOutput;
            default -> null;
        };
    }

    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (shape == null) {
            return null;
        }
        if (side == null) {
            return energy;
        }
        return faceMode(pos, side) == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case FLUID, GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT || mode == SideMode.HOT_LEG ? ConnectionMode.OUTPUT
                    : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            default -> ConnectionMode.NONE;
        };
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    @Override
    protected void onSideConfigChanged() {
        setChanged();
        if (level != null && shape != null) {
            MultiblockAutomation.refresh(level, shape.min(), shape.max());
        }
    }

    // --- Getters for the GUI, Jade and the API ---

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    public FilteredFluidTank getHotCoolant() {
        return hotCoolant;
    }

    public FilteredFluidTank getLeadLithium() {
        return leadLithium;
    }

    public FilteredFluidTank getHotLeadLithium() {
        return hotLeadLithium;
    }

    public FilteredFluidTank getLiquidHelium() {
        return liquidHelium;
    }

    public FilteredFluidTank getLiquidNitrogen() {
        return liquidNitrogen;
    }

    public double getVacuum() {
        return vacuum;
    }

    public double getCoilTemp() {
        return coilTemp;
    }

    public double getField() {
        return field;
    }

    public double getCsCharge() {
        return csNeeded > 0.0 ? Mth.clamp(csCharge / csNeeded, 0.0, 1.0) : 0.0;
    }

    public PlasmaSim.Event getLastEvent() {
        return lastEvent;
    }

    public double getThermalPerTick() {
        return thermalPerTick;
    }

    public double getHeatDelivered() {
        return heatDelivered;
    }

    public double getBredRate() {
        return bredRate;
    }

    public double getBurnRate() {
        return burnRate;
    }

    // The total heating the modules could give now (MW).
    public double heatingAvailable(ServerLevel level) {
        double total = 0.0;
        for (FusionModuleBlockEntity module : modules(level, FusionModuleBlock.Kind.GYROTRON)) {
            total += module.available();
        }
        for (FusionModuleBlockEntity module : modules(level, FusionModuleBlock.Kind.NEUTRAL_BEAM_INJECTOR)) {
            total += module.available();
        }
        return total;
    }

    // The start-up checklist, in order (FusionReactorMenu.CHECK_*): vacuum, magnets cold, field up, solenoid charged,
    // heating ready, fuel ready.
    public int checklist() {
        if (shape == null) {
            return 0;
        }
        int bits = 0;
        if (vacuum <= VACUUM_READY) {
            bits |= 1;
        }
        if (coldEnough()) {
            bits |= 2;
        }
        if (field > 0.2 && field >= fieldTarget() * 0.95) {
            bits |= 4;
        }
        if (solenoidCharged()) {
            bits |= 8;
        }
        if (moduleCount(FusionModuleBlock.Kind.GYROTRON) + moduleCount(FusionModuleBlock.Kind.NEUTRAL_BEAM_INJECTOR) > 0 && heatingSetpoint > 0) {
            bits |= 16;
        }
        if (moduleCount(FusionModuleBlock.Kind.PELLET_INJECTOR) + moduleCount(FusionModuleBlock.Kind.GAS_PUFF_VALVE) > 0) {
            bits |= 32;
        }
        return bits;
    }

    // --- The GUI's data ---

    // One reading as the GUI gets it (FusionReactorMenu.DATA_*: fixed-point ints); the machine control API reads these too.
    public int dataValue(int index) {
        PlasmaSim p = sim;
        if (index >= FusionReactorMenu.DATA_HISTORY && index < FusionReactorMenu.DATA_HISTORY + 3 * HISTORY) {
            int i = index - FusionReactorMenu.DATA_HISTORY;
            return history[i / HISTORY][i % HISTORY];
        }
        if (index == FusionReactorMenu.DATA_MODULES) {
            int packed = 0;
            for (int i = 0; i < moduleCounts.length; i++) {
                packed |= Math.min(15, moduleCounts[i]) << (i * 4);
            }
            return packed;
        }
        boolean live = p != null && p.alive;
        return switch (index) {
            case FusionReactorMenu.DATA_STATUS -> status.ordinal();
            case FusionReactorMenu.DATA_FORMED -> shape != null ? 1 : 0;
            case FusionReactorMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
            case FusionReactorMenu.DATA_SIDE_CONFIG -> packedSides();
            case FusionReactorMenu.DATA_RADIUS -> getRadius();
            case FusionReactorMenu.DATA_STELLARATOR -> isStellarator() ? 1 : 0;
            case FusionReactorMenu.DATA_TEMPERATURE -> live ? (int) Math.round(p.t * 10.0) : 0;
            case FusionReactorMenu.DATA_DENSITY -> live ? (int) Math.round(p.electrons() * 1000.0) : 0;
            case FusionReactorMenu.DATA_CURRENT -> live ? (int) Math.round(p.ip * 100.0) : 0;
            case FusionReactorMenu.DATA_FIELD -> (int) Math.round(field * 100.0);
            case FusionReactorMenu.DATA_FUSION -> p != null ? (int) Math.round(p.pFusion * 10.0) : 0;
            case FusionReactorMenu.DATA_HEATING -> p != null ? (int) Math.round(p.pHeating * 10.0) : 0;
            case FusionReactorMenu.DATA_Q -> p != null ? (int) Math.round(Math.min(99_999.0, p.q() * 100.0)) : 0;
            case FusionReactorMenu.DATA_TAU -> live ? (int) Math.round(p.tauE * 1000.0) : 0;
            case FusionReactorMenu.DATA_BETA -> live ? (int) Math.round(p.betaN * 100.0) : 0;
            case FusionReactorMenu.DATA_GREENWALD -> live ? (int) Math.round(p.greenwald * 1000.0) : 0;
            case FusionReactorMenu.DATA_Q95 -> live ? (int) Math.round(Math.min(99.0, p.q95) * 100.0) : 0;
            case FusionReactorMenu.DATA_POSITION -> live ? (int) Math.round(p.z / Math.max(0.01, p.a * 0.15) * 1000.0) : 0;
            case FusionReactorMenu.DATA_PULSE_LEFT -> live && !p.stellarator ? (int) Math.min(999_999.0, p.pulseLeft()) : -1;
            case FusionReactorMenu.DATA_NEUTRON -> p != null ? (int) Math.round(p.pNeutron * 10.0) : 0;
            case FusionReactorMenu.DATA_ALPHA -> p != null ? (int) Math.round(p.pAlpha * 10.0) : 0;
            case FusionReactorMenu.DATA_RADIATED -> p != null ? (int) Math.round((p.pBrems + p.pImpurity) * 10.0) : 0;
            case FusionReactorMenu.DATA_IMPURITY -> (int) Math.round(impurity() * 10_000.0);
            case FusionReactorMenu.DATA_FLAGS -> (live ? 1 : 0) | (p != null && p.ignited() ? 2 : 0) | (run ? 4 : 0) | (repeat ? 8 : 0)
                    | (energised ? 16 : 0) | (hasDiagnostics() ? 32 : 0) | (wantsPlasma() ? 64 : 0);
            case FusionReactorMenu.DATA_EVENT -> lastEvent.ordinal();
            case FusionReactorMenu.DATA_VACUUM -> (int) Math.round(Math.log10(Math.max(VACUUM_FLOOR, vacuum)) * 100.0);
            case FusionReactorMenu.DATA_COIL_TEMP -> (int) Math.round(coilTemp * 10.0);
            case FusionReactorMenu.DATA_FIELD_TARGET -> (int) Math.round(fieldTarget() * 100.0);
            case FusionReactorMenu.DATA_CS_CHARGE -> (int) Math.round(getCsCharge() * 1000.0);
            case FusionReactorMenu.DATA_ENERGY -> energy.getAmountAsInt();
            case FusionReactorMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
            case FusionReactorMenu.DATA_ENERGY_USE -> energyUse;
            case FusionReactorMenu.DATA_WATER -> water.getAmount();
            case FusionReactorMenu.DATA_HOT_COOLANT -> hotCoolant.getAmount();
            case FusionReactorMenu.DATA_HELIUM_LOOP -> heliumCooled() ? 1 : 0;
            case FusionReactorMenu.DATA_LEAD_LITHIUM -> leadLithium.getAmount();
            case FusionReactorMenu.DATA_HOT_LEAD_LITHIUM -> hotLeadLithium.getAmount();
            case FusionReactorMenu.DATA_LIQUID_HELIUM -> liquidHelium.getAmount();
            case FusionReactorMenu.DATA_LIQUID_NITROGEN -> liquidNitrogen.getAmount();
            case FusionReactorMenu.DATA_TANK -> TANK;
            case FusionReactorMenu.DATA_WALL_TEMP -> (int) Math.round(wallTemperature());
            case FusionReactorMenu.DATA_BLANKET_TEMP -> (int) Math.round(blanketTemperature());
            case FusionReactorMenu.DATA_FW_WEAR -> (int) Math.round(fwWear * 1000.0);
            case FusionReactorMenu.DATA_DIV_WEAR -> (int) Math.round(divWear * 1000.0);
            case FusionReactorMenu.DATA_BLANKET_WEAR -> (int) Math.round(blanketWear * 1000.0);
            case FusionReactorMenu.DATA_COIL_DAMAGE -> (int) Math.round(coilDamage * 1000.0);
            case FusionReactorMenu.DATA_TBR -> (int) Math.round(tbr() * 1000.0);
            case FusionReactorMenu.DATA_BURN -> (int) Math.round(burnRate * 1000.0);
            case FusionReactorMenu.DATA_BRED -> (int) Math.round(bredRate * 1000.0);
            case FusionReactorMenu.DATA_HEATING_SET -> heatingSetpoint;
            case FusionReactorMenu.DATA_DENSITY_SET -> densitySetpoint;
            case FusionReactorMenu.DATA_CURRENT_SET -> currentSetpoint;
            case FusionReactorMenu.DATA_FIELD_SET -> fieldSetpoint;
            case FusionReactorMenu.DATA_FUEL_MODE -> fuelMode.ordinal();
            case FusionReactorMenu.DATA_CHECKLIST -> checklist();
            case FusionReactorMenu.DATA_HEAT_AVAILABLE -> level instanceof ServerLevel server ? (int) Math.round(heatingAvailable(server) * 10.0) : 0;
            case FusionReactorMenu.DATA_ALARMS -> alarms;
            case FusionReactorMenu.DATA_SILENCED -> silenced;
            case FusionReactorMenu.DATA_THERMAL -> (int) Math.min(Integer.MAX_VALUE, Math.round(thermalPerTick));
            case FusionReactorMenu.DATA_PUMPING -> (int) Math.round(pumping * 1000.0);
            case FusionReactorMenu.DATA_HISTORY_HEAD -> historyHead;
            case FusionReactorMenu.DATA_MAX_CURRENT -> p != null ? (int) Math.round(p.maxCurrent() * 100.0) : 0;
            case FusionReactorMenu.DATA_DENSITY_LIMIT -> p != null ? (int) Math.round(p.densityLimit() * 1000.0) : 0;
            case FusionReactorMenu.DATA_LEAK -> (int) Math.round(leak * 1000.0);
            case FusionReactorMenu.DATA_QUENCH_UNITS -> quenchUnits;
            case FusionReactorMenu.DATA_FUEL -> (int) Math.round(fuelDelivered * 10.0);
            default -> 0;
        };
    }

    // --- Saving and syncing ---

    private void sync() {
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        shape = in.getLong("axis").map(axis -> new TokamakStructure.Shape(BlockPos.of(axis), in.getIntOr("radius", TokamakLayout.MIN_RADIUS),
                in.getBooleanOr("stellarator", false), Direction.from2DDataValue(in.getIntOr("front", 0)))).orElse(null);
        portDefaults.load(in);
        energy.deserialize(in.childOrEmpty("energy"));
        water.deserialize(in.childOrEmpty("water"));
        hotCoolant.deserialize(in.childOrEmpty("hot_coolant"));
        leadLithium.deserialize(in.childOrEmpty("lead_lithium"));
        hotLeadLithium.deserialize(in.childOrEmpty("hot_lead_lithium"));
        liquidHelium.deserialize(in.childOrEmpty("liquid_helium"));
        liquidNitrogen.deserialize(in.childOrEmpty("liquid_nitrogen"));
        vacuum = in.getDoubleOr("vacuum", 1.0);
        leak = in.getDoubleOr("leak", 0.0);
        coilTemp = in.getDoubleOr("coil_temp", AMBIENT_K);
        field = in.getDoubleOr("field", 0.0);
        energised = field > 0.0;
        csCharge = in.getDoubleOr("cs_charge", 0.0);
        wallHeat = in.getDoubleOr("wall_heat", 0.0);
        blanketHeat = in.getDoubleOr("blanket_heat", 0.0);
        fwWear = in.getDoubleOr("fw_wear", 0.0);
        divWear = in.getDoubleOr("divertor_wear", 0.0);
        blanketWear = in.getDoubleOr("blanket_wear", 0.0);
        coilDamage = in.getDoubleOr("coil_damage", 0.0);
        activation = in.getDoubleOr("activation", 0.0);
        fluence = in.getDoubleOr("fluence", 0.0);
        heatingSetpoint = in.getIntOr("heating", 50);
        densitySetpoint = in.getIntOr("density", 85);
        currentSetpoint = in.getIntOr("current", 100);
        fieldSetpoint = in.getIntOr("field_set", 100);
        fuelMode = in.getIntOr("fuel_mode", 0) == 1 ? FuelMode.DHE3 : FuelMode.DT;
        run = in.getBooleanOr("run", false);
        repeat = in.getBooleanOr("repeat", true);
        alarms = in.getIntOr("alarms", 0);
        silenced = in.getIntOr("silenced", 0);
        alive = in.getBooleanOr("alive", false);
        glow = in.getIntOr("glow", 0);
        scanned = false;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (shape != null) {
            out.putLong("axis", shape.axis().asLong());
            out.putInt("radius", shape.radius());
            out.putBoolean("stellarator", shape.stellarator());
            out.putInt("front", shape.front().get2DDataValue());
        }
        portDefaults.save(out);
        energy.serialize(out.child("energy"));
        water.serialize(out.child("water"));
        hotCoolant.serialize(out.child("hot_coolant"));
        leadLithium.serialize(out.child("lead_lithium"));
        hotLeadLithium.serialize(out.child("hot_lead_lithium"));
        liquidHelium.serialize(out.child("liquid_helium"));
        liquidNitrogen.serialize(out.child("liquid_nitrogen"));
        out.putDouble("vacuum", vacuum);
        out.putDouble("leak", leak);
        out.putDouble("coil_temp", coilTemp);
        out.putDouble("field", field);
        out.putDouble("cs_charge", csCharge);
        out.putDouble("wall_heat", wallHeat);
        out.putDouble("blanket_heat", blanketHeat);
        out.putDouble("fw_wear", fwWear);
        out.putDouble("divertor_wear", divWear);
        out.putDouble("blanket_wear", blanketWear);
        out.putDouble("coil_damage", coilDamage);
        out.putDouble("activation", activation);
        out.putDouble("fluence", fluence);
        out.putInt("heating", heatingSetpoint);
        out.putInt("density", densitySetpoint);
        out.putInt("current", currentSetpoint);
        out.putInt("field_set", fieldSetpoint);
        out.putInt("fuel_mode", fuelMode.ordinal());
        out.putBoolean("run", run);
        out.putBoolean("repeat", repeat);
        out.putInt("alarms", alarms);
        out.putInt("silenced", silenced);
        out.putBoolean("alive", alive);
        out.putInt("glow", glow);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        if (shape == null) {
            return Component.translatable("container.arcforge.fusion_reactor");
        }
        return Component.translatable(shape.stellarator() ? "container.arcforge.fusion_reactor.stellarator" : "container.arcforge.fusion_reactor.tokamak",
                shape.radius(), shape.sizeText());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FusionReactorMenu(containerId, inventory, worldPosition, items, data);
    }
}
