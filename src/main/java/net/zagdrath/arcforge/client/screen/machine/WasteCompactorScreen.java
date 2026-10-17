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
import net.zagdrath.arcforge.blockentity.machine.WasteCompactorBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.WasteCompactorMenu;

// The Waste Compactor's GUI (textures/gui/container/waste_compactor.png): the FE gauge, the waste over an empty drum,
// the progress arrow and the full drum, the status line under them.
public class WasteCompactorScreen extends MachineScreen<WasteCompactorMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int PROGRESS_X = 82, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    private static final int LED_X = 56, LED_Y = 66;
    private static final int STATUS_X = 64, STATUS_Y = 66;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");
    private final Identifier ghostDrum = sprite("ghost_drum");

    public WasteCompactorScreen(WasteCompactorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "waste_compactor", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
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
        ghost(graphics, ghostDrum, !menu.getSlotFor(WasteCompactorBlockEntity.SLOT_DRUM).hasItem(), WasteCompactorMenu.DRUM_X, WasteCompactorMenu.DRUM_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        graphics.text(font, menu.getStatus().getDescription(), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getEnergyCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        }
    }
}
