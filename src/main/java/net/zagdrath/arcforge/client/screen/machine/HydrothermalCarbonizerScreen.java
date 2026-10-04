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
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.client.gui.tab.HeatTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.HydrothermalCarbonizerMenu;

// The Grain Dryer's layout: the water tank on the left, the biomass slot, the arrow and the Bio-Coal, the returned-water
// tank, the status, the heat bar with a tick at the temperature it needs (200°C), and the heat buffer on the right.
// Positions are relative to leftPos/topPos.
public class HydrothermalCarbonizerScreen extends MachineScreen<HydrothermalCarbonizerMenu> {
    private static final int BUFFER_X = 157, BUFFER_Y = 19;
    private static final int WATER_X = 25, RETURNED_X = 139, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int PROGRESS_X = 68, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LED_X = 43, LED_Y = 54;
    private static final int STATUS_X = 51, STATUS_Y = 54, STATUS_W = 84;
    private static final int HEAT_X = 43, HEAT_Y = 66, HEAT_W = 92;
    private static final int TICK_Y = 63, TICK_W = 3, TICK_H = 7;
    // The discard toggle under the returned-water tank, in the gap above the inventory (as the Electrolyzer's vents).
    private static final int DISCARD_X = RETURNED_X - 1, DISCARD_Y = 71, DISCARD_W = 14, DISCARD_H = 12;
    private static final int HOVER = 0x30FFFFFF;

    private final Identifier heatBuffer = sprite("heat_buffer");
    private final Identifier progress = sprite("progress");
    private final Identifier minTempTick = sprite("min_temp_tick");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier discardOn = sprite("vent_on");
    private final Identifier discardOff = sprite("vent_off");
    private int mouseX, mouseY;

    public HydrothermalCarbonizerScreen(HydrothermalCarbonizerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "hydrothermal_carbonizer", List.of(
                new HeatTab(menu::getHeat,
                        Component.translatable("gui.arcforge.usage"),
                        () -> Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()))));
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.HEAT ? sideModeName("heat_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, heatBuffer, x, y, BUFFER_X, BUFFER_Y, menu.getHeat(), menu.getHeatCapacity());
        drawFluidTank(graphics, Fluids.WATER, menu.getWater(), menu.getTankCapacity(), tankGauge, tankGauge, x, y, WATER_X, TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, Fluids.WATER, menu.getReturned(), menu.getTankCapacity(), tankGauge, tankGauge, x, y, RETURNED_X, TANK_Y, TANK_W, TANK_H);
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
        drawHeatBar(graphics, x, y, HEAT_X, HEAT_Y, HEAT_W, menu.getTemperature());
        // White tick at the temperature it needs to work.
        int tickX = HEAT_X + HeatScale.fillWidth(HEAT_W, menu.getMinTemperature()) - 1;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, minTempTick, x + tickX, y + TICK_Y, TICK_W, TICK_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.isDiscardingWater() ? discardOn : discardOff, x + DISCARD_X, y + DISCARD_Y,
                DISCARD_W, DISCARD_H);
        if (isHovering(DISCARD_X, DISCARD_Y, DISCARD_W, DISCARD_H, mouseX, mouseY)) {
            graphics.fill(x + DISCARD_X, y + DISCARD_Y, x + DISCARD_X + DISCARD_W, y + DISCARD_Y + DISCARD_H, HOVER);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && minecraft.gameMode != null
                && isHovering(DISCARD_X, DISCARD_Y, DISCARD_W, DISCARD_H, event.x(), event.y())) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, HydrothermalCarbonizerMenu.BUTTON_DISCARD);
            ArcforgeGui.playClickSound();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    private MutableComponent temperatureLine() {
        return Component.translatable("gui.arcforge.celsius_needs", menu.getTemperature(),
                Component.translatable("gui.arcforge.hydrothermal_carbonizer.needs", menu.getMinTemperature()));
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(DISCARD_X, DISCARD_Y, DISCARD_W, DISCARD_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hydrothermal_carbonizer.discard"));
            lines.add(Component.translatable(menu.isDiscardingWater() ? "gui.arcforge.hydrothermal_carbonizer.discard_on"
                    : "gui.arcforge.hydrothermal_carbonizer.discard_off").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (isHovering(BUFFER_X - 1, BUFFER_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.hu_stored", ArcforgeGui.grouped(menu.getHeat()), ArcforgeGui.grouped(menu.getHeatCapacity())));
            lines.add(temperatureLine().withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.hu_per_tick_loss", menu.getHeatUsage()).withStyle(ChatFormatting.GOLD));
        } else if (isHovering(WATER_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, Fluids.WATER, Component.translatable("gui.arcforge.empty"), menu.getWater(), menu.getTankCapacity());
        } else if (isHovering(RETURNED_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, Fluids.WATER, Component.translatable("gui.arcforge.empty"), menu.getReturned(), menu.getTankCapacity());
            lines.add(Component.translatable("gui.arcforge.hydrothermal_carbonizer.returned_hint").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(HEAT_X, TICK_Y, HEAT_W, TICK_H, mouseX, mouseY)) {
            lines.add(temperatureLine());
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
