/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jei;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;

// What Arcforge's JEI categories share: a title from jei.arcforge.<name>, a machine icon, and small grey
// text lines under the slots.
abstract class ArcforgeCategory<T> extends AbstractRecipeCategory<T> {
    static final int TEXT_COLOR = 0xFF404040;

    ArcforgeCategory(IRecipeType<T> type, String name, ItemLike icon, IGuiHelper gui, int width, int height) {
        super(type, Component.translatable("jei.arcforge." + name), gui.createDrawableItemLike(icon), width, height);
    }

    // A line of text from x, cut short with "…" if it would run past the category's right edge.
    void text(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        graphics.text(Minecraft.getInstance().font, fit(text, getWidth() - x), x, y, TEXT_COLOR, false);
    }

    // Right-aligned to x (and cut short if it's wider than the category).
    void textRight(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        Component fitted = fit(text, x);
        graphics.text(Minecraft.getInstance().font, fitted, x - Minecraft.getInstance().font.width(fitted), y, TEXT_COLOR, false);
    }

    private static Component fit(Component text, int width) {
        var font = Minecraft.getInstance().font;
        if (font.width(text) <= width) {
            return text;
        }
        return Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(0, width - font.width("…"))) + "…");
    }

    static Component seconds(int ticks) {
        return Component.translatable("jei.arcforge.seconds", String.format(Locale.ROOT, "%.1f", ticks / 20.0));
    }
}
