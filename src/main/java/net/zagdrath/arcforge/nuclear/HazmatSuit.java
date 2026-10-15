/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import java.util.Map;

import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.tag.ModItemTags;

// The Hazmat Suit: four lead-lined pieces (#arcforge:hazmat_suit). Worn, they cut the radiation reaching the wearer
// from outside: the full set lets through radiation.hazmatTransmission (5%), fewer pieces part of that (helmet 15%,
// chestplate 40%, leggings 30%, boots 15% of the protection). What the wearer carries is inside the suit and isn't cut.
// Little armour otherwise; repaired with Lead Plates.
public final class HazmatSuit {
    public static final ArmorMaterial ARMOR = new ArmorMaterial(12,
            Map.of(ArmorType.HELMET, 1, ArmorType.CHESTPLATE, 3, ArmorType.LEGGINGS, 2, ArmorType.BOOTS, 1, ArmorType.BODY, 3),
            9, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, ModItemTags.REPAIRS_HAZMAT_SUIT,
            ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.fromNamespaceAndPath(Arcforge.MODID, "hazmat")));

    private static final EquipmentSlot[] SLOTS = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
    private static final double[] SHARE = { 0.15, 0.40, 0.30, 0.15 };

    private HazmatSuit() {}

    public static int pieces(LivingEntity entity) {
        int count = 0;
        for (EquipmentSlot slot : SLOTS) {
            count += entity.getItemBySlot(slot).is(ModItemTags.HAZMAT_SUIT) ? 1 : 0;
        }
        return count;
    }

    // The fraction of outside radiation that reaches the wearer (1 with no pieces on).
    public static double transmission(LivingEntity entity) {
        double cover = 0.0;
        for (int i = 0; i < SLOTS.length; i++) {
            if (entity.getItemBySlot(SLOTS[i]).is(ModItemTags.HAZMAT_SUIT)) {
                cover += SHARE[i];
            }
        }
        return 1.0 - cover * (1.0 - ArcforgeConfig.HAZMAT_TRANSMISSION.getAsDouble());
    }
}
