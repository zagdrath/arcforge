/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.tag;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.zagdrath.arcforge.Arcforge;

public final class ModItemTags {
    // Items accepted in machine upgrade slots. Empty until upgrade items exist.
    public static final TagKey<Item> UPGRADES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "upgrades"));

    // Items the Arcforge Furnace burns for heat (coal coke by default).
    public static final TagKey<Item> ARCFORGE_FURNACE_FUELS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "arcforge_furnace_fuels"));

    // Items the Combustion Plant and Firebox burn (coal, charcoal and coal blocks by default).
    public static final TagKey<Item> COMBUSTION_FUEL = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "combustion_fuel"));

    private ModItemTags() {}
}
