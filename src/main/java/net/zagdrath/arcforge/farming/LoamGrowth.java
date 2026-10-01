/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.farming;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.zagdrath.arcforge.block.farming.ArcforgeCropBlock;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// What nutrients do for the crop on Loam Farmland. Whenever the crop grows a stage by itself (a random tick, not bone
// meal) while its soil has nutrients, the stage uses nutrientsPerStage, and with a (growthMultiplier - 1) chance it
// grows one more stage straight away, which uses as much again. So on average it grows growthMultiplier times as fast
// (1.5 by default) for as long as the nutrients last. On soil enriched with NPK Fertilizer the multiplier is
// npkGrowthMultiplier (2 by default: every stage brings one more; past 2, a chance of a third), and the enrichment goes
// when the nutrients run out. Anything with an "age" property counts: wheat, carrots, potatoes,
// beetroots, melon and pumpkin stems, and most modded crops. Legumes (Soybeans) put legumeNutrientsPerStage back instead
// of using any, and grow at the nutrient rate whatever the soil holds; a non-legume after a legume also gets the crop
// rotation bonus (CropRotation).
public final class LoamGrowth {
    private LoamGrowth() {}

    // Called after the plant at cropPos grew by itself. Returns how many stages Loam Farmland counted (0 when its soil
    // isn't Loam Farmland, or has no nutrients and no bonus to give).
    public static int onCropGrew(ServerLevel level, BlockPos cropPos, RandomSource random) {
        BlockPos soilPos = cropPos.below();
        BlockState soil = level.getBlockState(soilPos);
        if (!(soil.getBlock() instanceof LoamFarmlandBlock)) {
            return 0;
        }
        boolean legume = CropRotation.isLegume(level.getBlockState(cropPos));
        int nutrients = LoamFarmlandBlock.nutrients(soil);
        int stages = 0;
        boolean enriched = LoamFarmlandBlock.isEnriched(soil);
        if (legume) {
            // A legume puts nutrients back with every stage instead of using them, so it always has the bonus.
            int perStage = ArcforgeConfig.LOAM_LEGUME_NUTRIENTS_PER_STAGE.getAsInt();
            nutrients = Math.min(LoamFarmlandBlock.MAX_NUTRIENTS, nutrients + perStage);
            stages = 1;
            double bonus = (enriched ? ArcforgeConfig.LOAM_NPK_GROWTH_MULTIPLIER : ArcforgeConfig.LOAM_GROWTH_MULTIPLIER).getAsDouble() - 1.0;
            while (bonus > 0 && (bonus >= 1.0 || random.nextDouble() < bonus) && growOnce(level, cropPos)) {
                bonus -= 1.0;
                nutrients = Math.min(LoamFarmlandBlock.MAX_NUTRIENTS, nutrients + perStage);
                stages++;
            }
        } else if (nutrients > 0) {
            int perStage = ArcforgeConfig.LOAM_NUTRIENTS_PER_STAGE.getAsInt();
            nutrients = Math.max(0, nutrients - perStage);
            stages = 1;
            double bonus = (enriched ? ArcforgeConfig.LOAM_NPK_GROWTH_MULTIPLIER : ArcforgeConfig.LOAM_GROWTH_MULTIPLIER).getAsDouble() - 1.0;
            // Each whole unit of bonus is a sure extra stage; what's left over is the chance of one more.
            while (nutrients > 0 && bonus > 0 && (bonus >= 1.0 || random.nextDouble() < bonus) && growOnce(level, cropPos)) {
                bonus -= 1.0;
                nutrients = Math.max(0, nutrients - perStage);
                stages++;
            }
        }
        // Crop rotation: a non-legume after a legume has a (rotationMultiplier - 1) chance of a stage more, nutrients or
        // not, which uses none.
        if (!legume && LoamFarmlandBlock.isAfterLegume(soil)) {
            double rotation = ArcforgeConfig.LOAM_ROTATION_MULTIPLIER.getAsDouble() - 1.0;
            while (rotation > 0 && (rotation >= 1.0 || random.nextDouble() < rotation) && growOnce(level, cropPos)) {
                rotation -= 1.0;
                stages++;
            }
        }
        if (nutrients != soil.getValue(LoamFarmlandBlock.NUTRIENTS) || enriched && nutrients == 0) {
            level.setBlock(soilPos, soil.setValue(LoamFarmlandBlock.NUTRIENTS, nutrients).setValue(LoamFarmlandBlock.ENRICHED, enriched && nutrients > 0),
                    Block.UPDATE_CLIENTS);
        }
        return stages;
    }

    // Grows the plant at cropPos one more stage, if it can (not ripe, and a tall crop has room). Returns whether it did.
    private static boolean growOnce(ServerLevel level, BlockPos cropPos) {
        BlockState crop = level.getBlockState(cropPos);
        boolean room = !(crop.getBlock() instanceof ArcforgeCropBlock tall) || tall.hasRoomToGrow(level, cropPos, crop);
        BlockState grown = room ? nextStage(crop) : null;
        if (grown == null) {
            return false;
        }
        level.setBlock(cropPos, grown, Block.UPDATE_CLIENTS);
        return true;
    }

    // The plant one "age" stage on, or null if it has no age or is fully grown.
    public static @Nullable BlockState nextStage(BlockState plant) {
        for (Property<?> property : plant.getProperties()) {
            if (property instanceof IntegerProperty age && "age".equals(property.getName())) {
                int value = plant.getValue(age);
                int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(value);
                return value < max ? plant.setValue(age, value + 1) : null;
            }
        }
        return null;
    }
}
