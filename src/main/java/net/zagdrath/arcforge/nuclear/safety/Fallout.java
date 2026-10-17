/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear.safety;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.nuclear.FalloutBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.WasteLog;
import net.zagdrath.arcforge.registry.ModBlocks;

// Fallout: the fission products a failed reactor lets out, as radioactive dust on the ground. Each chunk keeps how
// contaminated its ground is (GROUND, saved with the chunk), a dose rate in mSv/h at the ground split into a short-lived
// part and a long-lived part (nuclear.failures.falloutLongShare of it), each decaying with its half-life
// (falloutShortHalfLife and falloutLongHalfLife, in in-game days), worked out lazily from when it was last written.
//  - A release (release) inside a sealed containment stays in its air (see Containment.activity). Anywhere else it
//    settles over the chunks within falloutRadius chunks, most near the release and more downwind; Fallout dust
//    blocks settle on the ground of each (falloutBlocks).
//  - Spreading: every minute a share of each contaminated chunk's fallout (falloutSpreadRate) spreads to the four
//    chunks round it, most to the one downwind (the wind: a direction for each in-game day, from the world's seed),
//    settling there as dust too (falloutSpreads). Radioactive blocks loose in the world (Corium, Solidified Corium)
//    seep into their chunk's ground (seep, coriumSeep), unless a sealed containment holds them.
//  - Dust (FalloutBlock) lies in layers like snow, drifted to different heights; a chunk's ground is synced to the
//    players watching it, whose clients show its air glowing with green motes (client.FalloutParticles).
//  - Standing in it: the ground's dose rate, under cover a tenth of it (see ambient and RadiationField), and a share
//    of it picked up on the skin and gear (see Contamination).
// nuclear.failures.fallout turns all of it off.
public final class Fallout {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Arcforge.MODID);

    // A chunk's ground: its short- and long-lived contamination (mSv/h) when last written, and when (game time).
    public record Ground(float shortLived, float longLived, long time) {
        public static final Ground NONE = new Ground(0.0F, 0.0F, 0L);
        public static final Codec<Ground> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("short", 0.0F).forGetter(Ground::shortLived),
                Codec.FLOAT.optionalFieldOf("long", 0.0F).forGetter(Ground::longLived),
                Codec.LONG.optionalFieldOf("time", 0L).forGetter(Ground::time))
                .apply(i, Ground::new));
        public static final StreamCodec<io.netty.buffer.ByteBuf, Ground> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Ground::shortLived,
                ByteBufCodecs.FLOAT, Ground::longLived,
                ByteBufCodecs.VAR_LONG, Ground::time,
                Ground::new);

        public boolean isEmpty() {
            return shortLived <= 0.0F && longLived <= 0.0F;
        }

        // As it is now, decayed from when it was written.
        public Ground at(long now) {
            if (isEmpty() || now <= time) {
                return this;
            }
            double days = (now - time) / 24_000.0;
            double s = shortLived * Math.pow(0.5, days / ArcforgeConfig.FALLOUT_SHORT_HALF_LIFE.getAsDouble());
            double l = longLived * Math.pow(0.5, days / ArcforgeConfig.FALLOUT_LONG_HALF_LIFE.getAsDouble());
            return s + l < 1.0E-3 ? NONE : new Ground((float) s, (float) l, now);
        }

        public double total() {
            return shortLived + longLived;
        }

        Ground plus(double amount, long now) {
            Ground was = at(now);
            double longShare = ArcforgeConfig.FALLOUT_LONG_SHARE.getAsDouble();
            return new Ground((float) (was.shortLived + amount * (1.0 - longShare)), (float) (was.longLived + amount * longShare), now);
        }

        Ground scaled(double factor, long now) {
            Ground was = at(now);
            return new Ground((float) (was.shortLived * factor), (float) (was.longLived * factor), now);
        }
    }

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Ground>> GROUND = ATTACHMENT_TYPES.register("fallout",
            () -> AttachmentType.builder(() -> Ground.NONE)
                    .serialize(Ground.CODEC.fieldOf("fallout"), ground -> !ground.isEmpty())
                    // Every player watching the chunk sees it, for the air's motes (see client.FalloutParticles).
                    .sync((holder, player) -> true, Ground.STREAM_CODEC)
                    .build());

    // Below this a chunk counts as clean (its dust can go).
    public static final double CLEAN = 0.05;
    private static final int SPREAD_INTERVAL = 1_200;
    // Dust deposits on a chunk at once, and the layers one deposit can add.
    private static final int MAX_DUST_PER_CHUNK = 96, MAX_LAYERS_PER_DEPOSIT = 3;
    // The loaded chunks with fallout, per level (what the wind works on).
    private static final Map<Level, LongOpenHashSet> CONTAMINATED = new WeakHashMap<>();

    private Fallout() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(Fallout::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(Fallout::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(Fallout::onLevelTick);
        NeoForge.EVENT_BUS.addListener(Fallout::onLevelUnload);
    }

    public static boolean enabled() {
        return ArcforgeConfig.FALLOUT_ENABLED.getAsBoolean();
    }

    // --- Reading ---

    // A loaded chunk's ground now (NONE for one that isn't loaded).
    public static Ground ground(ServerLevel level, ChunkPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
        return chunk != null ? chunk.getData(GROUND).at(level.getGameTime()) : Ground.NONE;
    }

    // The dose rate (mSv/h) the ground's fallout gives at a point: all of it out in the open, a tenth under cover.
    public static double ambient(ServerLevel level, Vec3 at) {
        if (!enabled()) {
            return 0.0;
        }
        BlockPos pos = BlockPos.containing(at);
        Ground ground = ground(level, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
        if (ground.isEmpty()) {
            return 0.0;
        }
        boolean open = pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) - 1;
        return ground.total() * (open ? 1.0 : 0.1);
    }

    // The wind's direction today (radians, the way it blows: 0 toward +x, pi/2 toward +z).
    public static double wind(ServerLevel level) {
        long day = level.getGameTime() / 24_000L;
        RandomSource random = RandomSource.create(level.getSeed() * 31L + day * 0x9E3779B97F4A7C15L);
        return random.nextDouble() * Math.PI * 2.0;
    }

    // --- Writing ---

    // Fission products let out at a point (amount: the ground dose rate they'd give, mSv/h, summed over the chunks
    // they settle on). In a sealed containment they stay in its air.
    public static void release(ServerLevel level, Vec3 at, double amount) {
        if (!enabled() || amount <= 0.0) {
            return;
        }
        Containment containment = Containments.at(level, BlockPos.containing(at));
        if (containment != null) {
            containment.activity += airDose(containment, amount);
            return;
        }
        settle(level, at, amount);
    }

    // What a release does to a containment's air: the same activity in a closed room of air rather than over a chunk's
    // ground (a small room gets it strongest).
    public static double airDose(Containment containment, double amount) {
        return amount * 64.0 / Math.max(64, containment.airVolume());
    }

    // Spread over the chunks round the point, most at it and downwind.
    public static void settle(ServerLevel level, Vec3 at, double amount) {
        int radius = ArcforgeConfig.FALLOUT_RADIUS.getAsInt();
        int cx = Mth.floor(at.x) >> 4, cz = Mth.floor(at.z) >> 4;
        double wind = wind(level);
        double wx = Math.cos(wind), wz = Math.sin(wind);
        Map<ChunkPos, Double> weights = new HashMap<>();
        double sum = 0.0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double d2 = dx * dx + dz * dz;
                if (d2 > radius * radius + 0.5) {
                    continue;
                }
                double d = Math.sqrt(d2);
                double downwind = d > 0.0 ? Math.max(0.0, (dx * wx + dz * wz) / d) : 1.0;
                double weight = Math.exp(-d2 / Math.max(1.0, radius * radius * 0.5)) * (1.0 + 2.0 * downwind);
                weights.put(new ChunkPos(cx + dx, cz + dz), weight);
                sum += weight;
            }
        }
        for (Map.Entry<ChunkPos, Double> entry : weights.entrySet()) {
            add(level, entry.getKey(), amount * entry.getValue() / sum);
        }
    }

    // Adds contamination to a chunk (loading it), and settles dust on its ground.
    public static void add(ServerLevel level, ChunkPos pos, double amount) {
        if (amount <= 0.0) {
            return;
        }
        LevelChunk chunk = level.getChunk(pos.x(), pos.z());
        long now = level.getGameTime();
        chunk.setData(GROUND, chunk.getData(GROUND).plus(amount, now));
        chunk.markUnsaved();
        CONTAMINATED.computeIfAbsent(level, key -> new LongOpenHashSet()).add(ChunkPos.pack(pos.x(), pos.z()));
        if (ArcforgeConfig.FALLOUT_BLOCKS.getAsBoolean()) {
            dust(level, pos, amount);
        }
    }

    // Fallout dust on the chunk's ground, like snow: a deposit for every 0.7 mSv/h (up to MAX_DUST_PER_CHUNK at once),
    // each one to three layers (more for a heavier fall) on the top surface at a random spot, piling onto dust already
    // there up to a full block, so the ground ends up drifted at different heights.
    private static void dust(ServerLevel level, ChunkPos pos, double amount) {
        RandomSource random = level.getRandom();
        double deposits = amount / 0.7;
        int count = Math.min(MAX_DUST_PER_CHUNK, (int) deposits + (random.nextDouble() < deposits % 1.0 ? 1 : 0));
        int heaviest = (int) Math.max(1, Math.min(MAX_LAYERS_PER_DEPOSIT, Math.round(amount / 8.0)));
        for (int i = 0; i < count; i++) {
            int x = (pos.x() << 4) + random.nextInt(16), z = (pos.z() << 4) + random.nextInt(16);
            settleDust(level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z), 1 + random.nextInt(heaviest));
        }
    }

    // Layers of dust onto the top surface at pos (onto dust already below or there, up to 8 layers).
    private static void settleDust(ServerLevel level, BlockPos pos, int layers) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(ModBlocks.FALLOUT.get()) && below.getValue(FalloutBlock.LAYERS) < FalloutBlock.MAX_LAYERS) {
            pos = pos.below();
        }
        BlockState there = level.getBlockState(pos);
        if (there.is(ModBlocks.FALLOUT.get())) {
            int now = there.getValue(FalloutBlock.LAYERS);
            if (now < FalloutBlock.MAX_LAYERS) {
                level.setBlock(pos, there.setValue(FalloutBlock.LAYERS, Math.min(FalloutBlock.MAX_LAYERS, now + layers)), Block.UPDATE_ALL);
            }
            return;
        }
        BlockState dust = ModBlocks.FALLOUT.get().defaultBlockState().setValue(FalloutBlock.LAYERS, Math.min(FalloutBlock.MAX_LAYERS, layers));
        if (there.isAir() && dust.canSurvive(level, pos)) {
            level.setBlock(pos, dust, Block.UPDATE_ALL);
        }
    }

    // A radioactive block's slow seep (Corium, Solidified Corium) into its chunk's ground: into a sealed containment's
    // air instead, if it's in one. No new dust (it seeps, it doesn't fall).
    public static void seep(ServerLevel level, BlockPos pos, double amount) {
        if (!enabled() || amount <= 0.0) {
            return;
        }
        Containment containment = Containments.at(level, pos);
        if (containment != null) {
            containment.activity += airDose(containment, amount);
            return;
        }
        LevelChunk chunk = level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        chunk.setData(GROUND, chunk.getData(GROUND).plus(amount, level.getGameTime()));
        chunk.markUnsaved();
        CONTAMINATED.computeIfAbsent(level, key -> new LongOpenHashSet()).add(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
    }

    // --- The wind ---

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // The server's clock, for what decays wherever it is (see WasteLog).
        WasteLog.now = level.getGameTime();
        if (level.getGameTime() % SPREAD_INTERVAL != 0) {
            return;
        }
        LongOpenHashSet set = CONTAMINATED.get(level);
        if (set == null || set.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        double wind = wind(level);
        double wx = Math.cos(wind), wz = Math.sin(wind);
        double rate = ArcforgeConfig.FALLOUT_SPREADS.getAsBoolean() ? ArcforgeConfig.FALLOUT_SPREAD_RATE.getAsDouble() : 0.0;
        // Each side's share of what spreads: the downwind side three times a still day's share, the upwind side least.
        int[][] sides = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        double[] share = new double[4];
        double sum = 0.0;
        for (int i = 0; i < 4; i++) {
            share[i] = 1.0 + 2.0 * Math.max(0.0, sides[i][0] * wx + sides[i][1] * wz);
            sum += share[i];
        }
        for (long key : set.toLongArray()) {
            ChunkPos at = ChunkPos.unpack(key);
            int x = at.x(), z = at.z();
            LevelChunk chunk = level.getChunkSource().getChunkNow(x, z);
            if (chunk == null) {
                set.remove(key);
                continue;
            }
            Ground ground = chunk.getData(GROUND).at(now);
            if (ground.total() < CLEAN) {
                chunk.setData(GROUND, Ground.NONE);
                chunk.markUnsaved();
                set.remove(key);
                continue;
            }
            if (rate <= 0.0) {
                // Still written back decayed, so the clients' motes follow it.
                chunk.setData(GROUND, ground);
                continue;
            }
            double spread = ground.total() * rate, moved = 0.0;
            for (int i = 0; i < 4; i++) {
                LevelChunk side = level.getChunkSource().getChunkNow(x + sides[i][0], z + sides[i][1]);
                if (side == null) {
                    continue;
                }
                double part = spread * share[i] / sum;
                side.setData(GROUND, side.getData(GROUND).plus(part, now));
                side.markUnsaved();
                set.add(ChunkPos.pack(x + sides[i][0], z + sides[i][1]));
                moved += part;
                if (ArcforgeConfig.FALLOUT_BLOCKS.getAsBoolean() && part >= CLEAN && level.getRandom().nextDouble() < Math.min(1.0, part)) {
                    dust(level, new ChunkPos(x + sides[i][0], z + sides[i][1]), Math.max(0.7, part));
                }
            }
            chunk.setData(GROUND, ground.scaled(1.0 - moved / ground.total(), now));
            chunk.markUnsaved();
        }
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk && chunk.hasData(GROUND)
                && !chunk.getData(GROUND).isEmpty()) {
            CONTAMINATED.computeIfAbsent(level, key -> new LongOpenHashSet()).add(ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z()));
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LongOpenHashSet set = CONTAMINATED.get(level);
            if (set != null) {
                set.remove(ChunkPos.pack(event.getChunk().getPos().x(), event.getChunk().getPos().z()));
            }
        }
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            CONTAMINATED.remove(level);
            Containments.forget(level);
        }
    }
}
