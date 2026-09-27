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
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.SteamTurbineMenu;
import net.zagdrath.arcforge.steam.SteamGrade;

// Layout follows steam_turbine_gui_layout.json. All positions are relative to leftPos/topPos.
public class SteamTurbineScreen extends MachineScreen<SteamTurbineMenu> {
    private static final int STEAM_X = 9, STEAM_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int ENERGY_X = 157, ENERGY_Y = 19;
    private static final int LABEL_X = 31, VALUE_RIGHT = 145, STEAM_TEXT_Y = 23, FLOW_Y = 34, OUTPUT_Y = 45;
    private static final int LED_X = 31, LED_Y = 57, STATUS_X = 39, STATUS_Y = 57;
    static final int NONE_COLOR = 0xFF707070;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier steamFill = sprite("steam_fill");
    private final Identifier tankGauge = sprite("tank_gauge");

    public SteamTurbineScreen(SteamTurbineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "steam_turbine", List.of(new EnergyTab(menu::getEnergy, menu::getFePerTick)), false);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case INPUT -> sideModeName("steam_input");
            case ENERGY -> sideModeName("energy_output");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawFluidTank(graphics, menu.getSteam(), menu.getSteamAmount(), menu.getSteamCapacity(), steamFill, tankGauge,
                x, y, STEAM_X, STEAM_Y, TANK_W, TANK_H);
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.steam"), LABEL_X, STEAM_TEXT_Y, ArcforgeGui.LABEL, false);
        SteamGrade grade = menu.getSteamAmount() > 0 ? SteamGrade.of(menu.getSteam()) : null;
        if (grade != null) {
            textRight(graphics, grade.shortName(), VALUE_RIGHT, STEAM_TEXT_Y, grade.guiColor());
        } else {
            textRight(graphics, Component.literal("—"), VALUE_RIGHT, STEAM_TEXT_Y, NONE_COLOR);
        }
        graphics.text(font, Component.translatable("gui.arcforge.flow"), LABEL_X, FLOW_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.flow_value", menu.getFlow(), menu.getMaxFlow()), VALUE_RIGHT, FLOW_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.output"), LABEL_X, OUTPUT_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.fe_per_tick_gain", ArcforgeGui.grouped(menu.getFePerTick())), VALUE_RIGHT, OUTPUT_Y, ArcforgeGui.ACCENT);
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(STEAM_X - 1, STEAM_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getSteam(), Component.translatable("gui.arcforge.steam"), menu.getSteamAmount(), menu.getSteamCapacity());
        } else if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
        }
    }
}
