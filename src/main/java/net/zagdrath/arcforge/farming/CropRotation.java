/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.farming;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModTriggers;
import net.zagdrath.arcforge.tag.ModBlockTags;
import net.zagdrath.arcforge.tag.ModItemTags;

// Legumes (#arcforge:legumes: Soybeans) and crop rotation.
//  - A legume growing on Loam Farmland puts nutrients back instead of using them (LoamGrowth).
//  - Loam Farmland remembers whether the last ripe crop harvested from it was a legume (AFTER_LEGUME). A non-legume
//    then grows rotationMultiplier times as fast until it's harvested, which clears the mark (a legume after a legume
//    keeps it). Harvests count however they happen: by hand, a Sickle or Scythe, or a Harvester (CropHarvest and
//    FarmingEvents call onHarvested).
//  - In the Glass Cloche, Grow Chamber, Hydroponic Cell and Greenhouse Array, legumes use no fertilizer or Nutrient
//    Solution but grow as fast as if they had it.
public final class CropRotation {
    // The category the crop_rotation advancement listens for (arcforge:machine_produced).
    public static final String ADVANCEMENT_CATEGORY = "crop_rotation";

    private CropRotation() {}

    public static boolean isLegume(BlockState crop) {
        return crop.is(ModBlockTags.LEGUMES);
    }

    public static boolean isLegumeSeed(ItemStack seed) {
        return !seed.isEmpty() && seed.is(ModItemTags.LEGUMES);
    }

    // The rotation bonus for the crop at cropPos: rotationMultiplier for a non-legume on farmland a legume was last
    // harvested from, otherwise 1.
    public static double multiplier(BlockState crop, BlockState soil) {
        return !isLegume(crop) && LoamFarmlandBlock.isAfterLegume(soil) ? ArcforgeConfig.LOAM_ROTATION_MULTIPLIER.getAsDouble() : 1.0;
    }

    // A ripe crop (crop, as it was) was just harvested from cropPos, by player if one did it: the farmland under it
    // remembers whether it was a legume. A non-legume harvested with the rotation bonus counts for the crop_rotation
    // advancement (for the player, or else everyone near it).
    public static void onHarvested(ServerLevel level, BlockPos cropPos, BlockState crop, @Nullable ServerPlayer player) {
        if (!CropHarvest.isMature(crop)) {
            return;
        }
        BlockPos soilPos = cropPos.below();
        BlockState soil = level.getBlockState(soilPos);
        if (!(soil.getBlock() instanceof LoamFarmlandBlock)) {
            return;
        }
        boolean legume = isLegume(crop);
        boolean rotated = !legume && soil.getValue(LoamFarmlandBlock.AFTER_LEGUME);
        if (soil.getValue(LoamFarmlandBlock.AFTER_LEGUME) != legume) {
            level.setBlock(soilPos, soil.setValue(LoamFarmlandBlock.AFTER_LEGUME, legume), Block.UPDATE_CLIENTS);
        }
        if (rotated) {
            ItemStack harvested = new ItemStack(crop.getBlock().asItem());
            List<ServerPlayer> players = player != null && !(player instanceof FakePlayer) ? List.of(player)
                    : ArcforgeAdvancements.nearby(level, cropPos);
            for (ServerPlayer each : players) {
                ModTriggers.MACHINE_PRODUCED.get().trigger(each, harvested, null, ADVANCEMENT_CATEGORY);
            }
        }
    }
}
