/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// Flax, Rapeseed, Sorghum and Soybeans: vanilla crops (ages 0-7, planted from their seeds) that grow on anything in
// #minecraft:supports_crops, so vanilla farmland and both Loam Farmlands, and use Loam Farmland's nutrients like any
// crop (LoamGrowth). What a crop drops is in its loot table.
// Flax, Rapeseed and Sorghum grow two blocks tall (Soybeans never do: their tallFromAge is past the last age): from tallFromAge their model reaches into the block above, so they
// only grow into that stage (by random tick or bone meal) while the space above is empty, and their outline follows.
public class ArcforgeCropBlock extends CropBlock {
    private final Supplier<? extends ItemLike> seed;
    private final int tallFromAge;

    public ArcforgeCropBlock(Supplier<? extends ItemLike> seed, int tallFromAge, BlockBehaviour.Properties properties) {
        super(properties);
        this.seed = seed;
        this.tallFromAge = tallFromAge;
    }

    // The crop's seed item (what pick-block gives).
    @Override
    protected ItemLike getBaseSeedId() {
        return seed.get();
    }

    // The tall stages' outline rises through the block above as they grow, to 30 px when grown (the art's tallest).
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int age = getAge(state);
        if (age < tallFromAge) {
            return super.getShape(state, level, pos, context);
        }
        int tallStages = getMaxAge() - tallFromAge + 1;
        return Block.box(0, 0, 0, 16, 16 + 14.0 * (age - tallFromAge + 1) / tallStages, 16);
    }

    // Whether the next stage fits: a stage that reaches the block above needs it empty.
    public boolean hasRoomToGrow(LevelReader level, BlockPos pos, BlockState state) {
        return getAge(state) + 1 < tallFromAge || level.getBlockState(pos.above()).isAir();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (hasRoomToGrow(level, pos, state)) {
            super.randomTick(state, level, pos, random);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return super.isValidBonemealTarget(level, pos, state, source) && hasRoomToGrow(level, pos, state);
    }

    // Bone meal can jump several stages; it stops below the tall stages when the space above is taken.
    @Override
    public void growCrops(Level level, BlockPos pos, BlockState state) {
        super.growCrops(level, pos, state);
        BlockState grown = level.getBlockState(pos);
        if (grown.getBlock() == this && getAge(grown) >= tallFromAge && !level.getBlockState(pos.above()).isAir()) {
            level.setBlock(pos, getStateForAge(Math.min(getAge(grown), tallFromAge - 1)), 2);
        }
    }
}
