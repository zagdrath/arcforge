/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModDataComponents;

// What every Arcforged tool and armour piece shares (ArcforgedItem, ArcforgedAreaToolItem): it holds FE in the same
// ENERGY component as the Arc tools (charged through Capabilities.Energy.ITEM: Energy Cell slots, the Chargepad,
// Batteries). While it has charge its wear is paid in FE, fePerDurability a point, instead of durability, and a
// damaged piece restores selfRepairPerSecond points a second from its charge. With no charge, or with the FE buffer off in
// config, it wears and repairs like any other gear, so it never stops working. Like netherite it shrugs off fire and
// lava as an item, and also survives explosions.
public final class ArcforgedGear {
    // The charge bar's colour (the Arcforged tier cyan, as the Arc tools' bar).
    public static final int BAR_COLOR = 0x40B6D8;

    private ArcforgedGear() {}

    public static boolean enabled() {
        return ArcforgeConfig.ARCFORGED_FE_BUFFER.getAsBoolean();
    }

    public static int capacity() {
        return ArcforgeConfig.ARCFORGED_CAPACITY.getAsInt();
    }

    public static int receiveRate() {
        return ArcforgeConfig.ARCFORGED_RECEIVE.getAsInt();
    }

    public static int fePerDurability() {
        return Math.max(1, ArcforgeConfig.ARCFORGED_FE_PER_DURABILITY.getAsInt());
    }

    // The FE the piece holds (0 with the buffer off).
    public static int energy(ItemStack stack) {
        return enabled() ? Math.min(ArcToolItem.energy(stack), capacity()) : 0;
    }

    public static boolean charged(ItemStack stack) {
        return energy(stack) > 0;
    }

    // Wear of `amount` durability: as much as the charge covers is paid in FE; what's left (returned) wears the item. A
    // player with infinite materials (creative) pays nothing, as vanilla wears nothing for them.
    public static int absorbWear(ItemStack stack, int amount, @Nullable LivingEntity user) {
        if (amount <= 0 || !enabled() || user instanceof Player player && player.hasInfiniteMaterials()) {
            return amount;
        }
        int per = fePerDurability();
        int covered = Math.min(amount, energy(stack) / per);
        if (covered > 0) {
            ArcToolItem.spend(stack, covered * per);
        }
        return amount - covered;
    }

    // Once a second, a damaged piece with charge restores up to selfRepairPerSecond points of durability from its FE.
    public static void selfRepair(ItemStack stack, long gameTime) {
        if (!enabled() || !stack.isDamaged() || gameTime % 20 != 0) {
            return;
        }
        int per = fePerDurability();
        int points = Math.min(Math.min(ArcforgeConfig.ARCFORGED_REPAIR_PER_SECOND.getAsInt(), stack.getDamageValue()), energy(stack) / per);
        if (points > 0) {
            stack.setDamageValue(stack.getDamageValue() - points);
            ArcToolItem.spend(stack, points * per);
        }
    }

    // Whether a held piece changing from `oldStack` to `newStack` is only its wear or charge (self-repair, wear paid in FE,
    // charging): then the hand doesn't bob as if a new item was drawn, and mining doesn't restart.
    public static boolean onlyWearOrChargeChanged(ItemStack oldStack, ItemStack newStack) {
        if (!newStack.is(oldStack.getItem())) {
            return false;
        }
        ItemStack before = oldStack.copy(), after = newStack.copy();
        for (ItemStack stack : new ItemStack[] {before, after}) {
            stack.remove(DataComponents.DAMAGE);
            stack.remove(ModDataComponents.ENERGY.get());
        }
        return ItemStack.isSameItemSameComponents(before, after);
    }

    // As an item entity: immune to fire and lava (the fire-resistant property) and to explosions.
    public static boolean canBeHurtBy(DamageSource source) {
        return !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypeTags.IS_FIRE);
    }

    // The tooltip: the charge (with what it covers), then the durability (vanilla's own line shows it with advanced
    // tooltips on).
    public static void tooltip(ItemStack stack, Consumer<Component> builder, boolean advanced) {
        if (enabled()) {
            int energy = energy(stack);
            builder.accept(Component.translatable("tooltip.arcforge.arcforged.energy", String.format("%,d", energy), String.format("%,d", capacity()))
                    .withStyle(ChatFormatting.AQUA));
            builder.accept(Component.translatable(energy > 0 ? "tooltip.arcforge.arcforged.covers" : "tooltip.arcforge.arcforged.empty",
                    String.format("%,d", energy / fePerDurability())).withStyle(energy > 0 ? ChatFormatting.DARK_AQUA : ChatFormatting.DARK_GRAY));
        }
        if (stack.isDamageableItem() && !advanced) {
            int max = stack.getMaxDamage();
            builder.accept(Component.translatable("tooltip.arcforge.arcforged.durability", String.format("%,d", max - stack.getDamageValue()),
                    String.format("%,d", max)).withStyle(ChatFormatting.GRAY));
        }
    }

    // The charge bar's width, 0 to 13 px (as the durability bar's).
    public static int barWidth(ItemStack stack) {
        int capacity = capacity();
        return capacity <= 0 ? 0 : Math.round(13.0F * Math.clamp(energy(stack), 0, capacity) / capacity);
    }
}
