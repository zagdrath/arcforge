/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.worldgen;

import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// Ore generation that follows the "ores" config (see ArcforgeConfig.ORES), for the ores' worldgen files:
//   feature arcforge:config_ore             an ore vein ("targets", "size", "discard_chance_on_air_exposure" as
//                                           minecraft:ore, plus "ore"), its size and air discard from the config
//   placement arcforge:config_count         "ore", "default": veins per chunk from the config
//   placement arcforge:config_height        "ore", "shape" (uniform or trapezoid), "default_min", "default_max":
//                                           a height between the config's minY and maxY
//   condition arcforge:ore_enabled          "ore": whether the config lets that ore generate
// Each falls back to its JSON values for an ore the config doesn't know (or before the config loads).
public final class ModWorldgen {
    private static final DeferredRegister<MapCodec<? extends Feature>> FEATURE_TYPES = DeferredRegister.create(Registries.FEATURE_TYPE, Arcforge.MODID);
    private static final DeferredRegister<MapCodec<? extends PlacementModifier>> PLACEMENT_TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Arcforge.MODID);
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Arcforge.MODID);

    static {
        FEATURE_TYPES.register("config_ore", () -> ConfigOre.CODEC);
        PLACEMENT_TYPES.register("config_count", () -> ConfigCount.CODEC);
        PLACEMENT_TYPES.register("config_height", () -> ConfigHeight.CODEC);
        CONDITIONS.register("ore_enabled", () -> OreEnabled.CODEC);
    }

    private ModWorldgen() {}

    public static void register(IEventBus modEventBus) {
        FEATURE_TYPES.register(modEventBus);
        PLACEMENT_TYPES.register(modEventBus);
        CONDITIONS.register(modEventBus);
    }

    // The config's settings for an ore, or null if it has none (or the config isn't loaded yet).
    private static ArcforgeConfig.@Nullable OreSettings settings(String ore) {
        ArcforgeConfig.OreSettings settings = ArcforgeConfig.ORES.get(ore);
        return settings != null && ArcforgeConfig.SPEC.isLoaded() ? settings : null;
    }

    // --- The ore vein ---

    public record ConfigOre(String ore, List<BlockReplacement> targets, int size, float discard) implements Feature {
        public static final MapCodec<ConfigOre> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("ore").forGetter(ConfigOre::ore),
                Codec.list(BlockReplacement.CODEC).fieldOf("targets").forGetter(ConfigOre::targets),
                Codec.intRange(0, 64).fieldOf("size").forGetter(ConfigOre::size),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("discard_chance_on_air_exposure", 0.0F).forGetter(ConfigOre::discard))
                .apply(i, ConfigOre::new));

        @Override
        public MapCodec<ConfigOre> codec() {
            return CODEC;
        }

        // A vanilla ore vein, sized from the config.
        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            ArcforgeConfig.OreSettings settings = settings(ore);
            int veinSize = settings != null ? settings.veinSize().getAsInt() : size;
            float airDiscard = settings != null ? (float) settings.airExposureDiscard().getAsDouble() : discard;
            return new OreFeature(targets, veinSize, airDiscard).place(level, generator, random, origin);
        }
    }

    // --- How many veins a chunk tries ---

    public record ConfigCount(String ore, int fallback) implements RepeatingPlacement {
        public static final MapCodec<ConfigCount> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("ore").forGetter(ConfigCount::ore),
                Codec.intRange(0, 4096).fieldOf("default").forGetter(ConfigCount::fallback))
                .apply(i, ConfigCount::new));

        @Override
        public int count(RandomSource random, BlockPos origin) {
            ArcforgeConfig.OreSettings settings = settings(ore);
            return settings != null ? settings.veinsPerChunk().getAsInt() : fallback;
        }

        @Override
        public MapCodec<ConfigCount> codec() {
            return CODEC;
        }
    }

    // --- At what height ---

    public record ConfigHeight(String ore, String shape, int defaultMin, int defaultMax) implements PlacementModifier {
        public static final MapCodec<ConfigHeight> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("ore").forGetter(ConfigHeight::ore),
                Codec.STRING.optionalFieldOf("shape", "uniform").forGetter(ConfigHeight::shape),
                Codec.INT.fieldOf("default_min").forGetter(ConfigHeight::defaultMin),
                Codec.INT.fieldOf("default_max").forGetter(ConfigHeight::defaultMax))
                .apply(i, ConfigHeight::new));

        @Override
        public void modify(PlacementContext context, RandomSource random, BlockPos origin, Consumer<BlockPos> output) {
            ArcforgeConfig.OreSettings settings = settings(ore);
            int min = settings != null ? settings.minY().getAsInt() : defaultMin;
            int max = settings != null ? settings.maxY().getAsInt() : defaultMax;
            output.accept(origin.atY(sample(random, Math.min(min, max), Math.max(min, max), shape.equals("trapezoid"))));
        }

        // Uniform, or a triangle peaking in the middle (as minecraft:trapezoid with no plateau).
        static int sample(RandomSource random, int min, int max, boolean trapezoid) {
            if (!trapezoid) {
                return Mth.randomBetweenInclusive(random, min, max);
            }
            int range = max - min;
            int half = range / 2;
            return min + Mth.randomBetweenInclusive(random, 0, range - half) + Mth.randomBetweenInclusive(random, 0, half);
        }

        @Override
        public MapCodec<ConfigHeight> codec() {
            return CODEC;
        }
    }

    // --- Whether it generates at all ---

    public record OreEnabled(String ore) implements ICondition {
        public static final MapCodec<OreEnabled> CODEC = Codec.STRING.fieldOf("ore").xmap(OreEnabled::new, OreEnabled::ore);

        @Override
        public boolean test(IContext context) {
            ArcforgeConfig.OreSettings settings = settings(ore);
            return settings == null || settings.isEnabled();
        }

        @Override
        public MapCodec<? extends ICondition> codec() {
            return CODEC;
        }
    }
}
