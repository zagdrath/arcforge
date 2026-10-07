/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.WrenchItem;
import net.zagdrath.arcforge.network.WrenchModePayload;

// Shift + mouse wheel with the Wrench in the main hand changes its mode (on the server, which says so in
// the action bar) instead of scrolling the hotbar.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class WrenchScrollHandler {
    private WrenchScrollHandler() {}

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.screen != null || player == null || !player.isShiftKeyDown()
                || !(player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof WrenchItem)) {
            return;
        }
        int direction = (int) Math.signum(event.getScrollDeltaY());
        if (direction == 0) {
            return;
        }
        event.setCanceled(true);
        ClientPacketDistributor.sendToServer(new WrenchModePayload(direction));
    }
}
