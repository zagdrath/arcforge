/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.experience.LiquidExperience;

// Shows the Liquid Experience a machine has banked (see ExperienceBank): the mB and the points they are, out of its tank.
// It gives it out of Experience faces (or ports); a full tank stops banking.
public class ExperienceTab extends SideTab {
    private static final int XP_COLOR = 0xFF9CE85A;

    private final IntSupplier amount;
    private final IntSupplier capacity;

    public ExperienceTab(IntSupplier amount, IntSupplier capacity) {
        super(ArcforgeGui.widget("icon_experience"), Component.translatable("gui.arcforge.tab.experience"), 64, 74, Side.LEFT);
        this.amount = amount;
        this.capacity = capacity;
    }

    private Component storedText() {
        return Component.translatable("gui.arcforge.experience_tab.banked", ArcforgeGui.grouped(amount.getAsInt()),
                ArcforgeGui.grouped(capacity.getAsInt()));
    }

    private Component pointsText() {
        return Component.translatable("gui.arcforge.experience_tab.points", ArcforgeGui.grouped(amount.getAsInt() / LiquidExperience.MB_PER_POINT));
    }

    private Component stateText() {
        int held = amount.getAsInt();
        return Component.translatable(capacity.getAsInt() > 0 && held >= capacity.getAsInt() ? "gui.arcforge.experience_tab.full"
                : "gui.arcforge.experience_tab.banking");
    }

    @Override
    protected int expandedWidth() {
        Font font = Minecraft.getInstance().font;
        int widest = Math.max(font.width(Component.translatable("gui.arcforge.experience_tab.label")), font.width(storedText()));
        widest = Math.max(widest, Math.max(font.width(pointsText()), font.width(stateText())));
        return fitWidth(font, widest);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("gui.arcforge.experience_tab.label"), x + CONTENT_INSET, y + 26, ArcforgeGui.TOOLTIP_GRAY, false);
        graphics.text(font, storedText(), x + CONTENT_INSET, y + 36, ArcforgeGui.WHITE, false);
        graphics.text(font, pointsText(), x + CONTENT_INSET, y + 48, XP_COLOR, false);
        graphics.text(font, stateText(), x + CONTENT_INSET, y + 58, ArcforgeGui.TOOLTIP_GRAY, false);
    }
}
