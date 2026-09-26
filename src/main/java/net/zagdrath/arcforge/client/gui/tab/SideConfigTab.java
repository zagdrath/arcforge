/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;

// Cross of block faces for configuring IO per side.
// Left click cycles forward, right click cycles back, shift-click clears to none.
public class SideConfigTab extends SideTab {
    private static final Identifier FACE_LOCKED = ArcforgeGui.widget("face_locked");
    private static final Identifier FACE_HOVER = ArcforgeGui.widget("face_hover");
    private static final int FACE_SIZE = 16;

    private final Function<RelativeSide, SideMode> modes;
    private final BiConsumer<RelativeSide, Integer> action;

    // action receives a side and one of SideConfig.ACTION_*.
    public SideConfigTab(Function<RelativeSide, SideMode> modes, BiConsumer<RelativeSide, Integer> action) {
        super(ArcforgeGui.widget("icon_side_config"), Component.translatable("gui.arcforge.tab.side_config"), 100, 86);
        this.modes = modes;
        this.action = action;
    }

    private static int faceX(RelativeSide side) {
        return switch (side) {
            case TOP, FRONT, BOTTOM -> 32;
            case LEFT -> 14;
            case RIGHT -> 50;
            case BACK -> 68;
        };
    }

    private static int faceY(RelativeSide side) {
        return switch (side) {
            case TOP -> 24;
            case BOTTOM -> 60;
            default -> 42;
        };
    }

    private static RelativeSide faceAt(int localX, int localY) {
        for (RelativeSide side : RelativeSide.values()) {
            if (ArcforgeGui.isInside(localX, localY, faceX(side), faceY(side), FACE_SIZE, FACE_SIZE)) {
                return side;
            }
        }
        return null;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        RelativeSide hovered = faceAt(mouseX - x, mouseY - y);
        for (RelativeSide side : RelativeSide.values()) {
            Identifier sprite = SideConfig.isLocked(side)
                    ? FACE_LOCKED
                    : ArcforgeGui.widget("face_" + modes.apply(side).getSerializedName());
            int fx = x + faceX(side);
            int fy = y + faceY(side);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, fx, fy, FACE_SIZE, FACE_SIZE);
            if (side == hovered && !SideConfig.isLocked(side)) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FACE_HOVER, fx, fy, FACE_SIZE, FACE_SIZE);
            }
        }
    }

    @Override
    protected boolean contentClicked(MouseButtonEvent event, int localX, int localY) {
        RelativeSide side = faceAt(localX, localY);
        if (side == null || SideConfig.isLocked(side) || (event.button() != 0 && event.button() != 1)) {
            return false;
        }
        int sideAction = event.hasShiftDown() ? SideConfig.ACTION_CLEAR
                : event.button() == 1 ? SideConfig.ACTION_PREVIOUS
                : SideConfig.ACTION_NEXT;
        action.accept(side, sideAction);
        ArcforgeGui.playClickSound();
        return true;
    }

    @Override
    protected void addTooltip(List<Component> lines, int localX, int localY) {
        RelativeSide side = faceAt(localX, localY);
        if (side == null) {
            return;
        }
        lines.add(side.getDescription());
        lines.add(SideConfig.isLocked(side)
                ? Component.translatable("gui.arcforge.side_mode.locked").withStyle(ChatFormatting.GRAY)
                : modes.apply(side).getDescription().copy().withStyle(ChatFormatting.GRAY));
    }
}
