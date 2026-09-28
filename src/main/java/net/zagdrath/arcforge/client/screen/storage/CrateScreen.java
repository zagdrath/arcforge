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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.SideConfigTab;
import net.zagdrath.arcforge.client.gui.tab.SideTabPanel;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.storage.CrateMenu;

// A Crate's GUI (layout: gui_layouts.json "crate" and "crate_scroll"): six rows of its slots and, on the Tempered
// Crate and up, a scrollbar (mouse wheel over the slots or the bar, or drag the thumb), then the inventory and the
// side tabs. Positions are relative to leftPos/topPos.
public class CrateScreen extends AbstractContainerScreen<CrateMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/crate.png");
    private static final Identifier BACKGROUND_SCROLL = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/crate_scroll.png");
    private static final Identifier SCROLLER = ArcforgeGui.sprite("container/crate/scroller");
    private static final Identifier SCROLLER_DISABLED = ArcforgeGui.sprite("container/crate/scroller_disabled");
    private static final int TRACK_X = 175, TRACK_Y = 18, TRACK_WIDTH = 12, TRACK_HEIGHT = 106;
    private static final int THUMB_HEIGHT = 15;
    private static final int GRID_WIDTH = 9 * 18, GRID_HEIGHT = 6 * 18;
    // SideTabPanel places its tabs for a 176-wide GUI.
    private static final int STANDARD_WIDTH = 176;

    private final SideTabPanel tabs = new SideTabPanel();
    private final boolean scrolls;
    private boolean dragging;

    public CrateScreen(CrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, menu.getMaxOffset() > 0 ? 194 : 176, 222);
        this.scrolls = menu.getMaxOffset() > 0;
        this.inventoryLabelY = 128;
        tabs.add(new SideConfigTab(menu::getSideMode,
                (side, action) -> sendButton(MachineMenuButtons.sideButtonId(side, action)),
                () -> sendButton(MachineMenuButtons.CLEAR_SIDES)));
    }

    private void sendButton(int buttonId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private int tabsLeft() {
        return leftPos + imageWidth - STANDARD_WIDTH;
    }

    @Override
    public void init() {
        super.init();
        tabs.layout(tabsLeft(), topPos);
    }

    // --- Scrolling ---

    private void scrollTo(int offset) {
        int clamped = Mth.clamp(offset, 0, menu.getMaxOffset());
        if (clamped != menu.getRowOffset()) {
            // The button goes first, so any click after it lands on the rows now in view.
            sendButton(CrateMenu.SCROLL_BUTTON + clamped);
            menu.setRowOffset(clamped);
        }
    }

    private int thumbY() {
        int maxOffset = menu.getMaxOffset();
        return TRACK_Y + (maxOffset == 0 ? 0 : Math.round((TRACK_HEIGHT - THUMB_HEIGHT) * menu.getRowOffset() / (float) maxOffset));
    }

    private boolean isOverTrack(double mouseX, double mouseY) {
        return scrolls && ArcforgeGui.isInside(mouseX, mouseY, leftPos + TRACK_X, topPos + TRACK_Y, TRACK_WIDTH, TRACK_HEIGHT);
    }

    private boolean isOverGrid(double mouseX, double mouseY) {
        return ArcforgeGui.isInside(mouseX, mouseY, leftPos + CrateMenu.SLOTS_X - 1, topPos + CrateMenu.SLOTS_Y - 1, GRID_WIDTH, GRID_HEIGHT);
    }

    // The offset for a thumb centred on this mouse y.
    private void dragTo(double mouseY) {
        float along = (float) (mouseY - topPos - TRACK_Y - THUMB_HEIGHT / 2.0) / (TRACK_HEIGHT - THUMB_HEIGHT);
        scrollTo(Math.round(Mth.clamp(along, 0.0F, 1.0F) * menu.getMaxOffset()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrolls && (isOverGrid(mouseX, mouseY) || isOverTrack(mouseX, mouseY)) && scrollY != 0) {
            scrollTo(menu.getRowOffset() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu.getCarried().isEmpty() && tabs.mouseClicked(event)) {
            return true;
        }
        if (isOverTrack(event.x(), event.y())) {
            dragging = true;
            dragTo(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging) {
            dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    // Clicks on the side tabs aren't "outside" the GUI, so carried items aren't thrown.
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop) {
        return super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop) && !tabs.isInside(mouseX, mouseY);
    }

    // Areas covered by side tabs, for recipe-viewer integrations (JEI/EMI) to avoid.
    public List<Rect2i> getExtraAreas() {
        return tabs.getAreas();
    }

    // --- Rendering ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, scrolls ? BACKGROUND_SCROLL : BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        if (scrolls) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.getMaxOffset() > 0 ? SCROLLER : SCROLLER_DISABLED,
                    leftPos + TRACK_X, topPos + thumbY(), TRACK_WIDTH, THUMB_HEIGHT);
        }
        tabs.render(graphics, font, tabsLeft(), topPos, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcforgeGui.LABEL, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        tabs.addTooltip(lines, mouseX, mouseY);
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }
}
