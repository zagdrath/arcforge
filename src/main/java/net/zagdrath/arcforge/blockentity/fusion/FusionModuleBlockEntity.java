/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.fusion;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.fusion.FusionModuleBlock;
import net.zagdrath.arcforge.block.nuclear.NuclearEntityBlock;
import net.zagdrath.arcforge.blockentity.nuclear.NuclearBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.energy.GeneratorEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// A Fusion Reactor's port module (see FusionModuleBlock.Kind), set against a Vessel Port in its cryostat wall and facing
// it. It keeps what it runs on, filled from any side but its front, and the reactor draws on it each second while it
// runs it:
//  - Neutral Beam Injector: FE and Deuterium; heats the plasma (up to fusion.reactor.nbiPower MW), drives some of its
//    current and fuels it a little with the beam's deuterium;
//  - Gyrotron: FE; heats the electrons by microwaves (ECH, up to gyrotronPower MW), and breaks the gas down to start a
//    plasma;
//  - Pellet Injector: Liquid Helium and Deuterium, Tritium and Helium-3; fires frozen fuel pellets deep into the plasma,
//    the main fuelling (up to pelletRate mB a second);
//  - Gas Puff Valve: Deuterium, Tritium or Helium-3 puffed in at the edge (up to gasPuffRate; half of it reaches the
//    plasma, the rest goes straight to the pumps);
//  - Cryopump: Liquid Helium and FE; pumps the vessel down to vacuum and takes the exhaust (unburnt fuel and helium ash,
//    as Fusion Exhaust) out of the divertor, which it gives out of its sides and back;
//  - Diagnostics Port: nothing; the GUI shows the plasma's temperature, density and position in full while one is fitted;
//  - Direct Energy Converter: takes part of the plasma's charged-particle outflow (up to directConverterPower MW) as it
//    streams out along the field to the divertor, decelerates it on its grids and turns it straight into FE, which it
//    gives out of every side but its front; what it takes no longer heats the wall.
public class FusionModuleBlockEntity extends NuclearBlockEntity implements NuclearEntityBlock.ServerTicking, FluidInteractable {
    public static final int FUEL_TANK = 16_000, HELIUM_TANK = 8_000, EXHAUST_TANK = 32_000;

    private final FusionModuleBlock.Kind kind;
    private final @Nullable ConsumerEnergyHandler energy;
    private final List<FilteredFluidTank> inputs = new ArrayList<>();
    private final @Nullable FilteredFluidTank exhaust;
    // A Direct Energy Converter's output buffer, and the FE it makes each tick until the reactor's next second.
    private final @Nullable GeneratorEnergyHandler generator;
    private int fePerTick;
    // When the reactor last gave it its second (game time): a converter no reactor runs stops making FE.
    private long lastConvert;
    private final ResourceHandler<FluidResource> fluids;
    // What the reactor did with it in its last second (for Jade and the model's lights).
    private boolean running;
    private double lastMegawatts;

