/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.mojang.blaze3d.platform.InputConstants;

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

// Cross of block faces for configuring IO per side, plus a button that clears every face.
// Left click cycles forward, right click cycles back, shift-click clears one face.
public class SideConfigTab extends SideTab {
    private static final Identifier FACE_HOVER = ArcforgeGui.widget("face_hover");
    private static final Identifier ICON_CLEAR = ArcforgeGui.widget("icon_clear");
    private static final Identifier BUTTON = ArcforgeGui.widget("button");
    private static final Identifier BUTTON_HOVER = ArcforgeGui.widget("button_hover");
    private static final int FACE_SIZE = 16;
    // Clear-all button, bottom-right, level with the bottom face.
    private static final int CLEAR_X = 66, CLEAR_Y = 58, CLEAR_SIZE = 20;

    private final Function<RelativeSide, SideMode> modes;
    private final BiConsumer<RelativeSide, Integer> action;
    private final Runnable clearAll;

    // action receives a side and one of SideConfig.ACTION_*.
    public SideConfigTab(Function<RelativeSide, SideMode> modes, BiConsumer<RelativeSide, Integer> action, Runnable clearAll) {
        super(ArcforgeGui.widget("icon_side_config"), Component.translatable("gui.arcforge.tab.side_config"), 100, 84);
        this.modes = modes;
        this.action = action;
        this.clearAll = clearAll;
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

    private static boolean isOverClear(int localX, int localY) {
        return ArcforgeGui.isInside(localX, localY, CLEAR_X, CLEAR_Y, CLEAR_SIZE, CLEAR_SIZE);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        RelativeSide hovered = faceAt(mouseX - x, mouseY - y);
        for (RelativeSide side : RelativeSide.values()) {
            int fx = x + faceX(side);
            int fy = y + faceY(side);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.widget("face_" + modes.apply(side).getSerializedName()), fx, fy, FACE_SIZE, FACE_SIZE);
            if (side == hovered) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FACE_HOVER, fx, fy, FACE_SIZE, FACE_SIZE);
            }
        }

        boolean clearHovered = isOverClear(mouseX - x, mouseY - y);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, clearHovered ? BUTTON_HOVER : BUTTON, x + CLEAR_X, y + CLEAR_Y, CLEAR_SIZE, CLEAR_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ICON_CLEAR, x + CLEAR_X + 2, y + CLEAR_Y + 2, 16, 16);
    }

    @Override
    protected boolean contentClicked(MouseButtonEvent event, int localX, int localY) {
        if (isOverClear(localX, localY) && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            clearAll.run();
            ArcforgeGui.playClickSound();
            return true;
        }

        RelativeSide side = faceAt(localX, localY);
        if (side == null || (event.button() != InputConstants.MOUSE_BUTTON_LEFT && event.button() != InputConstants.MOUSE_BUTTON_RIGHT)) {
            return false;
        }
        int sideAction = event.hasShiftDown() ? SideConfig.ACTION_CLEAR
                : event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? SideConfig.ACTION_PREVIOUS
                : SideConfig.ACTION_NEXT;
        action.accept(side, sideAction);
        ArcforgeGui.playClickSound();
        return true;
    }

    @Override
    protected void addTooltip(List<Component> lines, int localX, int localY) {
        if (isOverClear(localX, localY)) {
            lines.add(Component.translatable("gui.arcforge.side.clear_all"));
            return;
        }
        RelativeSide side = faceAt(localX, localY);
        if (side != null) {
            lines.add(side.getDescription());
            lines.add(modes.apply(side).getDescription().copy().withStyle(ChatFormatting.GRAY));
        }
    }
}
