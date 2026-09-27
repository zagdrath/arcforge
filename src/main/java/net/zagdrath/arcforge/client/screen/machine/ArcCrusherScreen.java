/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.ArcCrusherMenu;

// Layout follows arc_crusher_gui_layout.json. All positions are relative to leftPos/topPos.
public class ArcCrusherScreen extends MachineScreen<ArcCrusherMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int PROGRESS_X = 67, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LED_X = 43, LED_Y = 58;
    private static final int STATUS_X = 51, STATUS_Y = 58;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");

    public ArcCrusherScreen(ArcCrusherMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "arc_crusher", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    // The arrow fills left to right: w = ceil(21 * progress / total).
    static void drawProgress(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int progress, int total) {
        if (total > 0 && progress > 0) {
            int width = Math.min(PROGRESS_W, Mth.ceil(PROGRESS_W * (float) progress / total));
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, PROGRESS_W, PROGRESS_H, 0, 0, x, y, width, PROGRESS_H);
        }
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        }
    }
}
