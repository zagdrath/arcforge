/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.tag.ModBlockTags;

// What Arcforge's machines may break: the Block Breaker takes anything breakable, and the Arc Quarry adds its own
// limits on top.
public final class MineRules {
    private MineRules() {}

    // Not air or a fluid, not unbreakable, not #arcforge:breaker_blacklist and not part of a multiblock.
    public static boolean canBreak(Level level, BlockPos pos, BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlock) && state.getDestroySpeed(level, pos) >= 0
                && !state.is(ModBlockTags.BREAKER_BLACKLIST) && !(state.getBlock() instanceof MultiblockPart);
    }

    // The quarry also leaves fluids (and waterlogged blocks) and anything with a block entity alone.
    public static boolean canQuarry(Level level, BlockPos pos, BlockState state) {
        return canBreak(level, pos, state) && state.getFluidState().isEmpty() && level.getBlockEntity(pos) == null;
    }
}
