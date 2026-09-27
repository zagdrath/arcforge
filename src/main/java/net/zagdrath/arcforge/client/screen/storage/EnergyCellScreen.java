/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.storage;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.RedstoneTab;
import net.zagdrath.arcforge.client.gui.tab.SideConfigTab;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.storage.EnergyCellMenu;

// Layout follows layout.json -> energy_cell.
public class EnergyCellScreen extends StorageScreen<EnergyCellMenu> {
    // The recess is baked into the background; the fill sits 1px inside it.
    private static final int RECESS_X = 64, RECESS_Y = 16, RECESS_W = 16, RECESS_H = 56;
    private static final int FILL_X = 65, FILL_Y = 17, FILL_W = 14, FILL_H = 54;
    private static final int TEXT_X = 91;
    // The amount and the capacity get a line each, so big numbers still fit the screen.
    private static final int STORED_LABEL_Y = 21, STORED_Y = 30, CAPACITY_Y = 39, IN_Y = 49, OUT_Y = 58;
    private static final int IN_COLOR = 0xFF55FF55, OUT_COLOR = 0xFFFF5555;

    private final Identifier energyFill = sprite("energy_fill");

    public EnergyCellScreen(EnergyCellMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "energy_cell");
        tabs.add(new RedstoneTab(menu::getRedstoneMode, mode -> sendButton(MachineMenuButtons.redstoneButtonId(mode))))
                .add(new SideConfigTab(menu::getSideMode,
                        (side, action) -> sendButton(MachineMenuButtons.sideButtonId(side, action)),
                        () -> sendButton(MachineMenuButtons.CLEAR_SIDES)));
    }

    // Cropped from the bottom (not scaled) so the segment lines stay put.
    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        int height = scaledRound(menu.getEnergy(), menu.getCapacity(), FILL_H);
        if (height > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, energyFill, FILL_W, FILL_H, 0, FILL_H - height,
                    x + FILL_X, y + FILL_Y + FILL_H - height, FILL_W, height);
        }
    }

    @Override
    protected void drawInfo(GuiGraphicsExtractor graphics) {
        graphics.text(font, Component.translatable("gui.arcforge.stored"), TEXT_X, STORED_LABEL_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.fe_amount", ArcforgeGui.compact(menu.getEnergy())), TEXT_X, STORED_Y, ArcforgeGui.WHITE, false);
        graphics.text(font, Component.translatable("gui.arcforge.fe_capacity", ArcforgeGui.compact(menu.getCapacity())), TEXT_X, CAPACITY_Y, ArcforgeGui.LABEL, false);
        graphics.text(font, Component.translatable("gui.arcforge.fe_in", ArcforgeGui.compact(menu.getReceivedPerTick())), TEXT_X, IN_Y, IN_COLOR, false);
        graphics.text(font, Component.translatable("gui.arcforge.fe_out", ArcforgeGui.compact(menu.getExtractedPerTick())), TEXT_X, OUT_Y, OUT_COLOR, false);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(RECESS_X, RECESS_Y, RECESS_W, RECESS_H, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
