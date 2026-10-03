/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.multiblock.GasTurbineArrayCasingBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineOutputs;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.PowerGeneration;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.GasTurbineArrayMenu;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModCapabilities;
import net.zagdrath.arcforge.registry.ModSounds;
import net.zagdrath.arcforge.steam.Lubricant;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Gas Turbine Array (see ShellMultiblockBlockEntity): a turbine 5 to 9 blocks long that burns Fuel Burner
// fuels straight to FE. It burns up to maxFuelHeatPerLength x L HU/t of fuel (2,500 x L: about 34,000 FE/t at 9 long) (the limit is heat, so Hydrogen's
// thin HU/mB isn't punished), scaled by the throttle: full in the standard redstone modes, signal / 15 in
// THROTTLE. Each HU makes simpleCycleFactor FE (1.5), less for fuels burning cooler than the reference
// temperature, scaled by how close the rotor is to its speed, +8% with lubricant, and times power.generationMultiplier.
// Its output cap is its full-throttle lubricated output, and its FE buffer energyBufferTicks of that. A quarter of the heat
// leaves as exhaust at half the burn temperature, pushed out of Heat ports (a Steam Boiler Array takes it)
// or vented. One end is the intake, which needs air in front of it; the other is the exhaust. Starting takes
// 20 ticks of ignition; running dry is a flameout, and it waits 20 ticks before it can ignite again.
public class GasTurbineArrayBlockEntity extends ShellMultiblockBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    // Eight blades look the same every 45°, so a turn of more than 22.5° a tick would look like it went
    // backwards; the stagger hides most of that, and full speed turns 60° a tick.
    private static final double MAX_DEGREES_PER_TICK = 60.0;
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.ENERGY, SideMode.LUBRICANT, SideMode.HEAT);
    private static final int SYNC_INTERVAL = 5;
    private static final int SIGNAL_CHECK_INTERVAL = 10;
    // The hottest exhaust (Hydrogen, 1,400°C x 0.5, with room above).
    private static final int EXHAUST_MAX_CELSIUS = 1_400;
    private static final int MAX_LENGTH = 9;

    public enum Phase {
        OFF, IGNITING, RUNNING;

        static Phase byId(int id) {
            Phase[] values = values();
            return id >= 0 && id < values.length ? values[id] : OFF;
        }
    }

    // Replaced (keeping what it holds) when the length changes, since its size and rate follow it.
    private GeneratorEnergyHandler energy;
    private final FilteredFluidTank fuel;
    private final ResourceHandler<FluidResource> fuelInput;
    private final FilteredFluidTank lubricant;
    private final ResourceHandler<FluidResource> lubricantInput;
    private final ResourceHandler<FluidResource> fluidAutomation;
    private final HeatBuffer exhaust;
    private final HeatHandler exhaustOutput;
    private final ContainerData data;
    private final List<BlockCapabilityCache<EnergyHandler, @Nullable Direction>> energyTargets = new ArrayList<>();
    private final List<BlockCapabilityCache<HeatHandler, @Nullable Direction>> heatTargets = new ArrayList<>();
    private boolean targetsDirty = true;

    private Phase phase = Phase.OFF;
    private int ignitionLeft;
    private int reignitionDelay;
    // Which end is the intake (chosen when the structure forms), and whether it has air in front of it.
    private Direction.AxisDirection intakeEnd = Direction.AxisDirection.NEGATIVE;
    private boolean intakeOpen = true;
    private boolean intakeChecked;
    // The strongest redstone signal into any casing (-1 until read).
    private int signal = -1;
    // Fuel drained from the tank but not yet burned (mB), and lubricant used but not yet taken.
    private double fuelBuffered;
    private double lubricantUsed;
    private double rpm;
    private double burnedHu;
    private int fePerTick;
    private int exhaustHu;
    private int exhaustCelsius;
    // Exhaust vented last tick (HU): what nothing took.
    private int ventedHu;
    private float syncedRpm;
    private float syncedLoad;
    private boolean syncedVenting;
    private int syncedExhaustHu;
    private long lastSync;

    // Client side: the rotor angle, advanced as frames are drawn.
    private float clientAngle;
    private double clientTime = -1;

    public GasTurbineArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.GAS_TURBINE_ARRAY.get(), pos, state, 0, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE),
                SIDE_MODES);
        this.energy = newEnergy(5);
        this.fuel = new FilteredFluidTank(ArcforgeConfig.GAS_TURBINE_TANK_PER_LENGTH.getAsInt() * 5, GasTurbineArrayBlockEntity::isFuel, this::setChanged);
        this.fuelInput = new AutomationResourceHandler<>(fuel, index -> true, index -> false);
        this.lubricant = new FilteredFluidTank(ArcforgeConfig.TURBINE_ARRAY_LUBRICANT_CAPACITY.getAsInt(), Lubricant::isLubricant, this::setChanged);
        this.lubricantInput = new AutomationResourceHandler<>(lubricant, index -> true, index -> false);
        this.fluidAutomation = new CombinedResourceHandler<>(fuelInput, lubricantInput);
        // Twenty ticks of a full-length array's exhaust.
        this.exhaust = new HeatBuffer((int) Math.ceil(20 * ArcforgeConfig.GAS_TURBINE_MAX_HU_PER_LENGTH.getAsInt() * MAX_LENGTH
                * ArcforgeConfig.GAS_TURBINE_EXHAUST_FRACTION.getAsDouble()), EXHAUST_MAX_CELSIUS, this::setChanged);
        this.exhaustOutput = exhaust.output();
        this.data = new WideIntContainerData(GasTurbineArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case GasTurbineArrayMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case GasTurbineArrayMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case GasTurbineArrayMenu.DATA_FUEL -> fuel.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(fuel.getResource(0).getFluid()) : -1;
                    case GasTurbineArrayMenu.DATA_FUEL_AMOUNT -> fuel.getAmount();
                    case GasTurbineArrayMenu.DATA_FUEL_CAPACITY -> fuel.getCapacity();
                    case GasTurbineArrayMenu.DATA_FUEL_MB_X100 -> (int) Math.round(fuelPerTick() * 100.0);
                    case GasTurbineArrayMenu.DATA_THROTTLE -> (int) Math.round(throttle() * 100.0);
                    case GasTurbineArrayMenu.DATA_FE_PER_TICK -> fePerTick;
                    case GasTurbineArrayMenu.DATA_RPM -> (int) Math.round(rpm);
                    case GasTurbineArrayMenu.DATA_MAX_RPM -> maxRpm();
                    case GasTurbineArrayMenu.DATA_LENGTH -> getLength();
                    case GasTurbineArrayMenu.DATA_STATUS -> status.ordinal();
                    case GasTurbineArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case GasTurbineArrayMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    case GasTurbineArrayMenu.DATA_LUBRICANT -> lubricant.getAmount();
                    case GasTurbineArrayMenu.DATA_LUBRICANT_CAPACITY -> lubricant.getCapacity();
                    case GasTurbineArrayMenu.DATA_LUBRICANT_FLUID -> lubricant.getAmount() > 0
                            ? net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(lubricant.getResource(0).getFluid()) : -1;
                    case GasTurbineArrayMenu.DATA_EXHAUST_HU -> exhaustHu;
                    case GasTurbineArrayMenu.DATA_EXHAUST_CELSIUS -> exhaustCelsius;
                    case GasTurbineArrayMenu.DATA_VENTED_HU -> ventedHu;
                    case GasTurbineArrayMenu.DATA_FE_PER_MB_X10 -> (int) Math.round(fePerMb(BurnerFuel.of(fuel.getResource(0))) * 10.0);
                    default -> 0;
                };
            }
        };
    }

    // A fuel the turbine burns: a Fuel Burner fuel not marked "gas_turbine": false.
    public static boolean isFuel(FluidResource resource) {
        BurnerFuel fuel = BurnerFuel.of(resource);
        return fuel != null && fuel.gasTurbine();
    }

    // --- The numbers (see the class comment) ---

    // The fuel's burn temperature, or the reference temperature if it doesn't say.
    public static int burnTemperature(BurnerFuel fuel) {
        return fuel.burnTemperature().orElse(ArcforgeConfig.GAS_TURBINE_REFERENCE_TEMPERATURE.getAsInt());
    }

    public static double efficiency(int celsius) {
        return Math.min(1.0, celsius / (double) ArcforgeConfig.GAS_TURBINE_REFERENCE_TEMPERATURE.getAsInt());
    }

    // FE per HU burned, at full rotor speed, without lubricant and before the power multiplier.
    public static double fePerHu(int celsius) {
        return ArcforgeConfig.GAS_TURBINE_SIMPLE_CYCLE_FACTOR.getAsDouble() * efficiency(celsius);
    }

    // FE per mB of this fuel as GUIs and JEI show it: at full rotor speed, without lubricant, times the power multiplier.
    public static double fePerMb(@Nullable BurnerFuel fuel) {
        return fuel == null ? 0.0 : fuel.huPerMb() * fePerHu(burnTemperature(fuel)) * PowerGeneration.multiplier();
    }

    public static int exhaustCelsius(BurnerFuel fuel) {
        return (int) Math.round(burnTemperature(fuel) * ArcforgeConfig.GAS_TURBINE_EXHAUST_TEMPERATURE_FACTOR.getAsDouble());
    }

    public int getLength() {
        ShellStructure.Shell shell = getShell();
        return shell != null ? shell.length() : 5;
    }

    public int maxHuPerTick() {
        return ArcforgeConfig.GAS_TURBINE_MAX_HU_PER_LENGTH.getAsInt() * getLength();
    }

    // The most FE/t it makes at this length: full throttle on a fuel at the reference temperature, lubricated, times
    // the power multiplier. Also the most its energy ports push out a tick.
    public static int maxOutput(int length) {
        return PowerGeneration.cap((double) ArcforgeConfig.GAS_TURBINE_MAX_HU_PER_LENGTH.getAsInt() * length
                * ArcforgeConfig.GAS_TURBINE_SIMPLE_CYCLE_FACTOR.getAsDouble() * Lubricant.bonus());
    }

    public int maxOutput() {
        return maxOutput(getLength());
    }

    private GeneratorEnergyHandler newEnergy(int length) {
        int output = maxOutput(length);
        long capacity = (long) output * ArcforgeConfig.GAS_TURBINE_ENERGY_BUFFER_TICKS.getAsInt();
        return new GeneratorEnergyHandler((int) Math.min(Integer.MAX_VALUE, capacity), output, this::setChanged);
    }

    public static int maxRpm() {
        return ArcforgeConfig.GAS_TURBINE_MAX_RPM.getAsInt();
    }

    // How hard it is asked to run (0-1): signal / 15 in THROTTLE, else full.
    public double throttle() {
        return redstoneMode == RedstoneMode.THROTTLE ? Math.max(0, signal) / 15.0 : 1.0;
    }

    // The fuel it burns at the current throttle, in mB/t (0 when not running).
    public double fuelPerTick() {
        BurnerFuel burning = BurnerFuel.of(fuel.getResource(0));
        return phase == Phase.RUNNING && burning != null ? burnedHu / burning.huPerMb() : 0.0;
    }

    @Override
    protected ShellStructure structure() {
        return GasTurbineArrayCasingBlock.STRUCTURE;
    }

    @Override
    public List<RedstoneMode> getAllowedRedstoneModes() {
        return RedstoneMode.THROTTLED;
    }

    @Override
    public void setShell(ShellStructure.@Nullable Shell shell) {
        super.setShell(shell);
        if (shell != null) {
            fuel.setCapacity(ArcforgeConfig.GAS_TURBINE_TANK_PER_LENGTH.getAsInt() * shell.length());
            GeneratorEnergyHandler resized = newEnergy(shell.length());
            if (resized.getCapacityAsInt() != energy.getCapacityAsInt()) {
                resized.generate(energy.getAmountAsInt());
                energy = resized;
            }
        }
        targetsDirty = true;
    }

    // The intake is the end with air in front of its middle (the negative end if both or neither do); the
    // two end-centre casings are marked.
    @Override
    public void onFormed(@Nullable Direction facing) {
        ShellStructure.Shell shell = getShell();
        if (shell != null && level != null) {
            boolean negativeOpen = openAt(level, shell, Direction.AxisDirection.NEGATIVE);
            boolean positiveOpen = openAt(level, shell, Direction.AxisDirection.POSITIVE);
            intakeEnd = positiveOpen && !negativeOpen ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
            markEnd(level, shell.endCenter(intakeEnd), GasTurbineArrayCasingBlock.End.INTAKE);
            markEnd(level, shell.endCenter(intakeEnd.opposite()), GasTurbineArrayCasingBlock.End.EXHAUST);
            intakeOpen = intakeEnd == Direction.AxisDirection.NEGATIVE ? negativeOpen : positiveOpen;
            intakeChecked = true;
        }
        super.onFormed(facing);
    }

    private static void markEnd(Level level, BlockPos pos, GasTurbineArrayCasingBlock.End end) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof GasTurbineArrayCasingBlock && state.getValue(GasTurbineArrayCasingBlock.END) != end) {
            level.setBlock(pos, state.setValue(GasTurbineArrayCasingBlock.END, end), Block.UPDATE_ALL);
        }
    }

    private static boolean openAt(Level level, ShellStructure.Shell shell, Direction.AxisDirection end) {
        return level.getBlockState(shell.endCenter(end).relative(Direction.fromAxisAndDirection(shell.axis(), end))).isAir();
    }

    // The direction the intake faces out of the structure.
    public Direction intakeFacing() {
        ShellStructure.Shell shell = getShell();
        return Direction.fromAxisAndDirection(shell != null ? shell.axis() : Direction.Axis.X, intakeEnd);
    }

    public Direction.AxisDirection getIntakeEnd() {
        return intakeEnd;
    }

    public boolean isIntakeOpen() {
        return intakeOpen;
    }

    @Override
    public void onPortsChanged() {
        targetsDirty = true;
    }

    @Override
    protected void onSideConfigChanged() {
        super.onSideConfigChanged();
        targetsDirty = true;
    }

    @Override
    protected void tickMaster(ServerLevel level) {
        // Once a second: full speed counts for the advancements (arcforge:turbine_full_speed), with the throttle
        // signal (15 in any mode but Throttle).
        if (level.getGameTime() % 20 == 0 && rpm >= 0.99 * maxRpm()) {
            int throttleSignal = getRedstoneMode() == RedstoneMode.THROTTLE ? Math.max(signal, 0) : 15;
            ArcforgeAdvancements.turbineFullSpeed(this, multiblockId(), length(), throttleSignal);
        }
        ShellStructure.Shell shell = getShell();
        if (shell == null) {
            return;
        }
        long time = level.getGameTime();
        if (!intakeChecked || time % ArcforgeConfig.GAS_TURBINE_INTAKE_CHECK_INTERVAL.getAsInt() == 0) {
            intakeOpen = openAt(level, shell, intakeEnd);
            intakeChecked = true;
        }
        if (signal < 0 || time % SIGNAL_CHECK_INTERVAL == 0) {
            signal = readSignal(level);
        }
        boolean allowed = redstoneMode == RedstoneMode.THROTTLE ? signal > 0 : redstoneMode.canRun(isPowered());
        BurnerFuel burning = BurnerFuel.of(fuel.getResource(0));
        boolean hasFuel = burning != null && burning.gasTurbine() && fuel.getAmount() > 0;
        boolean canRun = allowed && intakeOpen && !energy.isFull();
        if (reignitionDelay > 0) {
            reignitionDelay--;
        }

        burnedHu = 0;
        boolean flamedOut = false;
        switch (phase) {
            case OFF -> {
                if (canRun && hasFuel && reignitionDelay == 0) {
                    phase = Phase.IGNITING;
                    ignitionLeft = ArcforgeConfig.GAS_TURBINE_IGNITION_TICKS.getAsInt();
                    playSound(level, shell, ModSounds.GAS_TURBINE_ARRAY_IGNITE.get());
                }
            }
            case IGNITING -> {
                if (!canRun || !hasFuel) {
                    phase = Phase.OFF;
                } else if (--ignitionLeft <= 0) {
                    phase = Phase.RUNNING;
                }
            }
            case RUNNING -> {
                if (!canRun) {
                    phase = Phase.OFF;
                } else if (!burn(burning)) {
                    flamedOut = true;
                }
            }
        }
        if (flamedOut) {
            phase = Phase.OFF;
            fuelBuffered = 0;
            reignitionDelay = ArcforgeConfig.GAS_TURBINE_REIGNITION_DELAY.getAsInt();
            playSound(level, shell, ModSounds.GAS_TURBINE_ARRAY_FLAMEOUT.get());
        }

        int maxHu = maxHuPerTick();
        boolean lubricated = lubricant.getAmount() > 0;
        double target = maxRpm() * burnedHu / maxHu;
        double lubricantBoost = lubricated && burnedHu > 0 ? ArcforgeConfig.GAS_TURBINE_LUBRICANT_SPIN_UP.getAsDouble() : 1.0;
        double spinUp = ArcforgeConfig.GAS_TURBINE_SPIN_UP.getAsDouble() * lubricantBoost;
        if (target > rpm) {
            // Speeding up closes a share of the gap, but never more than full speed / spoolTime a tick, so a throttle
            // slammed from 0 to 15 takes spoolTime ticks to wind up while small changes still follow quickly.
            double maxStep = maxRpm() * lubricantBoost / Math.max(1, ArcforgeConfig.GAS_TURBINE_SPOOL_TIME.getAsInt());
            rpm += Math.min((target - rpm) * spinUp, maxStep);
        } else {
            rpm += (target - rpm) * ArcforgeConfig.GAS_TURBINE_SPIN_DOWN.getAsDouble();
        }
        if (rpm < 0.5 && target == 0) {
            rpm = 0;
        }
        // Exhaust nothing took since last tick is vented. Heat ports push it out in pushHeat, but Thermodynamic
        // Conduits pull it on their own tick, after this one, so it has to wait a tick for them.
        ventedHu = exhaust.remove(exhaust.getStored());
        fePerTick = 0;
        exhaustHu = 0;
        if (burnedHu > 0 && burning != null) {
            int celsius = burnTemperature(burning);
            double spun = Mth.clamp(rpm / Math.max(target, 1.0), 0.0, 1.0);
            double bonus = 1.0 + (lubricated ? Lubricant.bonus() - 1.0 : 0.0);
            fePerTick = energy.generate(PowerGeneration.fe(burnedHu * fePerHu(celsius) * spun * bonus));
            if (lubricated) {
                useLubricant();
            }
            exhaustHu = (int) Math.round(burnedHu * ArcforgeConfig.GAS_TURBINE_EXHAUST_FRACTION.getAsDouble());
            exhaustCelsius = exhaustCelsius(burning);
            exhaust.setProducingAt(exhaustCelsius);
            exhaust.add(exhaustHu);
            setChanged();
        } else {
            exhaust.setProducing(false);
        }
        // Exhaust heat goes out of the Heat ports; whatever nothing takes by next tick is vented (above).
        pushHeat(level);

        status = status(allowed, hasFuel, target);
        pushEnergy(level);
        boolean changed = Math.abs(rpm - syncedRpm) >= 1.0 || isVenting() != syncedVenting || Math.abs(load() - syncedLoad) >= 0.01F
                || exhaustHu != syncedExhaustHu;
        if (changed && time - lastSync >= SYNC_INTERVAL || (rpm == 0 && syncedRpm != 0)) {
            syncedRpm = (float) rpm;
            syncedLoad = load();
            syncedVenting = isVenting();
            syncedExhaustHu = exhaustHu;
            lastSync = time;
            sync();
        }
    }

    // Burns this tick's fuel; false if the tank can't cover it (a flameout).
    private boolean burn(@Nullable BurnerFuel burning) {
        if (burning == null || !burning.gasTurbine()) {
            return false;
        }
        double wantHu = maxHuPerTick() * throttle();
        double needMb = wantHu / burning.huPerMb();
        if (fuelBuffered < needMb) {
            int drain = (int) Math.min(Math.ceil(needMb - fuelBuffered - 1.0E-9), fuel.getAmount());
            if (drain > 0) {
                try (Transaction tx = Transaction.openRoot()) {
                    fuelBuffered += fuel.extract(0, fuel.getResource(0), drain, tx);
                    tx.commit();
                }
            }
        }
        if (fuelBuffered + 1.0E-9 < needMb) {
            return false;
        }
        fuelBuffered = Math.max(0.0, fuelBuffered - needMb);
        burnedHu = wantHu;
        return true;
    }

    private MachineStatus status(boolean allowed, boolean hasFuel, double target) {
        if (!allowed) {
            return redstoneMode == RedstoneMode.THROTTLE ? MachineStatus.NO_SIGNAL : MachineStatus.DISABLED;
        }
        if (!intakeOpen) {
            return MachineStatus.INTAKE_SHUT;
        }
        if (energy.isFull()) {
            return MachineStatus.FULL;
        }
        return switch (phase) {
            case IGNITING -> MachineStatus.IGNITING;
            case RUNNING -> rpm < target * 0.95 ? MachineStatus.SPOOLING : MachineStatus.RUNNING;
            case OFF -> reignitionDelay > 0 ? MachineStatus.FLAMEOUT
                    : !hasFuel ? (rpm > 1 ? MachineStatus.COASTING : MachineStatus.NO_FUEL)
                    : MachineStatus.COASTING;
        };
    }

    // The share of full heat throughput burning now (what the combustor glow shows).
    private float load() {
        int maxHu = maxHuPerTick();
        return maxHu > 0 ? (float) Mth.clamp(burnedHu / maxHu, 0.0, 1.0) : 0.0F;
    }

    private int readSignal(ServerLevel level) {
        int best = 0;
        for (BlockPos pos : BlockPos.betweenClosed(getMinCorner(), getMaxCorner())) {
            if (isPart(pos)) {
                best = Math.max(best, level.getBestNeighborSignal(pos));
                if (best >= 15) {
                    break;
                }
            }
        }
        return best;
    }

    private void playSound(ServerLevel level, ShellStructure.Shell shell, net.minecraft.sounds.SoundEvent sound) {
        Vec3 centre = Vec3.atCenterOf(shell.min()).add(Vec3.atCenterOf(shell.max())).scale(0.5);
        level.playSound(null, centre.x, centre.y, centre.z, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    // 1 mB every lubricantInterval ticks for every 3 blocks of length (as the Steam Turbine Array).
    private void useLubricant() {
        lubricantUsed += getLength() / 3.0 / ArcforgeConfig.TURBINE_ARRAY_LUBRICANT_INTERVAL.getAsInt();
        int whole = (int) Math.min(Math.floor(lubricantUsed), lubricant.getAmount());
        if (whole > 0) {
            lubricantUsed -= whole;
            try (Transaction tx = Transaction.openRoot()) {
                lubricant.extract(0, lubricant.getResource(0), whole, tx);
                tx.commit();
            }
        }
    }

    // What touches the energy and Heat port faces, found again when the ports change.
    private void refreshTargets(ServerLevel level) {
        targetsDirty = false;
        energyTargets.clear();
        heatTargets.clear();
        for (MultiblockPorts.Port port : MultiblockPorts.list(level, this)) {
            BlockPos target = port.pos().relative(port.face()).immutable();
            if (port.mode() == SideMode.ENERGY) {
                energyTargets.add(BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, target, port.face().getOpposite()));
            } else if (port.mode() == SideMode.HEAT) {
                heatTargets.add(BlockCapabilityCache.create(ModCapabilities.HEAT, level, target, port.face().getOpposite()));
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

    private void pushHeat(ServerLevel level) {
        if (targetsDirty) {
            refreshTargets(level);
        }
        if (exhaust.getStored() <= 0) {
            return;
        }
        // At least what it's making, so none of it is lost when the contact rate is lower.
        int rate = Math.max(ArcforgeConfig.HEAT_CONTACT_RATE.getAsInt(), exhaustHu);
        for (BlockCapabilityCache<HeatHandler, @Nullable Direction> target : heatTargets) {
            HeatHandler handler = target.getCapability();
            if (handler != null) {
                MachineOutputs.moveHeat(exhaust, handler, rate);
            }
        }
    }

    public GeneratorEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getFuel() {
        return fuel;
    }

    public FilteredFluidTank getLubricant() {
        return lubricant;
    }

    public Phase getPhase() {
        return phase;
    }

    public double getRpm() {
        return rpm;
    }

    public int getFePerTick() {
        return fePerTick;
    }

    public double getBurnedHu() {
        return burnedHu;
    }

    public int getExhaustHu() {
        return exhaustHu;
    }

    public int getExhaustCelsius() {
        return exhaustCelsius;
    }

    public boolean isVenting() {
        return ventedHu > 0;
    }

    public int getVentedHu() {
        return ventedHu;
    }

    // --- Client side ---

    // The client sets the hook (to GasTurbineArraySound.keepPlaying and the exhaust haze), so this class never
    // loads client code on a server.
    public static Consumer<GasTurbineArrayBlockEntity> clientHook = turbine -> {};

    public void clientTick() {
        if (isMaster()) {
            clientHook.accept(this);
        }
    }

    public float getSyncedRpm() {
        return syncedRpm;
    }

    // The last synced share of full heat throughput (0-1).
    public float getSyncedLoad() {
        return syncedLoad;
    }

    public boolean isSyncedVenting() {
        return syncedVenting;
    }

    public int getSyncedExhaustHu() {
        return syncedExhaustHu;
    }

    // The rotor angle in degrees at this moment, at the last synced speed, on the Steam Turbine Array's
    // square-root curve.
    public float advanceAngle(double time) {
        if (clientTime >= 0) {
            double share = Mth.clamp(syncedRpm / (double) maxRpm(), 0.0, 1.0);
            clientAngle = (float) ((clientAngle + (time - clientTime) * Math.sqrt(share) * MAX_DEGREES_PER_TICK) % 360.0);
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
            case INPUT -> fuelInput;
            case LUBRICANT -> lubricantInput;
            default -> null;
        };
    }

    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return energy;
        }
        return faceMode(pos, side) == SideMode.ENERGY ? energy : null;
    }

    public @Nullable HeatHandler heatHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return exhaustOutput;
        }
        return faceMode(pos, side) == SideMode.HEAT ? exhaustOutput : null;
    }

    // Liquid fuel through fluid conduits and gaseous fuel (Hydrogen) through gas conduits, both into Input
    // ports; lubricant through fluid conduits.
    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.INPUT || mode == SideMode.LUBRICANT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ITEM -> ConnectionMode.NONE;
        };
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fuel.setCapacity(ArcforgeConfig.GAS_TURBINE_TANK_PER_LENGTH.getAsInt() * getLength());
        energy = newEnergy(getLength());
        energy.deserialize(input.childOrEmpty("energy"));
        fuel.deserialize(input.childOrEmpty("fuel"));
        lubricant.deserialize(input.childOrEmpty("lubricant"));
        phase = Phase.byId(input.getIntOr("phase", 0));
        ignitionLeft = input.getIntOr("ignition_left", 0);
        reignitionDelay = input.getIntOr("reignition_delay", 0);
        intakeEnd = input.getBooleanOr("intake_positive", false) ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE;
        intakeOpen = input.getBooleanOr("intake_open", true);
        fuelBuffered = input.getDoubleOr("fuel_buffered", 0.0);
        lubricantUsed = input.getDoubleOr("lubricant_used", 0.0);
        rpm = input.getDoubleOr("rpm", 0.0);
        syncedRpm = (float) rpm;
        syncedLoad = input.getFloatOr("load", 0.0F);
        ventedHu = input.getIntOr("vented_hu", 0);
        syncedVenting = isVenting();
        exhaustHu = input.getIntOr("exhaust_hu", 0);
        syncedExhaustHu = exhaustHu;
        targetsDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        fuel.serialize(output.child("fuel"));
        lubricant.serialize(output.child("lubricant"));
        output.putInt("phase", phase.ordinal());
        output.putInt("ignition_left", ignitionLeft);
        output.putInt("reignition_delay", reignitionDelay);
        output.putBoolean("intake_positive", intakeEnd == Direction.AxisDirection.POSITIVE);
        output.putBoolean("intake_open", intakeOpen);
        output.putDouble("fuel_buffered", fuelBuffered);
        output.putDouble("lubricant_used", lubricantUsed);
        output.putDouble("rpm", rpm);
        output.putFloat("load", load());
        output.putInt("vented_hu", ventedHu);
        output.putInt("exhaust_hu", exhaustHu);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.gas_turbine_array.sized", getLength());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GasTurbineArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
