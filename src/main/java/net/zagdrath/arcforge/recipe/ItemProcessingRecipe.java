/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.recipe;

import java.util.Optional;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;

// An item made into another item, with an optional bonus item rolled once per operation, over time ticks: what
// ItemLane runs (Milling and Seed Extracting).
public interface ItemProcessingRecipe {
    Ingredient ingredient();

    ItemStackTemplate result();

    Optional<ItemStackTemplate> bonus();

    float bonusChance();

    int time();
}
