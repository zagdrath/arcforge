/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.multiblock.OilDerrickMenu;

// The Electric Pump's frame: the FE gauge on the left, the screen (how deep the hole is against how deep the oil lies,
// the Steel Pipes still to come, the depth as a bar, the status), the Steel Pipe slot and the Water tank on the right.
// All positions are relative to leftPos/topPos.
public class OilDerrickScreen extends MachineScreen<OilDerrickMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int TANK_X = 155, TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int LABEL_X = 31, VALUE_RIGHT = 121, DEPTH_Y = 23, PIPES_Y = 34;
    private static final int BAR_X = 31, BAR_Y = 45, BAR_W = 90, BAR_H = 4;
    private static final int LED_X = 31, LED_Y = 56, STATUS_X = 39, STATUS_Y = 56, STATUS_W = 82;
    private static final int BAR_EMPTY = 0xFF0E0E0E;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier waterFill = sprite("water_fill");
    private final Identifier tankGauge = sprite("tank_gauge");
    private final Identifier ghostPipe = sprite("ghost_pipe");

    public OilDerrickScreen(OilDerrickMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "oil_derrick", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)), false);
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return switch (mode) {
            case ENERGY -> sideModeName("energy_input");
            case INPUT -> sideModeName("water_pipe_input");
            default -> super.sideModeName(mode);
        };
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawFluidTank(graphics, Fluids.WATER, menu.getWater(), menu.getWaterCapacity(), waterFill, tankGauge, x, y, TANK_X, TANK_Y, TANK_W, TANK_H);
        graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_W, y + BAR_Y + BAR_H, BAR_EMPTY);
        int target = menu.getTarget();
        if (target > 0) {
            float done = (menu.getDepth() + menu.getProgress() / (float) Math.max(1, menu.getTicksPerBlock())) / target;
            int width = Math.min(BAR_W, Mth.ceil(BAR_W * done));
            if (width > 0) {
                graphics.fill(x + BAR_X, y + BAR_Y, x + BAR_X + width, y + BAR_Y + BAR_H, ArcforgeGui.ACCENT);
            }
        }
        drawLed(graphics, x, y, LED_X, LED_Y);
        ghost(graphics, ghostPipe, !menu.getSlot(0).hasItem(), OilDerrickMenu.PIPES_X, OilDerrickMenu.PIPES_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.oil_derrick.depth"), LABEL_X, DEPTH_Y, ArcforgeGui.LABEL, false);
        Component depth = menu.getTarget() > 0
                ? Component.translatable("gui.arcforge.oil_derrick.depth_of", menu.getDepth(), menu.getTarget())
                : Component.translatable("gui.arcforge.oil_derrick.no_oil");
        textRight(graphics, depth, VALUE_RIGHT, DEPTH_Y, ArcforgeGui.TEXT);
        graphics.text(font, Component.translatable("gui.arcforge.oil_derrick.pipes"), LABEL_X, PIPES_Y, ArcforgeGui.LABEL, false);
        textRight(graphics, Component.literal(String.valueOf(menu.getPipesNeeded())), VALUE_RIGHT, PIPES_Y, ArcforgeGui.ACCENT);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(TANK_X - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, Fluids.WATER, Fluids.WATER.getFluidType().getDescription(), menu.getWater(), menu.getWaterCapacity());
            lines.add(Component.translatable("gui.arcforge.oil_derrick.mud").withStyle(ChatFormatting.DARK_GRAY));
        } else if (isHovering(LABEL_X, PIPES_Y - 1, VALUE_RIGHT - LABEL_X, 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.oil_derrick.pipes_hint").withStyle(ChatFormatting.GRAY));
        } else if (isHovering(BAR_X, BAR_Y - 2, BAR_W, BAR_H + 4, mouseX, mouseY) && menu.getTarget() > 0) {
            lines.add(Component.translatable("gui.arcforge.oil_derrick.depth_of", menu.getDepth(), menu.getTarget()));
        } else if (isHovering(LED_X, STATUS_Y - 1, VALUE_RIGHT - LED_X, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
