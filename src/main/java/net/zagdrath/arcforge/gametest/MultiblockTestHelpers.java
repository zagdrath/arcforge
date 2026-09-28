/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

// Building multiblocks in tests.
final class MultiblockTestHelpers {
    private MultiblockTestHelpers() {}

    // A solid 3x3x3 cube of casings with its minimum corner at min (it forms a tick or two later).
    static void buildCube(GameTestHelper helper, BlockPos min, Block casing) {
        for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(2, 2, 2))) {
            helper.setBlock(pos.immutable(), casing);
        }
    }

    // The centre block entity of the cube with its minimum corner at min.
    static <T extends BlockEntity> T centre(GameTestHelper helper, BlockPos min, Class<T> type) {
        return helper.getBlockEntity(min.offset(1, 1, 1), type);
    }
}
