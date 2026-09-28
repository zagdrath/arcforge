/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentSyncHandler;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.zagdrath.arcforge.Arcforge;

// Where multiblock ports are kept: per chunk, by block position, a mode for each face (see PortFaces). They're
// kept by position rather than in the block, so a port stays where it was set when its block is broken and
// put back, or the structure breaks and forms again; they only count on a block that can hold ports in a
// formed structure (see MultiblockPorts). The data is saved with the chunk and synced to the players who can
// see it. Clients keep a copy of it that chunk meshing (off the main thread) can read, and re-mesh the blocks
// whose ports change.
public final class PortStore {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Arcforge.MODID);

    private static final Codec<Map<BlockPos, PortFaces>> MAP_CODEC = Codec.list(Entry.CODEC)
            .xmap(entries -> {
                Map<BlockPos, PortFaces> map = new HashMap<>();
                entries.forEach(entry -> map.put(entry.pos(), entry.faces()));
                return Map.copyOf(map);
            }, map -> map.entrySet().stream().map(entry -> new Entry(entry.getKey(), entry.getValue())).toList());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Map<BlockPos, PortFaces>>> PORTS = ATTACHMENT_TYPES.register("ports",
            () -> AttachmentType.<Map<BlockPos, PortFaces>>builder(() -> Map.of())
                    .serialize(MAP_CODEC.fieldOf("ports"), map -> !map.isEmpty())
                    .sync(new SyncHandler())
                    .build());

    // Client side: each chunk's ports, by chunk, for meshing to read.
    private static final Map<Long, Map<BlockPos, PortFaces>> CLIENT = new ConcurrentHashMap<>();

    // Client side: re-meshes blocks whose ports changed. The client sets it.
    public static Consumer<Set<BlockPos>> clientRemesh = positions -> {};

    private record Entry(BlockPos pos, PortFaces faces) {
        static final Codec<Entry> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                PortFaces.CODEC.fieldOf("faces").forGetter(Entry::faces)).apply(instance, Entry::new));
    }

    private PortStore() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(PortStore::onChunkUnload);
    }

    // The ports on the block at pos, on a server level or (from the synced copy) a client's, including while
    // its chunk meshes.
    public static PortFaces get(BlockGetter level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            if (!server.isLoaded(pos)) {
                return PortFaces.NONE;
            }
            return server.getChunkAt(pos).getData(PORTS).getOrDefault(pos, PortFaces.NONE);
        }
        Map<BlockPos, PortFaces> chunk = CLIENT.get(ChunkPos.pack(pos));
        return chunk != null ? chunk.getOrDefault(pos, PortFaces.NONE) : PortFaces.NONE;
    }

    // Server side: sets the ports on the block at pos (and syncs them). Returns whether they changed.
    public static boolean set(Level level, BlockPos pos, PortFaces faces) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        LevelChunk chunk = server.getChunkAt(pos);
        Map<BlockPos, PortFaces> old = chunk.getData(PORTS);
        if (old.getOrDefault(pos, PortFaces.NONE).equals(faces)) {
            return false;
        }
        Map<BlockPos, PortFaces> updated = new HashMap<>(old);
        if (faces.isEmpty()) {
            updated.remove(pos);
        } else {
            updated.put(pos.immutable(), faces);
        }
        chunk.setData(PORTS, Map.copyOf(updated));
        chunk.markUnsaved();
        return true;
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            CLIENT.remove(event.getChunk().getPos().pack());
        }
    }

    // Client side: forget everything (on leaving a world).
    public static void clearClient() {
        CLIENT.clear();
    }

    // Sends a chunk's whole port map; clients keep a copy and re-mesh what changed.
    private static final class SyncHandler implements AttachmentSyncHandler<Map<BlockPos, PortFaces>> {
        @Override
        public void write(RegistryFriendlyByteBuf buf, Map<BlockPos, PortFaces> ports, boolean initialSync) {
            buf.writeVarInt(ports.size());
            ports.forEach((pos, faces) -> {
                buf.writeBlockPos(pos);
                PortFaces.STREAM_CODEC.encode(buf, faces);
            });
        }

        @Override
        public @Nullable Map<BlockPos, PortFaces> read(IAttachmentHolder holder, RegistryFriendlyByteBuf buf, @Nullable Map<BlockPos, PortFaces> previous) {
            int size = buf.readVarInt();
            Map<BlockPos, PortFaces> ports = new HashMap<>();
            for (int i = 0; i < size; i++) {
                ports.put(buf.readBlockPos(), PortFaces.STREAM_CODEC.decode(buf));
            }
            Map<BlockPos, PortFaces> copy = Map.copyOf(ports);
            if (holder instanceof LevelChunk chunk) {
                CLIENT.put(chunk.getPos().pack(), copy);
                Set<BlockPos> changed = new HashSet<>();
                Map<BlockPos, PortFaces> before = previous != null ? previous : Map.of();
                before.forEach((pos, faces) -> {
                    if (!faces.equals(copy.get(pos))) {
                        changed.add(pos);
                    }
                });
                copy.forEach((pos, faces) -> {
                    if (!faces.equals(before.get(pos))) {
                        changed.add(pos);
                    }
                });
                if (!changed.isEmpty()) {
                    clientRemesh.accept(changed);
                }
            }
            return copy;
        }
    }
}
