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
import net.zagdrath.arcforge.item.tool.ArcToolItem;
import net.zagdrath.arcforge.item.tool.ToolModules;
import net.zagdrath.arcforge.menu.tool.ArcToolMenu;
import net.zagdrath.arcforge.registry.ModDataComponents;

// Client to server: sneak + scroll with an Arc tool in hand. Steps to the next (or previous) installed module and
// flips it, then shows every module's state on the action bar.
public record ToolModuleScrollPayload(int delta) implements CustomPacketPayload {
    public static final Type<ToolModuleScrollPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Arcforge.MODID, "tool_module_scroll"));
    public static final StreamCodec<ByteBuf, ToolModuleScrollPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ToolModuleScrollPayload::new, ToolModuleScrollPayload::delta);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(ToolModuleScrollPayload payload, IPayloadContext context) {
        scroll(context.player(), payload.delta());
    }

    public static void scroll(Player player, int delta) {
        ItemStack stack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!(stack.getItem() instanceof ArcToolItem tool) || delta == 0) {
            return;
        }
        ToolModules modules = ArcToolItem.modules(stack);
        int index = modules.step(delta, tool.moduleSlots());
        if (index < 0) {
            player.sendOverlayMessage(Component.translatable("tooltip.arcforge.arc_tool.no_modules"));
            return;
        }
        ToolModules toggled = modules.toggle(index);
        stack.set(ModDataComponents.TOOL_MODULES.get(), toggled);
        if (modules.turnedOffBy(toggled, index).isPresent()) {
            ArcToolMenu.announce(player, modules, toggled, index);
            return;
        }
        player.sendOverlayMessage(Component.translatable(toggled.isOn(index) ? "message.arcforge.arc_tool.module_on" : "message.arcforge.arc_tool.module_off",
                toggled.slot(index).get().displayName()).append(" · ").append(ArcToolItem.moduleList(toggled, tool.moduleSlots())));
    }
}
