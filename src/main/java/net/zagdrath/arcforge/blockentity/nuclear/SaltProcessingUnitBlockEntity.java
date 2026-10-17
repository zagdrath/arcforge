/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.nuclear.RadiationSource;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;

// A Salt Processing Unit, against a molten salt reactor's vessel: it takes a stream of the circulating Fuel Salt and
// strips its fission products out online (so poisons don't build up and the salt never needs replacing), for
// nuclear.reprocessing.saltProcessingEnergy FE a tick, nuclear.reprocessing.saltProcessingRate of the salt's fission
// products a tick, into High-Level Liquid Waste (WASTE_PER_UNIT mB per whole salt's worth) that's pumped out of its
// faces. The reactor runs it (see FissionReactorBlockEntity).
public class SaltProcessingUnitBlockEntity extends NuclearBlockEntity implements RadiationSource {
    public static final double WASTE_PER_UNIT = 200_000.0;

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank waste;
    private final ResourceHandler<FluidResource> output;
    private double owed;
    private boolean running;

    public SaltProcessingUnitBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SALT_PROCESSING_UNIT.get(), pos, state);
        int fe = ArcforgeConfig.SALT_PROCESSING_ENERGY.getAsInt();
        this.energy = new ConsumerEnergyHandler(fe * 400, fe * 4, this::setChanged);
        this.waste = new FilteredFluidTank(16_000, resource -> resource.is(ModFluids.HIGH_LEVEL_WASTE.get()), this::setChanged);
        this.output = new AutomationResourceHandler<>(waste, index -> false, index -> true);
    }

    public EnergyHandler getEnergyHandler() {
        return energy;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public ResourceHandler<FluidResource> getFluidHandler() {
        return output;
    }

    public FilteredFluidTank getWaste() {
        return waste;
    }

    public boolean isRunning() {
        return running;
    }

    // One tick against the salt's fission products (0..1); returns how much it took out.
    public double process(double fissionProducts) {
        running = false;
        if (fissionProducts <= 0.0 || waste.getSpace() <= 0 || !energy.consume(ArcforgeConfig.SALT_PROCESSING_ENERGY.getAsInt())) {
            return 0.0;
        }
        double taken = Math.min(fissionProducts, ArcforgeConfig.SALT_PROCESSING_RATE.getAsDouble());
        owed += taken * WASTE_PER_UNIT;
        int whole = (int) Math.floor(owed);
        if (whole > 0) {
            int moved = Math.min(whole, waste.getSpace());
            waste.set(0, FluidResource.of(ModFluids.HIGH_LEVEL_WASTE.get()), waste.getAmount() + moved);
            owed -= whole;
        }
        running = true;
        return taken;
    }

    @Override
    public double radiationRate() {
        return waste.getAmount() > 0 ? RadiationField.rate(waste.getResource(0).toStack(waste.getAmount())) * 0.05 : 0.0;
    }

    @Override
    public Vec3 radiationCentre() {
        return Vec3.atCenterOf(worldPosition);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy.deserialize(in.childOrEmpty("energy"));
        waste.deserialize(in.childOrEmpty("waste"));
        owed = in.getDoubleOr("owed", 0.0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        energy.serialize(out.child("energy"));
        waste.serialize(out.child("waste"));
        out.putDouble("owed", owed);
    }
}
