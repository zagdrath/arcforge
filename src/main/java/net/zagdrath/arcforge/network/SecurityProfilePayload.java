/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.security.SecurityMode;

// Server to client: the viewer's own security profile, for the Security Terminal screen, sent when it opens and
// after each edit. error/errorArg explain a refused edit ("" when there's none).
public record SecurityProfilePayload(SecurityMode defaultMode, List<Entry> trusted, boolean enabled, String error, String errorArg)
        implements CustomPacketPayload {
    public record Entry(UUID id, String name) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Entry::id,
                ByteBufCodecs.STRING_UTF8, Entry::name,
                Entry::new);
    }

    public static final Type<SecurityProfilePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "security_profile"));
    public static final StreamCodec<ByteBuf, SecurityProfilePayload> STREAM_CODEC = StreamCodec.composite(
            SecurityMode.STREAM_CODEC, SecurityProfilePayload::defaultMode,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), SecurityProfilePayload::trusted,
            ByteBufCodecs.BOOL, SecurityProfilePayload::enabled,
            ByteBufCodecs.STRING_UTF8, SecurityProfilePayload::error,
            ByteBufCodecs.STRING_UTF8, SecurityProfilePayload::errorArg,
            SecurityProfilePayload::new);

    // The latest received, read by the terminal screen (client only).
    public static SecurityProfilePayload latest = new SecurityProfilePayload(SecurityMode.PUBLIC, List.of(), true, "", "");
    public static Consumer<SecurityProfilePayload> clientHandler = payload -> latest = payload;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SecurityProfilePayload payload, IPayloadContext context) {
        clientHandler.accept(payload);
    }
}
