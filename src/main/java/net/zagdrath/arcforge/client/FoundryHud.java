/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.tool.FoundrySuit;

// The full Foundry Suit's lava shield, above the hunger bar: a lava icon and a bar of the protection left, or of the
// recharge while it refills. Shown in lava and while the shield isn't full.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class FoundryHud {
    private static final Identifier ICON = sprite("lava_icon");
    private static final Identifier BACKGROUND = sprite("bar_bg");
    private static final Identifier FILL = sprite("bar_fill");
    private static final Identifier COOLDOWN = sprite("bar_cooldown");
    private static final int BAR_W = 40, BAR_H = 3;

    private FoundryHud() {}

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, "hud/foundry/" + name);
    }

    @SubscribeEvent
    static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.AIR_LEVEL, Identifier.fromNamespaceAndPath(Arcforge.MODID, "foundry_lava"), FoundryHud::draw);
    }

    private static void draw(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.options.hideGui || !FoundrySuit.fullSet(player)) {
            return;
        }
        FoundrySuit.LavaShield shield = player.getData(FoundrySuit.LAVA_SHIELD);
        int max = ArcforgeConfig.FOUNDRY_LAVA_SHIELD_TICKS.getAsInt();
        if (!player.isInLava() && shield.remaining() >= max) {
            return;
        }
        boolean bubbles = player.getAirSupply() < player.getMaxAirSupply();
        int x = graphics.guiWidth() / 2 + 10;
        int y = graphics.guiHeight() - 49 - (bubbles ? 10 : 0);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ICON, x, y, 9, 9);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, x + 11, y + 2, BAR_W + 2, BAR_H + 2);
        boolean recharging = shield.remaining() < max && shield.cooldown() > 0 && !player.isInLava();
        int width = recharging
                ? Math.round((float) BAR_W * shield.cooldown() / ArcforgeConfig.FOUNDRY_LAVA_COOLDOWN_TICKS.getAsInt())
                : Math.round((float) BAR_W * shield.remaining() / Math.max(1, max));
        if (width > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, recharging ? COOLDOWN : FILL, BAR_W, BAR_H, 0, 0, x + 12, y + 3, width, BAR_H);
        }
    }
}
