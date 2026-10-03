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
import net.zagdrath.arcforge.menu.machine.TreeCutterMenu;

// The Block Breaker's layout: the FE gauge on the left, three sapling slots and the Bone Meal slot (with a ghost) beside
// the area's size and the last tree's logs, the status, and the 3x3 buffer on the right.
public class TreeCutterScreen extends MachineScreen<TreeCutterMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int INFO_X = 50, AREA_Y = 40, LOGS_Y = 49, INFO_W = 45;
    private static final int LED_X = 30, LED_Y = 62;
    // Clipped short of the buffer grid (x 97).
    private static final int STATUS_X = 38, STATUS_Y = 62, STATUS_W = 57;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier ghostSapling = sprite("ghost_sapling");
    private final Identifier ghostBoneMeal = sprite("ghost_bone_meal");

    public TreeCutterScreen(TreeCutterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "tree_cutter", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)));
        enableAutoEject();
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        ghost(graphics, ghostSapling, !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem(),
                TreeCutterMenu.SAPLING_X, TreeCutterMenu.SAPLING_Y);
        ghost(graphics, ghostBoneMeal, !menu.getSlot(3).hasItem(), TreeCutterMenu.FERTILIZER_X, TreeCutterMenu.FERTILIZER_Y);
        drawLed(graphics, x, y, LED_X, LED_Y);
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        int size = menu.getSize();
        graphics.text(font, clipped(Component.translatable("gui.arcforge.tree_cutter.area", size, size), INFO_W), INFO_X, AREA_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, clipped(Component.translatable("gui.arcforge.tree_cutter.logs", menu.getLastLogs()), INFO_W), INFO_X, LOGS_Y,
                ArcforgeGui.LABEL, false);
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
        } else if (isHovering(INFO_X, AREA_Y - 1, INFO_W, 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.tree_cutter.area_hint", menu.getSize(), menu.getSize()));
        } else if (isHovering(INFO_X, LOGS_Y - 1, INFO_W, 10, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.tree_cutter.felled", menu.getFelled()));
        } else if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, 10, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
        }
    }
}
