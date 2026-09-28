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
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.menu.machine.ArcQuarryConfigMenu;

// Client to server: the Arc Quarry settings screen's radius and Y range. The server clamps them (see
// QuarrySettings.withArea) and syncs the result back.
public record ArcQuarryAreaPayload(int containerId, int radius, int minY, int maxY) implements CustomPacketPayload {
    public static final Type<ArcQuarryAreaPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "arc_quarry_area"));
    public static final StreamCodec<ByteBuf, ArcQuarryAreaPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ArcQuarryAreaPayload::containerId,
            ByteBufCodecs.VAR_INT, ArcQuarryAreaPayload::radius,
            ByteBufCodecs.INT, ArcQuarryAreaPayload::minY,
            ByteBufCodecs.INT, ArcQuarryAreaPayload::maxY,
            ArcQuarryAreaPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ArcQuarryAreaPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    // Applies it if the player still has that quarry's settings open. Returns whether it did.
    public static boolean apply(Player player, ArcQuarryAreaPayload payload) {
        if (player.containerMenu instanceof ArcQuarryConfigMenu menu && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            ArcQuarryBlockEntity quarry = menu.quarry(player);
            if (quarry != null) {
                quarry.setSettings(quarry.getSettings().withArea(payload.radius(), payload.minY(), payload.maxY(), player.level()));
                return true;
            }
        }
        return false;
    }
}
