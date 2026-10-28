/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;

// Electrically powered landing protection. One damaging landing costs a fixed amount regardless of height;
// ordinary steps cost nothing. The FE component charges through the same capability as Arc Tools.
public class LongFallBootsItem extends Item {
    public static final int CAPACITY = 100_000;
    public static final int RECEIVE_RATE = 2_000;
    public static final int LANDING_COST = 1_000;
    private final boolean armored;

    public LongFallBootsItem(Properties properties) {
        this(false, properties);
    }

    public LongFallBootsItem(boolean armored, Properties properties) {
        super(properties(properties, armored));
        this.armored = armored;
    }

    private static Properties properties(Properties properties, boolean armored) {
        if (armored) {
            properties.attributes(SteelMaterials.STEEL_ARMOR.createAttributes(ArmorType.BOOTS));
        }
        return properties.stacksTo(1)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET)
                        .setAsset(SteelMaterials.STEEL_ARMOR.assetId()).setEquipSound(SteelMaterials.STEEL_ARMOR.equipSound()).build());
    }

    public boolean armored() {
        return armored;
    }

    public static void restoreArmor(ItemStack stack) {
        if (stack.getItem() instanceof LongFallBootsItem boots && boots.armored()) {
            stack.set(DataComponents.ATTRIBUTE_MODIFIERS, boots.getDefaultInstance().get(DataComponents.ATTRIBUTE_MODIFIERS));
            stack.set(DataComponents.EQUIPPABLE, boots.getDefaultInstance().get(DataComponents.EQUIPPABLE));
        }
    }

    public static boolean absorb(LivingEntity wearer, DamageSource source, float amount) {
        ItemStack boots = wearer.getItemBySlot(EquipmentSlot.FEET);
        if (wearer.level().isClientSide() || amount <= 0 || !source.is(DamageTypeTags.IS_FALL)
                || !(boots.getItem() instanceof LongFallBootsItem) || ArcToolItem.energy(boots) < LANDING_COST) {
            return false;
        }
        ArcToolItem.spend(boots, LANDING_COST);
        wearer.resetFallDistance();
        return true;
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * Math.clamp(ArcToolItem.energy(stack), 0, CAPACITY) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ArcToolItem.BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.arcforge.shock_boots.energy", String.format("%,d", ArcToolItem.energy(stack)),
                String.format("%,d", CAPACITY)).withStyle(ChatFormatting.GRAY));
        boolean ready = ArcToolItem.energy(stack) >= LANDING_COST;
        builder.accept(Component.translatable(ready ? "tooltip.arcforge.shock_boots.ready" : "tooltip.arcforge.shock_boots.empty")
                .withStyle(ready ? ChatFormatting.GREEN : ChatFormatting.RED));
    }
}
