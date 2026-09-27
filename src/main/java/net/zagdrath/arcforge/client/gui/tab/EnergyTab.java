/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui.tab;

import java.util.function.IntSupplier;

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
        super(ArcforgeGui.widget("icon_energy"), Component.translatable("gui.arcforge.tab.energy"), 100, 74);
        this.stored = stored;
        this.perTick = perTick;
        this.usage = usage;
    }

    // For machines that use FE: "Usage -%d FE/t".
    public static EnergyTab usage(IntSupplier stored, IntSupplier perTick) {
        return new EnergyTab(stored, perTick, true);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
        graphics.text(font, Component.translatable("gui.arcforge.stored"), x + 6, y + 26, ArcforgeGui.TOOLTIP_GRAY, false);
        graphics.text(font, Component.translatable("gui.arcforge.fe_amount", String.format("%,d", stored.getAsInt())), x + 6, y + 36, ArcforgeGui.WHITE, false);
        if (usage) {
            graphics.text(font, Component.translatable("gui.arcforge.usage"), x + 6, y + 48, ArcforgeGui.TOOLTIP_GRAY, false);
            graphics.text(font, Component.translatable("gui.arcforge.fe_per_tick_loss", perTick.getAsInt()), x + 6, y + 58, USAGE_COLOR, false);
        } else {
            graphics.text(font, Component.translatable("gui.arcforge.output"), x + 6, y + 48, ArcforgeGui.TOOLTIP_GRAY, false);
            graphics.text(font, Component.translatable("gui.arcforge.fe_per_tick_gain", perTick.getAsInt()), x + 6, y + 58, ArcforgeGui.TOOLTIP_GREEN, false);
        }
    }
}
