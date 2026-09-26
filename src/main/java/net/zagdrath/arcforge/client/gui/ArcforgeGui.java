/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.zagdrath.arcforge.Arcforge;

// Shared GUI colours and helpers for every Arcforge machine screen.
public final class ArcforgeGui {
    public static final int ACCENT = 0xFF5FD4C4;
    public static final int TEXT = 0xFFE0E0E0;
    public static final int LABEL = 0xFFA0A0A0;
    public static final int TOOLTIP_GRAY = 0xFFAAAAAA;
    public static final int TOOLTIP_GREEN = 0xFF55FF55;
    public static final int WHITE = 0xFFFFFFFF;

    private ArcforgeGui() {}

    // GUI atlas sprite id, e.g. sprite("widget/arcforge/tab") -> arcforge:widget/arcforge/tab.
    public static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, path);
    }

    public static Identifier widget(String name) {
        return sprite("widget/arcforge/" + name);
    }

    public static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
