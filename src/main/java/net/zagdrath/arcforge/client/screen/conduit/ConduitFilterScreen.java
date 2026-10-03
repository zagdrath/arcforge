/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.conduit;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.screen.GhostSlotScreen;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.item.conduit.ConduitFilterItem;
import net.zagdrath.arcforge.menu.conduit.ConduitFilterMenu;

// The Conduit Filter's settings: nine ghost slots with a match chip under each set one (Exact, or a tag), and
// buttons for allowlist/denylist, components (item conduits only) and direction, with a summary beside them.
// Everything drawn comes from the installed filter as synced; every click is a menu button (see ConduitFilterMenu).
// Positions are relative to leftPos/topPos.
public class ConduitFilterScreen extends AbstractContainerScreen<ConduitFilterMenu> implements GhostSlotScreen {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/conduit_filter.png");
    private static final Identifier BUTTON = ArcforgeGui.widget("button");
    private static final Identifier BUTTON_HOVER = ArcforgeGui.widget("button_hover");
    private static final Identifier FLUID_FALLBACK = ArcforgeGui.widget("icon_clear");

    private static final int SLOT_X = 9, SLOT_Y = 19, SLOT_PITCH = 18;
    private static final int CHIP_X = SLOT_X + 4, CHIP_Y = 38, CHIP_WIDTH = 8, CHIP_HEIGHT = 6;
    private static final int BUTTON_Y = 46, BUTTON_SIZE = 20;
    private static final int LIST_MODE_X = 8, COMPONENTS_X = 30, DIRECTION_X = 52;
    private static final int SUMMARY_X = 76, SUMMARY_Y = 52;
    private static final int HOVER = 0x80FFFFFF;

    public ConduitFilterScreen(ConduitFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private static Identifier sprite(String name) {
        return ArcforgeGui.sprite("container/conduit_filter/" + name);
    }

    @Override
    public void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
    }

    // --- Layout ---

    private static int slotX(int slot) {
        return SLOT_X + slot * SLOT_PITCH;
    }

    @Override
    public Rect2i ghostSlotArea(int slot) {
        return new Rect2i(leftPos + slotX(slot), topPos + SLOT_Y, 16, 16);
    }

    private static int chipX(int slot) {
        return CHIP_X + slot * SLOT_PITCH;
    }

