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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
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
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.PowerGeneration;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.SteamTurbineArrayMenu;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.steam.BoilerCore;
import net.zagdrath.arcforge.steam.Lubricant;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Steam Turbine Array (see ShellMultiblockBlockEntity): a turbine L blocks long with a rotor of L - 2
// blade sets, sized to its cross-section's shorter side. It grows with its sections s = width x height / 9 x L (a
// 3x3x3 is 3, a 7x9x15 is 105): it takes up to 40 x s mB/t of steam at the array's FE per mB (Steam 20, High-Pressure
// 36, Superheated 56, times power.generationMultiplier; a 7x9x15 on Superheated makes 235,200 FE/t, more with its
// bonuses). Its output cap is a full flow of Superheated Steam with both bonuses, its FE buffer energyBufferTicks of
// that, and its energy ports share the cap. The rotor spins up toward a speed set
// by the power in the steam, flow times FE per mB, so a higher grade spins it faster (full speed is a full
// flow of Superheated); it takes about 5 s to get there and coasts down (about 10 s) when the steam stops; the output is
// scaled by how close the rotor is to that speed, so opening the valve gives a rising output over a few
// seconds. Steam is used either way. Its front is a long side (the window, see chooseFront), so the
// generator end (positive along the axis) is on its left or right. A new turbine's ports are an energy port
// on the generator end's cap and a steam port on the bearing end's. Heavy Oil in its lubricant tank (fed
// through lubricant ports) adds 8% to its output and doubles how fast the rotor spins up while it
// generates, using 1 mB every 20 ticks for every 3 sections. Spent steam vents, unless the turbine
// has an Exhaust port: then it goes into an exhaust tank as Exhaust Steam, pushed out of the Exhaust ports
// every tick, and while that tank has room the turbine makes 10% more (added to the lubricant bonus). A full
// exhaust tank vents the rest and loses the bonus, so the turbine never stalls.
public class SteamTurbineArrayBlockEntity extends ShellMultiblockBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    // The rotor's four blades look the same every 90°, so a turn of more than 45° a tick would look like it
    // was going backwards: full speed stays under that.
    private static final double MAX_DEGREES_PER_TICK = 36.0;
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.ENERGY, SideMode.LUBRICANT, SideMode.EXHAUST);
    private static final double SPIN_UP = 0.01;
    private static final double SPIN_DOWN = 0.005;
    // The rotor speed is sent to clients at most this often (and when it changes noticeably).
    private static final int SYNC_INTERVAL = 5;

    // Replaced (keeping what it holds) when the structure's size changes, since its size and rate follow it.
    private GeneratorEnergyHandler energy;
    private final FilteredFluidTank steam;
    private final ResourceHandler<FluidResource> steamInput;
    private final FilteredFluidTank lubricant;
    private final ResourceHandler<FluidResource> lubricantInput;
    private final FilteredFluidTank exhaust;
    private final ResourceHandler<FluidResource> exhaustOutput;
    private final ResourceHandler<FluidResource> fluidAutomation;
    // Whether any port is an Exhaust port (set when the ports change), and whether the exhaust drained this
    // tick (the vacuum bonus).
    private boolean hasExhaust;
    private boolean vacuum;
    // Lubricant used but not yet taken from the tank (mB).
    private double lubricantUsed;
    private final ContainerData data;
    private final List<BlockCapabilityCache<EnergyHandler, @Nullable Direction>> energyTargets = new ArrayList<>();
    private final List<BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>> exhaustTargets = new ArrayList<>();
    private boolean targetsDirty = true;
    private double rpm;
    private int flow;
    private int fePerTick;
    private float syncedRpm;
    private int syncedFlow;
    private long lastSync;
    // Client side: how thick the steam in the rotor chamber looks (0-1), easing toward the synced flow.
    private float shownSteam = -1;

    // Client side: the rotor angle, advanced as frames are drawn.
    private float clientAngle;
    private double clientTime = -1;

    public SteamTurbineArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.STEAM_TURBINE_ARRAY.get(), pos, state, 0, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.NONE, SideMode.INPUT, SideMode.ENERGY, SideMode.NONE, SideMode.NONE),
                SIDE_MODES);
        this.energy = newEnergy(3.0);
        this.steam = new FilteredFluidTank(ArcforgeConfig.TURBINE_ARRAY_TANK_PER_SECTION.getAsInt() * 3, BoilerCore::isSteam, this::setChanged);
        this.steamInput = new AutomationResourceHandler<>(steam, index -> true, index -> false);
        this.lubricant = new FilteredFluidTank(ArcforgeConfig.TURBINE_ARRAY_LUBRICANT_CAPACITY.getAsInt(), Lubricant::isLubricant, this::setChanged);
        this.lubricantInput = new AutomationResourceHandler<>(lubricant, index -> true, index -> false);
        this.exhaust = new FilteredFluidTank(ArcforgeConfig.TURBINE_ARRAY_EXHAUST_PER_SECTION.getAsInt() * 3,
                resource -> resource.is(ModFluids.EXHAUST_STEAM.get()), this::setChanged);
        this.exhaustOutput = new AutomationResourceHandler<>(exhaust, index -> false, index -> true);
        this.fluidAutomation = new CombinedResourceHandler<>(steamInput, lubricantInput, exhaustOutput);
        this.data = new WideIntContainerData(SteamTurbineArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case SteamTurbineArrayMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case SteamTurbineArrayMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case SteamTurbineArrayMenu.DATA_STEAM -> steam.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(steam.getResource(0).getFluid()) : -1;
                    case SteamTurbineArrayMenu.DATA_STEAM_AMOUNT -> steam.getAmount();
                    case SteamTurbineArrayMenu.DATA_STEAM_CAPACITY -> steam.getCapacity();
                    case SteamTurbineArrayMenu.DATA_FLOW -> flow;
                    case SteamTurbineArrayMenu.DATA_MAX_FLOW -> maxFlow();
                    case SteamTurbineArrayMenu.DATA_FE_PER_TICK -> fePerTick;
                    case SteamTurbineArrayMenu.DATA_RPM -> (int) Math.round(rpm);
                    case SteamTurbineArrayMenu.DATA_MAX_RPM -> maxRpm();
                    case SteamTurbineArrayMenu.DATA_LENGTH -> getLength();
                    case SteamTurbineArrayMenu.DATA_STATUS -> status.ordinal();
                    case SteamTurbineArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case SteamTurbineArrayMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    case SteamTurbineArrayMenu.DATA_LUBRICANT -> lubricant.getAmount();
                    case SteamTurbineArrayMenu.DATA_LUBRICANT_CAPACITY -> lubricant.getCapacity();
                    case SteamTurbineArrayMenu.DATA_LUBRICANT_FLUID -> lubricant.getAmount() > 0
                            ? net.minecraft.core.registries.BuiltInRegistries.FLUID.getId(lubricant.getResource(0).getFluid()) : -1;
                    case SteamTurbineArrayMenu.DATA_EXHAUST -> !hasExhaust ? SteamTurbineArrayMenu.EXHAUST_VENTING
                            : vacuum ? SteamTurbineArrayMenu.EXHAUST_VACUUM : SteamTurbineArrayMenu.EXHAUST_FULL;
                    default -> 0;
                };
            }
        };
    }

    public int getLength() {
        ShellStructure.Shell shell = getShell();
        return shell != null ? shell.length() : 3;
    }

    // How many 3x3 one-block-long sections it is: width x height / 9 x length (3 for a 3x3x3).
    public double getSections() {
        ShellStructure.Shell shell = getShell();
        return shell != null ? SteamBoilerArrayBlockEntity.sections(shell) : 3.0;
    }

    public int maxFlow() {
        return SteamBoilerArrayBlockEntity.scaled(ArcforgeConfig.TURBINE_ARRAY_FLOW_PER_SECTION.getAsInt(), getSections());
    }

    // The most FE/t it can make: a full flow of Superheated Steam with lubricant and the vacuum bonus, times the power
    // multiplier. Also the most its energy ports push out a tick.
    public static int maxOutput(double sections) {
        int flow = SteamBoilerArrayBlockEntity.scaled(ArcforgeConfig.TURBINE_ARRAY_FLOW_PER_SECTION.getAsInt(), sections);
        return PowerGeneration.cap(flow * SteamGrade.SUPERHEATED.arrayFePerMb() * (Lubricant.bonus() + ArcforgeConfig.TURBINE_ARRAY_VACUUM_BONUS.getAsDouble()));
    }

    public int maxOutput() {
        return maxOutput(getSections());
    }

    private GeneratorEnergyHandler newEnergy(double sections) {
        int output = maxOutput(sections);
        long capacity = (long) output * ArcforgeConfig.TURBINE_ARRAY_ENERGY_BUFFER_TICKS.getAsInt();
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

    // "W×H×L" (width across, height, length) for names and tooltips.
    public String sizeText() {
        ShellStructure.Shell shell = getShell();
        if (shell == null) {
            return "3×3×3";
        }
        int width = shell.size(shell.axis() == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        return width + "×" + shell.size(Direction.Axis.Y) + "×" + shell.length();
    }

    public static int maxRpm() {
        return ArcforgeConfig.TURBINE_ARRAY_MAX_RPM.getAsInt();
    }

    @Override
    protected ShellStructure structure() {
        return SteamTurbineArrayCasingBlock.STRUCTURE;
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
        steam.setCapacity(SteamBoilerArrayBlockEntity.scaled(ArcforgeConfig.TURBINE_ARRAY_TANK_PER_SECTION.getAsInt(), sections));
        exhaust.setCapacity(SteamBoilerArrayBlockEntity.scaled(ArcforgeConfig.TURBINE_ARRAY_EXHAUST_PER_SECTION.getAsInt(), sections));
    }

    @Override
    public void onFormed(@Nullable Direction facing) {
        ShellStructure.Shell shell = getShell();
        if (shell == null) {
            super.onFormed(facing);
            return;
        }
        // A rebuilt turbine breaks a tie the way it faced before.
        setStructureFacing(chooseFront(shell, wasFormedBefore() ? getFacing() : facing));
        super.onFormed(null);
    }

    @Override
    public void onPortsChanged() {
        targetsDirty = true;
        hasExhaust = level != null && MultiblockPorts.list(level, this).stream().anyMatch(port -> port.mode() == SideMode.EXHAUST);
    }

    // The front is one of the two long horizontal sides: the one with more Pressure Glass, else the one
    // toward the player who completed it (facing), else the one that puts the generator on the right.
    private Direction chooseFront(ShellStructure.Shell shell, @Nullable Direction facing) {
        Direction generatorEnd = Direction.fromAxisAndDirection(shell.axis(), Direction.AxisDirection.POSITIVE);
        Direction preferred = generatorEnd.getClockWise();
        Direction other = preferred.getOpposite();
        int preferredGlass = glassOn(shell, preferred);
        int otherGlass = glassOn(shell, other);
        return otherGlass > preferredGlass || otherGlass == preferredGlass && facing == other ? other : preferred;
    }

    private int glassOn(ShellStructure.Shell shell, Direction side) {
        if (level == null) {
            return 0;
        }
        int face = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? shell.size(side.getAxis()) - 1 : 0;
        int count = 0;
        for (BlockPos pos : shell.positions()) {
            if (shell.offset(pos, side.getAxis()) == face && level.getBlockState(pos).getBlock() instanceof PressureGlassBlock) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected void onSideConfigChanged() {
        super.onSideConfigChanged();
        targetsDirty = true;
    }

    @Override
    protected void tickMaster(ServerLevel level) {
        // Once a second: full speed counts for the advancements (arcforge:turbine_full_speed).
        if (level.getGameTime() % 20 == 0 && rpm >= 0.99 * maxRpm()) {
            ArcforgeAdvancements.turbineFullSpeed(this, multiblockId(), length(), 15);
        }
        // Arrays formed before the front moved to the window faced along the axis: turn them and reset
        // their side modes, which were relative to that facing.
        ShellStructure.Shell shell = getShell();
        if (shell != null && getFacing().getAxis() == shell.axis()) {
            sideConfig.reset();
            onFormed(null);
            onSideConfigChanged();
        }
        flow = 0;
        fePerTick = 0;
        SteamGrade grade = SteamGrade.of(steam.getResource(0));
        int maxFlow = maxFlow();
        boolean running = redstoneMode.canRun(isPowered());
        if (running && grade != null && steam.getAmount() > 0 && !energy.isFull()) {
            try (Transaction tx = Transaction.openRoot()) {
                flow = steam.extract(0, steam.getResource(0), Math.min(steam.getAmount(), maxFlow), tx);
                tx.commit();
            }
        }
        // With an Exhaust port the spent steam is kept as Exhaust Steam; while there was room for it, the
        // turbine gets the vacuum bonus. Whatever doesn't fit vents, as it all does without one.
        vacuum = hasExhaust && exhaust.getAmount() < exhaust.getCapacity();
        if (hasExhaust && flow > 0) {
            try (Transaction tx = Transaction.openRoot()) {
                exhaust.insert(0, FluidResource.of(ModFluids.EXHAUST_STEAM.get()), flow, tx);
                tx.commit();
            }
        }
        boolean lubricated = lubricant.getAmount() > 0;
        // The rotor's speed follows the power in the steam: flow times the grade's FE per mB, against a full
        // flow of Superheated Steam. Higher grades drive it faster for the same flow.
        double target = grade != null ? (double) maxRpm() * flow * grade.arrayFePerMb() / (maxFlow * SteamGrade.SUPERHEATED.arrayFePerMb()) : 0.0;
        double spinUp = SPIN_UP * (lubricated && flow > 0 ? ArcforgeConfig.LUBRICANT_SPIN_UP.getAsDouble() : 1.0);
        rpm += (target - rpm) * (target > rpm ? spinUp : SPIN_DOWN);
        if (rpm < 0.5 && target == 0) {
            rpm = 0;
        }
        if (flow > 0 && grade != null) {
            double spun = Mth.clamp(rpm / Math.max(target, 1.0), 0.0, 1.0);
            double bonus = 1.0 + (lubricated ? Lubricant.bonus() - 1.0 : 0.0) + (vacuum ? ArcforgeConfig.TURBINE_ARRAY_VACUUM_BONUS.getAsDouble() : 0.0);
            fePerTick = energy.generate(PowerGeneration.fe(flow * grade.arrayFePerMb() * spun * bonus));
            if (lubricated) {
                useLubricant();
            }
            setChanged();
        }

        if (!running) {
            status = MachineStatus.DISABLED;
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
        boolean changed = Math.abs(rpm - syncedRpm) >= 1.0 || flow != syncedFlow;
        if (changed && level.getGameTime() - lastSync >= SYNC_INTERVAL || (rpm == 0 && syncedRpm != 0)) {
            syncedRpm = (float) rpm;
            syncedFlow = flow;
            lastSync = level.getGameTime();
            sync();
        }
    }

    // 1 mB every lubricantInterval ticks for every 3 sections.
    private void useLubricant() {
        lubricantUsed += getSections() / 3.0 / ArcforgeConfig.TURBINE_ARRAY_LUBRICANT_INTERVAL.getAsInt();
        int whole = (int) Math.min(Math.floor(lubricantUsed), lubricant.getAmount());
        if (whole > 0) {
            lubricantUsed -= whole;
            try (Transaction tx = Transaction.openRoot()) {
                lubricant.extract(0, lubricant.getResource(0), whole, tx);
                tx.commit();
            }
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

    // Pushes FE out of every casing on an energy face of the box, up to the output rate in total.
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

    // Pushes Exhaust Steam out of every Exhaust port face, up to the turbine's max flow a tick in total.
    private void pushExhaust(ServerLevel level) {
        if (targetsDirty) {
            refreshTargets(level);
        }
        int budget = maxFlow();
        for (BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> target : exhaustTargets) {
            if (budget <= 0) {
                break;
            }
            ResourceHandler<FluidResource> handler = target.getCapability();
            if (handler != null) {
                budget -= ResourceHandlerUtil.move(exhaustOutput, handler, resource -> true, budget, null);
            }
        }
    }

    public GeneratorEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getSteam() {
        return steam;
    }

    public FilteredFluidTank getLubricant() {
        return lubricant;
    }

    public FilteredFluidTank getExhaust() {
        return exhaust;
    }

    public boolean hasExhaust() {
        return hasExhaust;
    }

    // Whether it got the vacuum bonus last tick.
    public boolean isVacuum() {
        return vacuum;
    }

    public double getRpm() {
        return rpm;
    }

    public int getFePerTick() {
        return fePerTick;
    }

    // Client side: keeps the running sound going while the rotor turns. The client sets the hook (to
    // TurbineArraySound.keepPlaying), so this class never loads client code on a server.
    public static Consumer<SteamTurbineArrayBlockEntity> clientSoundHook = turbine -> {};

    public void clientTick() {
        if (isMaster()) {
            clientSoundHook.accept(this);
        }
    }

    // Client side: the last synced rotor speed.
    public float getSyncedRpm() {
        return syncedRpm;
    }

    // Client side: the steam density to draw, easing toward the share of the maximum flow going through.
    public float easeSteamDensity(float share) {
        float target = Math.min(1.0F, (float) syncedFlow / Math.max(1, maxFlow()));
        shownSteam = shownSteam < 0 ? target : shownSteam + (target - shownSteam) * share;
        return shownSteam;
    }

    // Client side: the rotor angle in degrees at this moment, at the last synced speed. It turns at up to
    // MAX_DEGREES_PER_TICK at full speed, on a square-root curve so it looks lively at part speed too.
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
            case INPUT -> steamInput;
            case LUBRICANT -> lubricantInput;
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
            case FLUID -> mode == SideMode.LUBRICANT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ITEM, THERMAL -> ConnectionMode.NONE;
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
        lubricant.deserialize(input.childOrEmpty("lubricant"));
        exhaust.deserialize(input.childOrEmpty("exhaust"));
        hasExhaust = input.getBooleanOr("has_exhaust", false);
        lubricantUsed = input.getDoubleOr("lubricant_used", 0.0);
        rpm = input.getDoubleOr("rpm", 0.0);
        syncedRpm = (float) rpm;
        flow = input.getIntOr("flow", 0);
        syncedFlow = flow;
        targetsDirty = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        steam.serialize(output.child("steam"));
        lubricant.serialize(output.child("lubricant"));
        exhaust.serialize(output.child("exhaust"));
        output.putBoolean("has_exhaust", hasExhaust);
        output.putDouble("lubricant_used", lubricantUsed);
        output.putDouble("rpm", rpm);
        output.putInt("flow", flow);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.steam_turbine_array.sized", sizeText());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SteamTurbineArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
