/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.multiblock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.client.gui.tab.PortsTab;
import net.zagdrath.arcforge.client.gui.tab.RedstoneTab;
import net.zagdrath.arcforge.client.gui.tab.SideTabPanel;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.network.PortsPayload;

// Shared frame of the multiblock GUIs: background, a title that changes with the structure,
// and the Redstone and Ports tabs.
public abstract class MultiblockScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    private final Identifier background;
    private final String machine;
    protected final SideTabPanel tabs;
    private final PortsTab ports;

    protected MultiblockScreen(M menu, Inventory inventory, Component title, String machine,
            Supplier<RedstoneMode> redstone, Supplier<List<PortsPayload.Entry>> ports) {
        super(menu, inventory, title);
        this.machine = machine;
        this.background = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/" + machine + ".png");
        this.ports = new PortsTab(ports);
        this.tabs = new SideTabPanel()
                .add(new RedstoneTab(redstone, mode -> sendButton(MachineMenuButtons.redstoneButtonId(mode))))
                .add(this.ports);
    }

    // Shows the auto-eject button on the Ports tab.
    protected void enableAutoEject(BooleanSupplier state) {
        ports.withAutoEject(state, () -> sendButton(MachineMenuButtons.TOGGLE_AUTO_EJECT));
    }

    protected Identifier sprite(String name) {
        return ArcforgeGui.sprite("container/" + machine + "/" + name);
    }

    private void sendButton(int buttonId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    public void init() {
        super.init();
        tabs.layout(leftPos, topPos);
    }

    // The title shown at the top, e.g. with the structure's size or "incomplete".
    protected abstract Component currentTitle();

    protected abstract void drawContents(GuiGraphicsExtractor graphics, int x, int y);

    protected abstract void addTooltip(List<Component> lines, int mouseX, int mouseY);

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, background, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        drawContents(graphics, leftPos, topPos);
        // Tabs overlap the panel edge, so they are drawn after the background.
        tabs.render(graphics, font, leftPos, topPos, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        Component shown = currentTitle();
        graphics.text(font, shown, (imageWidth - font.width(shown)) / 2, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcforgeGui.LABEL, false);
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

    // Draws a sprite over an empty slot to hint at what goes there.
    protected void ghost(GuiGraphicsExtractor graphics, Identifier sprite, boolean slotEmpty, int x, int y) {
        if (slotEmpty) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, leftPos + x, topPos + y, 16, 16);
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
}
