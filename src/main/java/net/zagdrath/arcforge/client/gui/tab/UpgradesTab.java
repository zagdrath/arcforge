/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.menu.slot.ToggleableSlot;

// Hosts the machine's upgrade slots. The slots are only active while this tab is fully open,
// and must be positioned in the menu where this tab's panel appears (see GeothermalPlantMenu).
public class UpgradesTab extends SideTab {
    private static final Identifier SLOT = ArcforgeGui.widget("slot_upgrade");
    private static final int SLOTS_X = 8, SLOTS_Y = 24, SLOT_PITCH = 20;

    private final List<ToggleableSlot> slots;

    public UpgradesTab(List<ToggleableSlot> slots) {
        super(ArcforgeGui.widget("icon_upgrades"), Component.translatable("gui.arcforge.tab.upgrades"), 100, 50);
        this.slots = slots;
        onFullyOpenChanged(false);
    }

    @Override
    protected void onFullyOpenChanged(boolean fullyOpen) {
        slots.forEach(slot -> slot.setActive(fullyOpen));
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        for (int i = 0; i < slots.size(); i++) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x + SLOTS_X + i * SLOT_PITCH, y + SLOTS_Y, 18, 18);
        }
    }
}
