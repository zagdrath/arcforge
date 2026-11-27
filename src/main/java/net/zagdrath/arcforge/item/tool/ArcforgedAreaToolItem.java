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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.zagdrath.arcforge.recipe.ArcforgedUpgradeRecipe;

// The Arcforged Hammer and Excavator: the Steel Hammer and Excavator's 3x3 (AreaToolItem, AreaToolEvents) with the
// Arcforged FE buffer (see ArcforgedGear). Each block of the 3x3 is one point of wear, paid in FE while charged.
public class ArcforgedAreaToolItem extends AreaToolItem {
    public ArcforgedAreaToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<ItemStack> onBroken) {
        return ArcforgedGear.absorbWear(stack, amount, entity);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        ArcforgedGear.selfRepair(stack, level.getGameTime());
    }

    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return ArcforgedGear.canBeHurtBy(source);
    }

    // Taken from the Smithing Table: the upgrade from steel takes more than one Arcforged Alloy, and the menu takes one
    // (see ArcforgedUpgradeRecipe.takeExtraAdditions).
    @Override
    public void onCraftedBy(ItemStack stack, Player player) {
        super.onCraftedBy(stack, player);
        ArcforgedUpgradeRecipe.takeExtraAdditions(player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        ArcforgedGear.tooltip(stack, builder, flag.isAdvanced());
    }
}
