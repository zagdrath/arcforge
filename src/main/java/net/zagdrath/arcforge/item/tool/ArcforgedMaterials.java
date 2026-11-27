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
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.tag.ModItemTags;

// Arcforged equipment, one tier above netherite, beside SteelMaterials: the same item classes and models as the steel
// tools and armour, mining at netherite level, repaired with Arcforged Alloy. Every piece also holds FE and pays its
// wear in FE while it has charge (see ArcforgedGear).
public final class ArcforgedMaterials {
    // 3,072 uses, speed 11, +5 attack (a sword deals 9 against netherite's 8), enchantability 18.
    public static final ToolMaterial TOOL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3_072, 11.0F, 5.0F, 18, ModItemTags.ARCFORGED_ALLOYS);

    // The Hammer and Excavator: three times the uses, at 70% of the speed (as the steel ones are to steel tools).
    public static final ToolMaterial AREA = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 9_216, 7.7F, 5.0F, 18, ModItemTags.ARCFORGED_ALLOYS);

    // How the armour looks worn: equipment/arcforged.json (the steel armour's layers in darker Arcforged steel).
    public static final ResourceKey<EquipmentAsset> EQUIPMENT = ResourceKey.create(EquipmentAssets.ROOT_ID,
            Identifier.fromNamespaceAndPath(Arcforge.MODID, "arcforged"));

    // 4 / 9 / 7 / 4 armour, toughness 4 and knockback resistance 0.15 a piece, durability multiplier 48, enchantability
    // 18, the steel armour's equip sound.
    public static final ArmorMaterial ARMOR = new ArmorMaterial(48,
            Map.of(ArmorType.HELMET, 4, ArmorType.CHESTPLATE, 9, ArmorType.LEGGINGS, 7, ArmorType.BOOTS, 4, ArmorType.BODY, 12),
            18, SoundEvents.ARMOR_EQUIP_IRON, 4.0F, 0.15F, ModItemTags.ARCFORGED_ALLOYS, EQUIPMENT);

    private ArcforgedMaterials() {}
}
