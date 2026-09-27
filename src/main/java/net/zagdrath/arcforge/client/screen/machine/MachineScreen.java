/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.screen.machine;

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
import net.zagdrath.arcforge.client.gui.HeatScale;
import net.zagdrath.arcforge.client.gui.tab.RedstoneTab;
import net.zagdrath.arcforge.client.gui.tab.SideConfigTab;
import net.zagdrath.arcforge.client.gui.tab.SideTab;
import net.zagdrath.arcforge.client.gui.tab.SideTabPanel;
import net.zagdrath.arcforge.client.gui.tab.UpgradesTab;
import net.zagdrath.arcforge.menu.machine.MachineMenu;

// Shared frame of the single-block machine GUIs: background, the machine's own tabs followed by
// Redstone, Sides and Upgrades, and helpers for the common gauges. Positions are relative to leftPos/topPos.
public abstract class MachineScreen<M extends MachineMenu> extends AbstractContainerScreen<M> {
    // The status line: LED then text.
    protected static final int LED_SIZE = 6;
    // Vertical gauges (FE and heat buffers) are 10x50 inside a 12x52 frame.
    protected static final int GAUGE_W = 10, GAUGE_H = 50;
    protected static final int FLAME_SIZE = 14;

    private final Identifier background;
    private final String machine;
    protected final SideTabPanel tabs = new SideTabPanel();

    protected MachineScreen(M menu, Inventory inventory, Component title, String machine, List<SideTab> machineTabs) {
        super(menu, inventory, title);
        this.machine = machine;
        this.background = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/gui/container/" + machine + ".png");
        machineTabs.forEach(tabs::add);
        tabs.add(new RedstoneTab(menu::getRedstoneMode, mode -> sendButton(MachineMenu.redstoneButtonId(mode))))
                .add(new SideConfigTab(menu::getSideMode,
                        (side, action) -> sendButton(MachineMenu.sideButtonId(side, action)),
                        () -> sendButton(MachineMenu.BUTTON_CLEAR_SIDES)))
                .add(new UpgradesTab(menu.getUpgradeSlots()));
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
        titleLabelX = (imageWidth - font.width(title)) / 2;
        tabs.layout(leftPos, topPos);
    }

    protected abstract void drawContents(GuiGraphicsExtractor graphics, int x, int y);

    // Screen text, relative to the GUI's corner.
    protected abstract void drawText(GuiGraphicsExtractor graphics);

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
        graphics.text(font, title, titleLabelX, titleLabelY, ArcforgeGui.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, ArcforgeGui.LABEL, false);
        drawText(graphics);
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

    // --- Drawing helpers (x, y are the GUI's corner) ---

    // A bottom-to-top gauge cropped from the bottom so its pixel pattern stays put.
    protected void drawGauge(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int gaugeX, int gaugeY, int value, int max) {
        int height = scaled(value, max, GAUGE_H);
        if (height > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, GAUGE_W, GAUGE_H, 0, GAUGE_H - height,
                    x + gaugeX, y + gaugeY + GAUGE_H - height, GAUGE_W, height);
        }
    }

    // A left-to-right bar filled to width, with the marker at its end.
    protected void drawBar(GuiGraphicsExtractor graphics, Identifier bar, Identifier marker, int x, int y, int barX, int barY, int barW, int barH, int width) {
        if (width > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, bar, barW, barH, 0, 0, x + barX, y + barY, width, barH);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, marker, x + barX + width - 1, y + barY - 1, 2, barH + 2);
        }
    }

    // The heat bar on the shared heat scale.
    protected void drawHeatBar(GuiGraphicsExtractor graphics, int x, int y, int barX, int barY, int barW, int celsius) {
        drawBar(graphics, sprite("heat_bar"), sprite("heat_marker"), x, y, barX, barY, barW, 4, HeatScale.fillWidth(barW, celsius));
    }

    // The flame: off, then lit from the bottom up to how much is left burning.
    protected void drawFlame(GuiGraphicsExtractor graphics, int x, int y, int flameX, int flameY, int left, int total) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("flame_off"), x + flameX, y + flameY, FLAME_SIZE, FLAME_SIZE);
        if (total > 0 && left > 0) {
            int height = Mth.ceil(FLAME_SIZE * (float) Math.min(left, total) / total);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("flame_on"), FLAME_SIZE, FLAME_SIZE, 0, FLAME_SIZE - height,
                    x + flameX, y + flameY + FLAME_SIZE - height, FLAME_SIZE, height);
        }
    }

    protected void drawLed(GuiGraphicsExtractor graphics, int x, int y, int ledX, int ledY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.getStatus().getLedSprite(machine), x + ledX, y + ledY, LED_SIZE, LED_SIZE);
    }

    // Draws a sprite over an empty slot to hint at what goes there.
    protected void ghost(GuiGraphicsExtractor graphics, Identifier sprite, boolean slotEmpty, int x, int y) {
        if (slotEmpty) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, leftPos + x, topPos + y, 16, 16);
        }
    }

    protected void textRight(GuiGraphicsExtractor graphics, Component text, int right, int y, int color) {
        graphics.text(font, text, right - font.width(text), y, color, false);
    }

    protected static int scaled(int value, int max, int size) {
        return max <= 0 ? 0 : (int) Math.min(size, Math.round((double) value * size / max));
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
