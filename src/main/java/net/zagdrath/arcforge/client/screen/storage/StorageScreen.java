/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.storage;

import java.util.ArrayList;
import java.util.List;

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
import net.zagdrath.arcforge.client.gui.tab.SideTabPanel;
import net.zagdrath.arcforge.menu.storage.StorageMenu;

// Storage block screens: a 176x166 background, the side tabs, and a ghost icon in the empty drain slot. Subclasses draw the gauge and the info screen. Positions are relative to leftPos/topPos.
public abstract class StorageScreen<M extends StorageMenu> extends AbstractContainerScreen<M> {
    protected final String name;
    private final Identifier background;
    private final Identifier ghostInput;
    protected final SideTabPanel tabs = new SideTabPanel();

    protected StorageScreen(M menu, Inventory inventory, Component title, String name) {
        super(menu, inventory, title);
        this.name = name;
        this.background = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/" + name + ".png");
        this.ghostInput = sprite(switch (name) {
            case "fluid_tank" -> "ghost_bucket";
            case "heat_cell" -> "ghost_capsule";
            case "pressurized_cylinder" -> "ghost_cartridge";
            default -> "ghost_battery";
        });
    }

    protected Identifier sprite(String sprite) {
        return ArcforgeGui.sprite("container/" + name + "/" + sprite);
    }

    protected void sendButton(int buttonId) {
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

    // --- Rendering ---

    protected abstract void drawContents(GuiGraphicsExtractor graphics, int x, int y);

    protected abstract void drawInfo(GuiGraphicsExtractor graphics);

    protected abstract void addTooltip(List<Component> lines, int mouseX, int mouseY);

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, background, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        drawContents(graphics, x, y);
        if (!menu.getInputSlot().hasItem()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ghostInput, x + menu.getInputSlot().x, y + menu.getInputSlot().y, 16, 16);
        }
        // Tabs overlap the panel edge, so they are drawn after the background.
        tabs.render(graphics, font, x, y, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcforgeGui.LABEL, false);
        drawInfo(graphics);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        addTooltip(lines, mouseX, mouseY);
        if (lines.isEmpty()) {
            tabs.addTooltip(lines, mouseX, mouseY);
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
        return super.mouseClicked(event, doubleClick);
    }

    // Clicks on the side tabs are not "outside" the GUI, so carried items are not thrown.
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop) {
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop) && !tabs.isInside(mouseX, mouseY);
    }

    // Areas covered by side tabs, for recipe-viewer integrations (JEI/EMI) to avoid.
    public List<Rect2i> getExtraAreas() {
        return tabs.getAreas();
    }

    protected static int scaledRound(long value, long max, int size) {
        return max <= 0 ? 0 : (int) Math.min(size, Math.round((double) value * size / max));
    }
}
