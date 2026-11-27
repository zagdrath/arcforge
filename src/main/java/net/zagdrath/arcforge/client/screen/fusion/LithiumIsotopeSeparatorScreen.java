/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.fusion;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.screen.machine.ArcCrusherScreen;
import net.zagdrath.arcforge.client.screen.machine.MachineScreen;
import net.zagdrath.arcforge.fusion.Lithium6;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.fusion.LithiumIsotopeSeparatorMenu;

// The Lithium Isotope Separator's GUI (textures/gui/container/lithium_isotope_separator.png, the Induction Furnace's
// layout): the FE gauge, the feed, the progress arrow and the product, the status and the enrichment it comes out at.
public class LithiumIsotopeSeparatorScreen extends MachineScreen<LithiumIsotopeSeparatorMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int PROGRESS_X = 78, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LED_X = 52, LED_Y = 58;
    private static final int STATUS_X = 60, STATUS_Y = 58, STATUS_W = 104;
    private static final int ASSAY_Y = 22;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");

    public LithiumIsotopeSeparatorScreen(LithiumIsotopeSeparatorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "lithium_isotope_separator", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getEnergyCapacity());
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
        if (menu.getNextAssay() > 0) {
            Component assay = Component.translatable("gui.arcforge.lithium_isotope_separator.assay", Lithium6.percent(menu.getNextAssay()));
            graphics.text(font, assay, PROGRESS_X + PROGRESS_W / 2 - font.width(assay) / 2, ASSAY_Y, ArcforgeGui.ACCENT, false);
        }
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getEnergyCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
            lines.add(Component.translatable("gui.arcforge.lithium_isotope_separator.hint").withStyle(ChatFormatting.GRAY));
        }
    }
}
