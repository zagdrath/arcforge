/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;

// A creative storage block's tab (see StorageBlock): what it does, and a Clear row that empties it.
public class CreativeTab extends SideTab {
    private static final int WIDTH = 112, TEXT_Y = 24, LINE_H = 10, ROW_H = 12, TEXT_INSET = 3;

    private final Component hint;
    private final Runnable clear;

    // hint says what to put in; clear sends the Clear button.
    public CreativeTab(Component hint, Runnable clear) {
        super(ArcforgeGui.widget("icon_clear"), Component.translatable("gui.arcforge.tab.creative"), WIDTH, 0);
        this.hint = hint;
        this.clear = clear;
    }

    private List<FormattedCharSequence> lines() {
        return Minecraft.getInstance().font.split(hint, WIDTH - 2 * CONTENT_INSET);
    }

    private int rowY() {
        return TEXT_Y + lines().size() * LINE_H + 2;
    }

    private int rowWidth() {
        return WIDTH - 2 * CONTENT_INSET;
    }

    @Override
    protected int expandedHeight() {
        return rowY() + ROW_H + 6;
    }

    private boolean onRow(int localX, int localY) {
        return ArcforgeGui.isInside(localX, localY, CONTENT_INSET, rowY(), rowWidth(), ROW_H);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        int lineY = y + TEXT_Y;
        for (FormattedCharSequence line : lines()) {
            graphics.text(font, line, x + CONTENT_INSET, lineY, ArcforgeGui.LABEL, false);
            lineY += LINE_H;
        }
        boolean hovered = onRow(mouseX - x, mouseY - y);
        graphics.outline(x + CONTENT_INSET, y + rowY(), rowWidth(), ROW_H, hovered ? ArcforgeGui.ACCENT : ArcforgeGui.LABEL);
        graphics.text(font, Component.translatable("gui.arcforge.creative.clear"), x + CONTENT_INSET + TEXT_INSET, y + rowY() + 2,
                hovered ? ArcforgeGui.ACCENT : ArcforgeGui.TEXT, false);
    }

    @Override
    protected boolean contentClicked(MouseButtonEvent event, int localX, int localY) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !onRow(localX, localY)) {
            return false;
        }
        clear.run();
        ArcforgeGui.playClickSound();
        return true;
    }

    @Override
    protected void addTooltip(List<Component> lines, int localX, int localY) {
        if (onRow(localX, localY)) {
            lines.add(Component.translatable("gui.arcforge.creative.clear"));
            lines.add(Component.translatable("gui.arcforge.creative.clear.hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
