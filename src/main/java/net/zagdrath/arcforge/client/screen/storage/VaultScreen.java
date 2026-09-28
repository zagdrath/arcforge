/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.storage;

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
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.SideConfigTab;
import net.zagdrath.arcforge.client.gui.tab.SideTabPanel;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.storage.VaultMenu;
import net.zagdrath.arcforge.storage.StorageCounts;

// A Vault's GUI (layout: gui_layouts.json "vault"): input and output slots, a screen with the stored item, its name,
// "12,400 / 65,536" and a fill bar, the lock and void buttons, then the inventory and the side tabs. Positions are
// relative to leftPos/topPos.
public class VaultScreen extends AbstractContainerScreen<VaultMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/vault.png");
    private static final Identifier FILL_BAR = ArcforgeGui.sprite("container/vault/fill_bar");
    private static final Identifier BUTTON = ArcforgeGui.widget("button");
    private static final Identifier BUTTON_HOVER = ArcforgeGui.widget("button_hover");
    private static final int ITEM_X = 36, ITEM_Y = 23;
    private static final int NAME_X = 57, NAME_Y = 24, NAME_WIDTH = 88, COUNT_Y = 36;
    private static final int BAR_X = 37, BAR_Y = 61, BAR_WIDTH = 104, BAR_HEIGHT = 2;
    private static final int BUTTON_X = 152, LOCK_Y = 17, VOID_Y = 43, BUTTON_SIZE = 20;
    // Dims the ghost item of an empty, locked vault.
    private static final int GHOST_SHADE = 0xA0101214;

    private final SideTabPanel tabs = new SideTabPanel();

    public VaultScreen(VaultMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        tabs.add(new SideConfigTab(menu::getSideMode,
                (side, action) -> sendButton(MachineMenuButtons.sideButtonId(side, action)),
                () -> sendButton(MachineMenuButtons.CLEAR_SIDES)));
    }

    private void sendButton(int buttonId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    public void init() {
        super.init();
        titleLabelX = (imageWidth - font.width(title)) / 2;
        tabs.layout(leftPos, topPos);
    }

    // The button under the mouse (its menu button id), or -1.
    private int buttonAt(double mouseX, double mouseY) {
        if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + BUTTON_X, topPos + LOCK_Y, BUTTON_SIZE, BUTTON_SIZE)) return VaultMenu.BUTTON_LOCK;
        if (ArcforgeGui.isInside(mouseX, mouseY, leftPos + BUTTON_X, topPos + VOID_Y, BUTTON_SIZE, BUTTON_SIZE)) return VaultMenu.BUTTON_VOID;
        return -1;
    }

    // --- Rendering ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);

        ItemStack template = menu.getTemplate();
        if (!template.isEmpty()) {
            graphics.fakeItem(template, x + ITEM_X, y + ITEM_Y);
            if (menu.getAmount() == 0) {
                graphics.fill(x + ITEM_X, y + ITEM_Y, x + ITEM_X + 16, y + ITEM_Y + 16, GHOST_SHADE);
            }
        }
        int fill = menu.getCapacity() <= 0 ? 0 : (int) ((long) BAR_WIDTH * menu.getAmount() / menu.getCapacity());
        if (menu.getAmount() > 0) {
            fill = Math.max(1, fill);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FILL_BAR, BAR_WIDTH, BAR_HEIGHT, 0, 0, x + BAR_X, y + BAR_Y, fill, BAR_HEIGHT);
        }

        int hovered = buttonAt(mouseX, mouseY);
        drawButton(graphics, LOCK_Y, menu.isLocked() ? "lock_on" : "lock_off", hovered == VaultMenu.BUTTON_LOCK);
        drawButton(graphics, VOID_Y, menu.isVoidMode() ? "void_on" : "void_off", hovered == VaultMenu.BUTTON_VOID);
        tabs.render(graphics, font, x, y, mouseX, mouseY);
    }

    private void drawButton(GuiGraphicsExtractor graphics, int buttonY, String icon, boolean hovered) {
        int bx = leftPos + BUTTON_X;
        int by = topPos + buttonY;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? BUTTON_HOVER : BUTTON, bx, by, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ArcforgeGui.sprite("container/vault/" + icon), bx + 2, by + 2, 16, 16);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcforgeGui.LABEL, false);
        ItemStack template = menu.getTemplate();
        if (menu.getAmount() == 0) {
            Component empty = Component.translatable(menu.isLocked() ? "gui.arcforge.vault.empty_locked" : "gui.arcforge.vault.empty");
            graphics.text(font, empty, NAME_X, NAME_Y, ArcforgeGui.LABEL, false);
        } else {
            graphics.text(font, clip(template.getHoverName().getString()), NAME_X, NAME_Y, ArcforgeGui.TEXT, false);
        }
        graphics.text(font, Component.translatable("gui.arcforge.vault.count", StorageCounts.grouped(menu.getAmount()),
                StorageCounts.grouped(menu.getCapacity())), NAME_X, COUNT_Y, ArcforgeGui.LABEL, false);
    }

    // Fits a name into the screen, ending in "…" if it's cut short.
    private String clip(String name) {
        if (font.width(name) <= NAME_WIDTH) {
            return name;
        }
        return font.plainSubstrByWidth(name, NAME_WIDTH - font.width("…")) + "…";
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        switch (buttonAt(mouseX, mouseY)) {
            case VaultMenu.BUTTON_LOCK -> lines.add(Component.translatable(menu.isLocked() ? "gui.arcforge.vault.lock_on" : "gui.arcforge.vault.lock_off"));
            case VaultMenu.BUTTON_VOID -> lines.add(Component.translatable(menu.isVoidMode() ? "gui.arcforge.vault.void_on" : "gui.arcforge.vault.void_off"));
            default -> tabs.addTooltip(lines, mouseX, mouseY);
        }
        if (lines.isEmpty() && menu.getAmount() == 0 && menu.isLocked() && !menu.getTemplate().isEmpty()
                && ArcforgeGui.isInside(mouseX, mouseY, leftPos + ITEM_X, topPos + ITEM_Y, 16, 16)) {
            lines.add(Component.translatable("tooltip.arcforge.vault.locked", menu.getTemplate().getHoverName()).withStyle(ChatFormatting.GOLD));
        }
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu.getCarried().isEmpty() && tabs.mouseClicked(event)) {
            return true;
        }
        int button = buttonAt(event.x(), event.y());
        if (button >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            sendButton(button);
            ArcforgeGui.playClickSound();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop) {
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop) && !tabs.isInside(mouseX, mouseY);
    }

    // Areas covered by side tabs, for recipe-viewer integrations (JEI/EMI) to avoid.
    public List<Rect2i> getExtraAreas() {
        return tabs.getAreas();
    }
}
