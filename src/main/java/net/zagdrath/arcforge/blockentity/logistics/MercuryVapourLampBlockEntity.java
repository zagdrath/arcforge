/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.logistics;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.zagdrath.arcforge.block.logistics.MercuryVapourLampBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;

// The Mercury Vapour Lamp: FE in from a conduit (or any FE source) on any side but the bulb's.
// Every tick it isn't turned off by redstone it draws energyPerTick; while it can, it's lit.
public class MercuryVapourLampBlockEntity extends BlockEntity implements ConduitConnectable {
    private final ConsumerEnergyHandler energy;

    public MercuryVapourLampBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.MERCURY_VAPOUR_LAMP.get(), pos, state);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.MERCURY_LAMP_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.MERCURY_LAMP_MAX_INPUT.getAsInt(),
                this::setChanged);
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean lit = !state.getValue(MercuryVapourLampBlock.POWERED) && energy.consume(ArcforgeConfig.MERCURY_LAMP_ENERGY_PER_TICK.getAsInt());
        if (lit != state.getValue(MercuryVapourLampBlock.LIT)) {
            level.setBlock(pos, state.setValue(MercuryVapourLampBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private boolean isBulbSide(@Nullable Direction side) {
        return side != null && side == getBlockState().getValue(MercuryVapourLampBlock.FACING);
    }

    // --- Capabilities: FE in on every side but the bulb's ---

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        return isBulbSide(side) ? null : energy;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return type == ConduitType.ENERGY && !isBulbSide(side) ? ConnectionMode.INPUT : ConnectionMode.NONE;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
    }
}
