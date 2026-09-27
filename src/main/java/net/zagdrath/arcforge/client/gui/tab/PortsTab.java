/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.network.PortsPayload;

// A multiblock's ports (see MultiblockPorts), which take the place of the Sides tab: one row each, with its
// mode and where it is (the side of the structure, and the offset from the controller), the auto-eject
// toggle, and how to set ports. Ports themselves are set in the world with the Wrench.
public class PortsTab extends SideTab {
    private static final int WIDTH = 128;
    private static final int EJECT_X = 8, EJECT_Y = 24, BUTTON_SIZE = 16;
    private static final int ROWS_Y = 44, ROW_HEIGHT = 20, MAX_ROWS = 3;
    private static final int TEXT_X = 28;
    private static final int HINT_Y = ROWS_Y + MAX_ROWS * ROW_HEIGHT + 12;
    private static final int HEIGHT = HINT_Y + 4 * 9 + 6;
    private static final Identifier EJECT_OFF = ArcforgeGui.widget("auto_eject_off");
    private static final Identifier EJECT_ON = ArcforgeGui.widget("auto_eject_on");
    private static final Identifier FACE_HOVER = ArcforgeGui.widget("face_hover");

    private final Supplier<List<PortsPayload.Entry>> ports;
    private @Nullable BooleanSupplier autoEject;
    private @Nullable Runnable toggleAutoEject;

    public PortsTab(Supplier<List<PortsPayload.Entry>> ports) {
        super(ArcforgeGui.widget("icon_side_config"), Component.translatable("gui.arcforge.tab.ports"), WIDTH, HEIGHT);
        this.ports = ports;
    }

    // Shows the auto-eject button, reading its state from state and sending toggle when clicked.
    public PortsTab withAutoEject(BooleanSupplier state, Runnable toggle) {
        this.autoEject = state;
        this.toggleAutoEject = toggle;
        return this;
    }

    private boolean isOverAutoEject(int localX, int localY) {
        return autoEject != null && ArcforgeGui.isInside(localX, localY, EJECT_X, EJECT_Y, BUTTON_SIZE, BUTTON_SIZE);
    }

    // Where a port is, e.g. "Front · x+1 y+2" (its offset from the controller, leaving out zeroes).
    private static Component location(PortsPayload.Entry port) {
        BlockPos offset = port.offset();
        StringBuilder where = new StringBuilder();
        append(where, "x", offset.getX());
        append(where, "y", offset.getY());
        append(where, "z", offset.getZ());
        Component at = where.isEmpty() ? Component.translatable("gui.arcforge.ports.controller") : Component.literal(where.toString());
        return Component.translatable("gui.arcforge.ports.location", port.side().getDescription(), at);
    }

    private static void append(StringBuilder where, String axis, int value) {
        if (value != 0) {
            if (!where.isEmpty()) {
                where.append(' ');
            }
            where.append(axis).append(value > 0 ? "+" : "").append(value);
        }
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        if (autoEject != null) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, autoEject.getAsBoolean() ? EJECT_ON : EJECT_OFF, x + EJECT_X, y + EJECT_Y, BUTTON_SIZE, BUTTON_SIZE);
            if (isOverAutoEject(mouseX - x, mouseY - y)) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FACE_HOVER, x + EJECT_X, y + EJECT_Y, BUTTON_SIZE, BUTTON_SIZE);
            }
            graphics.text(font, Component.translatable("gui.arcforge.ports.auto_eject"), x + TEXT_X, y + EJECT_Y + 4, ArcforgeGui.LABEL, false);
        }

        List<PortsPayload.Entry> list = ports.get();
        if (list.isEmpty()) {
            graphics.text(font, Component.translatable("gui.arcforge.ports.none"), x + EJECT_X, y + ROWS_Y + 4, ArcforgeGui.LABEL, false);
        }
        for (int i = 0; i < Math.min(MAX_ROWS, list.size()); i++) {
            PortsPayload.Entry port = list.get(i);
            int rowY = y + ROWS_Y + i * ROW_HEIGHT;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.widget("face_" + port.mode().getSerializedName()), x + EJECT_X, rowY, BUTTON_SIZE, BUTTON_SIZE);
            graphics.text(font, port.mode().getDescription(), x + TEXT_X, rowY, ArcforgeGui.TEXT, false);
            graphics.text(font, location(port), x + TEXT_X, rowY + 9, ArcforgeGui.LABEL, false);
        }
        if (list.size() > MAX_ROWS) {
            graphics.text(font, Component.translatable("gui.arcforge.ports.more", list.size() - MAX_ROWS),
                    x + TEXT_X, y + ROWS_Y + MAX_ROWS * ROW_HEIGHT, ArcforgeGui.LABEL, false);
        }

        int lineY = y + HINT_Y;
        for (FormattedCharSequence line : font.split(Component.translatable("gui.arcforge.ports.hint"), WIDTH - 2 * EJECT_X)) {
            graphics.text(font, line, x + EJECT_X, lineY, ArcforgeGui.LABEL, false);
            lineY += 9;
        }
    }

    @Override
    protected boolean contentClicked(MouseButtonEvent event, int localX, int localY) {
        if (isOverAutoEject(localX, localY) && toggleAutoEject != null && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            toggleAutoEject.run();
            ArcforgeGui.playClickSound();
            return true;
        }
        return false;
    }

    @Override
    protected void addTooltip(List<Component> lines, int localX, int localY) {
        if (autoEject != null && isOverAutoEject(localX, localY)) {
            String state = autoEject.getAsBoolean() ? "on" : "off";
            lines.add(Component.translatable("gui.arcforge.auto_eject." + state));
            lines.add(Component.translatable("gui.arcforge.auto_eject." + state + ".description").withStyle(ChatFormatting.GRAY));
        }
    }
}
