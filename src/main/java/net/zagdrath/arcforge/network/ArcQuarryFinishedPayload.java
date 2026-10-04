/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import java.util.function.Consumer;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;

// Server to the player who placed an Arc Quarry: it has finished, at pos, having mined `mined` blocks. The client shows a
// toast unless its notifications.arcQuarryFinished setting is off.
public record ArcQuarryFinishedPayload(BlockPos pos, int mined) implements CustomPacketPayload {
    public static final Type<ArcQuarryFinishedPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "arc_quarry_finished"));
    public static final StreamCodec<ByteBuf, ArcQuarryFinishedPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ArcQuarryFinishedPayload::pos,
            ByteBufCodecs.VAR_INT, ArcQuarryFinishedPayload::mined,
            ArcQuarryFinishedPayload::new);

    // Set by the client, where the toast lives.
    public static Consumer<ArcQuarryFinishedPayload> clientHandler = payload -> {};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ArcQuarryFinishedPayload payload, IPayloadContext context) {
        clientHandler.accept(payload);
    }
}
