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
import net.zagdrath.arcforge.menu.machine.HaberReactorMenu;

// The Chemical Reactor's frame without its slots: FE and the two input gas tanks on the left, the arrow, the heat buffer
// and the Ammonia tank on the right. Under the arrow: the temperature, the status, and the heat bar with a tick at the
// temperature it needs (450°C). All positions are relative to leftPos/topPos.
public class HaberReactorScreen extends MachineScreen<HaberReactorMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int[] TANK_X = { 25, 41, 155 };
    private static final int TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int BUFFER_X = 139, BUFFER_Y = 19;
    private static final int PROGRESS_X = 81, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int TEMP_X = 59, TEMP_Y = 22, TEXT_W = 76;
    private static final int LED_X = 59, LED_Y = 54;
    private static final int STATUS_X = 67, STATUS_Y = 54, STATUS_W = 68;
    private static final int HEAT_X = 59, HEAT_Y = 66, HEAT_W = 76;
    private static final int TICK_Y = 63, TICK_W = 3, TICK_H = 7;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier heatBuffer = sprite("heat_buffer");
    private final Identifier progress = sprite("progress");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier minTempTick = sprite("min_temp_tick");

    public HaberReactorScreen(HaberReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "haber_reactor", List.of(
                EnergyTab.usage(menu::getEnergy, menu::getUsage),
                new HeatTab(menu::getHeat,
                        Component.translatable("gui.arcforge.usage"),
                        () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()))));
    }

    // Ammonia leaves through this machine's Gas Output faces.
    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case ENERGY -> sideModeName("energy_input");
            case HEAT -> sideModeName("heat_input");
            case GAS_OUTPUT -> sideModeName("ammonia");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawGauge(graphics, heatBuffer, x, y, BUFFER_X, BUFFER_Y, menu.getHeat(), menu.getHeatCapacity());
        // The gases share the steam texture, tinted, so the gauge doubles as the (unused) fallback fill.
        for (int tank = 0; tank < TANK_X.length; tank++) {
            drawFluidTank(graphics, menu.getFluid(tank), menu.getFluidAmount(tank), menu.getTankCapacity(), tankGauge, tankGauge,
                    x, y, TANK_X[tank], TANK_Y, TANK_W, TANK_H);
        }
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
        // White tick at the temperature it needs to work.
        int tickX = HEAT_X + HeatScale.fillWidth(HEAT_W, menu.getMinTemperature()) - 1;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, minTempTick, x + tickX, y + TICK_Y, TICK_W, TICK_H);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(temperatureLine(), TEXT_W), TEMP_X, TEMP_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    private MutableComponent temperatureLine() {
        return Component.translatable("gui.arcforge.celsius_needs", menu.getTemperature(),
                Component.translatable("gui.arcforge.haber_reactor.needs", menu.getMinTemperature()));
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
            return;
        }
        if (isHovering(BUFFER_X - 1, BUFFER_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getHeatCapacity())));
            lines.add(temperatureLine().withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()).withStyle(ChatFormatting.GOLD));
            return;
        }
        for (int tank = 0; tank < TANK_X.length; tank++) {
            if (isHovering(TANK_X[tank] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
                Component empty = tank == 2 ? Component.translatable("fluid_type.arcforge.ammonia") : Component.translatable("gui.arcforge.empty");
                addFluidTooltip(lines, menu.getFluid(tank), empty, menu.getFluidAmount(tank), menu.getTankCapacity());
                return;
            }
        }
        if (isHovering(HEAT_X, TICK_Y, HEAT_W, TICK_H, mouseX, mouseY) || isHovering(TEMP_X, TEMP_Y - 1, TEXT_W, 10, mouseX, mouseY)) {
            lines.add(temperatureLine());
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
