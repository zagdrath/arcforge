/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.ElectricEnchanterBlockEntity;
import net.zagdrath.arcforge.menu.machine.ElectricEnchanterMenu;

// Client to server: an Electric Enchanter's GUI setting one target enchantment's level (0 clears it).
public record EnchanterTargetPayload(int containerId, Identifier enchantment, int level) implements CustomPacketPayload {
    public static final Type<EnchanterTargetPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "enchanter_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EnchanterTargetPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, EnchanterTargetPayload::containerId,
            Identifier.STREAM_CODEC, EnchanterTargetPayload::enchantment,
            ByteBufCodecs.VAR_INT, EnchanterTargetPayload::level,
            EnchanterTargetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(EnchanterTargetPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    // Sets the target if the player still has that Enchanter open. Returns whether it did.
    public static boolean apply(Player player, EnchanterTargetPayload payload) {
        if (player.containerMenu instanceof ElectricEnchanterMenu menu && menu.containerId == payload.containerId() && menu.stillValid(player)
                && player.level().getBlockEntity(menu.getPos()) instanceof ElectricEnchanterBlockEntity enchanter) {
            return enchanter.setTarget(ResourceKey.create(Registries.ENCHANTMENT, payload.enchantment()), payload.level());
        }
        return false;
    }
}
