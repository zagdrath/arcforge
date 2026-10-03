/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.arcforge.blockentity.machine.AssemblerBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.EnergyTab;
import net.zagdrath.arcforge.client.screen.GhostSlotScreen;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.machine.AssemblerMenu;
import net.zagdrath.arcforge.network.AssemblerPatternPayload;

// Layout follows gui_layouts.json "assembler" (176x206): the ghost pattern on the left, progress, the output with
// the result ghost and the two remainder slots, then the 18-slot buffer. Clicking a pattern cell sets or clears it
// (see AssemblerMenu); nothing is picked up. An item dragged from JEI onto a cell sets it too, through the same
// AssemblerPatternPayload as JEI's + (the whole pattern, with that cell changed).
public class AssemblerScreen extends MachineScreen<AssemblerMenu> implements GhostSlotScreen {
    private static final int ENERGY_X = 9, ENERGY_Y = 19;
    private static final int PROGRESS_X = 88, PROGRESS_Y = 35, PROGRESS_W = 21, PROGRESS_H = 15;
    // Under the arrow, clear of the output and leftover slots, and clipped short of the panel's edge (and the tabs).
    private static final int LED_X = 88, LED_Y = 64;
    private static final int STATUS_X = 96, STATUS_Y = 63, STATUS_W = 72;
    // Ghosts are drawn under a wash of the panel colour.
    private static final int GHOST_WASH = 0x808B8B8B;

    private final Identifier energyBar = sprite("energy_bar");
    private final Identifier progress = sprite("progress");

    public AssemblerScreen(AssemblerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, "assembler", List.of(EnergyTab.usage(menu::getEnergy, menu::getUsage)), true, 206);
        enableAutoEject();
    }

    // --- Pattern cells dragged from JEI ---

    @Override
    public int ghostSlotCount() {
        return AssemblerBlockEntity.PATTERN_SIZE;
    }

    @Override
    public Rect2i ghostSlotArea(int cell) {
        return new Rect2i(leftPos + AssemblerMenu.PATTERN_X + (cell % 3) * 18, topPos + AssemblerMenu.PATTERN_Y + (cell / 3) * 18, 16, 16);
    }

    // Any item, as clicking a cell with it takes.
    @Override
    public boolean acceptsGhostItem(int cell, ItemStack stack) {
        return !stack.isEmpty();
    }

    @Override
    public void setGhostItem(int cell, ItemStack stack) {
        List<ItemStack> cells = new ArrayList<>(AssemblerBlockEntity.PATTERN_SIZE);
        for (int i = 0; i < AssemblerBlockEntity.PATTERN_SIZE; i++) {
            cells.add(i == cell ? stack.copyWithCount(1) : menu.getPatternCell(i).copy());
        }
        ClientPacketDistributor.sendToServer(new AssemblerPatternPayload(menu.containerId, cells));
    }

    @Override
    protected Component sideModeName(SideMode mode) {
        return mode == SideMode.ENERGY ? sideModeName("energy_input") : super.sideModeName(mode);
    }

    @Override
    protected void drawContents(GuiGraphicsExtractor graphics, int x, int y) {
        drawGauge(graphics, energyBar, x, y, ENERGY_X, ENERGY_Y, menu.getEnergy(), menu.getCapacity());
        ArcCrusherScreen.drawProgress(graphics, progress, x + PROGRESS_X, y + PROGRESS_Y, menu.getProgress(), menu.getTotal());
        drawLed(graphics, x, y, LED_X, LED_Y);
        // What the pattern makes, as a ghost while the output slot is empty.
        ItemStack preview = menu.getPreview();
        if (!preview.isEmpty() && !menu.getSlot(AssemblerBlockEntity.SLOT_OUTPUT).hasItem()) {
            graphics.item(preview, x + AssemblerMenu.OUTPUT_X, y + AssemblerMenu.OUTPUT_Y);
            graphics.fill(x + AssemblerMenu.OUTPUT_X, y + AssemblerMenu.OUTPUT_Y, x + AssemblerMenu.OUTPUT_X + 16, y + AssemblerMenu.OUTPUT_Y + 16, GHOST_WASH);
        }
    }

    @Override
    protected void drawText(GuiGraphicsExtractor graphics) {
        // The pattern items are only ghosts: wash them over.
        for (int cell = 0; cell < 9; cell++) {
            int cx = AssemblerMenu.PATTERN_X + (cell % 3) * 18;
            int cy = AssemblerMenu.PATTERN_Y + (cell / 3) * 18;
            if (!menu.getPatternCell(cell).isEmpty()) {
                graphics.fill(cx, cy, cx + 16, cy + 16, GHOST_WASH);
            }
        }
        graphics.text(font, clipped(menu.getStatus().getDescription(), STATUS_W), STATUS_X, STATUS_Y, ArcforgeGui.TEXT, false);
    }

    // Clicking a pattern cell sets it from the carried item or clears it, instead of picking anything up.
    @Override
    protected void slotClicked(Slot slot, int slotId, int buttonNum, ContainerInput containerInput) {
        if (slot != null && menu.isPatternSlot(slot)) {
            int cell = menu.patternCell(slot);
            boolean set = buttonNum == InputConstants.MOUSE_BUTTON_LEFT && !menu.getCarried().isEmpty();
            int button = set ? AssemblerMenu.BUTTON_SET_CELL + cell : AssemblerMenu.BUTTON_CLEAR_CELL + cell;
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
            }
            return;
        }
        super.slotClicked(slot, slotId, buttonNum, containerInput);
    }

    @Override
    protected void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        if (isHovering(ENERGY_X - 1, ENERGY_Y - 1, GAUGE_W + 2, GAUGE_H + 2, mouseX, mouseY)) {
            lines.add(Component.translatable("gui.arcforge.fe_stored", ArcforgeGui.grouped(menu.getEnergy()), ArcforgeGui.grouped(menu.getCapacity())).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.fe_per_tick_loss", menu.getUsage()).withStyle(ChatFormatting.RED));
            return;
        }
        if (isHovering(AssemblerMenu.OUTPUT_X - 1, AssemblerMenu.OUTPUT_Y - 1, 18, 18, mouseX, mouseY)
                && !menu.getSlot(AssemblerBlockEntity.SLOT_OUTPUT).hasItem() && !menu.getPreview().isEmpty()) {
            lines.add(menu.getPreview().getHoverName());
            return;
        }
        if (isHovering(STATUS_X, STATUS_Y - 1, STATUS_W, font.lineHeight + 1, mouseX, mouseY)) {
            lines.add(menu.getStatus().getDescription());
            return;
        }
        if (isHovering(PROGRESS_X, PROGRESS_Y, PROGRESS_W, PROGRESS_H, mouseX, mouseY) && menu.getTotal() > 0) {
            lines.add(Component.translatable("gui.arcforge.progress_ticks", menu.getProgress(), menu.getTotal()));
        }
    }
}
