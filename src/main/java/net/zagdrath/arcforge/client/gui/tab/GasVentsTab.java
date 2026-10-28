/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */
package net.zagdrath.arcforge.client.gui.tab;

import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.machine.GasVents;
import net.zagdrath.arcforge.menu.machine.MachineMenu;

/** Output overflow controls live outside the gauges and inventory. */
public final class GasVentsTab extends SideTab {
    private final MachineMenu menu;
    private final IntConsumer send;

    public GasVentsTab(MachineMenu menu, IntConsumer send) {
        super(ArcforgeGui.sprite("container/electrolyzer/vent_on"), Component.translatable("gui.arcforge.tab.vents"), 150, 48);
        this.menu = menu;
        this.send = send;
    }

    @Override public boolean isVisible() { return menu.ventCount() > 0; }
    @Override protected int expandedHeight() { return 28 + menu.ventCount() * 20; }

    private Component name(int index) {
        var fluid = menu.ventFluid(index);
        return fluid == null || fluid == Fluids.EMPTY
                ? Component.translatable("gui.arcforge.vent.output", index + 1)
                : new FluidStack(fluid, 1).getHoverName();
    }

    @Override protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < menu.ventCount(); i++) {
            int row = y + 24 + i * 20;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    ArcforgeGui.sprite("container/electrolyzer/vent_" + (menu.ventEnabled(i) ? "on" : "off")), x + 8, row, 14, 12);
            graphics.text(font, font.substrByWidth(name(i), 116).getString(), x + 28, row + 2, ArcforgeGui.TEXT, false);
        }
    }

    private int rowAt(int x, int y) {
        int row = (y - 24) / 20;
        return x >= 6 && x < 144 && y >= 24 && row < menu.ventCount() && (y - 24) % 20 < 16 ? row : -1;
    }

    @Override protected boolean contentClicked(MouseButtonEvent event, int localX, int localY) {
        int row = rowAt(localX, localY);
        if (row < 0 || event.button() != 0) return false;
        send.accept(GasVents.BUTTON_FIRST + row);
        ArcforgeGui.playClickSound();
        return true;
    }

    @Override protected void addTooltip(List<Component> lines, int localX, int localY) {
        int row = rowAt(localX, localY);
        if (row < 0) return;
        lines.add(name(row));
        lines.add(Component.translatable("gui.arcforge.vent." + (menu.ventEnabled(row) ? "on" : "off")));
        lines.add(Component.translatable("gui.arcforge.vent.hint"));
    }
}
