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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.zagdrath.arcforge.block.storage.EnergyCellBlock;
import net.zagdrath.arcforge.block.storage.FluidTankBlock;
import net.zagdrath.arcforge.block.storage.HeatCellBlock;
import net.zagdrath.arcforge.block.storage.PressurizedCylinderBlock;
import net.zagdrath.arcforge.block.storage.StorageBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.registry.ModDataComponents;

// Fluid tank / pressurized cylinder / energy cell / heat cell item. Shows what it holds, which is kept when the block is broken.
public class StorageBlockItem extends BlockItem {
    public StorageBlockItem(StorageBlock block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        ConduitTier tier = ((StorageBlock) getBlock()).getTier();
        if (getBlock() instanceof FluidTankBlock || getBlock() instanceof PressurizedCylinderBlock) {
            FluidStack fluid = stack.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
            Component name = fluid.isEmpty() ? Component.translatable("gui.arcforge.empty") : fluid.getHoverName();
            int capacity = getBlock() instanceof FluidTankBlock ? tier.tankCapacity() : tier.cylinderCapacity();
            builder.accept(Component.translatable("tooltip.arcforge.tank.contents", name,
                    format(fluid.getAmount()), format(capacity)).withStyle(ChatFormatting.GRAY));
        } else if (getBlock() instanceof EnergyCellBlock) {
            int energy = stack.getOrDefault(ModDataComponents.ENERGY.get(), 0);
            builder.accept(Component.translatable("gui.arcforge.fe_stored", format(energy), format(tier.cellCapacity()))
                    .withStyle(ChatFormatting.GRAY));
        } else if (getBlock() instanceof HeatCellBlock) {
            int heat = stack.getOrDefault(ModDataComponents.HEAT.get(), 0);
            builder.accept(Component.translatable("gui.arcforge.hu_stored", format(heat), format(tier.heatCellCapacity()))
                    .withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.arcforge.heat_cell.insulation", tier.getDisplayName())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static String format(int value) {
        return String.format("%,d", value);
    }
}
