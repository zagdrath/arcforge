/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.JetpackFlight;
import net.zagdrath.arcforge.item.tool.JetpackItem;
import net.zagdrath.arcforge.item.tool.JetpackMode;
import net.zagdrath.arcforge.registry.ModDataComponents;

// Client to server: the Jetpack Mode key was pressed. Cycles the worn jetpack Normal, Hover, Off.
public record JetpackModePayload() implements CustomPacketPayload {
    public static final Type<JetpackModePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "jetpack_mode"));
    public static final StreamCodec<ByteBuf, JetpackModePayload> STREAM_CODEC = StreamCodec.unit(new JetpackModePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(JetpackModePayload payload, IPayloadContext context) {
        cycle(context.player());
    }

    public static void cycle(Player player) {
        ItemStack stack = JetpackFlight.worn(player);
        if (stack.isEmpty()) {
            return;
        }
        JetpackMode mode = JetpackItem.mode(stack).next();
        stack.set(ModDataComponents.JETPACK_MODE.get(), mode);
        player.sendOverlayMessage(Component.translatable("message.arcforge.jetpack.mode", mode.displayName()));
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.4F, 1.0F);
    }
}
