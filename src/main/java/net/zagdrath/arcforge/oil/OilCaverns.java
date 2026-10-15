/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.oil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;

// Oil deposits as real caverns (see OilCavern for the shape, world.oilDeposits.caverns to turn them off).
//
// World generation (place, the arcforge:oil_cavern feature, last of a chunk's features so caves, aquifers, lava, ores
// and springs are already there): in each new chunk, the part of any deposit's cavern in it is carved and filled with
// Crude Oil (Sour Crude Oil for a sour deposit) up to the level the deposit's oil reaches, its gas cap's dome left empty,
// and its 2-block shell laid in stone or deepslate over whatever was there, so nothing breaks in and the oil never leaks.
// Blocks with block entities (a structure's chests) and full solid blocks that aren't natural rock are left alone. The
// chunk is recorded in OilDeposits, which is how the rest of the mod knows its deposit is a cavern there. Above some
// Seeps and Pockets under deserts and badlands, a small tar pit of oil marks the deposit at the surface.
//
// Keeping blocks and amount in step (reconcile): the deposit's tracked amount stays the truth. Each loaded cavern chunk
// is checked every cavernCheckInterval ticks: source blocks gone from below its recorded level (buckets, other mods'
// pumps) are taken from the deposit at 1,000 mB each (only once each: cells that came to hold something else are
// remembered as missing). Every second the level the deposit's oil now reaches is compared with the chunk's, and when it
// has fallen the cells above it are emptied, from the top down, a layer at a time; holes below it are filled again. A
// chunk that wasn't loaded catches up when it loads.
//
// Existing worlds: a deposit any of whose chunks (its patch and a chunk round it) were generated before caverns came in
// gets no cavern at all and keeps working as it did, so no cavern is ever half carved against old ground (see
// OilDeposits.scanLegacy).
@EventBusSubscriber(modid = Arcforge.MODID)
public final class OilCaverns {
    // Blocks a cavern or its shell may replace besides air, fluids and anything not a full solid block.
    public static final TagKey<Block> REPLACEABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(Arcforge.MODID, "oil_cavern_replaceable"));
    private static final int LEVEL_CHECK_INTERVAL = 20;
    // Reconcile's block updates: sent to clients, with no shape updates to neighbours (which could reach, and load, the
    // next chunk).
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    // Chunks reconciled at most per tick per level.
    private static final int BUDGET = 4;

    // Caverns by dimension and deposit key, so their shapes are measured once.
    private static final Map<String, Map<Long, OilCavern>> CAVERNS = new ConcurrentHashMap<>();
    // Deposits by dimension and region (empty for a region with none).
    private static final Map<String, Map<Long, java.util.Optional<OilDeposits.Deposit>>> REGIONS = new ConcurrentHashMap<>();
    // The loaded chunks of each level that hold a cavern, and the ones waiting for a check (true: a full check).
    private static final Map<ServerLevel, Set<Long>> LOADED = new ConcurrentHashMap<>();
    private static final Map<ServerLevel, Map<Long, Boolean>> PENDING = new ConcurrentHashMap<>();
    // Caverns given to test deposits by the GameTests, by deposit key.
    private static final Map<Long, OilCavern> TEST_CAVERNS = new ConcurrentHashMap<>();
    // The server's deposits, set when the overworld loads, for world generation off the server thread.
    private static volatile @Nullable OilDeposits active;

    private OilCaverns() {}

    // A deposit's cavern (cached), or null when caverns are off or it's too small.
    public static @Nullable OilCavern cavern(OilDeposits.Deposit deposit, long seed) {
        OilCavern test = TEST_CAVERNS.get(deposit.key());
        if (test != null) {
            return test;
        }
        OilDeposits deposits = active;
        if (!ArcforgeConfig.OIL_CAVERNS.getAsBoolean() || deposit.radius() <= 0 || deposits != null && deposits.isLegacy(deposit)) {
            return null;
        }
        Map<Long, OilCavern> caverns = CAVERNS.computeIfAbsent(deposit.dimension(), d -> new ConcurrentHashMap<>());
        OilCavern cavern = caverns.get(deposit.key());
        if (cavern == null) {
            cavern = OilCavern.of(deposit, seed);
            if (caverns.size() > 512) {
                caverns.clear();
            }
            caverns.put(deposit.key(), cavern);
        }
        return cavern;
    }

    // The cavern under pos, when its chunk was generated with caverns and the deposit has one; else null.
    public static @Nullable OilCavern cavernAt(ServerLevel level, OilDeposits.Deposit deposit, BlockPos pos) {
        return OilDeposits.generatedWithCaverns(level, pos) ? cavern(deposit, level.getSeed()) : null;
    }

    private static Fluid oil(OilDeposits.Deposit deposit) {
        return deposit.sour() ? ModFluids.SOUR_CRUDE_OIL.get() : ModFluids.CRUDE_OIL.get();
    }

    private static BlockState oilBlock(OilDeposits.Deposit deposit) {
        return (deposit.sour() ? ModBlocks.SOUR_CRUDE_OIL.get() : ModBlocks.CRUDE_OIL.get()).defaultBlockState();
    }

    // Stone above y 8, deepslate below 0, and between them a mix leaning to deepslate the lower it is (as vanilla).
    private static BlockState rock(int x, int y, int z) {
        if (y >= 8) {
            return Blocks.STONE.defaultBlockState();
        }
        if (y < 0) {
            return Blocks.DEEPSLATE.defaultBlockState();
        }
        long hash = (x * 3129871L) ^ (z * 116129781L) ^ y;
        hash = hash * hash * 42317861L + hash * 11L;
        return ((hash >> 16) & 7) < 8 - y ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
    }

    // Whether a cavern or its shell may replace this block.
    private static boolean replaceable(LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.hasBlockEntity() || state.is(Blocks.BEDROCK)) {
            return false;
        }
        return state.isAir() || !state.getFluidState().isEmpty() || state.is(REPLACEABLE) || !state.isCollisionShapeFullBlock(level, pos);
    }

    // --- World generation ---

    // Carves the parts of any caverns in the chunk at origin, lays their shells and the surface seeps, and records the
    // chunk. Gives whether it placed anything.
    public static boolean place(WorldGenLevel level, BlockPos origin) {
        OilDeposits deposits = active;
        if (deposits == null || !ArcforgeConfig.OIL_CAVERNS.getAsBoolean()) {
            return false;
        }
        ServerLevel server = level.getLevel();
        String dimension = server.dimension().identifier().toString();
        if (!ArcforgeConfig.OIL_DIMENSIONS.get().contains(dimension)) {
            return false;
        }
        int chunkX = origin.getX() >> 4, chunkZ = origin.getZ() >> 4;
        OilDeposits.Deposit deposit = depositNear(server, chunkX, chunkZ);
        OilCavern cavern = deposit != null ? cavern(deposit, server.getSeed()) : null;
        boolean covered = deposit != null && deposit.covers(chunkX, chunkZ);
        if (cavern == null || !cavern.touches(chunkX, chunkZ)) {
            // A deposit reaching chunks generated before caverns stays as it was everywhere, so its new chunks aren't
            // recorded either.
            if (covered && !deposits.isLegacy(deposit)) {
                deposits.setCavernChunk(dimension, chunkX, chunkZ, OilDeposits.NO_CAVERN);
            }
            return false;
        }
        int level0 = cavern.levelFor(deposits.remaining(deposit));
        BlockState oil = oilBlock(deposit);
        int missing = 0;
        int[] bounds = cavern.bounds();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
            for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                if (x < bounds[0] || x > bounds[3] || z < bounds[2] || z > bounds[5]) {
                    continue;
                }
                for (int y = bounds[1]; y <= bounds[4]; y++) {
                    boolean inside = cavern.inside(x, y, z);
                    if (!inside && !cavern.inShell(x, y, z)) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!replaceable(level, pos, state)) {
                        if (inside && y < level0) {
                            missing++;
                        }
                        continue;
                    }
                    BlockState placed = !inside ? rock(x, y, z) : y < level0 ? oil : Blocks.AIR.defaultBlockState();
                    level.setBlock(pos, placed, Block.UPDATE_CLIENTS);
                }
            }
        }
        // Its missing cells are counted on its first check, once its neighbours' features are in (see reconcile).
        deposits.setCavernChunk(dimension, chunkX, chunkZ, OilDeposits.pack(level0, OilDeposits.UNCOUNTED));
        if (deposit.centreChunkX() == chunkX && deposit.centreChunkZ() == chunkZ) {
            seep(level, deposit, server.getSeed(), oil);
        }
        return true;
    }

    // The deposit of this chunk's region (caverns stay inside their regions), or null. Cached by region: rolling one
    // samples the biome.
    private static OilDeposits.@Nullable Deposit depositNear(ServerLevel level, int chunkX, int chunkZ) {
        OilDeposits.Deposit test = OilDeposits.testDeposit(level, chunkX, chunkZ);
        if (test != null) {
            return test;
        }
        int size = ArcforgeConfig.OIL_REGION_SIZE.getAsInt();
        int regionX = Math.floorDiv(chunkX, size), regionZ = Math.floorDiv(chunkZ, size);
        Map<Long, java.util.Optional<OilDeposits.Deposit>> regions = REGIONS.computeIfAbsent(level.dimension().identifier().toString(),
                d -> new ConcurrentHashMap<>());
        long key = OilDeposits.chunkKey(regionX, regionZ);
        java.util.Optional<OilDeposits.Deposit> deposit = regions.get(key);
        if (deposit == null) {
            deposit = java.util.Optional.ofNullable(OilDeposits.inRegion(level, regionX, regionZ));
            if (regions.size() > 4_096) {
                regions.clear();
            }
            regions.put(key, deposit);
        }
        return deposit.orElse(null);
    }

    // A small tar pit of oil at the surface over the middle of a Seep or Pocket, under a desert or badlands, by chance.
    private static void seep(WorldGenLevel level, OilDeposits.Deposit deposit, long seed, BlockState oil) {
        if (deposit.kind() != DepositKind.SEEP && deposit.kind() != DepositKind.POCKET) {
            return;
        }
        long roll = seed ^ deposit.key() * 0xD1B54A32D192ED03L ^ 0x7365657073L;
        roll = (roll ^ roll >>> 31) * 0x94D049BB133111EBL;
        if (((roll >>> 11) * 0x1.0p-53) >= ArcforgeConfig.OIL_SEEP_CHANCE.getAsDouble()) {
            return;
        }
        int cx = deposit.centreChunkX() * 16 + 8, cz = deposit.centreChunkZ() * 16 + 8;
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, cx, cz) - 1;
        BlockPos middle = new BlockPos(cx, top, cz);
        var biome = level.getBiome(middle);
        if (!biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_DESERT) && !biome.is(net.minecraft.tags.BiomeTags.IS_BADLANDS)) {
            return;
        }
        BlockState ground = level.getBlockState(middle);
        if (!ground.isSolid() || !ground.getFluidState().isEmpty()) {
            return;
        }
        double rx = 1.6 + ((roll >>> 3) & 7) * 0.15, rz = 1.6 + ((roll >>> 7) & 7) * 0.15;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // The pit: one deep, two in the middle; the ground round it and under it made solid, stained with Coarse Dirt.
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double d = dx * dx / (rx * rx) + dz * dz / (rz * rz);
                int x = cx + dx, z = cz + dz;
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
                if (Math.abs(y - top) > 1) {
                    continue;
                }
                pos.set(x, top, z);
                if (d <= 1.0) {
                    int depth = d < 0.35 ? 2 : 1;
                    for (int k = 0; k < depth; k++) {
                        level.setBlock(pos.set(x, top - k, z), oil, Block.UPDATE_CLIENTS);
                        BlockPos under = pos.below();
                        if (!level.getBlockState(under).isSolid()) {
                            level.setBlock(under, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                    level.setBlock(pos.set(x, top + 1, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                } else if (d <= 2.2) {
                    BlockState rim = level.getBlockState(pos);
                    if (!rim.isSolid() || rim.is(BlockTags.SAND) || ((x * 31 + z * 17) & 3) == 0) {
                        level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    // --- Keeping the blocks in step with the amount ---

    @SubscribeEvent
    static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        OilDeposits deposits = OilDeposits.get(level.getServer());
        if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
            active = deposits;
            CAVERNS.clear();
            REGIONS.clear();
        }
        // Before any of its chunks generate: the first time a dimension loads with caverns, note the chunks it already
        // has, so deposits reaching them stay as they were.
        if (ArcforgeConfig.OIL_DIMENSIONS.get().contains(level.dimension().identifier().toString())) {
            deposits.scanLegacy(level);
        }
    }

    @SubscribeEvent
    static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LOADED.remove(level);
            PENDING.remove(level);
            if (level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                active = null;
            }
        }
    }

    @SubscribeEvent
    static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        ChunkPos at = chunk.getPos();
        Long packed = OilDeposits.get(level.getServer()).cavernChunk(level.dimension().identifier().toString(), at.x(), at.z());
        if (packed != null && packed != OilDeposits.NO_CAVERN) {
            long key = ChunkPos.pack(at.x(), at.z());
            LOADED.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(key);
            // Caught up next tick: buckets taken from it while it was away aren't known, so only the level.
            pending(level).putIfAbsent(key, false);
        }
    }

    @SubscribeEvent
    static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ChunkPos at = event.getChunk().getPos();
            long key = ChunkPos.pack(at.x(), at.z());
            Set<Long> loaded = LOADED.get(level);
            if (loaded != null) {
                loaded.remove(key);
            }
            Map<Long, Boolean> pending = PENDING.get(level);
            if (pending != null) {
                pending.remove(key);
            }
        }
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Set<Long> loaded = LOADED.get(level);
        if (loaded == null || loaded.isEmpty()) {
            return;
        }
        Map<Long, Boolean> pending = pending(level);
        long time = level.getGameTime();
        int interval = ArcforgeConfig.OIL_CAVERN_CHECK_INTERVAL.getAsInt();
        List<long[]> work = new ArrayList<>(BUDGET);
        synchronized (pending) {
            if (time % LEVEL_CHECK_INTERVAL == 0) {
                for (long key : loaded) {
                    // Spread the full checks over the interval, by chunk. A chunk already waiting keeps its place in the
                    // queue; one just checked joins its tail, so every chunk gets its turn however many are loaded.
                    boolean full = Math.floorMod(time + key * 7, interval) < LEVEL_CHECK_INTERVAL;
                    pending.merge(key, full, Boolean::logicalOr);
                }
            }
            var queue = pending.entrySet().iterator();
            while (work.size() < BUDGET && queue.hasNext()) {
                Map.Entry<Long, Boolean> next = queue.next();
                work.add(new long[] { next.getKey(), next.getValue() ? 1 : 0 });
                queue.remove();
            }
        }
        for (long[] next : work) {
            ChunkPos at = ChunkPos.unpack(next[0]);
            // Only chunks that tick: setting blocks in one at the edge of the loaded area could load its neighbours.
            if (level.hasChunk(at.x(), at.z()) && level.shouldTickBlocksAt(new BlockPos(at.x() * 16, level.getMinY(), at.z() * 16))) {
                reconcile(level, at.x(), at.z(), next[1] != 0);
            }
        }
    }

    // The chunks of a level waiting for a check, oldest first.
    private static Map<Long, Boolean> pending(ServerLevel level) {
        return PENDING.computeIfAbsent(level, l -> java.util.Collections.synchronizedMap(new LinkedHashMap<>()));
    }

    // Brings a loaded cavern chunk in step with its deposit: with full (or when its level is about to move), first takes
    // any oil gone from it off the deposit; then lowers (or refills) the oil to the level the deposit's amount now
    // reaches. Public for the GameTests.
    public static void reconcile(ServerLevel level, int chunkX, int chunkZ, boolean full) {
        OilDeposits deposits = OilDeposits.get(level.getServer());
        String dimension = level.dimension().identifier().toString();
        Long packed = deposits.cavernChunk(dimension, chunkX, chunkZ);
        if (packed == null || packed == OilDeposits.NO_CAVERN) {
            return;
        }
        OilDeposits.Deposit deposit = depositNear(level, chunkX, chunkZ);
        OilCavern cavern = deposit != null ? cavern(deposit, level.getSeed()) : null;
        if (cavern == null || !cavern.touches(chunkX, chunkZ)) {
            return;
        }
        int stored = OilDeposits.level(packed), missing = OilDeposits.missing(packed);
        Fluid oil = oil(deposit);
        // Whenever the oil is about to be reset (its level moved) the cells below the old level are counted first, so
        // oil taken out of them is never refilled for free. A chunk's first check only takes the count as it stands
        // (what world generation, its own and its neighbours', left in it), charging nothing.
        boolean moved = cavern.levelFor(deposits.remaining(deposit)) != stored;
        if (full || moved || missing < 0) {
            int[] counts = count(level, cavern, chunkX, chunkZ, stored, oil);
            int gone = counts[0] - counts[1] - missing;
            if (missing >= 0 && gone > 0) {
                deposits.take(deposit, gone * 1_000L);
            }
            if (missing < 0) {
                missing = counts[0] - counts[1];
                deposits.setCavernChunk(dimension, chunkX, chunkZ, OilDeposits.pack(stored, missing));
            }
        }
        int target = cavern.levelFor(deposits.remaining(deposit));
        if (target != stored || full) {
            apply(level, cavern, chunkX, chunkZ, target, oilBlock(deposit), oil);
            int[] counts = count(level, cavern, chunkX, chunkZ, target, oil);
            deposits.setCavernChunk(dimension, chunkX, chunkZ, OilDeposits.pack(target, counts[0] - counts[1]));
        }
    }

    // {cavern cells below level in the chunk, of those the ones holding the deposit's oil source}.
    private static int[] count(ServerLevel level, OilCavern cavern, int chunkX, int chunkZ, int below, Fluid oil) {
        int cells = 0, sources = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
            for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                for (int y = cavern.minY; y < Math.min(below, cavern.maxY + 1); y++) {
                    if (!cavern.inside(x, y, z)) {
                        continue;
                    }
                    cells++;
                    FluidState fluid = level.getFluidState(pos.set(x, y, z));
                    if (fluid.isSource() && fluid.getType().isSame(oil)) {
                        sources++;
                    }
                }
            }
        }
        return new int[] { cells, sources };
    }

    // Oil sources in the cavern's cells below level (where they're empty or hold loose oil), nothing in its oil cells
    // above it (where they hold oil). Other blocks are left as they are.
    private static void apply(ServerLevel level, OilCavern cavern, int chunkX, int chunkZ, int below, BlockState oilBlock, Fluid oil) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = chunkX * 16; x < chunkX * 16 + 16; x++) {
            for (int z = chunkZ * 16; z < chunkZ * 16 + 16; z++) {
                for (int y = cavern.maxY; y >= cavern.minY; y--) {
                    if (!cavern.inside(x, y, z) || !cavern.holdsOil(y)) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    FluidState fluid = state.getFluidState();
                    boolean ours = fluid.getType().isSame(oil);
                    if (y < below) {
                        if (state.isAir() || ours && !fluid.isSource()) {
                            level.setBlock(pos, oilBlock, FLAGS);
                        }
                    } else if (ours) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
                    }
                }
            }
        }
    }

    // For the GameTests: marks a chunk as generated with caverns but holding none, so its deposit shows its sulfur and gas
    // cap.
    public static void markGenerated(ServerLevel level, BlockPos pos) {
        OilDeposits.get(level.getServer()).setCavernChunk(level.dimension().identifier().toString(), pos.getX() >> 4, pos.getZ() >> 4,
                OilDeposits.NO_CAVERN);
    }

    // For the GameTests: gives a test deposit (see OilDeposits.putTestDeposit) this cavern, so the Oil Derrick drills to
    // it, place carves it and reconcile keeps it in step.
    public static void putTestCavern(OilDeposits.Deposit deposit, OilCavern cavern) {
        TEST_CAVERNS.put(deposit.key(), cavern);
    }

    // Takes a test cavern away again, and forgets the chunk at pos was generated with caverns.
    public static void clearTestCavern(ServerLevel level, OilDeposits.Deposit deposit, BlockPos pos) {
        TEST_CAVERNS.remove(deposit.key());
        OilDeposits.get(level.getServer()).clearCavernChunk(level.dimension().identifier().toString(), pos.getX() >> 4, pos.getZ() >> 4);
    }
}
