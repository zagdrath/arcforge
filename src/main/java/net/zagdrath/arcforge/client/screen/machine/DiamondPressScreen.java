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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.DiamondPressMenu;

// The Hydrothermal Carbonizer's layout without tanks: FE on the left, the Graphite slot, the arrow and the Diamond, the
// status, the heat bar with a tick at the temperature it needs (1,400°C), and the heat buffer on the right.
// Positions are relative to leftPos/topPos.
public class DiamondPressScreen extends MachineScreen<DiamondPressMenu> {
    private static final int BUFFER_X = 157, BUFFER_Y = 19;
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int PROGRESS_X = 68, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LED_X = 43, LED_Y = 54;
    private static final int STATUS_X = 51, STATUS_Y = 54, STATUS_W = 84;
    private static final int HEAT_X = 43, HEAT_Y = 66, HEAT_W = 92;
    private static final int TICK_Y = 63, TICK_W = 3, TICK_H = 7;

    private final Identifier heatBuffer = sprite("heat_buffer");
    private final Identifier progress = sprite("progress");
    private final Identifier minTempTick = sprite("min_temp_tick");
    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier ghostGraphite = sprite("ghost_graphite");

    public DiamondPressScreen(DiamondPressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "diamond_press", List.of(
                EnergyTab.usage(menu::getEnergy, menu::getUsage),
                new HeatTab(menu::getHeat,
                        Component.translatable("gui.arcforge.usage"),
                        () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()))));
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case HEAT -> sideModeName("heat_input");
            case ENERGY -> sideModeName("energy_input");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, heatBuffer, x, y, BUFFER_X, BUFFER_Y, menu.getHeat(), menu.getHeatCapacity());
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        ghost(graphics, ghostGraphite, !menu.getSlot(0).hasItem(), DiamondPressMenu.INPUT_X, DiamondPressMenu.INPUT_Y);
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
        // White tick at the temperature it needs to work.
        int tickX = HEAT_X + HeatScale.fillWidth(HEAT_W, menu.getMinTemperature()) - 1;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, minTempTick, x + tickX, y + TICK_Y, TICK_W, TICK_H);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    private MutableComponent temperatureLine() {
        return Component.translatable("gui.arcforge.celsius_needs", menu.getTemperature(),
                Component.translatable("gui.arcforge.diamond_press.needs", menu.getMinTemperature()));
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(BUFFER_X - 1, BUFFER_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getHeatCapacity())));
            lines.add(temperatureLine().withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()).withStyle(ChatFormatting.GOLD));
        } else if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", ArcforgeGui.grouped(menu.getUsage())).withStyle(ChatFormatting.RED));
        } else if (isHovering(HEAT_X, TICK_Y, HEAT_W, TICK_H, mouseX, mouseY)) {
            lines.add(temperatureLine());
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
