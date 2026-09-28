/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

// Climate checks shared by machines that care about the weather where they stand (the Solar Thermal Array,
// the Condenser Array). Each passes its own list of biome tags from its config.
public final class ClimateHelper {
    private ClimateHelper() {}

    // A biome with one of these tags, or anywhere cold enough to snow.
    public static boolean isCold(Level level, BlockPos pos, List<? extends String> tags) {
        Holder<Biome> biome = level.getBiome(pos);
        return inAny(biome, tags) || biome.value().coldEnoughToSnow(pos, level.getSeaLevel());
    }

    // Where water boils away (the Nether).
    public static boolean waterEvaporates(Level level, BlockPos pos) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.WATER_EVAPORATES, pos);
    }

    // Whether the biome has one of these tags (written as "namespace:path"; bad entries are skipped).
    public static boolean inAny(Holder<Biome> biome, List<? extends String> tags) {
        for (String tag : tags) {
            Identifier id = Identifier.tryParse(tag);
            if (id != null && biome.is(TagKey.create(Registries.BIOME, id))) {
                return true;
            }
        }
        return false;
    }
}
