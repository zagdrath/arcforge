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
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.BlockPlacerMenu;

// Layout follows gui_layouts.json "block_placer". The slot it places from next has a cyan frame.
public class BlockPlacerScreen extends MachineScreen<BlockPlacerMenu> {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int GRID_SIZE = 54;

    private final Identifier energyBar = sprite("energy_bar");

    public BlockPlacerScreen(BlockPlacerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "block_placer", List.of(EnergyTab.usage(menu::getEnergy, () -> 0)));
    }

    @Override
    protected List<RedstoneMode> redstoneModes() {
        return RedstoneMode.WITH_PULSE;
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        int next = menu.getNextSlot();
        if (next >= 0) {
            int sx = BlockPlacerMenu.GRID_X + (next % 3) * 18;
            int sy = BlockPlacerMenu.GRID_Y + (next / 3) * 18;
            graphics.outline(sx - 1, sy - 1, 18, 18, ArcforgeGui.ACCENT);
        }
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            return;
        }
        // Over the grid but not a filled slot (the slot shows its own item tooltip).
        if (isHovering(BlockPlacerMenu.GRID_X - 1, BlockPlacerMenu.GRID_Y - 1, GRID_SIZE, GRID_SIZE, mouseX, mouseY)
                && (hoveredSlot == null || !hoveredSlot.hasItem())) {
            if (menu.isFrontBlocked()) {
                lines.add(Component.translatable("gui.arcforge.status.front_blocked"));
            } else if (menu.getNextSlot() >= 0) {
                ItemStack next = menu.getSlot(menu.getNextSlot()).getItem();
                lines.add(Component.translatable("gui.arcforge.placer.next", next.getHoverName()));
            }
            lines.add(menu.getStatus().getDescription().copy().withStyle(ChatFormatting.GRAY));
        }
    }
}
