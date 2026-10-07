/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.machine.ElectricPumpBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A slim pump column: an intake foot, the body, a cap with the fluid-out flange on top, and the FE
// connector out of the back.
public class ElectricPumpBlock extends MachineBlock {
    private static final MapCodec<ElectricPumpBlock> CODEC = simpleCodec(ElectricPumpBlock::new);

    @Override
    protected MapCodec<ElectricPumpBlock> codec() {
        return CODEC;
    }

    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.box(2, 0, 2, 14, 2, 14),
            Block.box(3, 2, 3, 13, 13, 13),
            Block.box(2, 13, 2, 14, 16, 14),
            Block.box(5, 4, 13, 11, 10, 16)));

    public ElectricPumpBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ElectricPumpBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.ELECTRIC_PUMP.get(),
                        (innerLevel, pos, blockState, pump) -> pump.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // Drips at the intake while pumping.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIT) && random.nextInt(3) == 0) {
            double x = pos.getX() + 0.3 + random.nextDouble() * 0.4;
            double z = pos.getZ() + 0.3 + random.nextDouble() * 0.4;
            level.addParticle(ParticleTypes.DRIPPING_WATER, x, pos.getY() + 0.05, z, 0.0, 0.0, 0.0);
        }
    }
}
