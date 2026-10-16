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
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.EngineFuel;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.PowerGeneration;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.output.OutputLimit;
import net.zagdrath.arcforge.machine.output.OutputLimited;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.DieselGeneratorMenu;
import net.zagdrath.arcforge.multiblock.DieselEngine;
import net.zagdrath.arcforge.multiblock.DieselGeneratorLayout;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.PlantKind;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Diesel Generator (see OilfieldStructures.PlantBox), run by its controller: an engine-generator set 3 wide and 3
// tall whose length sets its engine (DieselEngine: 5 long a V8, 7 a V12, 9 a V16). It burns a fuel from the
// arcforge:engine_fuels data map (Diesel, Gasoline, Biodiesel, Refinery Gas; never a sour fuel) straight into FE, each
// mB worth its fe_per_mb, a little more in the bigger engines (efficiencyV12, efficiencyV16). It runs up to speed over
// dieselGenerator.spinUpTicks and then makes its engine's full output (times power.generationMultiplier); when its FE
// buffer fills it throttles back to what's drawn, burning only the fuel for that, and coasts down when it has no fuel
// or redstone stops it. Its fuel tank grows with its length and its FE buffer with its output. Fuel goes in through
// Input ports; FE comes out of Energy ports, pushed to what they touch. The engine's fan and flywheel turn as fast as
// it runs (see DieselGeneratorRenderer), and its stacks smoke while it burns.
public class DieselGeneratorBlockEntity extends PlantControllerBlockEntity implements OutputLimited {
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.ENERGY);
    // Speed is synced to clients when it moves by this much.
    private static final float SPEED_STEP = 0.04F;
    // The flywheel's turn a tick (degrees) at full speed.
    public static final float FLYWHEEL_SPEED = 30.0F;

    private GeneratorEnergyHandler energy;
    private final FilteredFluidTank fuel;
    private final ResourceHandler<FluidResource> fuelInput;
    private final ContainerData data;
    private final OutputLimit outputLimit = new OutputLimit(OutputLimit.Unit.FE);
    private final List<BlockCapabilityCache<EnergyHandler, @Nullable Direction>> energyTargets = new ArrayList<>();
    private boolean targetsDirty = true;

    // How fast it runs (0 to 1), the FE it made last tick, the fuel it burned (mB/t), and fuel owed below a whole mB.
    private float speed;
    private int made;
    private double burned;
    private double pending;
    private float syncedSpeed = -1.0F;
    // Client side: the flywheel's angle (degrees) and when it was last moved on.
    private float angle;
    private float lastFrame = -1.0F;
    // Client side: how far the rain flap is into its flapping (0 at rest, 1 running), easing in and out.
    private float flapRun;
    private float lastFlapFrame = -1.0F;
    // The engine the tank and buffer are sized for: the one it was last formed as, kept while it's broken so a
    // half-built or damaged V16 doesn't lose what it holds.
    private DieselEngine sized = DieselEngine.V8;

    public DieselGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.DIESEL_GENERATOR.get(), PlantKind.DIESEL_GENERATOR, pos, state, 0, (level, slot, resource) -> false, UPGRADES,
                SIDE_MODES);
        this.energy = newEnergy(DieselEngine.V8);
        this.fuel = new FilteredFluidTank(tankCapacity(DieselEngine.V8), resource -> EngineFuel.of(resource) != null, this::setChanged);
        this.fuelInput = new AutomationResourceHandler<>(fuel, index -> true, index -> false);
        this.data = new WideIntContainerData(DieselGeneratorMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case DieselGeneratorMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case DieselGeneratorMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case DieselGeneratorMenu.DATA_MADE -> made;
                    case DieselGeneratorMenu.DATA_MAX_OUTPUT -> maxOutput();
                    case DieselGeneratorMenu.DATA_SPEED -> Math.round(speed * 1000);
                    case DieselGeneratorMenu.DATA_BURNED -> (int) Math.round(burned * 100);
                    case DieselGeneratorMenu.DATA_FUEL_FLUID -> fuel.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(fuel.getResource(0).getFluid()) : -1;
                    case DieselGeneratorMenu.DATA_FUEL -> fuel.getAmount();
                    case DieselGeneratorMenu.DATA_FUEL_CAPACITY -> fuel.getCapacity();
                    case DieselGeneratorMenu.DATA_FE_PER_MB -> fePerMb(EngineFuel.of(fuel.getResource(0)));
                    case DieselGeneratorMenu.DATA_STATUS -> status.ordinal();
                    case DieselGeneratorMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case DieselGeneratorMenu.DATA_SIDE_CONFIG -> packedSides();
                    default -> 0;
                };
            }
        };
    }

    // A client-side copy of the slots (none but the upgrade slots), for the menu.
    public static MachineItemHandler clientItems() {
        return clientItems(0, UPGRADES);
    }

    @Override
    protected List<float[]> solids(int length) {
        return DieselGeneratorLayout.of(DieselEngine.of(length)).solids();
    }

    // The engine it's built as (the one it was last formed as while it isn't formed).
    public DieselEngine getEngine() {
        return box != null ? DieselEngine.of(box.length()) : sized;
    }

    // FE/t at full speed for an engine, times the power multiplier.
    public static int maxOutput(DieselEngine engine) {
        return PowerGeneration.fe(switch (engine) {
            case V8 -> ArcforgeConfig.DIESEL_GENERATOR_MAX_OUTPUT.getAsInt();
            case V12 -> ArcforgeConfig.DIESEL_GENERATOR_MAX_OUTPUT_V12.getAsInt();
            case V16 -> ArcforgeConfig.DIESEL_GENERATOR_MAX_OUTPUT_V16.getAsInt();
        });
    }

    public int maxOutput() {
        return maxOutput(getEngine());
    }

    // How much more FE a mB makes in this engine than in the V8.
    public static double efficiency(DieselEngine engine) {
        return switch (engine) {
            case V8 -> 1.0;
            case V12 -> ArcforgeConfig.DIESEL_GENERATOR_EFFICIENCY_V12.getAsDouble();
            case V16 -> ArcforgeConfig.DIESEL_GENERATOR_EFFICIENCY_V16.getAsDouble();
        };
    }

    // FE one mB of this fuel makes in this engine, times the power multiplier (0 for no fuel).
    public static int fePerMb(@Nullable EngineFuel fuel, DieselEngine engine) {
        return fuel == null ? 0 : PowerGeneration.fe((int) Math.round(fuel.fePerMb() * efficiency(engine)));
    }

    public int fePerMb(@Nullable EngineFuel fuel) {
        return fePerMb(fuel, getEngine());
    }

    // The fuel tank: the V8's, times length / 5.
    public static int tankCapacity(DieselEngine engine) {
        return (int) Math.min(Integer.MAX_VALUE, (long) ArcforgeConfig.DIESEL_GENERATOR_TANK_CAPACITY.getAsInt() * engine.length / DieselEngine.V8.length);
    }

    // The FE buffer: the V8's, grown with the engine's output.
    public static int energyCapacity(DieselEngine engine) {
        long v8 = ArcforgeConfig.DIESEL_GENERATOR_ENERGY_CAPACITY.getAsInt();
        return (int) Math.min(Integer.MAX_VALUE, v8 * maxOutput(engine) / Math.max(1, maxOutput(DieselEngine.V8)));
    }

    private GeneratorEnergyHandler newEnergy(DieselEngine engine) {
        return new GeneratorEnergyHandler(energyCapacity(engine), ArcforgeConfig.DIESEL_GENERATOR_MAX_OUTPUT_RATE.getAsInt(), this::setChanged);
    }

    // Sizes the tank and buffer to the engine it's formed as (keeping what they hold, up to their new size); left as
    // they are while it isn't formed.
    private void resize() {
        if (box == null) {
            return;
        }
        DieselEngine engine = getEngine();
        sized = engine;
        fuel.setCapacity(tankCapacity(engine));
        if (energy.getCapacityAsInt() != energyCapacity(engine)) {
            GeneratorEnergyHandler resized = newEnergy(engine);
            resized.generate(energy.getAmountAsInt());
            energy = resized;
        }
    }

    // --- Running ---

    public void serverTick(ServerLevel level) {
        made = 0;
        burned = 0;
        if (!tickStructure(level)) {
            speed = 0.0F;
            syncSpeed();
            return;
        }
        boolean allowed = redstoneMode.canRun(powered);
        EngineFuel burning = EngineFuel.of(fuel.getResource(0));
        boolean fuelled = burning != null && (fuel.getAmount() > 0 || pending > 0);
        float target = allowed && fuelled ? 1.0F : 0.0F;
        float step = 1.0F / ArcforgeConfig.DIESEL_GENERATOR_SPIN_UP.getAsInt();
        speed = target > speed ? Math.min(target, speed + step) : Math.max(target, speed - step * 0.5F);
        int full = maxOutput();
        if (fuelled && allowed && speed > 0.0F) {
            int wanted = Math.round(full * speed);
            int room = energy.getCapacityAsInt() - energy.getAmountAsInt();
            int fe = Math.min(wanted, room);
            double perMb = fePerMb(burning);
            if (fe > 0 && perMb > 0) {
                double need = fe / perMb;
                // Whole mB from the tank, carried below a mB to the next tick.
                if (pending < need) {
                    int take = (int) Math.min(fuel.getAmount(), Math.ceil(need - pending));
                    if (take > 0) {
                        try (Transaction tx = Transaction.openRoot()) {
                            pending += fuel.extract(0, fuel.getResource(0), take, tx);
                            tx.commit();
                        }
                    }
                }
                double used = Math.min(need, pending);
                pending -= used;
                burned = used;
                // fe / perMb * perMb can land just under fe, so a fully fuelled tick makes fe exactly.
                made = energy.generate(used >= need ? fe : (int) Math.floor(used * perMb));
            }
            status = made <= 0 ? MachineStatus.THROTTLED : speed < 0.98F ? MachineStatus.SPINNING_UP : made < wanted ? MachineStatus.THROTTLED
                    : MachineStatus.GENERATING;
        } else {
            status = !allowed ? MachineStatus.DISABLED : speed > 0.0F ? MachineStatus.COASTING : MachineStatus.NO_FUEL;
        }
        setRunning(burned > 0);
        pushEnergy(level);
        outputLimit.tick(level, allowed && fuelled, () -> OutputLimit.anyPortTakes(level, this, SideMode.ENERGY, OutputLimit.Unit.FE),
                energy.getAmountAsInt(), energy.getCapacityAsInt(), energy.drained(), Math.round(full * speed));
        setChanged();
        syncSpeed();
    }

    private void syncSpeed() {
        if (Math.abs(speed - syncedSpeed) >= SPEED_STEP || speed == 0.0F && syncedSpeed != 0.0F || speed == 1.0F && syncedSpeed != 1.0F) {
            syncedSpeed = speed;
            sync();
        }
    }

    // What touches the Energy ports, found again when the ports change.
    private void refreshTargets(ServerLevel level) {
        targetsDirty = false;
        energyTargets.clear();
        for (MultiblockPorts.Port port : MultiblockPorts.list(level, this)) {
            if (port.mode() == SideMode.ENERGY) {
                BlockPos target = port.pos().relative(port.face()).immutable();
                energyTargets.add(BlockCapabilityCache.create(Capabilities.Energy.BLOCK, level, target, port.face().getOpposite()));
            }
        }
    }

    private void pushEnergy(ServerLevel level) {
        if (targetsDirty) {
            refreshTargets(level);
        }
        int budget = Math.min(energy.getAmountAsInt(), ArcforgeConfig.DIESEL_GENERATOR_MAX_OUTPUT_RATE.getAsInt() * Math.max(1, energyTargets.size()));
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

    @Override
    public void onPortsChanged() {
        targetsDirty = true;
    }

    @Override
    protected void onStructureChanged() {
        targetsDirty = true;
        resize();
    }

    // Client side: the flywheel's angle (degrees) this frame, turning as fast as it runs.
    public float advance(float now) {
        if (lastFrame >= 0.0F && syncedSpeed > 0.0F) {
            angle = (angle + Math.max(0.0F, Math.min(20.0F, now - lastFrame)) * FLYWHEEL_SPEED * syncedSpeed) % 360.0F;
        }
        lastFrame = now;
        return angle;
    }

    // Client side: the outlet's rain flap this frame (degrees up from shut): lying shut while it stands; running, each
    // exhaust pulse throws it up and it drops back on the pipe, faster and higher with its speed (about 3 flaps a second
    // slow, 6 flat out, up to 25 to 65 degrees). It eases into and out of flapping as the engine starts and stops.
    public float flap(float now) {
        float speed = getShownSpeed();
        float step = lastFlapFrame >= 0.0F ? Math.max(0.0F, Math.min(20.0F, now - lastFlapFrame)) : 20.0F;
        lastFlapFrame = now;
        float target = speed > 0.0F ? 1.0F : 0.0F;
        flapRun += (target - flapRun) * Math.min(1.0F, step * 0.1F);
        float pulse = Math.abs((float) Math.sin(now * (0.5F + 0.5F * speed)));
        return flapRun * (25.0F + 40.0F * speed) * pulse;
    }

    // The client sets the hook (to DieselGeneratorSound.clientTick: its running sound and exhaust smoke), so this class
    // never loads client code on a server.
    public static Consumer<DieselGeneratorBlockEntity> clientHook = generator -> {};

    public void clientTick() {
        if (getBox() != null) {
            clientHook.accept(this);
        }
    }

    // The middle of the exhaust outlet's mouth, in the world; null while it isn't formed.
    public @Nullable Vec3 exhaustOutlet() {
        var box = getBox();
        if (box == null) {
            return null;
        }
        ModelFrame frame = frame(box);
        float[] p = frame.worldPoint(DieselGeneratorLayout.CX, DieselGeneratorLayout.OUTLET_TOP, DieselGeneratorLayout.of(getEngine()).outletU());
        return Vec3.atLowerCornerOf(box.min()).add(p[0] / 16.0, p[1] / 16.0, p[2] / 16.0);
    }

    // Client side: how fast it runs (0 to 1).
    public float getShownSpeed() {
        return Math.max(0.0F, syncedSpeed);
    }

    public GeneratorEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getFuel() {
        return fuel;
    }

    public float getSpeed() {
        return speed;
    }

    // FE it made last tick.
    public int getMade() {
        return made;
    }

    // mB of fuel it burned last tick.
    public double getBurned() {
        return burned;
    }

    @Override
    public OutputLimit getOutputLimit() {
        return outputLimit;
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        return mode == null ? fuel : mode == SideMode.INPUT ? fuelInput : null;
    }

    @Override
    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return energy;
        }
        return faceMode(pos, side) == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case FLUID, GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            default -> ConnectionMode.NONE;
        };
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String id = input.getStringOr("engine", DieselEngine.V8.id());
        for (DieselEngine engine : DieselEngine.values()) {
            if (engine.id().equals(id)) {
                sized = engine;
            }
        }
        DieselEngine engine = getEngine();
        sized = engine;
        energy = newEnergy(engine);
        fuel.setCapacity(tankCapacity(engine));
        energy.deserialize(input.childOrEmpty("energy"));
        fuel.deserialize(input.childOrEmpty("fuel"));
        speed = input.getFloatOr("speed", 0.0F);
        pending = input.getDoubleOr("pending", 0.0);
        syncedSpeed = input.getFloatOr("shown_speed", 0.0F);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        fuel.serialize(output.child("fuel"));
        output.putString("engine", sized.id());
        output.putFloat("speed", speed);
        output.putDouble("pending", pending);
        output.putFloat("shown_speed", Math.max(0.0F, syncedSpeed));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.diesel_generator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new DieselGeneratorMenu(containerId, inventory, worldPosition, items, data);
    }
}