    public FusionModuleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FUSION_MODULE.get(), pos, state);
        this.kind = state.getBlock() instanceof FusionModuleBlock block ? block.kind() : FusionModuleBlock.Kind.DIAGNOSTICS;
        int megawatts = switch (kind) {
            case NEUTRAL_BEAM_INJECTOR -> ArcforgeConfig.FUSION_NBI_POWER.getAsInt();
            case GYROTRON -> ArcforgeConfig.FUSION_GYROTRON_POWER.getAsInt();
            case CRYOPUMP -> 1;
            default -> 0;
        };
        long perTick = (long) megawatts * ArcforgeConfig.FUSION_HEATING_FE_PER_MW.getAsInt();
        int capacity = (int) Math.min(Integer.MAX_VALUE / 2, perTick * 40L);
        this.energy = megawatts > 0 ? new ConsumerEnergyHandler(capacity, capacity, this::setChanged) : null;
        switch (kind) {
            case NEUTRAL_BEAM_INJECTOR -> inputs.add(tank(FUEL_TANK, ModFluids.DEUTERIUM.get()));
            case PELLET_INJECTOR -> {
                inputs.add(tank(HELIUM_TANK, ModFluids.LIQUID_HELIUM.get()));
                inputs.add(tank(FUEL_TANK, ModFluids.DEUTERIUM.get()));
                inputs.add(tank(FUEL_TANK, ModFluids.TRITIUM.get()));
                inputs.add(tank(FUEL_TANK, ModFluids.HELIUM_3.get()));
            }
            case GAS_PUFF_VALVE -> {
                inputs.add(tank(FUEL_TANK, ModFluids.DEUTERIUM.get()));
                inputs.add(tank(FUEL_TANK, ModFluids.TRITIUM.get()));
                inputs.add(tank(FUEL_TANK, ModFluids.HELIUM_3.get()));
            }
            case CRYOPUMP -> inputs.add(tank(HELIUM_TANK, ModFluids.LIQUID_HELIUM.get()));
            default -> {}
        }
        if (kind == FusionModuleBlock.Kind.DIRECT_CONVERTER) {
            long out = (long) ArcforgeConfig.FUSION_DEC_POWER.getAsInt() * ArcforgeConfig.FUSION_DEC_FE_PER_MW.getAsInt();
            int rate = (int) Math.min(Integer.MAX_VALUE / 40, out);
            this.generator = new GeneratorEnergyHandler(rate * 40, rate, this::setChanged);
        } else {
            this.generator = null;
        }
        this.exhaust = kind == FusionModuleBlock.Kind.CRYOPUMP
                ? new FilteredFluidTank(EXHAUST_TANK, resource -> resource.is(ModFluids.FUSION_EXHAUST.get()), this::setChanged) : null;
        List<ResourceHandler<FluidResource>> handlers = new ArrayList<>();
        for (FilteredFluidTank tank : inputs) {
            handlers.add(new SidedResourceHandler<>(tank, true, false, Integer.MAX_VALUE));
        }
        if (exhaust != null) {
            handlers.add(new SidedResourceHandler<>(exhaust, false, true, Integer.MAX_VALUE));
        }
        @SuppressWarnings("unchecked")
        ResourceHandler<FluidResource>[] array = handlers.toArray(new ResourceHandler[0]);
        this.fluids = new CombinedResourceHandler<>(array);
    }

    private FilteredFluidTank tank(int capacity, Fluid fluid) {
        return new FilteredFluidTank(capacity, resource -> resource.is(fluid), this::setChanged);
    }

    public FusionModuleBlock.Kind kind() {
        return kind;
    }

    public Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(FusionModuleBlock.FACING) ? state.getValue(FusionModuleBlock.FACING) : Direction.NORTH;
    }

    // --- What the reactor draws ---

    // Runs the heater at up to megawatts for a second (as far as its FE goes); gives the MW it delivered.
    public double heat(double megawatts) {
        lastMegawatts = 0.0;
        if (energy == null || megawatts <= 0.0) {
            return 0.0;
        }
        int fePerTick = ArcforgeConfig.FUSION_HEATING_FE_PER_MW.getAsInt();
        double can = Math.min(megawatts, energy.getAmountAsInt() / (20.0 * fePerTick));
        // The beam needs its deuterium too.
        if (kind == FusionModuleBlock.Kind.NEUTRAL_BEAM_INJECTOR) {
            int need = (int) Math.ceil(beamFuel(can));
            int got = take(ModFluids.DEUTERIUM.get(), need);
            if (got < need) {
                can = need > 0 ? can * got / need : 0.0;
            }
        }
        if (can <= 0.0) {
            return 0.0;
        }
        energy.consume((int) Math.ceil(can * 20.0 * fePerTick));
        lastMegawatts = can;
        running = true;
        setChanged();
        return can;
    }

    // The beam's deuterium a second (mB) at a power: what PlasmaSim's beam deposits.
    public static double beamFuel(double megawatts) {
        return megawatts * 0.002;
    }

    // The most heating it could give this second (MW), by its rating and FE.
    public double available() {
        if (energy == null) {
            return 0.0;
        }
        int rated = kind == FusionModuleBlock.Kind.NEUTRAL_BEAM_INJECTOR ? ArcforgeConfig.FUSION_NBI_POWER.getAsInt()
                : kind == FusionModuleBlock.Kind.GYROTRON ? ArcforgeConfig.FUSION_GYROTRON_POWER.getAsInt() : 0;
        return Math.min(rated, energy.getAmountAsInt() / (20.0 * ArcforgeConfig.FUSION_HEATING_FE_PER_MW.getAsInt()));
    }

    // Takes up to amount mB of a fluid it holds; gives what it took.
    public int take(Fluid fluid, int amount) {
        for (FilteredFluidTank tank : inputs) {
            if (tank.getAmount() > 0 && tank.getResource(0).is(fluid)) {
                int got = Math.min(amount, tank.getAmount());
                int left = tank.getAmount() - got;
                tank.set(0, left > 0 ? tank.getResource(0) : FluidResource.EMPTY, left);
                running |= got > 0;
                return got;
            }
        }
        return 0;
    }

    public int amount(Fluid fluid) {
        for (FilteredFluidTank tank : inputs) {
            if (tank.getAmount() > 0 && tank.getResource(0).is(fluid)) {
                return tank.getAmount();
            }
        }
        return 0;
    }

    // A Cryopump this second: it runs on a little Liquid Helium and FE. Gives whether it's cold and pumping.
    public boolean pump() {
        if (kind != FusionModuleBlock.Kind.CRYOPUMP || energy == null) {
            return false;
        }
        int fe = ArcforgeConfig.FUSION_HEATING_FE_PER_MW.getAsInt() / 4;
        if (amount(ModFluids.LIQUID_HELIUM.get()) < 1 || energy.getAmountAsInt() < fe * 20) {
            running = false;
            return false;
        }
        take(ModFluids.LIQUID_HELIUM.get(), 1);
        energy.consume(fe * 20);
        running = true;
        return true;
    }

    // A Direct Energy Converter this second: it takes up to its rating of the megawatts of charged particles offered and
    // makes FE from them each tick until the next. Gives the MW it took.
    public double convert(double megawatts) {
        if (generator == null) {
            return 0.0;
        }
        double took = Math.max(0.0, Math.min(megawatts, ArcforgeConfig.FUSION_DEC_POWER.getAsInt()));
        int rate = (int) Math.min(Integer.MAX_VALUE / 40, Math.round(took * ArcforgeConfig.FUSION_DEC_FE_PER_MW.getAsInt()));
        boolean was = running;
        fePerTick = net.zagdrath.arcforge.machine.PowerGeneration.fe(rate);
        lastConvert = level != null ? level.getGameTime() : 0L;
        lastMegawatts = took;
        running = took > 0.0;
        if (was != running) {
            sync();
        }
        setChanged();
        return took;
    }

    public int fePerTick() {
        return fePerTick;
    }

    public @Nullable GeneratorEnergyHandler getGenerator() {
        return generator;
    }

    // Puts Fusion Exhaust into a Cryopump's tank; gives how much fitted.
    public int putExhaust(int amount) {
        if (exhaust == null || amount <= 0) {
            return 0;
        }
        int fits = Math.min(amount, exhaust.getSpace());
        if (fits > 0) {
            exhaust.set(0, FluidResource.of(ModFluids.FUSION_EXHAUST.get()), exhaust.getAmount() + fits);
        }
        return fits;
    }

    public void idle() {
        fePerTick = 0;
        if (running || lastMegawatts != 0.0) {
            running = false;
            lastMegawatts = 0.0;
            sync();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public double lastMegawatts() {
        return lastMegawatts;
    }

    // --- Ticking: a Cryopump gives its exhaust out of every side but its front ---

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (generator != null) {
            convertTick(level, pos);
            return;
        }
        if (exhaust == null || exhaust.getAmount() <= 0) {
            return;
        }
        Direction front = getFacing();
        int budget = 2_000;
        for (Direction side : Direction.values()) {
            if (side == front || budget <= 0 || exhaust.getAmount() <= 0) {
                continue;
            }
            ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, pos.relative(side), side.getOpposite());
            if (target != null) {
                budget -= ResourceHandlerUtil.move(exhaust, target, resource -> true, budget, null);
            }
        }
    }

    // A Direct Energy Converter's tick: what it makes this tick, then out of every side but its front.
    private void convertTick(ServerLevel level, BlockPos pos) {
        if (fePerTick > 0 && level.getGameTime() - lastConvert > 40) {
            idle();
        }
        if (fePerTick > 0) {
            generator.generate(fePerTick);
        }
        if (generator.getAmountAsInt() <= 0) {
            return;
        }
        Direction front = getFacing();
        int budget = generator.getAmountAsInt();
        for (Direction side : Direction.values()) {
            if (side == front || budget <= 0) {
                continue;
            }
            EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, pos.relative(side), side.getOpposite());
            if (target != null) {
                budget -= EnergyHandlerUtil.move(generator, target, budget, null);
            }
        }
    }

    // --- Capabilities: not through its front, which is against the vessel ---

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        return side == getFacing() || inputs.isEmpty() && exhaust == null ? null : fluids;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        return side == getFacing() ? null : generator != null ? generator : energy;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return inputs.isEmpty() && exhaust == null ? null : fluids;
    }

    public @Nullable ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public List<FilteredFluidTank> getInputs() {
        return inputs;
    }

    public @Nullable FilteredFluidTank getExhaust() {
        return exhaust;
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        if (energy != null) {
            energy.deserialize(in.childOrEmpty("energy"));
        }
        for (int i = 0; i < inputs.size(); i++) {
            inputs.get(i).deserialize(in.childOrEmpty("tank_" + i));
        }
        if (exhaust != null) {
            exhaust.deserialize(in.childOrEmpty("exhaust"));
        }
        if (generator != null) {
            generator.deserialize(in.childOrEmpty("generator"));
        }
        fePerTick = in.getIntOr("fe_per_tick", 0);
        running = in.getBooleanOr("running", false);
        lastMegawatts = in.getDoubleOr("megawatts", 0.0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (energy != null) {
            energy.serialize(out.child("energy"));
        }
        for (int i = 0; i < inputs.size(); i++) {
            inputs.get(i).serialize(out.child("tank_" + i));
        }
        if (exhaust != null) {
            exhaust.serialize(out.child("exhaust"));
        }
        if (generator != null) {
            generator.serialize(out.child("generator"));
        }
        out.putInt("fe_per_tick", fePerTick);
        out.putBoolean("running", running);
        out.putDouble("megawatts", lastMegawatts);
    }
}
