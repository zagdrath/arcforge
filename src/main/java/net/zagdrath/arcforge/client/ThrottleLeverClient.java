/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.redstone.ThrottleLeverBlock;
import net.zagdrath.arcforge.network.ThrottleLeverPayload;

// While the crosshair is on a Throttle Lever: the mouse wheel moves it (up = +1, down = -1) instead of
// scrolling the hotbar, and a small popup under the crosshair shows the signal and a 15-segment gauge.
// Sneak + wheel is left to the held tool.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class ThrottleLeverClient {
    private static final Identifier PANEL = sprite("panel");
    private static final Identifier SEGMENT_ON = sprite("segment_on");
    private static final Identifier SEGMENT_MAX = sprite("segment_max");
    private static final Identifier SEGMENT_OFF = sprite("segment_off");
    private static final int PANEL_W = 72, PANEL_H = 20, BAR_X = 23, BAR_Y = 7, SEGMENT_W = 2, SEGMENT_H = 6, SEGMENT_PITCH = 3;

    private ThrottleLeverClient() {}

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, "hud/throttle_lever/" + name);
    }

    // The lever under the crosshair, or null.
    private static @Nullable BlockPos target(Minecraft minecraft) {
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return minecraft.level.getBlockState(hit.getBlockPos()).getBlock() instanceof ThrottleLeverBlock ? hit.getBlockPos() : null;
    }

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.screen != null || player == null || player.isSpectator()) {
            return;
        }
        // Sneak + wheel belongs to the held tool (Wrench mode, Arc tool modules).
        if (player.isShiftKeyDown()) {
            return;
        }
        BlockPos pos = target(minecraft);
        int direction = (int) Math.signum(event.getScrollDeltaY());
        if (pos == null || direction == 0) {
            return;
        }
        event.setCanceled(true);
        ClientPacketDistributor.sendToServer(new ThrottleLeverPayload(pos, direction));
    }

    @SubscribeEvent
    static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, Identifier.fromNamespaceAndPath(Arcforge.MODID, "throttle_lever"), ThrottleLeverClient::drawHud);
    }

    private static void drawHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) {
            return;
        }
        BlockPos pos = target(minecraft);
        if (pos == null) {
            return;
        }
        BlockState state = minecraft.level.getBlockState(pos);
        int power = state.getValue(ThrottleLeverBlock.POWER);
        int x = graphics.guiWidth() / 2 - PANEL_W / 2;
        int y = graphics.guiHeight() / 2 + 12;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, x, y, PANEL_W, PANEL_H);
        for (int i = 0; i < 15; i++) {
            Identifier segment = i >= power ? SEGMENT_OFF : i == 14 ? SEGMENT_MAX : SEGMENT_ON;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, segment, x + BAR_X + i * SEGMENT_PITCH, y + BAR_Y, SEGMENT_W, SEGMENT_H);
        }
        Component value = Component.literal(Integer.toString(power));
        graphics.text(minecraft.font, value, x + 12 - minecraft.font.width(value) / 2, y + 6, 0xFFFFFFFF, true);
        Component hint = Component.translatable("hud.arcforge.throttle_lever.hint");
        graphics.text(minecraft.font, hint, graphics.guiWidth() / 2 - minecraft.font.width(hint) / 2, y + PANEL_H + 2, 0xFFA0A0A0, true);
    }
}
