/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.config.ArcforgeClientConfig;
import net.zagdrath.arcforge.network.ArcQuarryFinishedPayload;
import net.zagdrath.arcforge.registry.ModItems;

// "Arc Quarry finished / Mined 1,234 blocks at x, y, z": the advancement toast's frame with the quarry's icon, for five
// seconds (scaled by the accessibility notification time).
public final class ArcQuarryToast implements Toast {
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/advancement");
    private static final long DISPLAY_TIME = 5_000L;
    private static final int TITLE_COLOR = 0xFFFFFF00;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final ItemStack icon = new ItemStack(ModItems.ARC_QUARRY.get());
    private final Component title;
    private final Component message;
    private Visibility visibility = Visibility.SHOW;

    private ArcQuarryToast(Component title, Component message) {
        this.title = title;
        this.message = message;
    }

    // The client half of ArcQuarryFinishedPayload.
    public static void show(ArcQuarryFinishedPayload payload) {
        if (!ArcforgeClientConfig.ARC_QUARRY_FINISHED_TOAST.getAsBoolean()) {
            return;
        }
        Component message = Component.translatable("toast.arcforge.arc_quarry_finished.message", ArcforgeGui.grouped(payload.mined()),
                payload.pos().getX(), payload.pos().getY(), payload.pos().getZ());
        Minecraft.getInstance().gui.toastManager().addToast(new ArcQuarryToast(Component.translatable("toast.arcforge.arc_quarry_finished"), message));
    }

    @Override
    public Visibility getWantedVisibility() {
        return visibility;
    }

    @Override
    public void update(ToastManager manager, long visibleTime) {
        visibility = visibleTime >= DISPLAY_TIME * manager.getNotificationDisplayTimeMultiplier() ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long visibleTime) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
        graphics.item(icon, 8, 8);
        graphics.text(font, title, 30, 7, TITLE_COLOR, false);
        graphics.text(font, font.split(message, width() - 34).getFirst(), 30, 18, TEXT_COLOR, false);
    }
}
