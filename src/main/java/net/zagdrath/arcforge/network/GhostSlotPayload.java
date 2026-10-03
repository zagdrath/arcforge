/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.menu.GhostSlotMenu;

// Client to server: an item, or a fluid or gas, dragged from JEI onto a ghost slot of the open menu (GhostSlotMenu).
// The menu buttons that set ghost slots read the item on the cursor, which a JEI drag never puts there, so the
// ingredient travels here instead and the menu applies it as that click would.
public record GhostSlotPayload(int containerId, int slot, ItemStack item, Optional<Fluid> fluid) implements CustomPacketPayload {
    public static final Type<GhostSlotPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "ghost_slot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GhostSlotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GhostSlotPayload::containerId,
            ByteBufCodecs.VAR_INT, GhostSlotPayload::slot,
            ItemStack.OPTIONAL_STREAM_CODEC, GhostSlotPayload::item,
            ByteBufCodecs.optional(ByteBufCodecs.registry(Registries.FLUID)), GhostSlotPayload::fluid,
            GhostSlotPayload::new);

    public static GhostSlotPayload item(int containerId, int slot, ItemStack stack) {
        return new GhostSlotPayload(containerId, slot, stack.copyWithCount(1), Optional.empty());
    }

    public static GhostSlotPayload fluid(int containerId, int slot, Fluid fluid) {
        return new GhostSlotPayload(containerId, slot, ItemStack.EMPTY, Optional.of(fluid));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(GhostSlotPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    // Sets the slot if the player still has that menu open and the slot takes what was dropped. Returns whether it did.
    public static boolean apply(Player player, GhostSlotPayload payload) {
        AbstractContainerMenu open = player.containerMenu;
        if (!(open instanceof GhostSlotMenu menu) || open.containerId != payload.containerId() || !open.stillValid(player)
                || payload.slot() < 0 || payload.slot() >= menu.ghostSlotCount()) {
            return false;
        }
        if (payload.fluid().isPresent()) {
            Fluid fluid = payload.fluid().get();
            return menu.acceptsGhostFluid(payload.slot(), fluid) && menu.setGhostFluid(player, payload.slot(), fluid);
        }
        ItemStack stack = payload.item();
        return !stack.isEmpty() && menu.acceptsGhostItem(payload.slot(), stack) && menu.setGhostItem(player, payload.slot(), stack);
    }
}
