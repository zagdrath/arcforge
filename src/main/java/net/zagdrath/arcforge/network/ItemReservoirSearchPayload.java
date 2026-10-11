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
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.menu.storage.ItemReservoirMenu;

// Client to server: the text in an Item Reservoir GUI's search box. The server's window then shows only the slots whose
// item matches it, from the top.
public record ItemReservoirSearchPayload(int containerId, String query) implements CustomPacketPayload {
    public static final Type<ItemReservoirSearchPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "item_reservoir_search"));
    public static final StreamCodec<ByteBuf, ItemReservoirSearchPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ItemReservoirSearchPayload::containerId,
            ByteBufCodecs.stringUtf8(ItemReservoirMenu.MAX_QUERY), ItemReservoirSearchPayload::query,
            ItemReservoirSearchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ItemReservoirSearchPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.containerMenu instanceof ItemReservoirMenu menu && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            menu.setQuery(payload.query());
        }
    }
}
