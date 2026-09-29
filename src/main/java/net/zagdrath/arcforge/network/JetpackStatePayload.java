/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;

// Server to the player and those tracking them: how hard their Jetpack fires (0 to 1) and on what (0 nothing,
// 1 steam, 2 flame), for the thrust sound and exhaust (see JetpackEffects).
public record JetpackStatePayload(int entityId, float output, byte exhaust) implements CustomPacketPayload {
    public static final Type<JetpackStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "jetpack_state"));
    public static final StreamCodec<ByteBuf, JetpackStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, JetpackStatePayload::entityId,
            ByteBufCodecs.FLOAT, JetpackStatePayload::output,
            ByteBufCodecs.BYTE, JetpackStatePayload::exhaust,
            JetpackStatePayload::new);

    // Set by the client, where the effects live.
    public static Consumer<JetpackStatePayload> clientHandler = payload -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(JetpackStatePayload payload, IPayloadContext context) {
        clientHandler.accept(payload);
    }
}
