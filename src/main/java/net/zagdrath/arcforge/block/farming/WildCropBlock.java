/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// Wild Flax, Rapeseed, Sorghum and Hops: a clump of the plant growing on grass and dirt (#minecraft:supports_vegetation),
// placed by worldgen in the biomes that suit it (data/arcforge/neoforge/biome_modifier/wild_*.json). Breaking one gives
// its seeds (and now and then the crop), from its loot table.
public class WildCropBlock extends VegetationBlock {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 13, 14);

    public WildCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.move(state.getOffset(pos));
    }
}
