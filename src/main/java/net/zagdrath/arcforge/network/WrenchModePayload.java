/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.WrenchItem;
import net.zagdrath.arcforge.item.tool.WrenchMode;
import net.zagdrath.arcforge.registry.ModDataComponents;

// Client to server: the player scrolled with Shift held and the Wrench in hand (see WrenchScrollHandler);
// its mode steps by direction (+1 or -1).
public record WrenchModePayload(int direction) implements CustomPacketPayload {
    public static final Type<WrenchModePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "wrench_mode"));
    public static final StreamCodec<ByteBuf, WrenchModePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(WrenchModePayload::new, WrenchModePayload::direction);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(WrenchModePayload payload, IPayloadContext context) {
        Player player = context.player();
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof WrenchItem) || payload.direction() == 0) {
            return;
        }
        WrenchMode mode = WrenchItem.mode(stack).cycle(Integer.signum(payload.direction()));
        stack.set(ModDataComponents.WRENCH_MODE.get(), mode);
        player.sendOverlayMessage(Component.translatable("message.arcforge.wrench.mode", mode.displayName()));
    }
}
