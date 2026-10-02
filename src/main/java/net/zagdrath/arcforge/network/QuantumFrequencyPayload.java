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
import net.zagdrath.arcforge.menu.logistics.QuantumTunnelMenu;
import net.zagdrath.arcforge.quantum.Frequency;

// Client to server, from an open Quantum Tunnel screen: put the tunnel on a frequency (SELECT, by name and owner; an
// empty owner is a public one), make one and put it on it (CREATE; a present owner makes it private to the sender),
// delete one the sender made (DELETE), or take it off its frequency (CLEAR). The server answers with the new state.
public record QuantumFrequencyPayload(int containerId, Action action, String name, Optional<UUID> owner) implements CustomPacketPayload {
    public enum Action { SELECT, CREATE, DELETE, CLEAR }

    public static final Type<QuantumFrequencyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "quantum_frequency"));
    public static final StreamCodec<ByteBuf, QuantumFrequencyPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuantumFrequencyPayload::containerId,
            ByteBufCodecs.idMapper(index -> Action.values()[index], Action::ordinal), QuantumFrequencyPayload::action,
            ByteBufCodecs.stringUtf8(Frequency.MAX_NAME_LENGTH * 4), QuantumFrequencyPayload::name,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), QuantumFrequencyPayload::owner,
            QuantumFrequencyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(QuantumFrequencyPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            apply(player, payload);
        }
    }

    // Applies the request if the player still has that tunnel's screen open, then sends them the result.
    public static String apply(ServerPlayer player, QuantumFrequencyPayload payload) {
        if (!(player.containerMenu instanceof QuantumTunnelMenu menu) || menu.containerId != payload.containerId() || !menu.stillValid(player)) {
            return "";
        }
        String error = menu.apply(player, payload);
        menu.sendState(player, error);
        return error;
    }
}
