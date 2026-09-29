/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.function.IntSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;

// Shows stored FE and the current output (generators) or usage (machines that use power).
public class EnergyTab extends SideTab {
    private static final int USAGE_COLOR = 0xFFFF5555;

    private final IntSupplier stored;
    private final IntSupplier perTick;
    private final boolean usage;

    public EnergyTab(IntSupplier stored, IntSupplier perTick) {
        this(stored, perTick, false);
    }

    private EnergyTab(IntSupplier stored, IntSupplier perTick, boolean usage) {
        super(ArcforgeGui.widget("icon_energy"), Component.translatable("gui.arcforge.tab.energy"), 64, 74, Side.LEFT);
        this.stored = stored;
        this.perTick = perTick;
        this.usage = usage;
    }

    // For machines that use FE: "Usage -%d FE/t".
    public static EnergyTab usage(IntSupplier stored, IntSupplier perTick) {
        return new EnergyTab(stored, perTick, true);
    }

    private Component storedText() {
        return Component.translatable("gui.arcforge.fe_amount", ArcforgeGui.grouped(stored.getAsInt()));
    }

    private Component rateLabel() {
        return Component.translatable(usage ? "gui.arcforge.usage" : "gui.arcforge.output");
    }

    // "+N FE/t" in green or "-N FE/t" in red; an idle machine reads a plain grey "0 FE/t" rather than "-0".
    private Component rateText() {
        int rate = perTick.getAsInt();
        if (rate == 0) {
            return Component.translatable("gui.arcforge.fe_per_tick", 0);
        }
        return Component.translatable(usage ? "gui.arcforge.fe_per_tick_loss" : "gui.arcforge.fe_per_tick_gain", ArcforgeGui.grouped(rate));
    }

    private int rateColor() {
        return perTick.getAsInt() == 0 ? ArcforgeGui.LABEL : usage ? USAGE_COLOR : ArcforgeGui.TOOLTIP_GREEN;
    }

    @Override
    protected int expandedWidth() {
        Font font = Minecraft.getInstance().font;
        int widest = Math.max(font.width(Component.translatable("gui.arcforge.stored")), font.width(storedText()));
        widest = Math.max(widest, Math.max(font.width(rateLabel()), font.width(rateText())));
        return fitWidth(font, widest);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("gui.arcforge.stored"), x + CONTENT_INSET, y + 26, ArcforgeGui.TOOLTIP_GRAY, false);
        graphics.text(font, storedText(), x + CONTENT_INSET, y + 36, ArcforgeGui.WHITE, false);
        graphics.text(font, rateLabel(), x + CONTENT_INSET, y + 48, ArcforgeGui.TOOLTIP_GRAY, false);
        graphics.text(font, rateText(), x + CONTENT_INSET, y + 58, rateColor(), false);
    }
}
