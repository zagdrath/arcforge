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

    // Coal coke blocks: the Arcforge Furnace breaks them open into nine coal coke for fuel and reagent.
    public static final TagKey<Item> COAL_COKE_BLOCKS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "storage_blocks/coal_coke"));

    // Items the Combustion Plant and Firebox burn (coal, charcoal and coal blocks by default).
    public static final TagKey<Item> COMBUSTION_FUEL = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "combustion_fuel"));

    // Dies: what a Metal Press die slot takes.
    public static final TagKey<Item> DIES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "dies"));

    // Plastic (Plastic Sheets), for Conduit Filters and Storage Upgrades.
    public static final TagKey<Item> PLASTICS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "plastics"));

    // Steel ingots: what steel tools and armour repair with.
    public static final TagKey<Item> STEEL_INGOTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots/steel"));

    // The Foundry Suit's pieces, and what repairs them (Rock Wool).
    public static final TagKey<Item> FOUNDRY_SUIT = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "foundry_suit"));
    public static final TagKey<Item> REPAIRS_FOUNDRY_SUIT = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "repairs_foundry_suit"));

    // Fuel whose burnt-out items leave Wood Ash in a Firebox or Combustion Plant (charcoal by default).
    public static final TagKey<Item> LEAVES_WOOD_ASH = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "leaves_wood_ash"));

    // Fertilizers for Loam Farmland (Compost, Wood Ash, Basic Slag, Mixed Fertilizer).
    public static final TagKey<Item> FERTILIZERS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "fertilizers"));

    // What the Fermenter's additive slot takes (Dried Hops): each one raises the Ethanol of a few operations.
    public static final TagKey<Item> FERMENTER_ADDITIVES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "fermenter_additives"));

    // Legumes (Soybeans): they put nutrients back into Loam Farmland, set up crop rotation, and need no fertilizer or
    // Nutrient Solution in the automated farms (CropRotation). The seeds; ModBlockTags.LEGUMES holds the crops.
    public static final TagKey<Item> LEGUMES = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "legumes"));

    // Biomass: what the Hydrothermal Carbonizer cooks into Bio-Coal (crops, seeds, leaves, saplings, sticks, vines, kelp,
    // Press Cake, Compost).
    public static final TagKey<Item> BIOMASS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "biomass"));

    private ModItemTags() {}
}
