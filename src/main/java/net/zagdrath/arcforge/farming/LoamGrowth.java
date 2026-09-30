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
// beetroots, melon and pumpkin stems, and most modded crops.
public final class LoamGrowth {
    private LoamGrowth() {}

    // Called after the plant at cropPos grew by itself. Returns how many stages were paid for (0 when its soil isn't
    // Loam Farmland with nutrients).
    public static int onCropGrew(ServerLevel level, BlockPos cropPos, RandomSource random) {
        BlockPos soilPos = cropPos.below();
        BlockState soil = level.getBlockState(soilPos);
        int nutrients = LoamFarmlandBlock.nutrients(soil);
        if (nutrients <= 0) {
            return 0;
        }
        int perStage = ArcforgeConfig.LOAM_NUTRIENTS_PER_STAGE.getAsInt();
        nutrients = Math.max(0, nutrients - perStage);
        int stages = 1;
        boolean enriched = LoamFarmlandBlock.isEnriched(soil);
        double bonus = (enriched ? ArcforgeConfig.LOAM_NPK_GROWTH_MULTIPLIER : ArcforgeConfig.LOAM_GROWTH_MULTIPLIER).getAsDouble() - 1.0;
        // Each whole unit of bonus is a sure extra stage; what's left over is the chance of one more.
        while (nutrients > 0 && bonus > 0 && (bonus >= 1.0 || random.nextDouble() < bonus)) {
            bonus -= 1.0;
            BlockState crop = level.getBlockState(cropPos);
            boolean room = !(crop.getBlock() instanceof ArcforgeCropBlock tall) || tall.hasRoomToGrow(level, cropPos, crop);
            BlockState grown = room ? nextStage(crop) : null;
            if (grown == null) {
                break;
            }
            level.setBlock(cropPos, grown, Block.UPDATE_CLIENTS);
            nutrients = Math.max(0, nutrients - perStage);
            stages++;
        }
        level.setBlock(soilPos, soil.setValue(LoamFarmlandBlock.NUTRIENTS, nutrients).setValue(LoamFarmlandBlock.ENRICHED, enriched && nutrients > 0),
                Block.UPDATE_CLIENTS);
        return stages;
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
