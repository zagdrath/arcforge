/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

// One expandable tab on the right edge of a machine GUI. Subclasses draw the panel content.
// All content coordinates are relative to the tab's top-left corner.
public abstract class SideTab {
    public static final int COLLAPSED_WIDTH = 22;
    public static final int SELECTED_WIDTH = 24;
    public static final int COLLAPSED_HEIGHT = 20;

    // Which edge of the GUI the tab hangs off: the machine's readouts on the left, its settings on the right.
    public enum Side {
        LEFT, RIGHT
    }

    private final Identifier icon;
    private final Component title;
    private final int expandedWidth;
    private final int expandedHeight;
    private final Side side;

    // 0 = collapsed, 1 = fully open. Animated by SideTabPanel.
    float progress;
    boolean open;
    int x;
    int y;

    protected SideTab(Identifier icon, Component title, int expandedWidth, int expandedHeight) {
        this(icon, title, expandedWidth, expandedHeight, Side.RIGHT);
    }

    protected SideTab(Identifier icon, Component title, int expandedWidth, int expandedHeight, Side side) {
        this.icon = icon;
        this.title = title;
        this.expandedWidth = expandedWidth;
        this.expandedHeight = expandedHeight;
        this.side = side;
    }

    public Side getSide() {
        return side;
    }

    // A tab can hide itself (the Security tab, when security is off); hidden tabs take no room.
    public boolean isVisible() {
        return true;
    }

    public Identifier getIcon() {
        return icon;
    }

    public Component getTitle() {
        return title;
    }

    public int getWidth() {
        return progress <= 0 ? COLLAPSED_WIDTH : Math.round(Mth.lerp(progress, SELECTED_WIDTH, expandedWidth()));
    }

    public int getHeight() {
        return Math.round(Mth.lerp(progress, COLLAPSED_HEIGHT, expandedHeight()));
    }

    // The open panel's size. A tab whose content changes can size it to what it shows now.
    protected int expandedWidth() {
        return expandedWidth;
    }

    protected int expandedHeight() {
        return expandedHeight;
    }

    public boolean isFullyOpen() {
        return progress >= 1.0F;
    }

    // Called whenever the tab becomes fully open or starts closing.
    protected void onFullyOpenChanged(boolean fullyOpen) {}

    protected abstract void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY);

    // Returns true if the click was handled. localX/localY are relative to the tab origin.
    protected boolean contentClicked(MouseButtonEvent event, int localX, int localY) {
        return false;
    }

    protected void addTooltip(List<Component> lines, int localX, int localY) {}

    // Tooltip while the tab is collapsed; defaults to its title.
    protected void addCollapsedTooltip(List<Component> lines) {
        lines.add(title);
    }
}
