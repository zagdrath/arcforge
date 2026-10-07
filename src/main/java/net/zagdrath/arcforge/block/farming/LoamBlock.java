/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming;

import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModBlocks;

// Loam: dirt worked with compost. A hoe tills it into Loam Farmland (getToolModifiedState). A sapling on it grows
// saplingGrowthMultiplier times as fast: each random tick Loam gets, the sapling above gets (multiplier - 1) more of its
// own (whole units for sure, the rest as a chance), and Loam and the sapling are ticked equally often. A tree grown on it
// leaves the Loam under its trunk, where grass would turn to dirt, so a sapling replanted there grows fast again.
public class LoamBlock extends Block {
    public LoamBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        double extra = ArcforgeConfig.LOAM_SAPLING_GROWTH_MULTIPLIER.getAsDouble() - 1.0;
        while (extra > 0 && (extra >= 1.0 || random.nextDouble() < extra)) {
            BlockState sapling = level.getBlockState(above);
            if (!sapling.is(BlockTags.SAPLINGS) || !sapling.isRandomlyTicking()) {
                return;
            }
            sapling.randomTick(level, above, random);
            extra -= 1.0;
        }
    }

    // A hoe tills it into Loam Farmland with air above, as it tills dirt into farmland.
    @Override
    public @Nullable BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility itemAbility, boolean simulate) {
        if (itemAbility == ItemAbilities.HOE_TILL && HoeItem.onlyIfAirAbove(context)) {
            return ModBlocks.LOAM_FARMLAND.get().defaultBlockState();
        }
        return super.getToolModifiedState(state, context, itemAbility, simulate);
    }

    @Override
    public boolean onTreeGrow(BlockState state, WorldGenLevel level, BiConsumer<BlockPos, BlockState> placeFunction, RandomSource random,
            BlockPos pos, TreeConfiguration config) {
        return true;
    }
}
