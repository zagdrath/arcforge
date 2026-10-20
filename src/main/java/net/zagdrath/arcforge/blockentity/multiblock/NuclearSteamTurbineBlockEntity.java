/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.multiblock.NuclearTurbineCasingBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.PowerGeneration;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.output.OutputLimit;
import net.zagdrath.arcforge.machine.output.OutputLimited;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.NuclearSteamTurbineMenu;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.nuclear.ReactorSteam;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.steam.NuclearTurbineLayout;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Nuclear Steam Turbine (see ShellMultiblockBlockEntity): a reactor's turbine-generator, 7x7 across and 21 to 45
// long, built for the flow of High-Pressure Steam a Fission Reactor's Steam Generators make (or a boiling core's
// Contaminated Steam, run the same way; see ReactorSteam). It grows with its sections s = 49 / 9 x L as the Steam Turbine
// Array does (a 7x7x21 is 114.3, a 7x7x45 245), taking up to maxFlowPerSection x s mB/t (240: a 7x7x45 takes 58,800)
// at fePerMb FE a mB (40, its reheaters making it a little more than the Steam Turbine Array's 36). Like a real large
// turbine it can't run on a trickle: under minimumFlow of its rated flow (25%) for tripSeconds it trips, closing its
// stop valves, and coasts down; it rolls up again once the steam coming into its tank has held over that for
// restartSeconds. A full Steam Boiler Array is too small to keep the biggest one running: in practice it needs a reactor.
// Its shaft runs at its synchronous speed (maxRpm) whatever the load, as a grid turbine-generator does: it takes about
// 15 s to roll up and about a minute to coast down, and the output follows the steam, scaled while it rolls up. Spent steam
// leaves through its Exhaust ports along the low-pressure section as Exhaust Steam, for Condenser Arrays (it vents
// without one, or when its exhaust tank is full, so it never stalls). Formed, it's drawn as a nuclear turbine-generator
// set (see NuclearTurbineLayout and NuclearSteamTurbineRenderer), and a new one gets its ports on the model's fitted
// spots (see defaultPorts).
public class NuclearSteamTurbineBlockEntity extends ShellMultiblockBlockEntity implements OutputLimited {
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.ENERGY, SideMode.EXHAUST);
    // Share of the gap to the target speed closed each tick: rolling up takes about 15 s, coasting down about a minute.
    private static final double ROLL_UP = 0.01, COAST_DOWN = 0.003;
    // The shaft's speed is sent to clients at most this often, and only when it has changed by SYNC_SHARE of full speed
    // (or come to rest), as are a trip and its flow when they change by SYNC_SHARE of its rated flow.
    private static final int SYNC_INTERVAL = 10;
    private static final double SYNC_SHARE = 0.02;

    // Replaced (keeping what it holds) when the structure's size changes, since its size and rate follow it.
    private GeneratorEnergyHandler energy;
    private final OutputLimit outputLimit = new OutputLimit(OutputLimit.Unit.FE);
    private final FilteredFluidTank steam;
    private final ResourceHandler<FluidResource> steamInput;
    private final FilteredFluidTank exhaust;
    private final ResourceHandler<FluidResource> exhaustOutput;
    private final ResourceHandler<FluidResource> fluidAutomation;
    private boolean hasExhaust;
    private final ContainerData data;
    private final List<BlockCapabilityCache<EnergyHandler, @Nullable Direction>> energyTargets = new ArrayList<>();
    private final List<BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>> exhaustTargets = new ArrayList<>();
    private boolean targetsDirty = true;
    private double rpm;
    private int flow;
    private int fePerTick;
    // Tripped on low flow (its stop valves shut); the ticks the flow has been under the minimum, or, while tripped, the
    // ticks the steam coming in has held over it; the steam in its tank after last tick (what came in is the rise).
    private boolean tripped;
    private int lowTicks, recoveredTicks;
    private int steamAfter;
    // What came in last tick (mB), averaged over about a second.
    private double inflow;
    private float syncedRpm;
    private int syncedFlow;
    private boolean syncedTripped;
    private long lastSync;

    // Client side: the shaft's angle, advanced as frames are drawn.
    private float clientAngle;
    private double clientTime = -1;

    public NuclearSteamTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.NUCLEAR_STEAM_TURBINE.get(), pos, state, 0, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE, SideMode.NONE), SIDE_MODES);
        this.energy = newEnergy(minSections());
        this.steam = new FilteredFluidTank(ArcforgeConfig.NUCLEAR_TURBINE_TANK_PER_SECTION.getAsInt(), NuclearSteamTurbineBlockEntity::takes,
                this::setChanged);
        this.steamInput = new AutomationResourceHandler<>(steam, index -> true, index -> false);
        this.exhaust = new FilteredFluidTank(ArcforgeConfig.NUCLEAR_TURBINE_EXHAUST_PER_SECTION.getAsInt(),
                resource -> resource.is(ModFluids.EXHAUST_STEAM.get()), this::setChanged);
        this.exhaustOutput = new AutomationResourceHandler<>(exhaust, index -> false, index -> true);
        this.fluidAutomation = new CombinedResourceHandler<>(steamInput, exhaustOutput);
        this.data = new WideIntContainerData(NuclearSteamTurbineMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case NuclearSteamTurbineMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case NuclearSteamTurbineMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case NuclearSteamTurbineMenu.DATA_STEAM -> steam.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(steam.getResource(0).getFluid()) : -1;
                    case NuclearSteamTurbineMenu.DATA_STEAM_AMOUNT -> steam.getAmount();
                    case NuclearSteamTurbineMenu.DATA_STEAM_CAPACITY -> steam.getCapacity();
                    case NuclearSteamTurbineMenu.DATA_FLOW -> flow;
                    case NuclearSteamTurbineMenu.DATA_MAX_FLOW -> maxFlow();
                    case NuclearSteamTurbineMenu.DATA_MIN_FLOW -> minFlow();
                    case NuclearSteamTurbineMenu.DATA_INFLOW -> (int) Math.round(inflow);
                    case NuclearSteamTurbineMenu.DATA_FE_PER_TICK -> fePerTick;
                    case NuclearSteamTurbineMenu.DATA_RPM -> (int) Math.round(rpm);
                    case NuclearSteamTurbineMenu.DATA_MAX_RPM -> maxRpm();
                    case NuclearSteamTurbineMenu.DATA_LENGTH -> getLength();
                    case NuclearSteamTurbineMenu.DATA_STATUS -> status.ordinal();
                    case NuclearSteamTurbineMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case NuclearSteamTurbineMenu.DATA_SIDE_CONFIG -> packedSides();
                    case NuclearSteamTurbineMenu.DATA_EXHAUST_AMOUNT -> exhaust.getAmount();
                    case NuclearSteamTurbineMenu.DATA_EXHAUST_CAPACITY -> exhaust.getCapacity();
                    case NuclearSteamTurbineMenu.DATA_EXHAUST -> hasExhaust ? 1 : 0;
                    case NuclearSteamTurbineMenu.DATA_TRIPPED -> tripped ? 1 : 0;
                    default -> 0;
                };
            }
        };
    }

    // High-Pressure Steam, or a boiling core's Contaminated Steam (run as High-Pressure).
    public static boolean takes(FluidResource resource) {
        return ReactorSteam.turbineGrade(resource) == SteamGrade.HIGH_PRESSURE;
    }

    public int getLength() {
        ShellStructure.Shell shell = getShell();
        return shell != null ? shell.length() : NuclearTurbineLayout.MIN_LENGTH;
    }

    private static double minSections() {
        return NuclearTurbineLayout.WIDTH * NuclearTurbineLayout.HEIGHT / 9.0 * NuclearTurbineLayout.MIN_LENGTH;
    }

    // Sections, as the Steam Turbine Array counts them: width x height / 9 x length (114.3 for a 7x7x21).
    public double getSections() {
        ShellStructure.Shell shell = getShell();
        return shell != null ? SteamBoilerArrayBlockEntity.sections(shell) : minSections();
    }

    // Its rated flow (mB/t).
    public int maxFlow() {
        return SteamBoilerArrayBlockEntity.scaled(ArcforgeConfig.NUCLEAR_TURBINE_FLOW_PER_SECTION.getAsInt(), getSections());
    }

    // The least flow it runs on (mB/t).
    public int minFlow() {
        return (int) Math.ceil(maxFlow() * ArcforgeConfig.NUCLEAR_TURBINE_MINIMUM_FLOW.getAsDouble());
    }

    // The most FE/t it makes (a full flow at speed, times the power multiplier); also the most its energy ports push out a tick.
    public static int maxOutput(double sections) {
        int flow = SteamBoilerArrayBlockEntity.scaled(ArcforgeConfig.NUCLEAR_TURBINE_FLOW_PER_SECTION.getAsInt(), sections);
        return PowerGeneration.cap(flow * ArcforgeConfig.NUCLEAR_TURBINE_FE_PER_MB.getAsDouble());
    }

    public int maxOutput() {
        return maxOutput(getSections());
    }

    private GeneratorEnergyHandler newEnergy(double sections) {
        int output = maxOutput(sections);
        long capacity = (long) output * ArcforgeConfig.NUCLEAR_TURBINE_ENERGY_BUFFER_TICKS.getAsInt();
        return new GeneratorEnergyHandler((int) Math.min(Integer.MAX_VALUE, capacity), output, this::setChanged);
    }

    // A new buffer for the current size, keeping the FE it held (as much as fits).
    private void resizeEnergy() {
        GeneratorEnergyHandler resized = newEnergy(getSections());
        if (resized.getCapacityAsInt() != energy.getCapacityAsInt()) {
            resized.generate(energy.getAmountAsInt());
            energy = resized;
        }
    }

    public static int maxRpm() {
        return ArcforgeConfig.NUCLEAR_TURBINE_MAX_RPM.getAsInt();
    }

    @Override
    protected ShellStructure structure() {
        return NuclearTurbineCasingBlock.STRUCTURE;
    }

    @Override
    public void setShell(ShellStructure.@Nullable Shell shell) {
        super.setShell(shell);
        if (shell != null) {
            resizeTanks();
            resizeEnergy();
        }
        targetsDirty = true;
    }

    private void resizeTanks() {
        double sections = getSections();
        steam.setCapacity(SteamBoilerArrayBlockEntity.scaled(ArcforgeConfig.NUCLEAR_TURBINE_TANK_PER_SECTION.getAsInt(), sections));
        exhaust.setCapacity(SteamBoilerArrayBlockEntity.scaled(ArcforgeConfig.NUCLEAR_TURBINE_EXHAUST_PER_SECTION.getAsInt(), sections));
    }

    @Override
    public void onFormed(@Nullable Direction facing) {
        ShellStructure.Shell shell = getShell();
        if (shell == null) {
            super.onFormed(facing);
            return;
        }
        // A rebuilt turbine keeps the front it had; a new one faces the player who finished it, if they stood beside a
        // long side, else the side that puts the generator on the right.
        Direction generatorEnd = Direction.fromAxisAndDirection(shell.axis(), Direction.AxisDirection.POSITIVE);
        Direction preferred = generatorEnd.getClockWise();
        Direction front = wasFormedBefore() && getFacing().getAxis() != shell.axis() ? getFacing()
                : facing == preferred.getOpposite() ? facing : preferred;
        setStructureFacing(front);
        super.onFormed(null);
    }

    @Override
    public void onPortsChanged() {
        targetsDirty = true;
        hasExhaust = level != null && MultiblockPorts.list(level, this).stream().anyMatch(port -> port.mode() == SideMode.EXHAUST);
    }

    // A new turbine's ports, on the model's fitted spots: steam under each steam chest, where the main steam pipes rise
    // through the deck; energy at the generator's terminal box; and an Exhaust port under each low-pressure casing's
    // exhaust hood, where a Condenser Array can sit.
    @Override
    public Map<BlockPos, MultiblockPorts.DefaultPort> defaultPorts(Level level) {
        ShellStructure.Shell shell = getShell();
        if (shell == null) {
            return Map.of();
        }
        NuclearTurbineLayout layout = NuclearTurbineLayout.of(shell, getFacing());
        // One port a block: if two spots ever share a block, the first listed keeps it.
        Map<BlockPos, MultiblockPorts.DefaultPort> ports = new LinkedHashMap<>();
        for (BlockPos pos : layout.steamSpots(shell)) {
            ports.putIfAbsent(pos, new MultiblockPorts.DefaultPort(SideMode.INPUT, Direction.DOWN));
        }
        ports.putIfAbsent(layout.energySpot(shell), new MultiblockPorts.DefaultPort(SideMode.ENERGY, layout.energyFace(shell)));
        for (BlockPos pos : layout.exhaustSpots(shell)) {
            ports.putIfAbsent(pos, new MultiblockPorts.DefaultPort(SideMode.EXHAUST, Direction.DOWN));
        }
        return ports;
    }

    @Override
    protected void onSideConfigChanged() {
        super.onSideConfigChanged();
        targetsDirty = true;
    }

    @Override
    protected void tickMaster(ServerLevel level) {
        // Once a second: at speed counts for the quests (arcforge:turbine_full_speed).
        if (level.getGameTime() % 20 == 0 && rpm >= 0.99 * maxRpm()) {
            ArcforgeAdvancements.turbineFullSpeed(this, multiblockId(), length(), 15);
        }
        int maxFlow = maxFlow(), minFlow = minFlow();
        // What came in since last tick: nothing else takes from its tank.
        int arrived = Math.max(0, steam.getAmount() - steamAfter);
        inflow += (arrived - inflow) * 0.05;
        flow = 0;
        fePerTick = 0;
        boolean running = canRun(level);
        if (running && !tripped && steam.getAmount() > 0 && !energy.isFull()) {
            try (Transaction tx = Transaction.openRoot()) {
                flow = steam.extract(0, steam.getResource(0), Math.min(steam.getAmount(), maxFlow), tx);
                tx.commit();
            }
            controlState.consumedFluid(flow);
        }
        // Low flow trips it; while tripped, the steam coming in holding over the minimum rolls it up again. A full FE
        // buffer only holds the steam back, it doesn't trip it.
        if (!tripped && running && !energy.isFull()) {
            lowTicks = flow < minFlow ? lowTicks + 1 : 0;
            if (lowTicks > ArcforgeConfig.NUCLEAR_TURBINE_TRIP_SECONDS.getAsInt() * 20) {
                tripped = true;
                recoveredTicks = 0;
            }
        } else if (tripped) {
            lowTicks = 0;
            recoveredTicks = inflow >= minFlow ? recoveredTicks + 1 : 0;
            if (recoveredTicks > ArcforgeConfig.NUCLEAR_TURBINE_RESTART_SECONDS.getAsInt() * 20) {
                tripped = false;
            }
        }
        // Tripped, its bypass valves dump the steam over half a tank straight to the condensers (or vent it), as a real
        // plant's do: the steam supply keeps flowing, so it's seen whether it holds over the minimum again.
        int bypass = tripped ? Math.max(0, steam.getAmount() - steam.getCapacity() / 2) : 0;
        if (bypass > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                steam.extract(0, steam.getResource(0), bypass, tx);
                tx.commit();
            }
        }
        if (hasExhaust && flow + bypass > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                controlState.producedFluid(exhaust.insert(0, FluidResource.of(ModFluids.EXHAUST_STEAM.get()), flow + bypass, tx));
                tx.commit();
            }
        }
        // It runs at its synchronous speed while the steam drives it, whatever the load.
        double target = flow > 0 ? maxRpm() : 0.0;
        rpm += (target - rpm) * (target > rpm ? ROLL_UP : COAST_DOWN);
        if (rpm < 0.5 && target == 0) {
            rpm = 0;
        }
        if (flow > 0) {
            double spun = Mth.clamp(rpm / Math.max(target, 1.0), 0.0, 1.0);
            fePerTick = energy.generate(PowerGeneration.fe(flow * ArcforgeConfig.NUCLEAR_TURBINE_FE_PER_MB.getAsDouble() * spun));
            setChanged();
        }
        if (!running) {
            status = stoppedStatus();
        } else if (tripped) {
            // At rest with no steam coming at all it's just waiting for steam.
            status = inflow < 1.0 && rpm < 1.0 && steam.getAmount() == 0 ? MachineStatus.NO_STEAM : MachineStatus.TRIPPED;
        } else if (energy.isFull()) {
            status = MachineStatus.FULL;
        } else if (flow > 0) {
            status = rpm < target * 0.95 ? MachineStatus.SPINNING_UP : MachineStatus.GENERATING;
        } else {
            status = rpm > 1 ? MachineStatus.COASTING : MachineStatus.NO_STEAM;
        }
        pushEnergy(level);
        if (hasExhaust && exhaust.getAmount() > 0) {
            pushExhaust(level);
        }
        steamAfter = steam.getAmount();
        int rated = PowerGeneration.fe(Math.min(steam.getAmount() + flow, maxFlow) * ArcforgeConfig.NUCLEAR_TURBINE_FE_PER_MB.getAsDouble());
        outputLimit.tick(level, running && !tripped && steam.getAmount() + flow > 0,
                () -> OutputLimit.anyPortTakes(level, this, SideMode.ENERGY, OutputLimit.Unit.FE),
                energy.getAmountAsInt(), energy.getCapacityAsInt(), energy.drained(), rated);
        // Clients only need the shaft's speed (for the model) and the trip: sent when they've changed noticeably.
        boolean changed = Math.abs(rpm - syncedRpm) >= SYNC_SHARE * maxRpm() || Math.abs(flow - syncedFlow) >= SYNC_SHARE * maxFlow
                || tripped != syncedTripped;
        if (changed && level.getGameTime() - lastSync >= SYNC_INTERVAL || rpm == 0 && syncedRpm != 0) {
            syncedRpm = (float) rpm;
            syncedFlow = flow;
            syncedTripped = tripped;
            lastSync = level.getGameTime();
            sync();
        }
    }

    // What touches the energy and Exhaust port faces, found again when the ports change.
    private void refreshTargets(ServerLevel level) {
        targetsDirty = false;
        energyTargets.clear();
        exhaustTargets.clear();
        for (BlockPos pos : BlockPos.betweenClosed(getMinCorner(), getMaxCorner())) {
            if (!isPart(pos)) {
                continue;
            }
            for (Direction side : Direction.values()) {
                SideMode mode = faceMode(pos, side);
                if (mode == SideMode.ENERGY) {
                    energyTargets.add(BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, pos.relative(side).immutable(), side.getOpposite()));
                } else if (mode == SideMode.EXHAUST) {
                    exhaustTargets.add(BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, pos.relative(side).immutable(), side.getOpposite()));
                }
            }
        }
    }

    // Pushes FE out of every energy face, up to the output cap in total.
    private void pushEnergy(ServerLevel level) {
        if (targetsDirty) {
            refreshTargets(level);
        }
        int budget = Math.min(energy.getAmountAsInt(), maxOutput());
        for (BlockCapabilityCache<EnergyHandler, @Nullable Direction> target : energyTargets) {
            if (budget <= 0) {
                break;
            }
            EnergyHandler handler = target.getCapability();
            if (handler != null) {
                budget -= EnergyHandlerUtil.move(energy, handler, budget, null);
            }
        }
    }

    // Pushes Exhaust Steam out of every Exhaust face, up to its rated flow a tick in total, spread over them: each port
    // offers its share first, so the condensers under the low-pressure casings all work.
    private void pushExhaust(ServerLevel level) {
        if (targetsDirty) {
            refreshTargets(level);
        }
        int budget = maxFlow();
        int share = exhaustTargets.isEmpty() ? budget : Math.max(1, budget / exhaustTargets.size());
        for (int pass = 0; pass < 2 && budget > 0; pass++) {
            for (BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> target : exhaustTargets) {
                if (budget <= 0) {
                    break;
                }
                ResourceHandler<FluidResource> handler = target.getCapability();
                if (handler != null) {
                    budget -= ResourceHandlerUtil.move(exhaustOutput, handler, resource -> true, pass == 0 ? Math.min(share, budget) : budget, null);
                }
            }
        }
        if (exhaust.getAmount() >= exhaust.getCapacity()) {
            // Full: what can't get out vents, so the turbine never stalls on its exhaust.
            try (Transaction tx = Transaction.openRoot()) {
                exhaust.extract(0, exhaust.getResource(0), exhaust.getAmount() / 2, tx);
                tx.commit();
            }
        }
    }

    public GeneratorEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getSteam() {
        return steam;
    }

    public FilteredFluidTank getExhaust() {
        return exhaust;
    }

    public boolean hasExhaust() {
        return hasExhaust;
    }

    public boolean isTripped() {
        return tripped;
    }

    public double getRpm() {
        return rpm;
    }

    public int getFlow() {
        return flow;
    }

    public int getFePerTick() {
        return fePerTick;
    }

    // The steam coming in, mB/t averaged over about a second.
    public double getInflow() {
        return inflow;
    }

    // Client side: keeps the running sound going while the shaft turns (TurbineArraySound.keepPlaying, set by the client).
    public static Consumer<NuclearSteamTurbineBlockEntity> clientSoundHook = turbine -> {};

    public void clientTick() {
        if (isMaster()) {
            clientSoundHook.accept(this);
        }
    }

    // Client side: the last synced shaft speed and trip.
    public float getSyncedRpm() {
        return syncedRpm;
    }

    public boolean isSyncedTripped() {
        return syncedTripped;
    }

    // Client side: the shaft's angle in degrees at this moment, at the last synced speed: 36° a tick at full speed (the
    // model's sight-port blades blur long before that), on a square-root curve so a slow roll shows.
    public float advanceAngle(double time) {
        if (clientTime >= 0) {
            double share = Mth.clamp(syncedRpm / (double) maxRpm(), 0.0, 1.0);
            clientAngle = (float) ((clientAngle + (time - clientTime) * Math.sqrt(share) * 36.0) % 360.0);
        }
        clientTime = time;
        return clientAngle;
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return fluidAutomation;
        }
        return switch (mode) {
            case INPUT -> steamInput;
            case EXHAUST -> exhaustOutput;
            default -> null;
        };
    }

    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return energy;
        }
        return faceMode(pos, side) == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.EXHAUST ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case FLUID, ITEM, THERMAL -> ConnectionMode.NONE;
        };
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        resizeTanks();
        energy = newEnergy(getSections());
        energy.deserialize(input.childOrEmpty("energy"));
        steam.deserialize(input.childOrEmpty("steam"));
        exhaust.deserialize(input.childOrEmpty("exhaust"));
        hasExhaust = input.getBooleanOr("has_exhaust", false);
        rpm = input.getDoubleOr("rpm", 0.0);
        syncedRpm = (float) rpm;
        flow = input.getIntOr("flow", 0);
        syncedFlow = flow;
        tripped = input.getBooleanOr("tripped", false);
        syncedTripped = tripped;
        inflow = input.getDoubleOr("inflow", 0.0);
        steamAfter = steam.getAmount();
        targetsDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        steam.serialize(output.child("steam"));
        exhaust.serialize(output.child("exhaust"));
        output.putBoolean("has_exhaust", hasExhaust);
        output.putDouble("rpm", rpm);
        output.putInt("flow", flow);
        output.putBoolean("tripped", tripped);
        output.putDouble("inflow", inflow);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.nuclear_steam_turbine.sized", "7×7×" + getLength());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new NuclearSteamTurbineMenu(containerId, inventory, worldPosition, items, data);
    }

    @Override
    public OutputLimit getOutputLimit() {
        return outputLimit;
    }
}
