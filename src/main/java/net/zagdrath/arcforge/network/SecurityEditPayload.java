/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import java.util.Optional;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.menu.machine.SecurityTerminalMenu;
import net.zagdrath.arcforge.security.SecurityMode;

// Client to server, from the Security Terminal screen: set my default mode, trust a player by name, or stop
// trusting one. Only applies with the terminal's menu open, and only to the sender's own profile.
public record SecurityEditPayload(Action action, SecurityMode mode, String name, Optional<UUID> player) implements CustomPacketPayload {
    public enum Action {
        SET_MODE, ADD, REMOVE;

        static final StreamCodec<ByteBuf, Action> STREAM_CODEC = ByteBufCodecs.idMapper(id -> values()[id], Action::ordinal);
    }

    public static final Type<SecurityEditPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "security_edit"));
    public static final StreamCodec<ByteBuf, SecurityEditPayload> STREAM_CODEC = StreamCodec.composite(
            Action.STREAM_CODEC, SecurityEditPayload::action,
            SecurityMode.STREAM_CODEC, SecurityEditPayload::mode,
            ByteBufCodecs.stringUtf8(16), SecurityEditPayload::name,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), SecurityEditPayload::player,
            SecurityEditPayload::new);

    public static SecurityEditPayload setMode(SecurityMode mode) {
        return new SecurityEditPayload(Action.SET_MODE, mode, "", Optional.empty());
    }

    public static SecurityEditPayload add(String name) {
        return new SecurityEditPayload(Action.ADD, SecurityMode.PUBLIC, name, Optional.empty());
    }

    public static SecurityEditPayload remove(UUID player) {
        return new SecurityEditPayload(Action.REMOVE, SecurityMode.PUBLIC, "", Optional.of(player));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SecurityEditPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof SecurityTerminalMenu menu) {
            apply(player, menu, payload);
        }
    }

    public static void apply(ServerPlayer player, SecurityTerminalMenu menu, SecurityEditPayload payload) {
        switch (payload.action()) {
            case SET_MODE -> menu.setMode(player, payload.mode());
            case ADD -> menu.add(player, payload.name());
            case REMOVE -> payload.player().ifPresent(id -> menu.remove(player, id));
        }
    }
}
