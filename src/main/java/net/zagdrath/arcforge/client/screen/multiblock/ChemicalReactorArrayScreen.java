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
import net.zagdrath.arcforge.blockentity.multiblock.ChemicalReactorArrayBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.ChemicalReactorArrayMenu;

// 220x222 (ChemicalReactorArrayMenu.WIDTH, the inventory centred): the energy gauge and the three input tanks on the
// left, a screen with the status, temperature, FE use and lanes in the middle, the output and by-product tanks on the
// right; under them one row per lane (its three inputs, its progress arrow, its output and by-product, and a lamp that
// lights while it runs beside its progress or "Idle").
public class ChemicalReactorArrayScreen extends MachineScreen<ChemicalReactorArrayMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int[] TANK_X = { 25, 40, 55, 184, 199 };
    private static final int TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    // The screen (72..180 across): status, then Temp and Usage with their values right-aligned, then the lanes.
    private static final int LED_X = 75, LED_Y = 22, STATUS_X = 83, STATUS_Y = 22, STATUS_W = 94;
    private static final int TEXT_X = 75, VALUE_RIGHT = 177, TEMP_Y = 34, USAGE_Y = 45, LANES_Y = 57;
    private static final int PROGRESS_X = 90, PROGRESS_W = 21, PROGRESS_H = 15, LANE_LED_X = 160, LANE_TEXT_RIGHT = 193;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier ledRunning = sprite("led_running");
    private final Identifier ledIdle = sprite("led_idle");

    public ChemicalReactorArrayScreen(ChemicalReactorArrayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "chemical_reactor_array", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage),
                new HeatTab(menu::getHeat, Component.translatable("gui.arcforge.usage"),
                        () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()),
                        Component.translatable("gui.arcforge.temp"),
                        () -> Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())))), true, ChemicalReactorArrayMenu.WIDTH, ChemicalReactorArrayMenu.HEIGHT, true);
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    private static int laneY(int lane) {
        return ChemicalReactorArrayMenu.FIRST_LANE_Y + lane * ChemicalReactorArrayMenu.LANE_PITCH;
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        for (int tank = 0; tank < TANK_X.length; tank++) {
            drawFluidTank(graphics, menu.getFluid(tank), menu.getFluidAmount(tank), menu.getTankCapacity(), tankGauge, tankGauge,
                    x, y, TANK_X[tank], TANK_Y, TANK_W, TANK_H);
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
        for (int lane = 0; lane < ChemicalReactorArrayBlockEntity.LANES; lane++) {
            int total = menu.getTotal(lane), done = menu.getProgress(lane);
            int rowY = laneY(lane);
            if (total > 0 && done > 0) {
                int width = Math.min(PROGRESS_W, Mth.ceil(PROGRESS_W * (float) done / total));
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, progress, PROGRESS_W, PROGRESS_H, 0, 0, x + PROGRESS_X, y + rowY + 1, width, PROGRESS_H);
            }
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.isLaneRunning(lane) ? ledRunning : ledIdle, x + LANE_LED_X, y + rowY + 5, LED_SIZE, LED_SIZE);
        }
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
        graphics.text(font, Component.translatable("gui.arcforge.temp"), TEXT_X, TEMP_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())), VALUE_RIGHT, TEMP_Y,
                ArcforgeGui.HEAT);
        graphics.text(font, Component.translatable("gui.arcforge.usage"), TEXT_X, USAGE_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.translatable("gui.arcforge.fe_per_tick", ArcforgeGui.grouped(menu.getUsage())), VALUE_RIGHT, USAGE_Y,
                ArcforgeGui.TEXT);
        graphics.text(font, clipped(Component.translatable("gui.arcforge.chemical_reactor_array.lanes"), VALUE_RIGHT - TEXT_X), TEXT_X, LANES_Y,
                ArcforgeGui.ACCENT, false);
        for (int lane = 0; lane < ChemicalReactorArrayBlockEntity.LANES; lane++) {
            int total = menu.getTotal(lane);
            Component text = total > 0 ? Component.literal(100 * menu.getProgress(lane) / total + "%")
                    : Component.translatable("gui.arcforge.chemical_reactor_array.idle");
            textRight(graphics, text, LANE_TEXT_RIGHT, laneY(lane) + 4, total > 0 ? ArcforgeGui.TEXT : ArcforgeGui.LABEL);
        }
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
            return;
        }
        for (int tank = 0; tank < TANK_X.length; tank++) {
            if (isHovering(TANK_X[tank] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
                addFluidTooltip(lines, menu.getFluid(tank), Component.translatable("gui.arcforge.empty"), menu.getFluidAmount(tank), menu.getTankCapacity());
                if (tank == ChemicalReactorArrayMenu.BYPRODUCT_TANK) {
                    lines.add(Component.translatable("gui.arcforge.chemical_reactor.byproduct_tank").withStyle(ChatFormatting.GRAY));
                }
                return;
            }
        }
        for (int lane = 0; lane < ChemicalReactorArrayBlockEntity.LANES; lane++) {
            int rowY = laneY(lane);
            if (isHovering(PROGRESS_X, rowY + 1, PROGRESS_W, PROGRESS_H, mouseX, mouseY) || isHovering(LANE_LED_X - 3, rowY + 1, LANE_TEXT_RIGHT - LANE_LED_X + 5, 14,
                    mouseX, mouseY)) {
                lines.add(Component.translatable("gui.arcforge.chemical_reactor_array.lane", lane + 1));
                if (menu.getTotal(lane) > 0) {
                    lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(lane), menu.getTotal(lane)).withStyle(ChatFormatting.GRAY));
                }
                return;
            }
        }
        if (isHovering(LED_X, STATUS_Y - 1, STATUS_W + 8, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        } else if (isHovering(TEXT_X, LANES_Y - 1, VALUE_RIGHT - TEXT_X, 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.chemical_reactor_array.lanes_hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
