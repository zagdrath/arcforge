/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.client.gui.tab.PressureTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.SuperheaterArrayMenu;
import net.zagdrath.arcforge.steam.BoilerPressure;
import net.zagdrath.arcforge.steam.SteamGrade;

// Layout follows gui_layouts.json "superheater_array": steam in on the left, steam out on the right, and
// between them the temperature, the heat bar (marked where High-Pressure and Superheated start), the grade it
// makes, and the status with the flow. All positions are relative to leftPos/topPos.
public class SuperheaterArrayScreen extends MachineScreen<SuperheaterArrayMenu> {
    private static final int IN_X = 9, OUT_X = 155, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int HEAT_X = 53, HEAT_Y = 34, HEAT_W = 92;
    private static final int TEXT_X = 53, VALUE_RIGHT = 145, TEMP_Y = 23, GRADE_Y = 42;
    private static final int LED_X = 53, LED_Y = 56, STATUS_X = 61, STATUS_Y = 56;
    private static final int RATE_GAP = 4;
    private static final int TICK_COLOR = 0xFFFFFFFF, PASSING_COLOR = 0xFF707070;

    private final Identifier steamFill = sprite("steam_fill");
    private final Identifier tankGauge = sprite("tank_gauge");

    public SuperheaterArrayScreen(SuperheaterArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "superheater_array", List.of(new PressureTab(SuperheaterArrayBlockEntity.PRESSURES, menu::getPressure,
                pressure -> sendPressure(menu, pressure),
                pressure -> "gui.arcforge.superheater.pressure." + pressure.getSerializedName() + ".hint")), false);
    }

    private static void sendPressure(SuperheaterArrayMenu menu, BoilerPressure pressure) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, SuperheaterArrayMenu.pressureButtonId(pressure));
        }
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case INPUT -> sideModeName("steam_input");
            case OUTPUT -> sideModeName("steam_output");
            case HEAT -> sideModeName("heat_input");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawFluidTank(graphics, menu.getInFluid(), menu.getInAmount(), menu.getTankCapacity(), steamFill, tankGauge, x, y, IN_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, menu.getOutFluid(), menu.getOutAmount(), menu.getTankCapacity(), steamFill, tankGauge, x, y, OUT_X, TANK_Y, TANK_W, TANK_H);
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
        // Where it can make each grade above plain Steam.
        for (SteamGrade grade : List.of(SteamGrade.HIGH_PRESSURE, SteamGrade.SUPERHEATED)) {
            int tickX = x + HEAT_X + HeatScale.fillWidth(HEAT_W, grade.minCelsius());
            graphics.fill(tickX, y + HEAT_Y, tickX + 1, y + HEAT_Y + 4, TICK_COLOR);
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.temp"), TEXT_X, TEMP_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())), VALUE_RIGHT, TEMP_Y, ArcforgeGui.TEXT);
        SteamGrade target = menu.getTarget();
        if (target != null) {
            graphics.text(font, Component.translatable("gui.arcforge.superheater.to", target.shortName()), TEXT_X, GRADE_Y, target.guiColor(), false);
        } else {
            graphics.text(font, Component.translatable("gui.arcforge.superheater.passing"), TEXT_X, GRADE_Y, PASSING_COLOR, false);
        }
        Component status = menu.getStatus().getDescription();
        graphics.text(font, status, STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
        // The flow shares the status line while it upgrades, if it fits; the tooltip always has it.
        Component rate = rateText();
        if (menu.getStatus() == MachineStatus.SUPERHEATING && STATUS_X + font.width(status) + RATE_GAP <= VALUE_RIGHT - font.width(rate)) {
            textRight(graphics, rate, VALUE_RIGHT, STATUS_Y, ArcforgeGui.ACCENT);
        }
    }

    private Component rateText() {
        return Component.translatable("gui.arcforge.mb_per_tick_gain", ArcforgeGui.grouped(menu.getFlow()));
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(IN_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getInFluid(), Component.translatable("gui.arcforge.steam"), menu.getInAmount(), menu.getTankCapacity());
        } else if (isHovering(OUT_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getOutFluid(), Component.translatable("gui.arcforge.steam"), menu.getOutAmount(), menu.getTankCapacity());
        } else if (isHovering(LED_X, STATUS_Y - 1, VALUE_RIGHT - LED_X, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
            lines.add(rateText().copy().withColor(ArcforgeGui.ACCENT));
            lines.add(Component.translatable("gui.arcforge.hu_per_tick_loss", ArcforgeGui.grouped(menu.getHeatUsed())).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(HEAT_X, HEAT_Y - 2, HEAT_W, 8, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())));
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getHeatCapacity()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
