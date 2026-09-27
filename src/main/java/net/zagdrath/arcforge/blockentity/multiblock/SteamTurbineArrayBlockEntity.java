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
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.machine.SteamTurbineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.SteamTurbineArrayMenu;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.BoilerCore;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Steam Turbine Array (see ShellMultiblockBlockEntity): a turbine L blocks long with a rotor of L - 2
// blade sets. It takes up to 40 x L mB/t of steam at the array's FE per mB (Steam 10, High-Pressure 18,
// Superheated 28; a 9-long array on Superheated makes 10,080 FE/t). The rotor spins up toward a speed set
// by the flow (about 5 s to get there) and coasts down (about 10 s) when the steam stops; the output is
// scaled by how close the rotor is to that speed, so opening the valve gives a rising output over a few
// seconds. Steam is used either way. Its front is a long side (the window, see chooseFront), so the
// generator end (positive along the axis) is on its left or right. A new turbine's ports are an energy port
// on the generator end's cap and a steam port on the bearing end's. Heavy Oil in its lubricant tank (fed
// through lubricant ports) adds 8% to its output and doubles how fast the rotor spins up while it
// generates, using 1 mB every 20 ticks for every 3 blocks of length.
public class SteamTurbineArrayBlockEntity extends ShellMultiblockBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.ENERGY, SideMode.LUBRICANT);
    private static final double SPIN_UP = 0.01;
    private static final double SPIN_DOWN = 0.005;
    // The rotor speed is sent to clients at most this often (and when it changes noticeably).
    private static final int SYNC_INTERVAL = 5;

    private final GeneratorEnergyHandler energy;
    private final FilteredFluidTank steam;
    private final ResourceHandler<FluidResource> steamInput;
    private final FilteredFluidTank lubricant;
    private final ResourceHandler<FluidResource> lubricantInput;
    private final ResourceHandler<FluidResource> fluidAutomation;
    // Lubricant used but not yet taken from the tank (mB).
    private double lubricantUsed;
    private final ContainerData data;
    private final List<BlockCapabilityCache<EnergyHandler, @Nullable Direction>> energyTargets = new ArrayList<>();
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
        this.energy = new GeneratorEnergyHandler(
                ArcforgeConfig.TURBINE_ARRAY_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.TURBINE_ARRAY_MAX_OUTPUT.getAsInt(),
                this::setChanged);
        this.steam = new FilteredFluidTank(ArcforgeConfig.TURBINE_ARRAY_TANK_PER_LENGTH.getAsInt() * 3, BoilerCore::isSteam, this::setChanged);
        this.steamInput = new AutomationResourceHandler<>(steam, index -> true, index -> false);
        this.lubricant = new FilteredFluidTank(ArcforgeConfig.TURBINE_ARRAY_LUBRICANT_CAPACITY.getAsInt(), SteamTurbineBlockEntity::isLubricant, this::setChanged);
        this.lubricantInput = new AutomationResourceHandler<>(lubricant, index -> true, index -> false);
        this.fluidAutomation = new CombinedResourceHandler<>(steamInput, lubricantInput);
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
                    default -> 0;
                };
            }
        };
    }

    public int getLength() {
        ShellStructure.Shell shell = getShell();
        return shell != null ? shell.length() : 3;
    }

    public int maxFlow() {
        return ArcforgeConfig.TURBINE_ARRAY_FLOW_PER_LENGTH.getAsInt() * getLength();
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
            steam.setCapacity(ArcforgeConfig.TURBINE_ARRAY_TANK_PER_LENGTH.getAsInt() * shell.length());
        }
        targetsDirty = true;
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

    // Energy out of the generator end, steam into the bearing end.
    @Override
    public Map<BlockPos, SideMode> defaultPorts(Level level) {
        ShellStructure.Shell shell = getShell();
        if (shell == null) {
            return super.defaultPorts(level);
        }
        Map<BlockPos, SideMode> ports = new LinkedHashMap<>();
        ports.put(shell.endCenter(Direction.AxisDirection.POSITIVE), SideMode.ENERGY);
        ports.put(shell.endCenter(Direction.AxisDirection.NEGATIVE), SideMode.INPUT);
        return ports;
    }

    @Override
    public void onPortsChanged() {
        targetsDirty = true;
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
        int face = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? ShellStructure.WIDTH - 1 : 0;
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
        boolean lubricated = lubricant.getAmount() > 0;
        double target = (double) maxRpm() * flow / maxFlow;
        double spinUp = SPIN_UP * (lubricated && flow > 0 ? ArcforgeConfig.LUBRICANT_SPIN_UP.getAsDouble() : 1.0);
        rpm += (target - rpm) * (target > rpm ? spinUp : SPIN_DOWN);
        if (rpm < 0.5 && target == 0) {
            rpm = 0;
        }
        if (flow > 0 && grade != null) {
            double spun = Mth.clamp(rpm / Math.max(target, 1.0), 0.0, 1.0);
            double bonus = lubricated ? SteamTurbineBlockEntity.lubricantBonus() : 1.0;
            fePerTick = energy.generate((int) Math.round(flow * grade.arrayFePerMb() * spun * bonus));
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
        boolean changed = Math.abs(rpm - syncedRpm) >= 1.0 || flow != syncedFlow;
        if (changed && level.getGameTime() - lastSync >= SYNC_INTERVAL || (rpm == 0 && syncedRpm != 0)) {
            syncedRpm = (float) rpm;
            syncedFlow = flow;
            lastSync = level.getGameTime();
            sync();
        }
    }

    // 1 mB every lubricantInterval ticks for every 3 blocks of length.
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

    // Pushes FE out of every casing on an energy face of the box, up to the output rate in total.
    private void pushEnergy(ServerLevel level) {
        if (targetsDirty) {
            targetsDirty = false;
            energyTargets.clear();
            for (BlockPos pos : BlockPos.betweenClosed(getMinCorner(), getMaxCorner())) {
                if (!isPart(pos)) {
                    continue;
                }
                for (Direction side : Direction.values()) {
                    if (faceMode(pos, side) == SideMode.ENERGY) {
                        energyTargets.add(BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, pos.relative(side).immutable(), side.getOpposite()));
                    }
                }
            }
        }
        int budget = Math.min(energy.getAmountAsInt(), ArcforgeConfig.TURBINE_ARRAY_MAX_OUTPUT.getAsInt());
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

    public GeneratorEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getSteam() {
        return steam;
    }

    public FilteredFluidTank getLubricant() {
        return lubricant;
    }

    public double getRpm() {
        return rpm;
    }

    public int getFePerTick() {
        return fePerTick;
    }

    // Client side: the steam density to draw, easing toward the share of the maximum flow going through.
    public float easeSteamDensity(float share) {
        float target = Math.min(1.0F, (float) syncedFlow / Math.max(1, maxFlow()));
        shownSteam = shownSteam < 0 ? target : shownSteam + (target - shownSteam) * share;
        return shownSteam;
    }

    // Client side: the rotor angle in degrees at this moment. It turns at up to 18° a tick (one turn a
    // second at full speed), at the last synced speed.
    public float advanceAngle(double time) {
        if (clientTime >= 0) {
            clientAngle = (float) ((clientAngle + (time - clientTime) * syncedRpm / maxRpm() * 18.0) % 360.0);
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
        return mode == SideMode.INPUT ? steamInput : mode == SideMode.LUBRICANT ? lubricantInput : null;
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
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.LUBRICANT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ITEM, THERMAL -> ConnectionMode.NONE;
        };
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        steam.setCapacity(ArcforgeConfig.TURBINE_ARRAY_TANK_PER_LENGTH.getAsInt() * getLength());
        energy.deserialize(input.childOrEmpty("energy"));
        steam.deserialize(input.childOrEmpty("steam"));
        lubricant.deserialize(input.childOrEmpty("lubricant"));
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
        output.putDouble("lubricant_used", lubricantUsed);
        output.putDouble("rpm", rpm);
        output.putInt("flow", flow);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.steam_turbine_array.sized", getLength());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new SteamTurbineArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
