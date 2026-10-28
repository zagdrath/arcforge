/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

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
    private @Nullable Supplier<Component> info;

    public UpgradesTab(List<ToggleableSlot> slots) {
        super(ArcforgeGui.widget("icon_upgrades"), Component.translatable("gui.arcforge.tab.upgrades"), 100, 50);
        this.slots = slots;
        onFullyOpenChanged(false);
    }

    // A line beside the slots saying what the upgrades do (e.g. a Heat Cell's "Leak x0.17").
    public UpgradesTab withInfo(Supplier<Component> info) {
        this.info = info;
        return this;
    }

    @Override
    public boolean isVisible() {
        return !slots.isEmpty();
    }

    @Override
    protected int expandedWidth() {
        return Math.max(100, 16 + slots.size() * SLOT_PITCH + (info != null ? 80 : 0));
    }

    @Override
    protected void addTooltip(List<Component> lines, int localX, int localY) {
        int index = (localX - SLOTS_X) / SLOT_PITCH;
        if (localX >= SLOTS_X && localY >= SLOTS_Y && localY < SLOTS_Y + 18 && index < slots.size()
                && slots.get(index).upgradeType() != null && !slots.get(index).hasItem()) {
            var type = slots.get(index).upgradeType();
            lines.add(Component.translatable("item.arcforge." + (type == net.zagdrath.arcforge.upgrade.UpgradeType.AUTOPILOT
                    ? "reactor_autopilot" : type.getSerializedName() + "_upgrade")));
        }
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
        if (info != null) {
            graphics.text(font, info.get(), x + SLOTS_X + slots.size() * SLOT_PITCH + 2, y + SLOTS_Y + 5, ArcforgeGui.TOOLTIP_GRAY, false);
        }
    }
}
