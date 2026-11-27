/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

// An Arcforged sword, pickaxe, axe, shovel, hoe or armour piece: the steel item's behaviour (its Properties give the tool
// or armour components, as Item::new does for steel) plus the FE buffer that pays its wear (see ArcforgedGear).
public class ArcforgedItem extends Item {
    public ArcforgedItem(Properties properties) {
        super(properties);
    }

    // Wear is paid in FE first; only what the charge can't cover reaches the durability.
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<ItemStack> onBroken) {
        return ArcforgedGear.absorbWear(stack, amount, entity);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        ArcforgedGear.selfRepair(stack, level.getGameTime());
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged ? oldStack != newStack : !ArcforgedGear.onlyWearOrChargeChanged(oldStack, newStack);
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return !ArcforgedGear.onlyWearOrChargeChanged(oldStack, newStack);
    }

    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return ArcforgedGear.canBeHurtBy(source);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        ArcforgedGear.tooltip(stack, builder, flag.isAdvanced());
    }
}
