/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.oil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Either;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;

// Oil deposits (see the world.oilDeposits config). The world is split into square regions regionSize chunks
// across; each may hold one deposit: a round patch of chunks deep underground, with a kind (DepositKind), an amount of
// Crude Oil and the height it lies at. Whether a region has one, and what it is, follows only from the world seed and
// the region, so nothing is generated or stored until it's pumped; regions whose middle is in an arcforge:oil_rich
// biome (deserts, oceans, badlands and plains) have deposits more often, and bigger ones. Each deposit is sweet or sour
// (more often sour under arcforge:sour_rich biomes, badlands and deserts) and may have a gas cap. What has been pumped
// out of each deposit (oil and gas) is saved here, once for the whole server. A drained deposit keeps giving a trickle
// (see PumpjackBlockEntity).
//
// In chunks generated with caverns (see OilCaverns), a deposit is also a real cavern of oil (OilCavern): those chunks
// are recorded here, each with the oil level last set in it and how many of its cavern's cells below that level held
// something else (see OilCaverns.reconcile). A deposit's sweet or sour oil and its gas cap only show where its chunks are
// recorded; in chunks generated before caverns it works as it always did: plain Crude Oil, no gas cap. The chunks a
// world already had when caverns came in are noted here too (scanLegacy): a deposit reaching any of them stays as it
// was everywhere.
public final class OilDeposits extends SavedData {
    public static final TagKey<Biome> OIL_RICH = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Arcforge.MODID, "oil_rich"));
    // Mixed into the seed, so deposits don't line up with anything else drawn from it.
    private static final long SALT = 0x6F696C5F64657073L;
    // The height a region's biome is sampled at.
    private static final int BIOME_Y = 64;

    // Biomes where deposits are more often sour (badlands and deserts).
    public static final TagKey<Biome> SOUR_RICH = TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(Arcforge.MODID, "sour_rich"));
    // Mixed into the seed for the sulfur and gas cap rolls, apart from the rolls that place a deposit (so adding them
    // left every deposit where it was).
    private static final long TRAIT_SALT = 0x737570686F726761L;

    // A deposit: its region, kind, how much it held (mB), its middle and radius (chunks), the height it lies at, whether
    // it's sour and whether it has a gas cap.
    public record Deposit(String dimension, int regionX, int regionZ, DepositKind kind, long amount, int centreChunkX, int centreChunkZ,
            int radius, int y, boolean sour, boolean gasCap) {
        public long key() {
            return (long) regionX << 32 | regionZ & 0xFFFFFFFFL;
        }

        public boolean covers(int chunkX, int chunkZ) {
            int dx = chunkX - centreChunkX, dz = chunkZ - centreChunkZ;
            return dx * dx + dz * dz <= radius * radius + radius;
        }
    }

    private record Entry(String dimension, int regionX, int regionZ, long extracted, long gas) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(Entry::dimension),
                Codec.INT.fieldOf("x").forGetter(Entry::regionX),
                Codec.INT.fieldOf("z").forGetter(Entry::regionZ),
                Codec.LONG.fieldOf("extracted").forGetter(Entry::extracted),
                Codec.LONG.optionalFieldOf("gas", 0L).forGetter(Entry::gas))
                .apply(i, Entry::new));
    }

    // A chunk generated with caverns: the oil level last set in it and the cavern cells below that held something else.
    private record ChunkEntry(String dimension, int chunkX, int chunkZ, int level, int missing) {
        static final Codec<ChunkEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(ChunkEntry::dimension),
                Codec.INT.fieldOf("x").forGetter(ChunkEntry::chunkX),
                Codec.INT.fieldOf("z").forGetter(ChunkEntry::chunkZ),
                Codec.INT.fieldOf("level").forGetter(ChunkEntry::level),
                Codec.INT.optionalFieldOf("missing", 0).forGetter(ChunkEntry::missing))
                .apply(i, ChunkEntry::new));
    }

    // A region file's chunks that existed before caverns came in: bit (x & 31) + (z & 31) * 32 of its 16 longs.
    private record LegacyRegion(String dimension, int regionX, int regionZ, List<Long> bits) {
        static final Codec<LegacyRegion> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dimension").forGetter(LegacyRegion::dimension),
                Codec.INT.fieldOf("x").forGetter(LegacyRegion::regionX),
                Codec.INT.fieldOf("z").forGetter(LegacyRegion::regionZ),
                Codec.LONG.listOf().fieldOf("chunks").forGetter(LegacyRegion::bits))
                .apply(i, LegacyRegion::new));
    }

    private record Stored(List<Entry> deposits, List<ChunkEntry> chunks, List<String> scanned, List<LegacyRegion> legacy) {
        static final Codec<Stored> CODEC = RecordCodecBuilder.create(i -> i.group(
                Entry.CODEC.listOf().fieldOf("deposits").forGetter(Stored::deposits),
                ChunkEntry.CODEC.listOf().optionalFieldOf("cavern_chunks", List.of()).forGetter(Stored::chunks),
                Codec.STRING.listOf().optionalFieldOf("scanned", List.of()).forGetter(Stored::scanned),
                LegacyRegion.CODEC.listOf().optionalFieldOf("legacy_chunks", List.of()).forGetter(Stored::legacy))
                .apply(i, Stored::new));
    }

    // Saves before caverns were a bare list of deposits.
    private static final Codec<OilDeposits> CODEC = Codec.either(Stored.CODEC, Entry.CODEC.listOf()).xmap(
            either -> either.map(OilDeposits::new, list -> new OilDeposits(new Stored(list, List.of(), List.of(), List.of()))),
            deposits -> Either.left(new Stored(deposits.entries(), deposits.chunkEntries(), List.copyOf(deposits.scanned),
                    deposits.legacyEntries())));
    public static final SavedDataType<OilDeposits> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "oil_deposits"), OilDeposits::new, CODEC);

    // Deposits the GameTests put under single chunks (by dimension and chunk), ahead of what the seed gives.
    private static final Map<String, Map<Long, Deposit>> TEST_DEPOSITS = new java.util.concurrent.ConcurrentHashMap<>();

    // Per dimension, the Crude Oil and the Natural Gas taken out of each deposit (by Deposit.key), in mB. Concurrent:
    // world generation reads them off the server thread.
    private final Map<String, Map<Long, Long>> extracted = new ConcurrentHashMap<>();
    private final Map<String, Map<Long, Long>> gasExtracted = new ConcurrentHashMap<>();
    // Per dimension, the chunks generated with caverns (by chunk key): the oil level and missing cells packed in a long
    // (see pack), or NO_CAVERN for a chunk with no cavern in it.
    private final Map<String, Map<Long, Long>> cavernChunks = new ConcurrentHashMap<>();
    public static final long NO_CAVERN = Long.MIN_VALUE;
    // Missing cells not yet counted: a chunk just generated, counted on its first check (see OilCaverns.reconcile).
    public static final int UNCOUNTED = -1;
    // The dimensions whose chunks from before caverns have been noted, and those chunks (by dimension and region key).
    private final Set<String> scanned = ConcurrentHashMap.newKeySet();
    private final Map<String, Map<Long, long[]>> legacy = new ConcurrentHashMap<>();
    // Whether each deposit (by dimension and key) reaches a chunk from before caverns, worked out once.
    private final Map<String, Map<Long, Boolean>> legacyDeposits = new ConcurrentHashMap<>();

    public OilDeposits() {}

    private OilDeposits(Stored stored) {
        this(stored.deposits(), stored.chunks());
        scanned.addAll(stored.scanned());
        for (LegacyRegion region : stored.legacy()) {
            long[] bits = new long[16];
            for (int i = 0; i < Math.min(16, region.bits().size()); i++) {
                bits[i] = region.bits().get(i);
            }
            legacy.computeIfAbsent(region.dimension(), d -> new ConcurrentHashMap<>()).put(chunkKey(region.regionX(), region.regionZ()), bits);
        }
    }

    private OilDeposits(List<Entry> entries, List<ChunkEntry> chunks) {
        for (Entry entry : entries) {
            long key = (long) entry.regionX() << 32 | entry.regionZ() & 0xFFFFFFFFL;
            if (entry.extracted() > 0) {
                extracted.computeIfAbsent(entry.dimension(), d -> new ConcurrentHashMap<>()).put(key, entry.extracted());
            }
            if (entry.gas() > 0) {
                gasExtracted.computeIfAbsent(entry.dimension(), d -> new ConcurrentHashMap<>()).put(key, entry.gas());
            }
        }
        for (ChunkEntry chunk : chunks) {
            cavernChunks.computeIfAbsent(chunk.dimension(), d -> new ConcurrentHashMap<>()).put(chunkKey(chunk.chunkX(), chunk.chunkZ()),
                    chunk.level() == Integer.MIN_VALUE ? NO_CAVERN : pack(chunk.level(), chunk.missing()));
        }
    }

    private List<Entry> entries() {
        Map<String, Map<Long, long[]>> all = new LinkedHashMap<>();
        extracted.forEach((dimension, map) -> map.forEach((key, mb) -> all.computeIfAbsent(dimension, d -> new LinkedHashMap<>())
                .computeIfAbsent(key, k -> new long[2])[0] = mb));
        gasExtracted.forEach((dimension, map) -> map.forEach((key, mb) -> all.computeIfAbsent(dimension, d -> new LinkedHashMap<>())
                .computeIfAbsent(key, k -> new long[2])[1] = mb));
        List<Entry> entries = new ArrayList<>();
        all.forEach((dimension, map) -> map.forEach((key, both) -> entries.add(new Entry(dimension, (int) (key >> 32), (int) (long) key, both[0],
                both[1]))));
        return entries;
    }

    private List<ChunkEntry> chunkEntries() {
        List<ChunkEntry> entries = new ArrayList<>();
        cavernChunks.forEach((dimension, map) -> map.forEach((key, packed) -> entries.add(new ChunkEntry(dimension, (int) (key >> 32), (int) (long) key,
                packed == NO_CAVERN ? Integer.MIN_VALUE : level(packed), packed == NO_CAVERN ? 0 : missing(packed)))));
        return entries;
    }

    private List<LegacyRegion> legacyEntries() {
        List<LegacyRegion> entries = new ArrayList<>();
        legacy.forEach((dimension, map) -> map.forEach((key, bits) -> {
            List<Long> list = new ArrayList<>(16);
            for (long word : bits) {
                list.add(word);
            }
            entries.add(new LegacyRegion(dimension, (int) (key >> 32), (int) (long) key, list));
        }));
        return entries;
    }

    public static OilDeposits get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return (long) chunkX << 32 | chunkZ & 0xFFFFFFFFL;
    }

    // A cavern chunk's oil level (the y below which its cells hold oil) and missing cells, in one long.
    public static long pack(int level, int missing) {
        return (long) level << 32 | missing & 0xFFFFFFFFL;
    }

    public static int level(long packed) {
        return (int) (packed >> 32);
    }

    public static int missing(long packed) {
        return (int) packed;
    }

    // --- Chunks generated with caverns ---

    // What's recorded for a chunk: null if it was generated before caverns (or with caverns off), NO_CAVERN if it was
    // generated with them but holds no cavern, else its packed level and missing cells.
    public @Nullable Long cavernChunk(String dimension, int chunkX, int chunkZ) {
        Map<Long, Long> chunks = cavernChunks.get(dimension);
        return chunks != null ? chunks.get(chunkKey(chunkX, chunkZ)) : null;
    }

    public void setCavernChunk(String dimension, int chunkX, int chunkZ, long packed) {
        cavernChunks.computeIfAbsent(dimension, d -> new ConcurrentHashMap<>()).put(chunkKey(chunkX, chunkZ), packed);
        setDirty();
    }

    // Forgets what's recorded for a chunk, as if it was generated before caverns (for the GameTests).
    public void clearCavernChunk(String dimension, int chunkX, int chunkZ) {
        Map<Long, Long> chunks = cavernChunks.get(dimension);
        if (chunks != null && chunks.remove(chunkKey(chunkX, chunkZ)) != null) {
            setDirty();
        }
    }

    // --- Chunks from before caverns ---

    // The first time a dimension loads with caverns (before any of its chunks generate), notes every chunk it already
    // has, from its region files' headers (a chunk is there when its entry in the 4 KiB location table isn't zero).
    // Deposits reaching any of them get no cavern (see isLegacy), so a world from before caverns never gets one half
    // carved against old ground.
    public void scanLegacy(ServerLevel level) {
        String dimension = level.dimension().identifier().toString();
        if (!scanned.add(dimension)) {
            return;
        }
        Map<Long, long[]> regions = new ConcurrentHashMap<>();
        for (Path folder : regionFolders(level)) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "r.*.*.mca")) {
                for (Path file : files) {
                    String[] parts = file.getFileName().toString().split("\\.");
                    if (parts.length != 4) {
                        continue;
                    }
                    long[] bits = chunksIn(file);
                    if (bits != null) {
                        regions.merge(chunkKey(Integer.parseInt(parts[1]), Integer.parseInt(parts[2])), bits, (a, b) -> {
                            for (int i = 0; i < 16; i++) {
                                a[i] |= b[i];
                            }
                            return a;
                        });
                    }
                }
            } catch (IOException | RuntimeException e) {
                Arcforge.LOGGER.warn("Couldn't read the region files in {} for oil caverns: deposits there may get caverns against old chunks", folder, e);
            }
        }
        if (!regions.isEmpty()) {
            legacy.put(dimension, regions);
            Arcforge.LOGGER.info("Oil caverns: {} region files of {} were there before caverns; deposits reaching them stay as they were", regions.size(),
                    dimension);
        }
        legacyDeposits.remove(dimension);
        setDirty();
    }

    // Where a dimension's region files are: dimensions/<namespace>/<path>/region, and for the vanilla dimensions also
    // their older homes (region, DIM-1/region, DIM1/region).
    private static List<Path> regionFolders(ServerLevel level) {
        Path root = level.getServer().getWorldPath(LevelResource.ROOT);
        Identifier id = level.dimension().identifier();
        List<Path> folders = new ArrayList<>();
        folders.add(root.resolve("dimensions").resolve(id.getNamespace()).resolve(id.getPath()).resolve("region"));
        if (level.dimension() == Level.OVERWORLD) {
            folders.add(root.resolve("region"));
        } else if (level.dimension() == Level.NETHER) {
            folders.add(root.resolve("DIM-1").resolve("region"));
        } else if (level.dimension() == Level.END) {
            folders.add(root.resolve("DIM1").resolve("region"));
        }
        return folders;
    }

    // The chunks a region file holds, as 16 longs of bits, or null if it holds none.
    private static long @Nullable [] chunksIn(Path file) throws IOException {
        byte[] header = new byte[4096];
        try (InputStream in = Files.newInputStream(file)) {
            if (in.readNBytes(header, 0, header.length) < header.length) {
                return null;
            }
        }
        long[] bits = new long[16];
        boolean any = false;
        for (int i = 0; i < 1024; i++) {
            int offset = (header[i * 4] & 0xFF) << 16 | (header[i * 4 + 1] & 0xFF) << 8 | header[i * 4 + 2] & 0xFF;
            if (offset != 0 && header[i * 4 + 3] != 0) {
                bits[i >> 6] |= 1L << (i & 63);
                any = true;
            }
        }
        return any ? bits : null;
    }

    public boolean isLegacyChunk(String dimension, int chunkX, int chunkZ) {
        Map<Long, long[]> regions = legacy.get(dimension);
        long[] bits = regions != null ? regions.get(chunkKey(chunkX >> 5, chunkZ >> 5)) : null;
        int index = (chunkX & 31) + (chunkZ & 31) * 32;
        return bits != null && (bits[index >> 6] >>> (index & 63) & 1L) != 0;
    }

    // Whether a deposit reaches a chunk from before caverns: any chunk of its patch, or within a chunk of it (where its
    // cavern's shell may reach). Such a deposit has no cavern and works as it always did.
    public boolean isLegacy(Deposit deposit) {
        Map<Long, long[]> regions = legacy.get(deposit.dimension());
        if (regions == null || regions.isEmpty()) {
            return false;
        }
        return legacyDeposits.computeIfAbsent(deposit.dimension(), d -> new ConcurrentHashMap<>()).computeIfAbsent(deposit.key(), key -> {
            int reach = deposit.radius() + 1;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (isLegacyChunk(deposit.dimension(), deposit.centreChunkX() + dx, deposit.centreChunkZ() + dz)) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    // Whether the chunk holding pos was generated with caverns, so its deposit shows its sulfur and gas cap.
    public static boolean generatedWithCaverns(ServerLevel level, BlockPos pos) {
        return get(level.getServer()).cavernChunk(level.dimension().identifier().toString(), SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ())) != null;
    }

    // The fluid this deposit gives at pos: Sour Crude Oil from a sour deposit where its chunks have caverns, else Crude
    // Oil.
    public static net.minecraft.world.level.material.Fluid oilAt(ServerLevel level, Deposit deposit, BlockPos pos) {
        return deposit.sour() && generatedWithCaverns(level, pos) ? net.zagdrath.arcforge.registry.ModFluids.SOUR_CRUDE_OIL.get()
                : net.zagdrath.arcforge.registry.ModFluids.CRUDE_OIL.get();
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
        return roll(level.getSeed(), dimension, regionX, regionZ, size, biome.is(OIL_RICH), biome.is(SOUR_RICH));
    }

    // As roll below, in an ordinary biome for sulfur.
    public static @Nullable Deposit roll(long seed, String dimension, int regionX, int regionZ, int size, boolean rich) {
        return roll(seed, dimension, regionX, regionZ, size, rich, false);
    }

    // What a region holds, from the seed alone: whether it has a deposit, its kind, amount, place, size and height, and
    // whether it's sour and has a gas cap (drawn from their own salt). Public for the GameTests.
    public static @Nullable Deposit roll(long seed, String dimension, int regionX, int regionZ, int size, boolean rich, boolean sourRich) {
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
        long traits = mix(seed ^ TRAIT_SALT ^ dimension.hashCode() * 0x9E3779B97F4A7C15L);
        traits = mix(traits ^ regionX * 0xC2B2AE3D27D4EB4FL);
        traits = mix(traits ^ regionZ * 0x165667B19E3779F9L);
        boolean sour = unit(traits = mix(traits)) < (sourRich ? ArcforgeConfig.OIL_SOUR_RICH_CHANCE : ArcforgeConfig.OIL_SOUR_CHANCE).getAsDouble();
        boolean gasCap = unit(mix(traits)) < ArcforgeConfig.OIL_GAS_CAP_CHANCE.getAsDouble();
        return new Deposit(dimension, regionX, regionZ, kind, amount, centreX, centreZ, radius, y, sour, gasCap);
    }

    // For the GameTests: a deposit of this kind and amount lying at y, under the chunk holding pos alone (its region
    // key is made up, far from any real region, so what's taken from it is its own). Gives the deposit.
    public static Deposit putTestDeposit(ServerLevel level, BlockPos pos, DepositKind kind, long amount, int y) {
        return putTestDeposit(level, pos, kind, amount, y, false, false);
    }

    public static Deposit putTestDeposit(ServerLevel level, BlockPos pos, DepositKind kind, long amount, int y, boolean sour, boolean gasCap) {
        String dimension = level.dimension().identifier().toString();
        int chunkX = SectionPos.blockToSectionCoord(pos.getX()), chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        Deposit deposit = new Deposit(dimension, Integer.MIN_VALUE + chunkX, Integer.MIN_VALUE + chunkZ, kind, amount, chunkX, chunkZ, 0, y, sour,
                gasCap);
        TEST_DEPOSITS.computeIfAbsent(dimension, d -> new java.util.concurrent.ConcurrentHashMap<>()).put(ChunkPosKey.of(pos), deposit);
        get(level.getServer()).setExtracted(deposit, 0);
        get(level.getServer()).setGasExtracted(deposit, 0);
        return deposit;
    }

    // The test deposit under a chunk, or null (OilCaverns looks here first, so a test cavern is carved and kept in step).
    public static @Nullable Deposit testDeposit(ServerLevel level, int chunkX, int chunkZ) {
        Map<Long, Deposit> tests = TEST_DEPOSITS.get(level.dimension().identifier().toString());
        return tests != null ? tests.get((long) chunkX << 32 | chunkZ & 0xFFFFFFFFL) : null;
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

    // --- What's left: oil ---

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
            extracted.computeIfAbsent(deposit.dimension(), d -> new ConcurrentHashMap<>()).merge(deposit.key(), taken, Long::sum);
            setDirty();
        }
        return taken;
    }

    // Sets how much has been taken out of a deposit (for the GameTests).
    public void setExtracted(Deposit deposit, long amount) {
        extracted.computeIfAbsent(deposit.dimension(), d -> new ConcurrentHashMap<>()).put(deposit.key(), Math.max(0L, amount));
        setDirty();
    }

    // --- What's left: the gas cap ---

    // The Natural Gas a deposit's gas cap held, in mB: gasPerBlock (times amountMultiplier) for each cell of its
    // cavern's dome. 0 with no cap.
    public static long gasAmount(Deposit deposit, long seed) {
        if (!deposit.gasCap()) {
            return 0L;
        }
        OilCavern cavern = OilCaverns.cavern(deposit, seed);
        int cells = cavern != null ? cavern.domeCells() : (int) Math.max(1, deposit.amount() / 9_000);
        return Math.round(cells * (double) ArcforgeConfig.OIL_GAS_PER_BLOCK.getAsInt() * ArcforgeConfig.OIL_AMOUNT_MULTIPLIER.getAsDouble());
    }

    public long gasExtracted(Deposit deposit) {
        Map<Long, Long> dimension = gasExtracted.get(deposit.dimension());
        return dimension != null ? dimension.getOrDefault(deposit.key(), 0L) : 0L;
    }

    public long gasRemaining(Deposit deposit, long seed) {
        return Math.max(0L, gasAmount(deposit, seed) - gasExtracted(deposit));
    }

    // Takes up to amount mB of gas out of the deposit's cap, and gives how much it had.
    public long takeGas(Deposit deposit, long seed, long amount) {
        long taken = Math.min(amount, gasRemaining(deposit, seed));
        if (taken > 0) {
            gasExtracted.computeIfAbsent(deposit.dimension(), d -> new ConcurrentHashMap<>()).merge(deposit.key(), taken, Long::sum);
            setDirty();
        }
        return taken;
    }

    // Sets how much gas has been taken out of a deposit (for the GameTests).
    public void setGasExtracted(Deposit deposit, long amount) {
        gasExtracted.computeIfAbsent(deposit.dimension(), d -> new ConcurrentHashMap<>()).put(deposit.key(), Math.max(0L, amount));
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
