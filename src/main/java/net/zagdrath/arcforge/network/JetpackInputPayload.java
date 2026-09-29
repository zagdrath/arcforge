/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.JetpackFlight;

// Client to server: whether the jump and sneak keys are held, sent while a Jetpack is worn (see JetpackFlight).
public record JetpackInputPayload(boolean jump, boolean sneak) implements CustomPacketPayload {
    public static final Type<JetpackInputPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "jetpack_input"));
    public static final StreamCodec<ByteBuf, JetpackInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, JetpackInputPayload::jump,
            ByteBufCodecs.BOOL, JetpackInputPayload::sneak,
            JetpackInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(JetpackInputPayload payload, IPayloadContext context) {
        JetpackFlight.setInput(context.player(), payload.jump(), payload.sneak());
    }
}
