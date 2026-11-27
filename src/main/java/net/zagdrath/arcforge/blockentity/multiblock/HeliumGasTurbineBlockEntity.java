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
import net.zagdrath.arcforge.block.multiblock.HeliumTurbineCasingBlock;
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
import net.zagdrath.arcforge.menu.multiblock.HeliumGasTurbineMenu;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.steam.HeliumTurbineLayout;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Helium Gas Turbine (see ShellMultiblockBlockEntity): a closed-cycle (Brayton) turbine-generator for a helium-cooled
// Fusion Reactor, 5x5 across and 15 to 31 long, a smaller set in the Nuclear Steam Turbine's manner. It grows with its
// sections s = 25 / 9 x L as the Steam Turbine Array does (a 5x5x15 is 41.7, a 5x5x31 86.1), taking up to
// maxFlowPerSection x s mB/t of Hot Helium (116: a 5x5x31 takes 9,989) at fePerMb FE a mB (800: 8 FE for each of the 100
// HU a mB of Hot Helium carries, a little more than steam's). The gas runs a closed loop: every mB it takes goes back out
// of its Output ports as Helium, for the reactor's coolant tank. It has nowhere to vent it, so with nowhere for the
// Helium to go it holds, and the reactor's hot side fills behind it. A gas turbine has no minimum flow to trip on: it
// runs at its synchronous speed (maxRpm) while there's gas to drive it, rolling up in about 15 s and coasting down in
// about a minute, the output following the flow. Formed, it's drawn as one turbine-generator set (see
// HeliumTurbineLayout and HeliumGasTurbineRenderer), and a new one gets its ports on the model's fitted spots (see
// defaultPorts).
public class HeliumGasTurbineBlockEntity extends ShellMultiblockBlockEntity implements OutputLimited {
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);
    private static final double ROLL_UP = 0.01, COAST_DOWN = 0.003;
    private static final int SYNC_INTERVAL = 10;
    private static final double SYNC_SHARE = 0.02;

    private GeneratorEnergyHandler energy;
    private final OutputLimit outputLimit = new OutputLimit(OutputLimit.Unit.FE);
    private final FilteredFluidTank hot;
    private final ResourceHandler<FluidResource> hotInput;
    private final FilteredFluidTank cooled;
    private final ResourceHandler<FluidResource> cooledOutput;
    private final ResourceHandler<FluidResource> fluidAutomation;
    private final ContainerData data;
    private final List<BlockCapabilityCache<EnergyHandler, @Nullable Direction>> energyTargets = new ArrayList<>();
    private final List<BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>> outputTargets = new ArrayList<>();
    private boolean targetsDirty = true;
    private double rpm;
    private int flow;
    private int fePerTick;
    private int hotAfter;
    private double inflow;
    private float syncedRpm;
    private int syncedFlow;
    private long lastSync;

    // Client side: the shaft's angle, advanced as frames are drawn.
    private float clientAngle;
    private double clientTime = -1;

    public HeliumGasTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.HELIUM_GAS_TURBINE.get(), pos, state, 0, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE, SideMode.NONE), SIDE_MODES);
        this.energy = newEnergy(minSections());
        this.hot = new FilteredFluidTank(ArcforgeConfig.HELIUM_TURBINE_TANK_PER_SECTION.getAsInt(), resource -> resource.is(ModFluids.HOT_HELIUM.get()),
                this::setChanged);
        this.hotInput = new AutomationResourceHandler<>(hot, index -> true, index -> false);
        this.cooled = new FilteredFluidTank(ArcforgeConfig.HELIUM_TURBINE_TANK_PER_SECTION.getAsInt(), resource -> resource.is(ModFluids.HELIUM.get()),
                this::setChanged);
        this.cooledOutput = new AutomationResourceHandler<>(cooled, index -> false, index -> true);
        this.fluidAutomation = new CombinedResourceHandler<>(hotInput, cooledOutput);
        this.data = new WideIntContainerData(HeliumGasTurbineMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case HeliumGasTurbineMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case HeliumGasTurbineMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case HeliumGasTurbineMenu.DATA_HOT_AMOUNT -> hot.getAmount();
                    case HeliumGasTurbineMenu.DATA_HOT_CAPACITY -> hot.getCapacity();
                    case HeliumGasTurbineMenu.DATA_FLOW -> flow;
                    case HeliumGasTurbineMenu.DATA_MAX_FLOW -> maxFlow();
                    case HeliumGasTurbineMenu.DATA_INFLOW -> (int) Math.round(inflow);
                    case HeliumGasTurbineMenu.DATA_FE_PER_TICK -> fePerTick;
                    case HeliumGasTurbineMenu.DATA_RPM -> (int) Math.round(rpm);
                    case HeliumGasTurbineMenu.DATA_MAX_RPM -> maxRpm();
                    case HeliumGasTurbineMenu.DATA_LENGTH -> getLength();
                    case HeliumGasTurbineMenu.DATA_STATUS -> status.ordinal();
                    case HeliumGasTurbineMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case HeliumGasTurbineMenu.DATA_SIDE_CONFIG -> packedSides();
                    case HeliumGasTurbineMenu.DATA_COOLED_AMOUNT -> cooled.getAmount();
                    case HeliumGasTurbineMenu.DATA_COOLED_CAPACITY -> cooled.getCapacity();
                    default -> 0;
                };
            }
        };
    }

    public int getLength() {
        ShellStructure.Shell shell = getShell();
        return shell != null ? shell.length() : HeliumTurbineLayout.MIN_LENGTH;
    }

    private static double minSections() {
        return HeliumTurbineLayout.WIDTH * HeliumTurbineLayout.HEIGHT / 9.0 * HeliumTurbineLayout.MIN_LENGTH;
    }

    // Sections, as the Steam Turbine Array counts them: width x height / 9 x length (41.7 for a 5x5x15).
    public double getSections() {
        ShellStructure.Shell shell = getShell();
        return shell != null ? SteamBoilerArrayBlockEntity.sections(shell) : minSections();
    }

    // Its rated flow (mB/t).
    public int maxFlow() {
        return SteamBoilerArrayBlockEntity.scaled(ArcforgeConfig.HELIUM_TURBINE_FLOW_PER_SECTION.getAsInt(), getSections());
    }

    // The most FE/t it makes (a full flow at speed, times the power multiplier); also the most its energy ports push out a tick.
    public static int maxOutput(double sections) {
        int flow = SteamBoilerArrayBlockEntity.scaled(ArcforgeConfig.HELIUM_TURBINE_FLOW_PER_SECTION.getAsInt(), sections);
        return PowerGeneration.cap(flow * ArcforgeConfig.HELIUM_TURBINE_FE_PER_MB.getAsDouble());
    }

    public int maxOutput() {
        return maxOutput(getSections());
    }

    private GeneratorEnergyHandler newEnergy(double sections) {
        int output = maxOutput(sections);
        long capacity = (long) output * ArcforgeConfig.HELIUM_TURBINE_ENERGY_BUFFER_TICKS.getAsInt();
        return new GeneratorEnergyHandler((int) Math.min(Integer.MAX_VALUE, capacity), output, this::setChanged);
    }

    private void resizeEnergy() {
        GeneratorEnergyHandler resized = newEnergy(getSections());
        if (resized.getCapacityAsInt() != energy.getCapacityAsInt()) {
            resized.generate(energy.getAmountAsInt());
            energy = resized;
        }
    }

    public static int maxRpm() {
        return ArcforgeConfig.HELIUM_TURBINE_MAX_RPM.getAsInt();
    }

    @Override
    protected ShellStructure structure() {
        return HeliumTurbineCasingBlock.STRUCTURE;
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
        int capacity = SteamBoilerArrayBlockEntity.scaled(ArcforgeConfig.HELIUM_TURBINE_TANK_PER_SECTION.getAsInt(), getSections());
        hot.setCapacity(capacity);
        cooled.setCapacity(capacity);
    }

    @Override
    public void onFormed(@Nullable Direction facing) {
        ShellStructure.Shell shell = getShell();
        if (shell == null) {
            super.onFormed(facing);
            return;
        }
        // As the Nuclear Steam Turbine: a rebuilt turbine keeps its front; a new one faces the player who finished it if
        // they stood beside a long side, else the side that puts the generator on the right.
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
    }

    // A new turbine's ports, on the model's fitted spots: Hot Helium in under each inlet duct's riser, the Helium back out
    // under the compressor's discharge pipe, and energy at the generator's terminal box.
    @Override
    public Map<BlockPos, MultiblockPorts.DefaultPort> defaultPorts(Level level) {
        ShellStructure.Shell shell = getShell();
        if (shell == null) {
            return Map.of();
        }
        HeliumTurbineLayout layout = HeliumTurbineLayout.of(shell, getFacing());
        Map<BlockPos, MultiblockPorts.DefaultPort> ports = new LinkedHashMap<>();
        for (BlockPos pos : layout.inletSpots(shell)) {
            ports.putIfAbsent(pos, new MultiblockPorts.DefaultPort(SideMode.INPUT, Direction.DOWN));
        }
        ports.putIfAbsent(layout.outletSpot(shell), new MultiblockPorts.DefaultPort(SideMode.OUTPUT, Direction.DOWN));
        ports.putIfAbsent(layout.energySpot(shell), new MultiblockPorts.DefaultPort(SideMode.ENERGY, Direction.DOWN));
        return ports;
    }

    @Override
    protected void onSideConfigChanged() {
        super.onSideConfigChanged();
        targetsDirty = true;
    }

    @Override
    protected void tickMaster(ServerLevel level) {
        if (level.getGameTime() % 20 == 0 && rpm >= 0.99 * maxRpm()) {
            ArcforgeAdvancements.turbineFullSpeed(this, multiblockId(), length(), 15);
        }
        int maxFlow = maxFlow();
        int arrived = Math.max(0, hot.getAmount() - hotAfter);
        inflow += (arrived - inflow) * 0.05;
        flow = 0;
        fePerTick = 0;
        boolean running = canRun(level);
        // The gas it takes has to go somewhere: as much as the cooled side has room for.
        int room = cooled.getSpace();
        if (running && hot.getAmount() > 0 && room > 0 && !energy.isFull()) {
            try (Transaction tx = Transaction.openRoot()) {
                flow = hot.extract(0, hot.getResource(0), Math.min(Math.min(hot.getAmount(), maxFlow), room), tx);
                tx.commit();
            }
            if (flow > 0) {
                cooled.set(0, FluidResource.of(ModFluids.HELIUM.get()), cooled.getAmount() + flow);
                controlState.consumedFluid(flow);
                controlState.producedFluid(flow);
            }
        }
        double target = flow > 0 ? maxRpm() : 0.0;
        rpm += (target - rpm) * (target > rpm ? ROLL_UP : COAST_DOWN);
        if (rpm < 0.5 && target == 0) {
            rpm = 0;
        }
        if (flow > 0) {
            double spun = Mth.clamp(rpm / Math.max(target, 1.0), 0.0, 1.0);
            fePerTick = energy.generate(PowerGeneration.fe(flow * ArcforgeConfig.HELIUM_TURBINE_FE_PER_MB.getAsDouble() * spun));
            setChanged();
        }
        if (!running) {
            status = stoppedStatus();
        } else if (energy.isFull()) {
            status = MachineStatus.FULL;
        } else if (hot.getAmount() > 0 && room <= 0) {
            status = MachineStatus.OUTPUT_FULL;
        } else if (flow > 0) {
            status = rpm < target * 0.95 ? MachineStatus.SPINNING_UP : MachineStatus.GENERATING;
        } else {
            status = rpm > 1 ? MachineStatus.COASTING : MachineStatus.NO_FLUID;
        }
        pushEnergy(level);
        if (cooled.getAmount() > 0) {
            pushHelium(level);
        }
        hotAfter = hot.getAmount();
        int rated = PowerGeneration.fe(Math.min(hot.getAmount() + flow, maxFlow) * ArcforgeConfig.HELIUM_TURBINE_FE_PER_MB.getAsDouble());
        outputLimit.tick(level, running && hot.getAmount() + flow > 0,
                () -> OutputLimit.anyPortTakes(level, this, SideMode.ENERGY, OutputLimit.Unit.FE),
                energy.getAmountAsInt(), energy.getCapacityAsInt(), energy.drained(), rated);
        boolean changed = Math.abs(rpm - syncedRpm) >= SYNC_SHARE * maxRpm() || Math.abs(flow - syncedFlow) >= SYNC_SHARE * maxFlow;
        if (changed && level.getGameTime() - lastSync >= SYNC_INTERVAL || rpm == 0 && syncedRpm != 0) {
            syncedRpm = (float) rpm;
            syncedFlow = flow;
            lastSync = level.getGameTime();
            sync();
        }
    }

    // What touches the energy and Output port faces, found again when the ports change.
    private void refreshTargets(ServerLevel level) {
        targetsDirty = false;
        energyTargets.clear();
        outputTargets.clear();
        for (BlockPos pos : BlockPos.betweenClosed(getMinCorner(), getMaxCorner())) {
            if (!isPart(pos)) {
                continue;
            }
            for (Direction side : Direction.values()) {
                SideMode mode = faceMode(pos, side);
                if (mode == SideMode.ENERGY) {
                    energyTargets.add(BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, pos.relative(side).immutable(), side.getOpposite()));
                } else if (mode == SideMode.OUTPUT) {
                    outputTargets.add(BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, pos.relative(side).immutable(), side.getOpposite()));
                }
            }
        }
    }

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

    // Pushes the cooled Helium out of every Output face, up to its tank a tick.
    private void pushHelium(ServerLevel level) {
        if (targetsDirty) {
            refreshTargets(level);
        }
        int budget = cooled.getAmount();
        for (BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> target : outputTargets) {
            if (budget <= 0) {
                break;
            }
            ResourceHandler<FluidResource> handler = target.getCapability();
            if (handler != null) {
                budget -= ResourceHandlerUtil.move(cooledOutput, handler, resource -> true, budget, null);
            }
        }
    }

    public GeneratorEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getHotHelium() {
        return hot;
    }

    public FilteredFluidTank getHelium() {
        return cooled;
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

    public double getInflow() {
        return inflow;
    }

    // Client side: keeps the running sound going while the shaft turns (TurbineArraySound.keepPlaying, set by the client).
    public static Consumer<HeliumGasTurbineBlockEntity> clientSoundHook = turbine -> {};

    public void clientTick() {
        if (isMaster()) {
            clientSoundHook.accept(this);
        }
    }

    public float getSyncedRpm() {
        return syncedRpm;
    }

    // Client side: the shaft's angle in degrees at this moment, at the last synced speed (as the Nuclear Steam Turbine's).
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
            case INPUT -> hotInput;
            case OUTPUT -> cooledOutput;
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
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
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
        hot.deserialize(input.childOrEmpty("hot_helium"));
        cooled.deserialize(input.childOrEmpty("helium"));
        rpm = input.getDoubleOr("rpm", 0.0);
        syncedRpm = (float) rpm;
        flow = input.getIntOr("flow", 0);
        syncedFlow = flow;
        inflow = input.getDoubleOr("inflow", 0.0);
        hotAfter = hot.getAmount();
        targetsDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        hot.serialize(output.child("hot_helium"));
        cooled.serialize(output.child("helium"));
        output.putDouble("rpm", rpm);
        output.putInt("flow", flow);
        output.putDouble("inflow", inflow);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.helium_gas_turbine.sized", "5×5×" + getLength());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new HeliumGasTurbineMenu(containerId, inventory, worldPosition, items, data);
    }

    @Override
    public OutputLimit getOutputLimit() {
        return outputLimit;
    }
}
