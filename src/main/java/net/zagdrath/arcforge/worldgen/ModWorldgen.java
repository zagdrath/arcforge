/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.worldgen;

import java.util.List;
import java.util.stream.Stream;

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
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraft.world.level.levelgen.placement.RepeatingPlacement;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.ClimateHelper;

// Ore generation that follows the "ores" config (see ArcforgeConfig.ORES), for the ores' worldgen files:
//   feature arcforge:config_ore             an ore vein ("targets", "size", "discard_chance_on_air_exposure" as
//                                           minecraft:ore, plus "ore"), its size and air discard from the config
//   feature arcforge:config_bed             a large flat bed of ore (Halite), the same fields: small veins laid on a
//                                           3-block grid over an oval (world.haliteBeds.radius), in one or more layers
//                                           (world.haliteBeds.layers, thickLayers under thickBiomeTags)
//   placement arcforge:config_count         "ore", "default": veins per chunk from the config
//   placement arcforge:config_height        "ore", "shape" (uniform or trapezoid), "default_min", "default_max":
//                                           a height between the config's minY and maxY
//   condition arcforge:ore_enabled          "ore": whether the config lets that ore generate
// Each falls back to its JSON values for an ore the config doesn't know (or before the config loads).
// On 26.1 a feature type is a Feature over its configuration (ConfigOre and ConfigBed, placed through Placing), and a
// placement type is a PlacementModifierType for a PlacementModifier subclass.
public final class ModWorldgen {
    private static final DeferredRegister<Feature<?>> FEATURE_TYPES = DeferredRegister.create(Registries.FEATURE, Arcforge.MODID);
    private static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Arcforge.MODID);
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Arcforge.MODID);

    private static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ConfigCount>> CONFIG_COUNT =
            PLACEMENT_TYPES.register("config_count", () -> () -> ConfigCount.CODEC);
    private static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ConfigHeight>> CONFIG_HEIGHT =
            PLACEMENT_TYPES.register("config_height", () -> () -> ConfigHeight.CODEC);

    static {
        FEATURE_TYPES.register("config_ore", () -> new Placing<>(ConfigOre.CODEC));
        FEATURE_TYPES.register("config_bed", () -> new Placing<>(ConfigBed.CODEC));
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

    // A feature configuration that places itself.
    public interface Placer extends FeatureConfiguration {
        boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin);
    }

    // The feature type of a Placer configuration.
    private static final class Placing<C extends Placer> extends Feature<C> {
        Placing(Codec<C> codec) {
            super(codec);
        }

        @Override
        public boolean place(FeaturePlaceContext<C> context) {
            return context.config().place(context.level(), context.chunkGenerator(), context.random(), context.origin());
        }
    }

    // --- The ore vein ---

    public record ConfigOre(String ore, List<OreConfiguration.TargetBlockState> targets, int size, float discard) implements Placer {
        public static final Codec<ConfigOre> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("ore").forGetter(ConfigOre::ore),
                Codec.list(OreConfiguration.TargetBlockState.CODEC).fieldOf("targets").forGetter(ConfigOre::targets),
                Codec.intRange(0, 64).fieldOf("size").forGetter(ConfigOre::size),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("discard_chance_on_air_exposure", 0.0F).forGetter(ConfigOre::discard))
                .apply(i, ConfigOre::new));

        // A vanilla ore vein, sized from the config.
        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            ArcforgeConfig.OreSettings settings = settings(ore);
            int veinSize = settings != null ? settings.veinSize().getAsInt() : size;
            float airDiscard = settings != null ? (float) settings.airExposureDiscard().getAsDouble() : discard;
            return Feature.ORE.place(new OreConfiguration(targets, veinSize, airDiscard), level, generator, random, origin);
        }
    }

    // --- A flat bed ---

    public record ConfigBed(String ore, List<OreConfiguration.TargetBlockState> targets, int size, float discard) implements Placer {
        public static final Codec<ConfigBed> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("ore").forGetter(ConfigBed::ore),
                Codec.list(OreConfiguration.TargetBlockState.CODEC).fieldOf("targets").forGetter(ConfigBed::targets),
                Codec.intRange(0, 64).fieldOf("size").forGetter(ConfigBed::size),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("discard_chance_on_air_exposure", 0.0F).forGetter(ConfigBed::discard))
                .apply(i, ConfigBed::new));
        // Veins a bed is laid from sit this far apart, and its layers this far apart in height.
        private static final int SPACING = 3, LAYER_STEP = 2;

        // An oval of small vanilla ore veins, 60% to 100% of the radius each way, on a grid with a block of jitter, in
        // layers stacked LAYER_STEP apart: a bed a block or two thick per layer that follows no surface, like a dried-up
        // sea. Thicker (more layers) where the bed's centre is under thickBiomeTags.
        @Override
        public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            ArcforgeConfig.OreSettings settings = settings(ore);
            int veinSize = settings != null ? settings.veinSize().getAsInt() : size;
            float airDiscard = settings != null ? (float) settings.airExposureDiscard().getAsDouble() : discard;
            boolean loaded = ArcforgeConfig.SPEC.isLoaded();
            int radius = loaded ? ArcforgeConfig.HALITE_BED_RADIUS.getAsInt() : 11;
            boolean thick = loaded && ClimateHelper.inAny(level.getBiome(origin), ArcforgeConfig.HALITE_THICK_BIOME_TAGS.get());
            int layers = !loaded ? 1 : thick ? ArcforgeConfig.HALITE_THICK_BED_LAYERS.getAsInt() : ArcforgeConfig.HALITE_BED_LAYERS.getAsInt();
            double rx = radius * (0.6 + 0.4 * random.nextDouble());
            double rz = radius * (0.6 + 0.4 * random.nextDouble());
            OreConfiguration vein = new OreConfiguration(targets, veinSize, airDiscard);
            boolean placed = false;
            for (int dx = -radius; dx <= radius; dx += SPACING) {
                for (int dz = -radius; dz <= radius; dz += SPACING) {
                    if ((dx * dx) / (rx * rx) + (dz * dz) / (rz * rz) > 1.0) {
                        continue;
                    }
                    for (int layer = 0; layer < layers; layer++) {
                        BlockPos at = origin.offset(dx + random.nextInt(3) - 1, layer * LAYER_STEP + random.nextInt(2), dz + random.nextInt(3) - 1);
                        placed |= Feature.ORE.place(vein, level, generator, random, at);
                    }
                }
            }
            return placed;
        }
    }

    // --- How many veins a chunk tries ---

    public static final class ConfigCount extends RepeatingPlacement {
        public static final MapCodec<ConfigCount> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("ore").forGetter(c -> c.ore),
                Codec.intRange(0, 4096).fieldOf("default").forGetter(c -> c.fallback))
                .apply(i, ConfigCount::new));

        private final String ore;
        private final int fallback;

        public ConfigCount(String ore, int fallback) {
            this.ore = ore;
            this.fallback = fallback;
        }

        @Override
        protected int count(RandomSource random, BlockPos origin) {
            ArcforgeConfig.OreSettings settings = settings(ore);
            return settings != null ? settings.veinsPerChunk().getAsInt() : fallback;
        }

        @Override
        public PlacementModifierType<?> type() {
            return CONFIG_COUNT.get();
        }
    }

    // --- At what height ---

    public static final class ConfigHeight extends PlacementModifier {
        public static final MapCodec<ConfigHeight> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.fieldOf("ore").forGetter(c -> c.ore),
                Codec.STRING.optionalFieldOf("shape", "uniform").forGetter(c -> c.shape),
                Codec.INT.fieldOf("default_min").forGetter(c -> c.defaultMin),
                Codec.INT.fieldOf("default_max").forGetter(c -> c.defaultMax))
                .apply(i, ConfigHeight::new));

        private final String ore, shape;
        private final int defaultMin, defaultMax;

        public ConfigHeight(String ore, String shape, int defaultMin, int defaultMax) {
            this.ore = ore;
            this.shape = shape;
            this.defaultMin = defaultMin;
            this.defaultMax = defaultMax;
        }

        @Override
        public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos origin) {
            ArcforgeConfig.OreSettings settings = settings(ore);
            int min = settings != null ? settings.minY().getAsInt() : defaultMin;
            int max = settings != null ? settings.maxY().getAsInt() : defaultMax;
            return Stream.of(origin.atY(sample(random, Math.min(min, max), Math.max(min, max), shape.equals("trapezoid"))));
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
        public PlacementModifierType<?> type() {
            return CONFIG_HEIGHT.get();
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
