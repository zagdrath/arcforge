/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import java.util.Optional;
import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.security.SecurityMode;

// Server to client, when a machine's menu opens and after its security changes: who owns it, the owner's profile
// mode, the block's override (if any), whether this player may change it, and whether security is on at all.
public record SecuritySyncPayload(int containerId, boolean owned, String ownerName, SecurityMode profileMode, Optional<SecurityMode> override,
        boolean canEdit, boolean enabled) implements CustomPacketPayload {
    public static final Type<SecuritySyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "security_sync"));
    public static final StreamCodec<ByteBuf, SecuritySyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SecuritySyncPayload::containerId,
            ByteBufCodecs.BOOL, SecuritySyncPayload::owned,
            ByteBufCodecs.STRING_UTF8, SecuritySyncPayload::ownerName,
            SecurityMode.STREAM_CODEC, SecuritySyncPayload::profileMode,
            ByteBufCodecs.optional(SecurityMode.STREAM_CODEC), SecuritySyncPayload::override,
            ByteBufCodecs.BOOL, SecuritySyncPayload::canEdit,
            ByteBufCodecs.BOOL, SecuritySyncPayload::enabled,
            SecuritySyncPayload::new);

    // The latest for the open menu, read by the Security tab (client only).
    public static SecuritySyncPayload latest = new SecuritySyncPayload(-1, false, "", SecurityMode.PUBLIC, Optional.empty(), false, false);
    public static Consumer<SecuritySyncPayload> clientHandler = payload -> latest = payload;

    public SecurityMode effectiveMode() {
        return override.orElse(profileMode);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SecuritySyncPayload payload, IPayloadContext context) {
        clientHandler.accept(payload);
    }
}
