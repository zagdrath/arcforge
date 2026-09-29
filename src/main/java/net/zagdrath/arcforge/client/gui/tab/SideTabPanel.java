/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;

// Stacks of side tabs on the left and right of a machine GUI. Only one tab is open at a time;
// tabs below an open panel shift down, and panels animate open/closed over ~6 ticks.
public class SideTabPanel {
    private static final Identifier TAB = ArcforgeGui.widget("tab");
    private static final Identifier TAB_HOVER = ArcforgeGui.widget("tab_hover");
    private static final Identifier TAB_SELECTED = ArcforgeGui.widget("tab_selected");
    private static final Identifier TAB_LEFT = ArcforgeGui.widget("tab_left");
    private static final Identifier TAB_LEFT_HOVER = ArcforgeGui.widget("tab_left_hover");
    private static final Identifier TAB_LEFT_SELECTED = ArcforgeGui.widget("tab_left_selected");
    private static final Identifier TAB_PANEL = ArcforgeGui.widget("tab_panel");
    private static final Identifier TAB_LEFT_PANEL = ArcforgeGui.widget("tab_left_panel");

    // Right tabs start 4 px inside the GUI's 176 px width, left tabs overlap its left edge by as much.
    private static final int RIGHT_X = 172;
    private static final int LEFT_EDGE = 4;
    private static final int TABS_Y = 6;
    private static final int GAP = 1;
    private static final int ICON_X = 3, ICON_Y = 2;
    private static final int TITLE_X = 22, TITLE_Y = 6;
    private static final float ANIMATION_MILLIS = 300.0F;

    private final List<SideTab> tabs = new ArrayList<>();
    private long lastUpdate = Util.getMillis();

    private List<SideTab> visible() {
        return tabs.stream().filter(SideTab::isVisible).toList();
    }

    public SideTabPanel add(SideTab tab) {
        tabs.add(tab);
        return this;
    }

    // Recomputes each tab's position from the GUI's top-left corner. Each side stacks down on its own; a left tab
    // grows leftward, so its x moves as it opens.
    public void layout(int guiLeft, int guiTop) {
        int leftY = guiTop + TABS_Y;
        int rightY = guiTop + TABS_Y;
        for (SideTab tab : visible()) {
            if (tab.getSide() == SideTab.Side.LEFT) {
                tab.x = guiLeft + LEFT_EDGE - tab.getWidth();
                tab.y = leftY;
                leftY += tab.getHeight() + GAP;
            } else {
                tab.x = guiLeft + RIGHT_X;
                tab.y = rightY;
                rightY += tab.getHeight() + GAP;
            }
        }
    }

    private void animate() {
        long now = Util.getMillis();
        float step = (now - lastUpdate) / ANIMATION_MILLIS;
        lastUpdate = now;
        for (SideTab tab : visible()) {
            boolean wasFullyOpen = tab.isFullyOpen();
            tab.progress = tab.open ? Math.min(1.0F, tab.progress + step) : Math.max(0.0F, tab.progress - step);
            if (wasFullyOpen != tab.isFullyOpen()) {
                tab.onFullyOpenChanged(tab.isFullyOpen());
            }
        }
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int guiLeft, int guiTop, int mouseX, int mouseY) {
        animate();
        layout(guiLeft, guiTop);
        for (SideTab tab : visible()) {
            int width = tab.getWidth();
            int height = tab.getHeight();
            boolean left = tab.getSide() == SideTab.Side.LEFT;
            if (tab.progress <= 0) {
                boolean hovered = ArcforgeGui.isInside(mouseX, mouseY, tab.x, tab.y, width, height);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? (left ? TAB_LEFT_HOVER : TAB_HOVER) : (left ? TAB_LEFT : TAB),
                        tab.x, tab.y, width, height);
            } else if (width <= SideTab.SELECTED_WIDTH) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, left ? TAB_LEFT_SELECTED : TAB_SELECTED,
                        left ? tab.x + width - SideTab.SELECTED_WIDTH : tab.x, tab.y, SideTab.SELECTED_WIDTH, SideTab.COLLAPSED_HEIGHT);
            } else {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, left ? TAB_LEFT_PANEL : TAB_PANEL, tab.x, tab.y, width, height);
            }

            // The icon sits 3 px from the tab's outer edge.
            int iconX = left ? tab.x + width - 16 - ICON_X : tab.x + ICON_X;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, tab.getIcon(), iconX, tab.y + ICON_Y, 16, 16);

            if (tab.isFullyOpen()) {
                int titleX = left ? tab.x + width - TITLE_X - font.width(tab.getTitle()) : tab.x + TITLE_X;
                graphics.text(font, tab.getTitle(), titleX, tab.y + TITLE_Y, ArcforgeGui.TEXT, false);
                tab.renderContent(graphics, font, tab.x, tab.y, mouseX, mouseY);
            }
        }
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        SideTab tab = tabAt(event.x(), event.y());
        if (tab == null) {
            return false;
        }

        // Header row toggles the tab; everything else goes to the panel content.
        if (event.y() < tab.y + SideTab.COLLAPSED_HEIGHT) {
            toggle(tab);
            ArcforgeGui.playClickSound();
            return true;
        }
        return tab.isFullyOpen() && tab.contentClicked(event, (int) event.x() - tab.x, (int) event.y() - tab.y);
    }

    // One tab open per side: opening one closes the others on its side.
    private void toggle(SideTab clicked) {
        boolean opening = !clicked.open;
        for (SideTab tab : visible()) {
            if (tab.getSide() == clicked.getSide()) {
                tab.open = false;
            }
        }
        clicked.open = opening;
    }

    public void addTooltip(List<Component> lines, double mouseX, double mouseY) {
        SideTab tab = tabAt(mouseX, mouseY);
        if (tab != null && tab.isFullyOpen()) {
            tab.addTooltip(lines, (int) mouseX - tab.x, (int) mouseY - tab.y);
        } else if (tab != null && tab.progress <= 0) {
            tab.addCollapsedTooltip(lines);
        }
    }

    private @Nullable SideTab tabAt(double mouseX, double mouseY) {
        for (SideTab tab : visible()) {
            if (ArcforgeGui.isInside(mouseX, mouseY, tab.x, tab.y, tab.getWidth(), tab.getHeight())) {
                return tab;
            }
        }
        return null;
    }

    public boolean isInside(double mouseX, double mouseY) {
        return tabAt(mouseX, mouseY) != null;
    }

    // Screen areas covered by tabs, for recipe viewers (JEI/EMI) to avoid.
    public List<Rect2i> getAreas() {
        List<Rect2i> areas = new ArrayList<>();
        for (SideTab tab : visible()) {
            areas.add(new Rect2i(tab.x, tab.y, tab.getWidth(), tab.getHeight()));
        }
        return areas;
    }
}
