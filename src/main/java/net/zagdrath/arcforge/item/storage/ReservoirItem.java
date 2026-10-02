/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.storage;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.zagdrath.arcforge.blockentity.storage.ReservoirBlockEntity;
import net.zagdrath.arcforge.registry.ModDataComponents;

// The Reservoir item: shows the share of fluid a broken Reservoir block kept.
public class ReservoirItem extends BlockItem {
    public ReservoirItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        FluidStack fluid = stack.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        Component name = fluid.isEmpty() ? Component.translatable("gui.arcforge.empty") : fluid.getHoverName();
        builder.accept(Component.translatable("tooltip.arcforge.tank.contents", name,
                String.format("%,d", fluid.getAmount()), String.format("%,d", ReservoirBlockEntity.CAPACITY)).withStyle(ChatFormatting.GRAY));
    }
}
