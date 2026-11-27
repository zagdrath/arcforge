/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.fusion;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.zagdrath.arcforge.fusion.Tritium;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModFluids;

// A Tritium Canister: a gas canister lined with tungsten and double-walled, so tritium doesn't get out of it (any other
// cartridge or tank leaks a little; see Tritium). It holds tritium (filled like a Gas Cartridge, tritium only); what
// decays to Helium-3 while it's carried is kept apart in it (HELIUM_3_CONTENTS), and comes out of it as a second tank.
public class TritiumCanisterItem extends Item {
    public static final int CAPACITY = 32_000, RATE = 1_000;
    private static final int DECAY_INTERVAL = 100;
    private static final int BAR_COLOR = 0xFFCDEBCB;

    public TritiumCanisterItem(Item.Properties properties) {
        super(properties);
    }

    public static FluidStack tritium(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
    }

    public static FluidStack helium3(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.HELIUM_3_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
    }

    // Brings the decay up to date: whole mB of tritium turned to Helium-3 since the clock was last set.
    public static void decay(ItemStack stack, long now) {
        Long clock = stack.get(ModDataComponents.DECAY_CLOCK.get());
        FluidStack tritium = tritium(stack);
        if (tritium.isEmpty() || !Tritium.is(tritium.getFluid())) {
            stack.remove(ModDataComponents.DECAY_CLOCK.get());
            return;
        }
        if (clock == null || clock > now) {
            stack.set(ModDataComponents.DECAY_CLOCK.get(), now);
            return;
        }
        int decayed = (int) Math.floor(tritium.getAmount() * Tritium.decayFraction(now - clock));
        if (decayed <= 0) {
            return;
        }
        int left = tritium.getAmount() - decayed;
        stack.set(ModDataComponents.FLUID_CONTENTS.get(), left > 0 ? SimpleFluidContent.copyOf(tritium.copyWithAmount(left)) : SimpleFluidContent.EMPTY);
        FluidStack helium = helium3(stack);
        int helium3 = (helium.isEmpty() ? 0 : helium.getAmount()) + decayed;
        stack.set(ModDataComponents.HELIUM_3_CONTENTS.get(), SimpleFluidContent.copyOf(new FluidStack(ModFluids.HELIUM_3.get(), Math.min(CAPACITY, helium3))));
        stack.set(ModDataComponents.DECAY_CLOCK.get(), now);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (level.getGameTime() % DECAY_INTERVAL == 0) {
            decay(stack, level.getGameTime());
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !tritium(stack).isEmpty();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(Mth.clamp((float) tritium(stack).getAmount() / CAPACITY, 0.0F, 1.0F) * 13.0F);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        FluidStack tritium = tritium(stack);
        FluidStack helium = helium3(stack);
        String capacity = String.format("%,d", CAPACITY);
        builder.accept(tritium.isEmpty() ? Component.translatable("gui.arcforge.empty").withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.arcforge.portable.fluid", tritium.getHoverName(), String.format("%,d", tritium.getAmount()), capacity)
                        .withStyle(ChatFormatting.GRAY));
        if (!helium.isEmpty()) {
            builder.accept(Component.translatable("tooltip.arcforge.tritium_canister.helium_3", String.format("%,d", helium.getAmount()))
                    .withStyle(ChatFormatting.GOLD));
        }
        builder.accept(Component.translatable("tooltip.arcforge.tritium_canister.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
