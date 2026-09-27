/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.SteamBoilerMenu;
import net.zagdrath.arcforge.steam.SteamGrade;

// The Steam Boiler and Steam Boiler Array screen: steam_boiler_gui_layout.json (the array's layout has the
// same positions, with its own background and sprites). All positions are relative to leftPos/topPos.
public class SteamBoilerScreen extends MachineScreen<SteamBoilerMenu> {
    private static final int WATER_X = 9, STEAM_X = 155, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int HEAT_X = 53, HEAT_Y = 34, HEAT_W = 92;
    private static final int TEXT_X = 53, VALUE_RIGHT = 145, TEMP_Y = 23, GRADE_Y = 42;
    private static final int LED_X = 53, LED_Y = 56, STATUS_X = 61, STATUS_Y = 56;
    private static final int TICK_COLOR = 0xFF5C5C5C, NOT_BOILING_COLOR = 0xFF707070;

    private final Identifier waterFill = sprite("water_fill");
    private final Identifier steamFill = sprite("steam_fill");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier ghostBucket = sprite("ghost_bucket");

    public SteamBoilerScreen(SteamBoilerMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, title, "steam_boiler");
    }

    protected SteamBoilerScreen(SteamBoilerMenu menu, Inventory inventory, Component title, String machine) {
        super(menu, inventory, title, machine, List.of(new HeatTab(menu::getHeat,
                Component.translatable("gui.arcforge.usage"),
                () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsed()),
                Component.translatable("gui.arcforge.temp"),
                () -> Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())))), false);
        enableAutoEject();
    }

    // The array's screen: its own background, and its size in the title.
    public static SteamBoilerScreen array(SteamBoilerMenu menu, Inventory inventory, Component title) {
        return new SteamBoilerScreen(menu, inventory, title, "steam_boiler_array");
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case INPUT -> sideModeName("water_input");
            case OUTPUT -> sideModeName("steam_output");
            case HEAT -> sideModeName("heat_input");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawFluidTank(graphics, menu.getWater(), menu.getWaterAmount(), menu.getWaterCapacity(), waterFill, tankGauge,
                x, y, WATER_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, menu.getSteam(), menu.getSteamAmount(), menu.getSteamCapacity(), steamFill, tankGauge,
                x, y, STEAM_X, TANK_Y, TANK_W, TANK_H);
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
        // Where each grade starts.
        for (SteamGrade grade : SteamGrade.values()) {
            int tickX = x + HEAT_X + HeatScale.fillWidth(HEAT_W, grade.minCelsius());
            graphics.fill(tickX, y + HEAT_Y, tickX + 1, y + HEAT_Y + 4, TICK_COLOR);
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
        ghost(graphics, ghostBucket, !menu.getBucketOutSlot().hasItem(), SteamBoilerMenu.BUCKET_OUT_X, SteamBoilerMenu.BUCKET_OUT_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.temp"), TEXT_X, TEMP_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())), VALUE_RIGHT, TEMP_Y, ArcforgeGui.TEXT);
        SteamGrade grade = menu.getGrade();
        if (grade != null) {
            graphics.text(font, grade.shortName(), TEXT_X, GRADE_Y, grade.guiColor(), false);
        } else {
            graphics.text(font, Component.translatable("gui.arcforge.not_boiling"), TEXT_X, GRADE_Y, NOT_BOILING_COLOR, false);
        }
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
        textRight(graphics, Component.translatable("gui.arcforge.mb_per_tick_gain", String.format(Locale.ROOT, "%.1f", menu.getRate())),
                VALUE_RIGHT, STATUS_Y, ArcforgeGui.ACCENT);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(WATER_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getWater(), Component.translatable("gui.arcforge.water"), menu.getWaterAmount(), menu.getWaterCapacity());
        } else if (isHovering(STEAM_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getSteam(), Component.translatable("gui.arcforge.steam"), menu.getSteamAmount(), menu.getSteamCapacity());
        } else if (isHovering(HEAT_X, HEAT_Y - 2, HEAT_W, 8, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())));
        }
    }
}
