/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.fusion;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.fusion.RemoteHandlingArmBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Remote Handling Arm (see RemoteHandlingArmBlockEntity): a robot arm on a wall mount, set against a Vessel Port
// and facing it (placed against a block it faces that block). Its renderer draws the arm, folded back while it idles.
public class RemoteHandlingArmBlock extends MachineBlock {
    // The wall mount: the half against the cryostat it faces (the arm folds back from it).
    private static final VoxelShape[] SHAPES = {
        Block.box(2, 0, 8, 14, 16, 16), Block.box(0, 0, 2, 8, 16, 14), Block.box(2, 0, 0, 14, 16, 8), Block.box(8, 0, 2, 16, 16, 14) };

    public RemoteHandlingArmBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked.getOpposite() : context.getHorizontalDirection();
        return defaultBlockState().setValue(FACING, facing);
    }

    // The mount stands against the cryostat wall, round the port it faces.
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RemoteHandlingArmBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level instanceof ServerLevel serverLevel) {
            return createTickerHelper(type, ModBlockEntityTypes.REMOTE_HANDLING_ARM.get(),
                    (innerLevel, pos, blockState, arm) -> arm.serverTick(serverLevel, pos, blockState));
        }
        return createTickerHelper(type, ModBlockEntityTypes.REMOTE_HANDLING_ARM.get(), (innerLevel, pos, blockState, arm) -> arm.clientTick());
    }
}
