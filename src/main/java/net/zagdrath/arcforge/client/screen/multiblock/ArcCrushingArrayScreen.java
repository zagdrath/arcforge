/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.ArcCrushingArrayMenu;

// Layout follows arc_crushing_array_gui_layout.json. All positions are relative to leftPos/topPos.
public class ArcCrushingArrayScreen extends MachineScreen<ArcCrushingArrayMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int PROGRESS_X = 52, FIRST_PROGRESS_Y = 20, LANE_PITCH = 18, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int TEXT_X = 125, YIELD_LABEL_Y = 23, YIELD_Y = 33, POWER_LABEL_Y = 45, POWER_Y = 55;
    private static final int SCREEN_X = 120, SCREEN_Y = 18, SCREEN_W = 50, SCREEN_H = 52;
    private static final int LED_X = 161, LED_Y = 23;
    private static final int SAVING_COLOR = 0xFF55FF55;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");

    public ArcCrushingArrayScreen(ArcCrushingArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "arc_crushing_array", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        for (int lane = 0; lane < ArcCrushingArrayBlockEntity.LANES; lane++) {
            int total = menu.getTotal(lane);
            int done = menu.getProgress(lane);
            if (total > 0 && done > 0) {
                int width = Math.min(PROGRESS_W, Mth.ceil(PROGRESS_W * (float) done / total));
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, progress, PROGRESS_W, PROGRESS_H, 0, 0,
                        x + PROGRESS_X, y + FIRST_PROGRESS_Y + lane * LANE_PITCH, width, PROGRESS_H);
            }
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.yield"), TEXT_X, YIELD_LABEL_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.array.yield_value", menu.getOreYield()), TEXT_X, YIELD_Y, ArcforgeGui.ACCENT, false);
        graphics.text(font, Component.translatable("gui.arcforge.power"), TEXT_X, POWER_LABEL_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.array.power_value", menu.getPowerSaving()), TEXT_X, POWER_Y, SAVING_COLOR, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(SCREEN_X, SCREEN_Y, SCREEN_W, SCREEN_H, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
            lines.add(Component.translatable("gui.arcforge.array.yield_hint", menu.getOreYield()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.array.power_hint", menu.getPowerSaving()).withStyle(ChatFormatting.GRAY));
        } else {
            for (int lane = 0; lane < ArcCrushingArrayBlockEntity.LANES; lane++) {
                if (isHovering(PROGRESS_X, FIRST_PROGRESS_Y + lane * LANE_PITCH, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal(lane) > 0) {
                    lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(lane), menu.getTotal(lane)));
                }
            }
        }
    }
}
