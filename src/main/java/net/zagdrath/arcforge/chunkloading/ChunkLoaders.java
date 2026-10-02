/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.chunkloading;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.registry.ModChunkLoading;

// Every Chunk Loader on the server, by dimension and position: its owner and the chunks it has forced (as chunk
// tickets of ModChunkLoading.LOADER held in its name). This is what the per-player limit counts, what the ticket
// check at level load keeps tickets for, and what lets a returning owner's loaders start again while their chunks are
// unloaded (with chunkLoader.requireOwnerOnline).
public class ChunkLoaders extends SavedData {
    public record Entry(GlobalPos pos, UUID owner, List<Long> chunks) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                GlobalPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                UUIDUtil.CODEC.fieldOf("owner").forGetter(Entry::owner),
                Codec.LONG.listOf().optionalFieldOf("chunks", List.of()).forGetter(Entry::chunks))
                .apply(i, Entry::new));
    }

    private static final Codec<ChunkLoaders> CODEC = Entry.CODEC.listOf()
            .xmap(ChunkLoaders::new, loaders -> List.copyOf(loaders.entries.values()));
    public static final SavedDataType<ChunkLoaders> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "chunk_loaders"), ChunkLoaders::new, CODEC);

    private final Map<GlobalPos, Entry> entries = new HashMap<>();

    public ChunkLoaders() {
        this(List.of());
    }

    private ChunkLoaders(List<Entry> saved) {
        saved.forEach(entry -> entries.put(entry.pos(), entry));
    }

    public static ChunkLoaders get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    public @Nullable Entry get(GlobalPos pos) {
        return entries.get(pos);
    }

    public void put(GlobalPos pos, UUID owner, List<Long> chunks) {
        Entry entry = new Entry(pos, owner, List.copyOf(chunks));
        if (!entry.equals(entries.get(pos))) {
            entries.put(pos, entry);
            setDirty();
        }
    }

    public void remove(GlobalPos pos) {
        if (entries.remove(pos) != null) {
            setDirty();
        }
    }

    // Chunks a player's loaders hold, leaving one loader out (the one asking).
    public int chunksOf(UUID owner, @Nullable GlobalPos except) {
        int count = 0;
        for (Entry entry : entries.values()) {
            if (entry.owner().equals(owner) && !entry.pos().equals(except)) {
                count += entry.chunks().size();
            }
        }
        return count;
    }

    public List<Entry> ownedBy(UUID owner) {
        List<Entry> owned = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (entry.owner().equals(owner)) {
                owned.add(entry);
            }
        }
        return owned;
    }

    // Drops every ticket a player's loaders hold (the owner logged out with requireOwnerOnline on); their block entities
    // see the owner is away and keep them dropped.
    public void release(MinecraftServer server, UUID owner) {
        for (Entry entry : ownedBy(owner)) {
            ServerLevel level = server.getLevel(entry.pos().dimension());
            if (level != null) {
                for (long chunk : entry.chunks()) {
                    ChunkPos at = ChunkPos.unpack(chunk);
                    ModChunkLoading.LOADER.forceChunk(level, entry.pos().pos(), at.x(), at.z(), false, true);
                }
            }
            put(entry.pos(), owner, List.of());
        }
    }

    // Loads the chunk of each of a player's loaders again (they logged back in); each loader's block entity then takes
    // the rest of its area itself.
    public void wake(MinecraftServer server, UUID owner) {
        for (Entry entry : ownedBy(owner)) {
            ServerLevel level = server.getLevel(entry.pos().dimension());
            if (level != null) {
                long chunk = ChunkPos.pack(entry.pos().pos());
                ModChunkLoading.LOADER.forceChunk(level, entry.pos().pos(), ChunkPos.unpack(chunk).x(), ChunkPos.unpack(chunk).z(), true, true);
                put(entry.pos(), owner, List.of(chunk));
            }
        }
    }
}
