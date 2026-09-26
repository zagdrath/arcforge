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

    private ModItemTags() {}
}
