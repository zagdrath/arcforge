/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.steam.BoilerPressure;

// A boiler's pressure setting (see BoilerPressure): one row per setting, the active one outlined.
public class PressureTab extends SideTab {
    private static final int WIDTH = 108;
    private static final int ROWS_X = 6, ROWS_Y = 24, ROW_W = WIDTH - 12, ROW_H = 12, TEXT_INSET = 3;

    private final List<BoilerPressure> rows;
    private final Supplier<BoilerPressure> current;
    private final Consumer<BoilerPressure> select;
    // The lang key of a row's hint.
    private final Function<BoilerPressure, String> hint;

    // Every setting, with the boiler's hints.
    public PressureTab(Supplier<BoilerPressure> current, Consumer<BoilerPressure> select) {
        this(List.of(BoilerPressure.values()), current, select, pressure -> "gui.arcforge.pressure." + pressure.getSerializedName() + ".hint");
    }

    // Only these settings (the Superheater Array has no plain-Steam row), with their own hints.
    public PressureTab(List<BoilerPressure> rows, Supplier<BoilerPressure> current, Consumer<BoilerPressure> select,
            Function<BoilerPressure, String> hint) {
        super(ArcforgeGui.widget("icon_pressure"), Component.translatable("gui.arcforge.tab.pressure"), WIDTH, ROWS_Y + rows.size() * ROW_H + 6);
        this.rows = rows;
        this.current = current;
        this.select = select;
        this.hint = hint;
    }

    private int rowY(BoilerPressure pressure) {
        return ROWS_Y + rows.indexOf(pressure) * ROW_H;
    }

    private @Nullable BoilerPressure rowAt(int localX, int localY) {
        for (BoilerPressure pressure : rows) {
            if (ArcforgeGui.isInside(localX, localY, ROWS_X, rowY(pressure), ROW_W, ROW_H)) {
                return pressure;
            }
        }
        return null;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        BoilerPressure hovered = rowAt(mouseX - x, mouseY - y);
        for (BoilerPressure pressure : rows) {
            boolean active = pressure == current.get();
            int rowX = x + ROWS_X;
            int rowY = y + rowY(pressure);
            if (active) {
                graphics.outline(rowX, rowY, ROW_W, ROW_H, ArcforgeGui.ACCENT);
            }
            int color = active ? ArcforgeGui.ACCENT : pressure == hovered ? ArcforgeGui.TEXT : ArcforgeGui.LABEL;
            graphics.text(font, pressure.getDescription(), rowX + TEXT_INSET, rowY + 2, color, false);
        }
    }

    @Override
    protected boolean contentClicked(MouseButtonEvent event, int localX, int localY) {
        BoilerPressure pressure = rowAt(localX, localY);
        if (pressure == null || event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        if (pressure != current.get()) {
            select.accept(pressure);
            ArcforgeGui.playClickSound();
        }
        return true;
    }

    @Override
    protected void addCollapsedTooltip(List<Component> lines) {
        super.addCollapsedTooltip(lines);
        lines.add(current.get().getDescription().copy().withStyle(ChatFormatting.GRAY));
    }

    @Override
    protected void addTooltip(List<Component> lines, int localX, int localY) {
        BoilerPressure pressure = rowAt(localX, localY);
        if (pressure != null) {
            lines.add(pressure.getDescription());
            lines.add(Component.translatable(hint.apply(pressure)).withStyle(ChatFormatting.GRAY));
        }
    }
}
