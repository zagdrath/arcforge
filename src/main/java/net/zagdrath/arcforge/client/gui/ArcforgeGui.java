/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.Arcforge;

// Shared GUI colours and helpers for every Arcforge machine screen.
public final class ArcforgeGui {
    public static final int ACCENT = 0xFF5FD4C4;
    // Heat amounts and rates (HU).
    public static final int HEAT = 0xFFFF9A3C;
    public static final int TEXT = 0xFFE0E0E0;
    public static final int LABEL = 0xFFA0A0A0;
    public static final int TOOLTIP_GRAY = 0xFFAAAAAA;
    public static final int TOOLTIP_GREEN = 0xFF55FF55;
    public static final int WHITE = 0xFFFFFFFF;

    private ArcforgeGui() {}

    // GUI atlas sprite id, e.g. sprite("widget/arcforge/tab") -> arcforge:widget/arcforge/tab.
    public static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, path);
    }

    public static Identifier widget(String name) {
        return sprite("widget/arcforge/" + name);
    }

    public static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    // "950", "12.4k", "2.5M", "1.2G": one decimal place, dropping a trailing ".0".
    public static String compact(long value) {
        if (value < 1_000) return Long.toString(value);
        if (value < 1_000_000) return oneDecimal(value / 1_000.0) + "k";
        if (value < 1_000_000_000) return oneDecimal(value / 1_000_000.0) + "M";
        return oneDecimal(value / 1_000_000_000.0) + "G";
    }

    // Millibuckets as buckets, e.g. 40,200 mB -> "40.2".
    public static String buckets(long millibuckets) {
        return oneDecimal(millibuckets / 1_000.0);
    }

    // Rounds down so a nearly-full store never reads as full.
    private static String oneDecimal(double value) {
        String text = String.format(Locale.ROOT, "%,.1f", Math.floor(value * 10.0) / 10.0);
        return text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    public static String grouped(long value) {
        return String.format("%,d", value);
    }

    // The bottom `fill` px of a width x height gauge whose bottom-left is (left, bottom): the fluid's still
    // texture, tinted and tiled in 16px steps, clipped to the fill; or the fallback sprite if the fluid has
    // no model.
    public static void drawFluid(GuiGraphicsExtractor graphics, Fluid fluid, int fill, Identifier fallback, int left, int bottom, int width, int height) {
        if (fill <= 0 || fluid == Fluids.EMPTY) {
            return;
        }
        try {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            TextureAtlasSprite still = model.stillMaterial().sprite();
            int tint = (model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(new FluidStack(fluid, 1)) : -1) | 0xFF000000;
            graphics.enableScissor(left, bottom - fill, left + width, bottom);
            for (int tileY = bottom - 16; tileY > bottom - fill - 16; tileY -= 16) {
                for (int tileX = left; tileX < left + width; tileX += 16) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, still, tileX, tileY, 16, 16, tint);
                }
            }
            graphics.disableScissor();
        } catch (RuntimeException e) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, fallback, width, height, 0, height - fill, left, bottom - fill, width, fill);
        }
    }
}
