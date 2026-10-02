/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.quantum.Frequency;
import net.zagdrath.arcforge.security.SecurityRules;

// Server to client: the frequencies the viewer may use and the one the open Quantum Tunnel is on, for its screen. error
// is a translation key explaining a refused request ("" for none).
public record QuantumStatePayload(int containerId, Optional<Entry> current, List<Entry> frequencies, String error) implements CustomPacketPayload {
    // A frequency as the viewer sees it: its name, its owner if private, who made it, and whether the viewer may delete it.
    public record Entry(String name, Optional<UUID> owner, String creatorName, boolean deletable) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(Frequency.MAX_NAME_LENGTH * 4), Entry::name,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Entry::owner,
                ByteBufCodecs.STRING_UTF8, Entry::creatorName,
                ByteBufCodecs.BOOL, Entry::deletable,
                Entry::new);

        public static Entry of(Frequency frequency, ServerPlayer viewer) {
            return new Entry(frequency.key().name(), frequency.key().owner(), frequency.creatorName(),
                    frequency.creator().equals(viewer.getUUID()) || SecurityRules.isOp(viewer));
        }

        public boolean isPrivate() {
            return owner.isPresent();
        }

        public boolean is(Entry other) {
            return name.equals(other.name) && owner.equals(other.owner);
        }
    }

    public static final Type<QuantumStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "quantum_state"));
    public static final StreamCodec<ByteBuf, QuantumStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuantumStatePayload::containerId,
            ByteBufCodecs.optional(Entry.STREAM_CODEC), QuantumStatePayload::current,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), QuantumStatePayload::frequencies,
            ByteBufCodecs.STRING_UTF8, QuantumStatePayload::error,
            QuantumStatePayload::new);

    // The latest received, read by the tunnel screen (client only).
    public static QuantumStatePayload latest = new QuantumStatePayload(-1, Optional.empty(), List.of(), "");
    public static Consumer<QuantumStatePayload> clientHandler = payload -> latest = payload;

    // The same list and current frequency (the error doesn't count).
    public boolean sameAs(QuantumStatePayload other) {
        return containerId == other.containerId && current.equals(other.current) && frequencies.equals(other.frequencies);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(QuantumStatePayload payload, IPayloadContext context) {
        clientHandler.accept(payload);
    }
}
