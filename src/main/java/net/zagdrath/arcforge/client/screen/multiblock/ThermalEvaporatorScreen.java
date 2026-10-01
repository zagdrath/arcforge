/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;
import java.util.Locale;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.client.screen.machine.ArcCrusherScreen;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.ThermalEvaporatorMenu;

// The input tank (Seawater or Brine) on the left, an arrow to the Salt slot, and the evaporation rate; the status and the
// heat bar, with a tick at the temperature it needs (100°C), under them; the Brine tank, the heat buffer and the Water
// tank on the right. All positions are relative to leftPos/topPos (the background is cut from the Biogas Digester's).
public class ThermalEvaporatorScreen extends MachineScreen<ThermalEvaporatorMenu> {
    private static final int INPUT_X = 9, OUTPUT_X = 123, WATER_X = 155, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int BUFFER_X = 139, BUFFER_Y = 19;
    private static final int PROGRESS_X = 28, PROGRESS_Y = 27, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int RATE_X = 76, RATE_Y = 24, RATE_VALUE_Y = 34, RATE_W = 43;
    private static final int LED_X = 27, LED_Y = 54;
    private static final int STATUS_X = 35, STATUS_Y = 54, STATUS_W = 84;
    private static final int HEAT_X = 27, HEAT_Y = 66, HEAT_W = 92;
    private static final int TICK_Y = 63, TICK_W = 3, TICK_H = 7;

    private final Identifier heatBuffer = sprite("heat_buffer");
    private final Identifier progress = sprite("progress");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier minTempTick = sprite("min_temp_tick");

    public ThermalEvaporatorScreen(ThermalEvaporatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "thermal_evaporator", List.of(new HeatTab(menu::getHeat,
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
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawFluidTank(graphics, menu.getInputFluid(), menu.getInput(), menu.getInputCapacity(), tankGauge, tankGauge, x, y, INPUT_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, menu.getOutputFluid(), menu.getOutput(), menu.getOutputCapacity(), tankGauge, tankGauge, x, y, OUTPUT_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, Fluids.WATER, menu.getWater(), menu.getWaterCapacity(), tankGauge, tankGauge, x, y, WATER_X, TANK_Y, TANK_W, TANK_H);
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
        graphics.text(font, clipped(Component.translatable("gui.arcforge.thermal_evaporator.rate"), RATE_W), RATE_X, RATE_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, clipped(Component.translatable("gui.arcforge.mb_per_tick", String.format(Locale.ROOT, "%.1f", menu.getRate())), RATE_W),
                RATE_X, RATE_VALUE_Y, ArcforgeGui.ACCENT, false);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    private MutableComponent temperatureLine() {
        return Component.translatable("gui.arcforge.celsius_needs", menu.getTemperature(),
                Component.translatable("gui.arcforge.thermal_evaporator.needs", menu.getMinTemperature()));
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(INPUT_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getInputFluid(), Component.translatable("gui.arcforge.thermal_evaporator.input"), menu.getInput(),
                    menu.getInputCapacity());
        } else if (isHovering(OUTPUT_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, menu.getOutputFluid(), Component.translatable("fluid_type.arcforge.brine"), menu.getOutput(), menu.getOutputCapacity());
        } else if (isHovering(WATER_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, Fluids.WATER, Component.translatable("gui.arcforge.empty"), menu.getWater(), menu.getWaterCapacity());
            lines.add(Component.translatable("gui.arcforge.thermal_evaporator.water_hint").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(BUFFER_X - 1, BUFFER_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getHeatCapacity())));
            lines.add(temperatureLine().withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()).withStyle(ChatFormatting.GOLD));
        } else if (isHovering(HEAT_X, TICK_Y, HEAT_W, TICK_H, mouseX, mouseY)) {
            lines.add(temperatureLine());
            lines.add(Component.translatable("gui.arcforge.thermal_evaporator.speed_hint").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.thermal_evaporator.progress", menu.getProgress(), menu.getTotal()));
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
