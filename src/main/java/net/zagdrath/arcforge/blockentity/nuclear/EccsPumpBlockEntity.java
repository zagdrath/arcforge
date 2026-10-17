/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.nuclear;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.safety.Containment;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;

// An ECCS Pump: Emergency Core Cooling's high-pressure injection, beside a reactor vessel. While the vessel's coolant
// is below nuclear.safety.eccsLevel the reactor runs it (see FissionReactorBlockEntity): it draws Water from a tank or
// pipe touching it (not the vessel), or failing that from its containment's Suppression Pool (recirculating what the
// Relief Valves vented), and forces it in, nuclear.safety.eccsPumpRate mB a tick for eccsPumpEnergy FE a tick.
public class EccsPumpBlockEntity extends NuclearBlockEntity {
    private final ConsumerEnergyHandler energy;
    private boolean running;

    public EccsPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ECCS_PUMP.get(), pos, state);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.ECCS_PUMP_ENERGY.getAsInt() * 200, ArcforgeConfig.ECCS_PUMP_ENERGY.getAsInt() * 4,
                this::setChanged);
    }

    public EnergyHandler getEnergyHandler() {
        return energy;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public boolean isRunning() {
        return running;
    }

    // Pumps up to max mB this tick (vessel is a block of the vessel, not drawn from); returns how much.
    public int pump(ServerLevel level, int max, @Nullable BlockPos vessel, @Nullable Containment containment) {
        int want = Math.min(max, ArcforgeConfig.ECCS_PUMP_RATE.getAsInt());
        running = false;
        if (want <= 0 || !energy.consume(ArcforgeConfig.ECCS_PUMP_ENERGY.getAsInt())) {
            return 0;
        }
        int got = 0;
        for (Direction direction : Direction.values()) {
            BlockPos at = worldPosition.relative(direction);
            if (got >= want || vessel != null && at.equals(vessel)) {
                continue;
            }
            ResourceHandler<FluidResource> source = level.getCapability(Capabilities.Fluid.BLOCK, at, direction.getOpposite());
            if (source == null) {
                continue;
            }
            try (Transaction tx = Transaction.openRoot()) {
                got += source.extract(FluidResource.of(Fluids.WATER), want - got, tx);
                tx.commit();
            }
        }
        if (got < want && containment != null && containment.sealed() && containment.poolBlocks() > 0) {
            got = want;
        }
        running = got > 0;
        return got;
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy.deserialize(in.childOrEmpty("energy"));
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        energy.serialize(out.child("energy"));
    }
}
