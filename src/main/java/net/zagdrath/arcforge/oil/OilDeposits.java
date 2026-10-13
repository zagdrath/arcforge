/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.oil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// Oil deposits (see the world.oilDeposits config). The world is split into square regions regionSize chunks
// across; each may hold one deposit: a round patch of chunks deep underground, with a kind (DepositKind), an amount of
// Crude Oil and the height it lies at. Whether a region has one, and what it is, follows only from the world seed and
// the region, so nothing is generated or stored until it's pumped; regions whose middle is in an arcforge:oil_rich
// biome (deserts, oceans, badlands and plains) have deposits more often, and bigger ones. What has been pumped out of
// each deposit is saved here, once for the whole server. A drained deposit keeps giving a trickle (see
// PumpjackBlockEntity).
public final class OilDeposits extends SavedData {
    public static final TagKey<Biome> OIL_RICH = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Arcforge.MODID, "oil_rich"));
    // Mixed into the seed, so deposits don't line up with anything else drawn from it.
    private static final long SALT = 0x6F696C5F64657073L;
    // The height a region's biome is sampled at.
    private static final int BIOME_Y = 64;

    // A deposit: its region, kind, how much it held (mB), its middle and radius (chunks) and the height it lies at.
    public record Deposit(String dimension, int regionX, int regionZ, DepositKind kind, long amount, int centreChunkX, int centreChunkZ,
            int radius, int y) {
        public long key() {
            return (long) regionX << 32 | regionZ & 0xFFFFFFFFL;
        }

        public boolean covers(int chunkX, int chunkZ) {
            int dx = chunkX - centreChunkX, dz = chunkZ - centreChunkZ;
            return dx * dx + dz * dz <= radius * radius + radius;
        }
    }

    private record Entry(String dimension, int regionX, int regionZ, long extracted) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension),
                Codec.INT.fieldOf("x").forGetter(Entry::regionX),
                Codec.INT.fieldOf("z").forGetter(Entry::regionZ),
                Codec.LONG.fieldOf("extracted").forGetter(Entry::extracted))
                .apply(i, Entry::new));
    }

    private static final Codec<OilDeposits> CODEC = Entry.CODEC.listOf().xmap(OilDeposits::new, OilDeposits::entries);
    public static final SavedDataType<OilDeposits> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "oil_deposits"), OilDeposits::new, CODEC);

    // Deposits the GameTests put under single chunks (by dimension and chunk), ahead of what the seed gives.
    private static final Map<String, Map<Long, Deposit>> TEST_DEPOSITS = new java.util.concurrent.ConcurrentHashMap<>();

    // Per dimension, the Crude Oil taken out of each deposit (by Deposit.key), in mB.
    private final Map<String, Map<Long, Long>> extracted = new HashMap<>();

    public OilDeposits() {}

    private OilDeposits(List<Entry> entries) {
        for (Entry entry : entries) {
            extracted.computeIfAbsent(entry.dimension(), d -> new HashMap<>())
                    .put((long) entry.regionX() << 32 | entry.regionZ() & 0xFFFFFFFFL, entry.extracted());
        }
    }

    private List<Entry> entries() {
        return extracted.entrySet().stream().flatMap(dimension -> dimension.getValue().entrySet().stream()
                .map(e -> new Entry(dimension.getKey(), (int) (e.getKey() >> 32), (int) (long) e.getKey(), e.getValue()))).toList();
    }

    public static OilDeposits get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    // --- Where deposits are ---

    // The deposit under the chunk holding pos, or null.
    public static @Nullable Deposit at(ServerLevel level, BlockPos pos) {
        Map<Long, Deposit> tests = TEST_DEPOSITS.get(level.dimension().identifier().toString());
        if (tests != null) {
            Deposit test = tests.get(ChunkPosKey.of(pos));
            if (test != null) {
                return test;
            }
        }
        Deposit deposit = inRegion(level, regionOf(SectionPos.blockToSectionCoord(pos.getX())), regionOf(SectionPos.blockToSectionCoord(pos.getZ())));
        return deposit != null && deposit.covers(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())) ? deposit : null;
    }

    private static int regionOf(int chunk) {
        return Math.floorDiv(chunk, ArcforgeConfig.OIL_REGION_SIZE.getAsInt());
    }

    // The deposit of a region, or null when it has none (or the dimension has no oil).
    public static @Nullable Deposit inRegion(ServerLevel level, int regionX, int regionZ) {
        String dimension = level.dimension().identifier().toString();
        if (!ArcforgeConfig.OIL_DIMENSIONS.get().contains(dimension)) {
            return null;
        }
        int size = ArcforgeConfig.OIL_REGION_SIZE.getAsInt();
        int middleX = (regionX * size + size / 2) * 16 + 8, middleZ = (regionZ * size + size / 2) * 16 + 8;
        Holder<Biome> biome = level.getUncachedNoiseBiome(QuartPos.fromBlock(middleX), QuartPos.fromBlock(BIOME_Y), QuartPos.fromBlock(middleZ));
        return roll(level.getSeed(), dimension, regionX, regionZ, size, biome.is(OIL_RICH));
    }

    // What a region holds, from the seed alone: whether it has a deposit, its kind, amount, place, size and height.
    // Public for the GameTests.
    public static @Nullable Deposit roll(long seed, String dimension, int regionX, int regionZ, int size, boolean rich) {
        long state = mix(seed ^ SALT ^ dimension.hashCode() * 0x9E3779B97F4A7C15L);
        state = mix(state ^ regionX * 0xC2B2AE3D27D4EB4FL);
        state = mix(state ^ regionZ * 0x165667B19E3779F9L);
        double chance = (rich ? ArcforgeConfig.OIL_RICH_CHANCE : ArcforgeConfig.OIL_CHANCE).getAsDouble();
        if (unit(state = mix(state)) >= chance) {
            return null;
        }
        DepositKind kind = DepositKind.pick(unit(state = mix(state)), rich);
        double share = unit(state = mix(state));
        long buckets = kind.minBuckets + Math.round(share * (kind.maxBuckets - kind.minBuckets));
        long amount = Math.max(1L, Math.round(buckets * 1_000L * ArcforgeConfig.OIL_AMOUNT_MULTIPLIER.getAsDouble()));
        int radius = Math.min(size / 2 - 1, kind.minRadius + (int) (unit(state = mix(state)) * (kind.maxRadius - kind.minRadius + 1)));
        radius = Math.max(1, radius);
        // The middle anywhere the whole patch stays inside the region.
        int span = Math.max(1, size - 2 * radius);
        int centreX = regionX * size + radius + (int) (unit(state = mix(state)) * span);
        int centreZ = regionZ * size + radius + (int) (unit(state = mix(state)) * span);
        int minY = ArcforgeConfig.OIL_MIN_Y.getAsInt(), maxY = Math.max(minY, ArcforgeConfig.OIL_MAX_Y.getAsInt());
        int y = minY + (int) (unit(mix(state)) * (maxY - minY + 1));
        return new Deposit(dimension, regionX, regionZ, kind, amount, centreX, centreZ, radius, y);
    }

    // For the GameTests: a deposit of this kind and amount lying at y, under the chunk holding pos alone (its region
    // key is made up, far from any real region, so what's taken from it is its own). Gives the deposit.
    public static Deposit putTestDeposit(ServerLevel level, BlockPos pos, DepositKind kind, long amount, int y) {
        String dimension = level.dimension().identifier().toString();
        int chunkX = SectionPos.blockToSectionCoord(pos.getX()), chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        Deposit deposit = new Deposit(dimension, Integer.MIN_VALUE + chunkX, Integer.MIN_VALUE + chunkZ, kind, amount, chunkX, chunkZ, 0, y);
        TEST_DEPOSITS.computeIfAbsent(dimension, d -> new java.util.concurrent.ConcurrentHashMap<>()).put(ChunkPosKey.of(pos), deposit);
        get(level.getServer()).setExtracted(deposit, 0);
        return deposit;
    }

    public static void clearTestDeposit(ServerLevel level, BlockPos pos) {
        Map<Long, Deposit> tests = TEST_DEPOSITS.get(level.dimension().identifier().toString());
        if (tests != null) {
            tests.remove(ChunkPosKey.of(pos));
        }
    }

    private static final class ChunkPosKey {
        static long of(BlockPos pos) {
            return (long) SectionPos.blockToSectionCoord(pos.getX()) << 32 | SectionPos.blockToSectionCoord(pos.getZ()) & 0xFFFFFFFFL;
        }
    }

    // SplitMix64's finaliser.
    private static long mix(long z) {
        z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
        z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
        return z ^ z >>> 31;
    }

    private static double unit(long value) {
        return (value >>> 11) * 0x1.0p-53;
    }

    // --- What's left ---

    public long extracted(Deposit deposit) {
        Map<Long, Long> dimension = extracted.get(deposit.dimension());
        return dimension != null ? dimension.getOrDefault(deposit.key(), 0L) : 0L;
    }

    public long remaining(Deposit deposit) {
        return Math.max(0L, deposit.amount() - extracted(deposit));
    }

    // Takes up to amount mB out of the deposit, and gives how much it had.
    public long take(Deposit deposit, long amount) {
        long taken = Math.min(amount, remaining(deposit));
        if (taken > 0) {
            extracted.computeIfAbsent(deposit.dimension(), d -> new HashMap<>()).merge(deposit.key(), taken, Long::sum);
            setDirty();
        }
        return taken;
    }

    // Sets how much has been taken out of a deposit (for the GameTests).
    public void setExtracted(Deposit deposit, long amount) {
        extracted.computeIfAbsent(deposit.dimension(), d -> new HashMap<>()).put(deposit.key(), Math.max(0L, amount));
        setDirty();
    }

    // Roughly how much is left, for players: two significant figures, in buckets.
    public static long roughBuckets(long mB) {
        // Rounded up, so a deposit with anything left never reads as 0.
        long buckets = (mB + 999) / 1_000;
        if (buckets < 100) {
            return buckets;
        }
        long scale = 1;
        while (buckets / scale >= 100) {
            scale *= 10;
        }
        return Math.round((double) buckets / scale) * scale;
    }
}
