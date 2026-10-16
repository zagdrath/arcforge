/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.nuclear;

import java.util.Locale;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.zagdrath.arcforge.nuclear.Enrichment;
import net.zagdrath.arcforge.nuclear.FuelRod;
import net.zagdrath.arcforge.registry.ModDamageTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;

// A Fuel Rod (see FuelRod): Zircaloy cladding full of Fuel Pellets, loaded into a Fission Reactor's Fuel Channels. Its
// bar shows its burnup. Fresh it's mildly radioactive; once it has been in a running core it's intensely radioactive
// (see RadiationField) and, until its decay heat fades, too hot to hold: it burns whoever carries it.
public class FuelRodItem extends Item {
    public FuelRodItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (level.getGameTime() % 20 != 0 || !(owner instanceof Player player) || player.getAbilities().invulnerable) {
            return;
        }
        FuelRod rod = stack.get(ModDataComponents.FUEL_ROD.get());
        if (rod != null && rod.isHot(level.getGameTime())) {
            player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.HOT_FUEL), 2.0F);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        FuelRod rod = stack.get(ModDataComponents.FUEL_ROD.get());
        return rod != null && rod.burnup() > 0.0F;
    }

    // Burnup fills it, green to red.
    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * FuelRod.of(stack).burnup());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float left = 1.0F - FuelRod.of(stack).burnup();
        return Mth.hsvToRgb(left / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        FuelRod rod = FuelRod.of(stack);
        builder.accept(Component.translatable("tooltip.arcforge.fuel_rod.fuel", Component.translatable("tooltip.arcforge.fuel_rod.type." + rod.type().getSerializedName()),
                Enrichment.percent(rod.enrichment())).withStyle(ChatFormatting.GRAY));
        if (rod.damaged()) {
            builder.accept(Component.translatable("tooltip.arcforge.fuel_rod.damaged").withStyle(ChatFormatting.DARK_RED));
        }
        if (rod.burnup() > 0.0F) {
            builder.accept(Component.translatable(rod.isSpent() ? "tooltip.arcforge.fuel_rod.spent" : "tooltip.arcforge.fuel_rod.burnup", rod.burnupText())
                    .withStyle(rod.isSpent() ? ChatFormatting.RED : ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.arcforge.fuel_rod.built_up", String.format(Locale.ROOT, "%.2f%%", rod.plutonium() * 100.0F),
                    String.format(Locale.ROOT, "%.0f%%", rod.fissionProducts() * 100.0F)).withStyle(ChatFormatting.DARK_GRAY));
        } else {
            builder.accept(Component.translatable("tooltip.arcforge.fuel_rod.fresh").withStyle(ChatFormatting.GREEN));
        }
        if (rod.dischargeHeat() > 0.0F && context.level() != null) {
            double heat = rod.decayHeat(context.level().getGameTime());
            builder.accept(Component.translatable(rod.isHot(context.level().getGameTime()) ? "tooltip.arcforge.fuel_rod.hot" : "tooltip.arcforge.fuel_rod.decay_heat",
                    String.format(Locale.ROOT, "%.1f", heat)).withStyle(rod.isHot(context.level().getGameTime()) ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
        }
    }
}
