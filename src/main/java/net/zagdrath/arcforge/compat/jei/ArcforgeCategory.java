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

    static void text(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        graphics.text(Minecraft.getInstance().font, text, x, y, TEXT_COLOR, false);
    }

    // Right-aligned to x.
    static void textRight(GuiGraphicsExtractor graphics, Component text, int x, int y) {
        graphics.text(Minecraft.getInstance().font, text, x - Minecraft.getInstance().font.width(text), y, TEXT_COLOR, false);
    }

    static Component seconds(int ticks) {
        return Component.translatable("jei.arcforge.seconds", String.format(Locale.ROOT, "%.1f", ticks / 20.0));
    }
}
