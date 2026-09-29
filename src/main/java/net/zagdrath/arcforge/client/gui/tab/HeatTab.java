/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;

// Shows stored heat and the rate it's made or used at, plus an optional extra row
// (e.g. the Geothermal Plant's nearby lava and magma).
public class HeatTab extends SideTab {
    private final IntSupplier stored;
    private final Component rateLabel;
    private final Supplier<Component> rate;
    private final @Nullable Component extraLabel;
    private final @Nullable Supplier<Component> extra;

    public HeatTab(IntSupplier stored, Component rateLabel, Supplier<Component> rate) {
        this(stored, rateLabel, rate, null, null);
    }

    public HeatTab(IntSupplier stored, Component rateLabel, Supplier<Component> rate,
            @Nullable Component extraLabel, @Nullable Supplier<Component> extra) {
        super(ArcforgeGui.widget("icon_heat"), Component.translatable("gui.arcforge.tab.heat"), 100, extra == null ? 74 : 96, Side.LEFT);
        this.stored = stored;
        this.rateLabel = rateLabel;
        this.rate = rate;
        this.extraLabel = extraLabel;
        this.extra = extra;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("gui.arcforge.stored"), x + 6, y + 26, ArcforgeGui.TOOLTIP_GRAY, false);
        graphics.text(font, Component.translatable("gui.arcforge.hu_amount", ArcforgeGui.grouped(stored.getAsInt())), x + 6, y + 36, ArcforgeGui.WHITE, false);
        graphics.text(font, rateLabel, x + 6, y + 48, ArcforgeGui.TOOLTIP_GRAY, false);
        graphics.text(font, rate.get(), x + 6, y + 58, ArcforgeGui.HEAT, false);
        if (extraLabel != null && extra != null) {
            graphics.text(font, extraLabel, x + 6, y + 70, ArcforgeGui.TOOLTIP_GRAY, false);
            graphics.text(font, extra.get(), x + 6, y + 80, ArcforgeGui.TEXT, false);
        }
    }
}
