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
import net.zagdrath.arcforge.menu.machine.ChemicalReactorMenu;

// All positions are relative to leftPos/topPos. Three input tanks on the left, the two input slots stacked beside them,
// the arrow, the output and by-product slots stacked after it, then the output and by-product tanks on the right; the
// status runs along under the tanks.
public class ChemicalReactorScreen extends MachineScreen<ChemicalReactorMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    // The three input tanks, the output tank and the by-product tank (ChemicalReactorMenu's tank order).
    private static final int[] TANK_X = { 25, 40, 55, 137, 155 };
    private static final int TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int PROGRESS_X = 90, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LED_X = 25, LED_Y = 73;
    private static final int STATUS_X = 33, STATUS_Y = 73, STATUS_W = 135;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");
    private final Identifier tankGauge = sprite("tank_gauge");

    public ChemicalReactorScreen(ChemicalReactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "chemical_reactor", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        // Every fluid here has a model, so the gauge doubles as the (unused) fallback fill.
        for (int tank = 0; tank < TANK_X.length; tank++) {
            drawFluidTank(graphics, menu.getFluid(tank), menu.getFluidAmount(tank), menu.getTankCapacity(), tankGauge, tankGauge,
                    x, y, TANK_X[tank], TANK_Y, TANK_W, TANK_H);
        }
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
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
                if (tank == ChemicalReactorMenu.BYPRODUCT_TANK) {
                    lines.add(Component.translatable("gui.arcforge.chemical_reactor.byproduct_tank").withStyle(ChatFormatting.GRAY));
                }
                return;
            }
        }
        if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
