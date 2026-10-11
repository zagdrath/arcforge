/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.storage;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.menu.storage.ItemReservoirMenu;
import net.zagdrath.arcforge.network.ItemReservoirSearchPayload;

// An Item Reservoir's GUI (textures/gui/container/item_reservoir.png: the scrolling Crate's panel with a search field
// recessed into the title row): six rows of the store's slots that match the search, a scrollbar (mouse wheel over the
// slots or the bar, or drag the thumb), then the inventory. Typing in the search box sends it to the server, which
// shows the matching slots from the top.
public class ItemReservoirScreen extends AbstractContainerScreen<ItemReservoirMenu> {
    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/item_reservoir.png");
    private static final Identifier SCROLLER = ArcforgeGui.sprite("container/crate/scroller");
    private static final Identifier SCROLLER_DISABLED = ArcforgeGui.sprite("container/crate/scroller_disabled");
    private static final int TRACK_X = 175, TRACK_Y = 18, TRACK_WIDTH = 12, TRACK_HEIGHT = 106;
    private static final int THUMB_HEIGHT = 15;
    private static final int GRID_WIDTH = 9 * 18, GRID_HEIGHT = 6 * 18;
    // The search field's recess (x 96, y 4, 91x12); the text starts after the magnifier.
    private static final int SEARCH_X = 108, SEARCH_Y = 6, SEARCH_WIDTH = 76, SEARCH_HEIGHT = 9;

    private @Nullable EditBox search;
    private boolean dragging;

    public ItemReservoirScreen(ItemReservoirMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 194, 222);
        this.inventoryLabelY = 128;
    }

    private void sendButton(int buttonId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    public void init() {
        super.init();
        String text = search != null ? search.getValue() : "";
        search = new EditBox(font, leftPos + SEARCH_X, topPos + SEARCH_Y, SEARCH_WIDTH, SEARCH_HEIGHT,
                Component.translatable("container.arcforge.item_reservoir.search"));
        search.setBordered(false);
        search.setMaxLength(ItemReservoirMenu.MAX_QUERY);
        search.setTextColor(ArcforgeGui.TEXT);
        search.setHint(Component.translatable("container.arcforge.item_reservoir.search"));
        search.setValue(text);
        search.setResponder(this::searchChanged);
        addRenderableWidget(search);
    }

    private void searchChanged(String text) {
        ClientPacketDistributor.sendToServer(new ItemReservoirSearchPayload(menu.containerId, text));
        menu.setRowOffset(0);
    }

    // Typing goes to the search box while it has focus (Escape still closes, so the inventory key doesn't).
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search != null && search.isFocused() && search.canConsumeInput() && !event.isEscape()) {
            search.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        menu.followServerOffset();
    }

    // --- Scrolling ---

    private void scrollTo(int offset) {
        int clamped = Mth.clamp(offset, 0, menu.getMaxOffset());
        if (clamped != menu.getRowOffset()) {
            // The button goes first, so any click after it lands on the rows now in view.
            sendButton(ItemReservoirMenu.SCROLL_BUTTON + clamped);
            menu.setRowOffset(clamped);
        }
    }

    private int thumbY() {
        int maxOffset = menu.getMaxOffset();
        return TRACK_Y + (maxOffset == 0 ? 0 : Math.round((TRACK_HEIGHT - THUMB_HEIGHT) * menu.getRowOffset() / (float) maxOffset));
    }

    private boolean isOverTrack(double mouseX, double mouseY) {
        return ArcforgeGui.isInside(mouseX, mouseY, leftPos + TRACK_X, topPos + TRACK_Y, TRACK_WIDTH, TRACK_HEIGHT);
    }

    private boolean isOverGrid(double mouseX, double mouseY) {
        return ArcforgeGui.isInside(mouseX, mouseY, leftPos + ItemReservoirMenu.SLOTS_X - 1, topPos + ItemReservoirMenu.SLOTS_Y - 1,
                GRID_WIDTH, GRID_HEIGHT);
    }

    private void dragTo(double mouseY) {
        float along = (float) (mouseY - topPos - TRACK_Y - THUMB_HEIGHT / 2.0) / (TRACK_HEIGHT - THUMB_HEIGHT);
        scrollTo(Math.round(Mth.clamp(along, 0.0F, 1.0F) * menu.getMaxOffset()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if ((isOverGrid(mouseX, mouseY) || isOverTrack(mouseX, mouseY)) && scrollY != 0) {
            scrollTo(menu.getRowOffset() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (isOverTrack(event.x(), event.y()) && menu.getMaxOffset() > 0) {
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

    // --- Rendering ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.getMaxOffset() > 0 ? SCROLLER : SCROLLER_DISABLED,
                leftPos + TRACK_X, topPos + thumbY(), TRACK_WIDTH, THUMB_HEIGHT);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcforgeGui.LABEL, false);
    }
}
