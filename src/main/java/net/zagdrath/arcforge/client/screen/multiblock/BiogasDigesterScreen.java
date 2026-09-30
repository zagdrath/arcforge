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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.client.screen.machine.ArcCrusherScreen;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.BiogasDigesterMenu;

// The water tank on the left; plant matter -> arrow -> Digestate, and how many lanes are busy; the status and the heat
// bar with a tick at the temperature it needs (35°C) under them; the heat buffer and the Biogas tank on the right. All
// positions are relative to leftPos/topPos.
public class BiogasDigesterScreen extends MachineScreen<BiogasDigesterMenu> {
    private static final int WATER_X = 9, GAS_X = 155, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int BUFFER_X = 139, BUFFER_Y = 19;
    private static final int PROGRESS_X = 52, PROGRESS_Y = 27, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LANES_X = 100, LANES_Y = 24, BUSY_Y = 34, LANES_W = 36;
    private static final int LED_X = 27, LED_Y = 54;
    private static final int STATUS_X = 35, STATUS_Y = 54, STATUS_W = 100;
    private static final int HEAT_X = 27, HEAT_Y = 66, HEAT_W = 105;
    private static final int TICK_Y = 63, TICK_W = 3, TICK_H = 7;

    private final Identifier heatBuffer = sprite("heat_buffer");
    private final Identifier progress = sprite("progress");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier minTempTick = sprite("min_temp_tick");

    public BiogasDigesterScreen(BiogasDigesterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "biogas_digester", List.of(new HeatTab(menu::getHeat,
                Component.translatable("gui.arcforge.usage"),
                () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()),
                Component.translatable("gui.arcforge.temp"),
                () -> Component.translatable("gui.arcforge.celsius", ArcforgeGui.grouped(menu.getTemperature())))), false);
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case HEAT -> sideModeName("heat_input");
            case GAS_OUTPUT -> sideModeName("biogas");
            case BYPRODUCT -> sideModeName("digestate");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawFluidTank(graphics, net.minecraft.world.level.material.Fluids.WATER, menu.getWater(), menu.getWaterCapacity(), tankGauge, tankGauge,
                x, y, WATER_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, menu.getGasFluid(), menu.getGas(), menu.getGasCapacity(), tankGauge, tankGauge,
                x, y, GAS_X, TANK_Y, TANK_W, TANK_H);
        drawGauge(graphics, heatBuffer, x, y, BUFFER_X, BUFFER_Y, menu.getHeat(), menu.getHeatCapacity());
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
        // White tick at the temperature it needs to work.
        int tickX = HEAT_X + HeatScale.fillWidth(HEAT_W, menu.getMinTemperature()) - 1;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, minTempTick, x + tickX, y + TICK_Y, TICK_W, TICK_H);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(Component.translatable("gui.arcforge.biogas_digester.lanes"), LANES_W), LANES_X, LANES_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.literal(menu.getBusy() + " / " + menu.getLanes()), LANES_X, BUSY_Y, ArcforgeGui.ACCENT, false);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    private MutableComponent temperatureLine() {
        return Component.translatable("gui.arcforge.celsius_needs", menu.getTemperature(),
                Component.translatable("gui.arcforge.biogas_digester.needs", menu.getMinTemperature()));
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(WATER_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, net.minecraft.world.level.material.Fluids.WATER, Component.translatable("gui.arcforge.empty"),
                    menu.getWater(), menu.getWaterCapacity());
        } else if (isHovering(GAS_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getGasFluid(), Component.translatable("fluid_type.arcforge.biogas"), menu.getGas(), menu.getGasCapacity());
        } else if (isHovering(BUFFER_X - 1, BUFFER_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getHeatCapacity())));
            lines.add(temperatureLine().withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()).withStyle(ChatFormatting.GOLD));
        } else if (isHovering(HEAT_X, TICK_Y, HEAT_W, TICK_H, mouseX, mouseY)) {
            lines.add(temperatureLine());
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        } else if (isHovering(LANES_X, LANES_Y - 1, LANES_W, BUSY_Y - LANES_Y + 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.biogas_digester.lanes_hint", menu.getBusy(), menu.getLanes()).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
