/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.AssemblerBlockEntity;
import net.zagdrath.arcforge.menu.machine.AssemblerMenu;

// Client to server: JEI's + on a crafting recipe, with an Assembler open. Sets its pattern to these nine ghost
// items (row by row); no real items move.
public record AssemblerPatternPayload(int containerId, List<ItemStack> cells) implements CustomPacketPayload {
    public static final Type<AssemblerPatternPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "assembler_pattern"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblerPatternPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AssemblerPatternPayload::containerId,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(AssemblerBlockEntity.PATTERN_SIZE)), AssemblerPatternPayload::cells,
            AssemblerPatternPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(AssemblerPatternPayload payload, IPayloadContext context) {
        apply(context.player(), payload);
    }

    // Sets the pattern if the player still has that Assembler open. Returns whether it did.
    public static boolean apply(Player player, AssemblerPatternPayload payload) {
        if (player.containerMenu instanceof AssemblerMenu menu && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            menu.setPattern(payload.cells());
            return true;
        }
        return false;
    }
}
