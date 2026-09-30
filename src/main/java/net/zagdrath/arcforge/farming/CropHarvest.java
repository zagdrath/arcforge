/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.farming;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.farming.TrellisBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModItems;

// Harvesting a ripe crop and planting it again, shared by the Harvester and the Sickle and Scythe.
// A crop (anything extending CropBlock: wheat, carrots, potatoes, beetroots, Flax, Rapeseed, Sorghum and most modded
// crops) is ripe at its max age. Its drops come from its loot table as if broken by hand; one seed is taken back out of
// them and the crop is reset to age 0, so it replants itself. With no seed in the drops it's left as air.
// A Trellis is ripe while bearing: its cones are picked and the vine is left full (TrellisBlock.pickCones).
public final class CropHarvest {
    private CropHarvest() {}

    // Where the harvest goes.
    public interface Sink {
        // Whether all of these would fit (nothing is harvested if not).
        boolean fits(List<ItemStack> stacks);

        void put(ItemStack stack);
    }

    public static boolean isMature(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        return state.getBlock() instanceof TrellisBlock && state.getValue(TrellisBlock.AGE) == TrellisBlock.BEARING;
    }

    // Whether this is something CropHarvest knows how to harvest, ripe or not.
    public static boolean isCrop(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.getBlock() instanceof TrellisBlock;
    }

    // Harvests the crop at pos if it's ripe and its harvest fits the sink. Returns whether it did.
    public static boolean harvest(ServerLevel level, BlockPos pos, Sink sink) {
        BlockState state = level.getBlockState(pos);
        if (!isMature(state)) {
            return false;
        }
        if (state.getBlock() instanceof TrellisBlock) {
            if (!sink.fits(List.of(new ItemStack(ModItems.HOP_CONES.get(), Math.max(1, ArcforgeConfig.HOP_CONES_MAX.getAsInt()))))) {
                return false;
            }
            ItemStack cones = TrellisBlock.pickCones(level, pos, state);
            if (!cones.isEmpty()) {
                sink.put(cones);
            }
            return true;
        }
        CropBlock crop = (CropBlock) state.getBlock();
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null));
        boolean replant = takeSeed(drops, state.getBlock().asItem());
        drops.removeIf(ItemStack::isEmpty);
        if (!sink.fits(drops)) {
            return false;
        }
        level.setBlock(pos, replant ? crop.getStateForAge(0) : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        drops.forEach(sink::put);
        return true;
    }

    // Takes one of the seed out of the drops. Returns whether there was one.
    private static boolean takeSeed(List<ItemStack> drops, Item seed) {
        for (ItemStack drop : drops) {
            if (drop.is(seed)) {
                drop.shrink(1);
                return true;
            }
        }
        return false;
    }

    // The square of crop positions a machine facing 'facing' works: (2r+1) blocks across, starting one block in front.
    public static List<BlockPos> areaInFront(BlockPos machine, Direction facing, int radius) {
        BlockPos centre = machine.relative(facing, radius + 1);
        return square(centre, radius);
    }

    public static List<BlockPos> square(BlockPos centre, int radius) {
        List<BlockPos> area = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                area.add(centre.offset(dx, 0, dz));
            }
        }
        return area;
    }
}
