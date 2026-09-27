/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.handbook;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

// Lets the Engineer's Handbook open an item's recipes in a recipe viewer. The JEI plugin sets it when JEI
// is running; without one, the handbook just says to install it.
public final class RecipeLinks {
    private static @Nullable Consumer<ItemStack> viewer;

    private RecipeLinks() {}

    public static void setViewer(@Nullable Consumer<ItemStack> viewer) {
        RecipeLinks.viewer = viewer;
    }

    public static boolean available() {
        return viewer != null;
    }

    public static void show(ItemStack stack) {
        if (viewer != null) {
            viewer.accept(stack);
        }
    }
}
