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
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.CarbonReclaimerMenu;
import net.zagdrath.arcforge.registry.ModFluids;

// FE and the two gas tanks (Carbon Dioxide, Hydrogen) on the left, the cost over the arrow, the Carbon Dust, and the
// Water tank on the right; the status under them. The arrow fills with the FE spent on the operation. Positions are
// relative to leftPos/topPos.
public class CarbonReclaimerScreen extends MachineScreen<CarbonReclaimerMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int[] TANK_X = { 25, 41, 155 };
    private static final int TANK_Y = 19, TANK_W = 12, TANK_H = 50;
    private static final int PROGRESS_X = 68, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int COST_X = 59, COST_Y = 20, COST_W = 92;
    private static final int LED_X = 59, LED_Y = 61;
    private static final int STATUS_X = 67, STATUS_Y = 61, STATUS_W = 84;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");
    private final Identifier tankGauge = sprite("tank_gauge");

    public CarbonReclaimerScreen(CarbonReclaimerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "carbon_reclaimer", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        drawFluidTank(graphics, ModFluids.CARBON_DIOXIDE.get(), menu.getCarbonDioxide(), menu.getTankCapacity(), tankGauge, tankGauge,
                x, y, TANK_X[0], TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, ModFluids.HYDROGEN.get(), menu.getHydrogen(), menu.getTankCapacity(), tankGauge, tankGauge,
                x, y, TANK_X[1], TANK_Y, TANK_W, TANK_H);
        drawFluidTank(graphics, Fluids.WATER, menu.getWater(), menu.getTankCapacity(), tankGauge, tankGauge, x, y, TANK_X[2], TANK_Y, TANK_W, TANK_H);
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getSpent(), menu.getCost());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(Component.translatable("gui.arcforge.carbon_reclaimer.cost", ArcforgeGui.grouped(menu.getCost())), COST_W),
                COST_X, COST_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(TANK_X[0] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, ModFluids.CARBON_DIOXIDE.get(), Component.translatable("gui.arcforge.empty"), menu.getCarbonDioxide(), menu.getTankCapacity());
        } else if (isHovering(TANK_X[1] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, ModFluids.HYDROGEN.get(), Component.translatable("gui.arcforge.empty"), menu.getHydrogen(), menu.getTankCapacity());
        } else if (isHovering(TANK_X[2] - 1, TANK_Y - 1, TANK_W + 2, TANK_H + 2, mouseX, mouseY)) {
            addFluidTooltip(lines, Fluids.WATER, Component.translatable("gui.arcforge.empty"), menu.getWater(), menu.getTankCapacity());
        } else if (isHovering(COST_X, COST_Y - 1, COST_W, 10, mouseX, mouseY) || isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.carbon_reclaimer.spent", ArcforgeGui.grouped(menu.getSpent()), ArcforgeGui.grouped(menu.getCost())));
            lines.add(Component.translatable("gui.arcforge.carbon_reclaimer.floor", ArcforgeGui.grouped(menu.getFloor())).withStyle(ChatFormatting.GRAY));
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
