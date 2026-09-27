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

// Stack of side tabs on the right of a machine GUI. Only one tab is open at a time;
// tabs below an open panel shift down, and panels animate open/closed over ~6 ticks.
public class SideTabPanel {
    private static final Identifier TAB = ArcforgeGui.widget("tab");
    private static final Identifier TAB_HOVER = ArcforgeGui.widget("tab_hover");
    private static final Identifier TAB_SELECTED = ArcforgeGui.widget("tab_selected");
    private static final Identifier TAB_PANEL = ArcforgeGui.widget("tab_panel");

    private static final int TABS_X = 172;
    private static final int TABS_Y = 6;
    private static final int GAP = 1;
    private static final int ICON_OFFSET = 4;
    private static final int TITLE_X = 24, TITLE_Y = 8;
    private static final float ANIMATION_MILLIS = 300.0F;

    private final List<SideTab> tabs = new ArrayList<>();
    private long lastUpdate = Util.getMillis();

    public SideTabPanel add(SideTab tab) {
        tabs.add(tab);
        return this;
    }

    // Recomputes each tab's position from the GUI's top-left corner.
    public void layout(int guiLeft, int guiTop) {
        int y = guiTop + TABS_Y;
        for (SideTab tab : tabs) {
            tab.x = guiLeft + TABS_X;
            tab.y = y;
            y += tab.getHeight() + GAP;
        }
    }

    private void animate() {
        long now = Util.getMillis();
        float step = (now - lastUpdate) / ANIMATION_MILLIS;
        lastUpdate = now;
        for (SideTab tab : tabs) {
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
        for (SideTab tab : tabs) {
            int width = tab.getWidth();
            int height = tab.getHeight();
            if (tab.progress <= 0) {
                boolean hovered = ArcforgeGui.isInside(mouseX, mouseY, tab.x, tab.y, width, height);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hovered ? TAB_HOVER : TAB, tab.x, tab.y, width, height);
            } else if (width <= SideTab.SELECTED_WIDTH) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TAB_SELECTED, tab.x, tab.y, SideTab.SELECTED_WIDTH, SideTab.COLLAPSED_HEIGHT);
            } else {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TAB_PANEL, tab.x, tab.y, width, height);
            }

            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, tab.getIcon(), tab.x + ICON_OFFSET, tab.y + ICON_OFFSET, 16, 16);

            if (tab.isFullyOpen()) {
                graphics.text(font, tab.getTitle(), tab.x + TITLE_X, tab.y + TITLE_Y, ArcforgeGui.TEXT, false);
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

    private void toggle(SideTab clicked) {
        boolean opening = !clicked.open;
        for (SideTab tab : tabs) {
            tab.open = false;
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
        for (SideTab tab : tabs) {
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
        for (SideTab tab : tabs) {
            areas.add(new Rect2i(tab.x, tab.y, tab.getWidth(), tab.getHeight()));
        }
        return areas;
    }
}
