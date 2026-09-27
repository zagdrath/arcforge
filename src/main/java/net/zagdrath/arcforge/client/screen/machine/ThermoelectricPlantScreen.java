/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.menu.machine.ThermoelectricPlantMenu;

// Layout follows thermoelectric_plant_gui_layout.json. All positions are relative to leftPos/topPos.
public class ThermoelectricPlantScreen extends MachineScreen<ThermoelectricPlantMenu> {
    private static final int HEAT_BUFFER_X = 9, HEAT_BUFFER_Y = 19;
    private static final int ENERGY_X = 157, ENERGY_Y = 19;
    private static final int HEAT_X = 31, HEAT_Y = 34, HEAT_W = 114;
    private static final int LED_X = 31, LED_Y = 56;
    private static final int STATUS_X = 39, STATUS_Y = 56;
    private static final int SCREEN_LEFT = 31, SCREEN_RIGHT = 145;
    private static final int HEAT_TEXT_Y = 23, EFFICIENCY_TEXT_Y = 42;

    private final Identifier heatBuffer = sprite("heat_buffer");
    private final Identifier energyBar = sprite("energy_bar");

    public ThermoelectricPlantScreen(ThermoelectricPlantMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "thermoelectric_plant", List.of(
                new EnergyTab(menu::getEnergy, menu::getFePerTick),
                new HeatTab(menu::getHeat,
                        Component.translatable("gui.arcforge.input"),
                        () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatPerTick()))));
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, heatBuffer, x, y, HEAT_BUFFER_X, HEAT_BUFFER_Y, menu.getHeat(), menu.getHeatCapacity());
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getEnergyCapacity());
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.heat_in"), SCREEN_LEFT, HEAT_TEXT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.celsius", menu.getTemperature()), SCREEN_RIGHT, HEAT_TEXT_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.efficiency"), SCREEN_LEFT, EFFICIENCY_TEXT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.percent", menu.getEfficiency()), SCREEN_RIGHT, EFFICIENCY_TEXT_Y, ArcforgeGui.TEXT);
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
        textRight(graphics, Component.translatable("gui.arcforge.fe_per_tick_gain", menu.getFePerTick()), SCREEN_RIGHT, STATUS_Y, ArcforgeGui.ACCENT);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(HEAT_BUFFER_X - 1, HEAT_BUFFER_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getHeatCapacity())));
            lines.add(Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatPerTick()).withStyle(ChatFormatting.GOLD));
        } else if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getEnergyCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_gain", menu.getFePerTick()).withStyle(ChatFormatting.GREEN));
        } else if (isHovering(HEAT_X, HEAT_Y - 2, HEAT_W, 8, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.celsius", menu.getTemperature()));
            lines.add(Component.translatable("gui.arcforge.thermoelectric.hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