    // The ghost slot under the mouse (screen coordinates), or -1.
    private int slotAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < FilterSettings.SIZE; slot++) {
            if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + slotX(slot) - 1, topPos + SLOT_Y - 1, 18, 18)) {
                return slot;
            }
        }
        return -1;
    }

    // The chip under the mouse (only set slots show one), or -1.
    private int chipAt(double mouseX, double mouseY) {
        FilterSettings settings = menu.getSettings();
        for (int slot = 0; slot < FilterSettings.SIZE; slot++) {
            if (!settings.entry(slot).isEmpty()
                    && ArcforgeGui.isInside(mouseX, mouseY, leftPos + chipX(slot), topPos + CHIP_Y, CHIP_WIDTH, CHIP_HEIGHT)) {
                return slot;
            }
        }
        return -1;
    }

    // The toggle button under the mouse, as its menu button id, or -1. Fluid and pressurized conduits have no components button.
    private int buttonAt(double mouseX, double mouseY) {
        if (isOverButton(mouseX, mouseY, LIST_MODE_X)) return ConduitFilterMenu.BUTTON_LIST_MODE;
        if (menu.isItemFilter() && isOverButton(mouseX, mouseY, COMPONENTS_X)) return ConduitFilterMenu.BUTTON_COMPONENTS;
        if (isOverButton(mouseX, mouseY, DIRECTION_X)) return ConduitFilterMenu.BUTTON_DIRECTION;
        return -1;
    }

    private boolean isOverButton(double mouseX, double mouseY, int x) {
        return ArcforgeGui.isInside(mouseX, mouseY, leftPos + x, topPos + BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE);
    }

    // --- Rendering ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        FilterSettings settings = menu.getSettings();

        int hoveredSlot = slotAt(mouseX, mouseY);
        for (int slot = 0; slot < FilterSettings.SIZE; slot++) {
            FilterSettings.Entry entry = settings.entry(slot);
            int sx = x + slotX(slot);
            int sy = y + SLOT_Y;
            if (entry.item().isPresent()) {
                graphics.fakeItem(entry.item().get().create(), sx, sy);
            } else if (entry.fluid().isPresent()) {
                ArcforgeGui.drawFluid(graphics, entry.fluid().get(), 16, FLUID_FALLBACK, sx, sy + 16, 16, 16);
            }
            if (!entry.isEmpty()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(entry.tag().isPresent() ? "match_tag" : "match_exact"),
                        x + chipX(slot), y + CHIP_Y, CHIP_WIDTH, CHIP_HEIGHT);
            }
            if (slot == hoveredSlot) {
                graphics.fill(sx, sy, sx + 16, sy + 16, HOVER);
            }
        }

        drawButton(graphics, LIST_MODE_X, settings.deny() ? "filter_deny" : "filter_allow", mouseX, mouseY);
        if (menu.isItemFilter()) {
            drawButton(graphics, COMPONENTS_X, settings.ignoreComponents() ? "components_ignore" : "components_match", mouseX, mouseY);
        }
        drawButton(graphics, DIRECTION_X, "dir_" + settings.flow().getSerializedName(), mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int buttonX, String icon, int mouseX, int mouseY) {
        int bx = leftPos + buttonX;
        int by = topPos + BUTTON_Y;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, isOverButton(mouseX, mouseY, buttonX) ? BUTTON_HOVER : BUTTON, bx, by, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(icon), bx + 2, by + 2, 16, 16);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcforgeGui.LABEL, false);
        FilterSettings settings = menu.getSettings();
        graphics.text(font, Component.translatable("gui.arcforge.conduit_filter.summary", settings.listName(), settings.flow().displayName()),
                SUMMARY_X, SUMMARY_Y, ArcforgeGui.LABEL, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        addTooltip(lines, mouseX, mouseY);
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    private void addTooltip(List<Component> lines, int mouseX, int mouseY) {
        FilterSettings settings = menu.getSettings();
        int slot = Math.max(slotAt(mouseX, mouseY), chipAt(mouseX, mouseY));
        if (slot >= 0) {
            FilterSettings.Entry entry = settings.entry(slot);
            if (entry.isEmpty()) {
                lines.add(Component.translatable(menu.isItemFilter() ? "gui.arcforge.conduit_filter.empty_item" : "gui.arcforge.conduit_filter.empty_fluid")
                        .withStyle(ChatFormatting.GRAY));
                return;
            }
            lines.add(entry.item().isPresent() ? entry.item().get().create().getHoverName() : ConduitFilterItem.describe(entry.withTag(null)));
            lines.add(entry.tag()
                    .<Component>map(tag -> Component.translatable("gui.arcforge.conduit_filter.match_tag", tag.toString()))
                    .orElseGet(() -> Component.translatable("gui.arcforge.conduit_filter.match_exact"))
                    .copy().withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.arcforge.conduit_filter.chip_hint").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        switch (buttonAt(mouseX, mouseY)) {
            case ConduitFilterMenu.BUTTON_LIST_MODE -> {
                lines.add(settings.listName());
                lines.add(Component.translatable(settings.deny() ? "gui.arcforge.conduit_filter.denylist.desc" : "gui.arcforge.conduit_filter.allowlist.desc")
                        .withStyle(ChatFormatting.GRAY));
            }
            case ConduitFilterMenu.BUTTON_COMPONENTS -> {
                String key = settings.ignoreComponents() ? "gui.arcforge.conduit_filter.components_ignore" : "gui.arcforge.conduit_filter.components_match";
                lines.add(Component.translatable(key));
                lines.add(Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY));
            }
            case ConduitFilterMenu.BUTTON_DIRECTION -> {
                lines.add(settings.flow().displayName());
                lines.add(Component.translatable("gui.arcforge.conduit_filter.dir_" + settings.flow().getSerializedName() + ".desc")
                        .withStyle(ChatFormatting.GRAY));
            }
            default -> {}
        }
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int buttonId = clickedButton(event);
        if (buttonId >= 0) {
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
            }
            ArcforgeGui.playClickSound();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    // The menu button a click sends: a ghost slot sets or clears it from the carried item; a chip steps its match
    // (right-click: backwards); the toggles are left-click only. -1 if the click isn't on any of them.
    private int clickedButton(MouseButtonEvent event) {
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
        boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        int chip = chipAt(event.x(), event.y());
        if (chip >= 0 && (left || right)) {
            return (left ? ConduitFilterMenu.CHIP_NEXT : ConduitFilterMenu.CHIP_PREVIOUS) + chip;
        }
        if (!left) {
            return -1;
        }
        int slot = slotAt(event.x(), event.y());
        return slot >= 0 ? slot : buttonAt(event.x(), event.y());
    }
}
