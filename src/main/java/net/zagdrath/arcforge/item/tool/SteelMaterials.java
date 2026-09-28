/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.Map;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.tag.ModItemTags;

// Steel equipment sits between iron and diamond, but mines at iron level: obsidian, ancient debris and
// Arcite still need diamond. Everything repairs with steel ingots.
public final class SteelMaterials {
    public static final ToolMaterial STEEL = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 500, 7.0F, 2.5F, 12, ModItemTags.STEEL_INGOTS);

    // The Hammer and Excavator: three times the uses, at 70% of the speed.
    public static final ToolMaterial STEEL_AREA = new ToolMaterial(BlockTags.INCORRECT_FOR_IRON_TOOL, 1_500, 4.9F, 2.5F, 12, ModItemTags.STEEL_INGOTS);

    public static final ArmorMaterial STEEL_ARMOR = new ArmorMaterial(25,
            Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 7, ArmorType.LEGGINGS, 5, ArmorType.BOOTS, 2, ArmorType.BODY, 7),
            12, SoundEvents.ARMOR_EQUIP_IRON, 1.0F, 0.0F, ModItemTags.STEEL_INGOTS,
            ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Arcforge.MODID, "steel")));

    private SteelMaterials() {}
}
