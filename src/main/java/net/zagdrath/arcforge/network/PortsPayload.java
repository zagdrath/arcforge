/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;

// Server to client: the ports of the multiblock whose menu is open (see PortSync), for its Ports tab.
public record PortsPayload(int containerId, List<Entry> ports) implements CustomPacketPayload {
    public static final Type<PortsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "ports"));

    // A port: its mode, the side of the structure it's on, and where it is from the controller.
    public record Entry(SideMode mode, RelativeSide side, BlockPos offset) {
        static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(SideMode::byId, SideMode::ordinal), Entry::mode,
                ByteBufCodecs.idMapper(RelativeSide::byId, RelativeSide::ordinal), Entry::side,
                BlockPos.STREAM_CODEC, Entry::offset,
                Entry::new);
    }

    public static final StreamCodec<ByteBuf, PortsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PortsPayload::containerId,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), PortsPayload::ports,
            PortsPayload::new);

    // Client side: the last list received.
    private static volatile @Nullable PortsPayload latest;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // The ports of the structure behind the open menu, if the server has sent them.
    public static List<Entry> forMenu(int containerId) {
        PortsPayload last = latest;
        return last != null && last.containerId() == containerId ? last.ports() : List.of();
    }

    static void handle(PortsPayload payload, IPayloadContext context) {
        latest = payload;
    }
}
