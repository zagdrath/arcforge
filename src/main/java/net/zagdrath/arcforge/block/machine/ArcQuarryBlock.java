/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.machine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.sound.MachineSounds;

// The Arc Quarry's main block: the centre of its 3x3x3, which draws the whole machine (the model spans -16..32). The
// other 26 positions are ArcQuarryBoundingBlocks that forward everything here. See ArcQuarryItem for placing it.
public class ArcQuarryBlock extends MachineBlock {
    public ArcQuarryBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    // The 27 positions of a quarry centred on `main`.
    public static List<BlockPos> positions(BlockPos main) {
        List<BlockPos> positions = new ArrayList<>(27);
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    positions.add(main.offset(dx, dy, dz));
                }
            }
        }
        return positions;
    }

    // Whether pos is one of the quarry's own positions.
    public static boolean isPart(BlockPos main, BlockPos pos) {
        return Math.abs(pos.getX() - main.getX()) <= 1 && Math.abs(pos.getY() - main.getY()) <= 1 && Math.abs(pos.getZ() - main.getZ()) <= 1;
    }

    // Clears the 26 bounding blocks around a main block that is being removed (nothing drops from them).
    public static void removeBoundingBlocks(Level level, BlockPos main) {
        for (BlockPos pos : positions(main)) {
            if (!pos.equals(main) && level.getBlockState(pos).getBlock() instanceof ArcQuarryBoundingBlock) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcQuarryBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.ARC_QUARRY.get(),
                        (innerLevel, pos, blockState, quarry) -> quarry.serverTick(serverLevel, pos, blockState))
                : createTickerHelper(type, ModBlockEntityTypes.ARC_QUARRY.get(), MachineSounds.clientTicker());
    }
}
