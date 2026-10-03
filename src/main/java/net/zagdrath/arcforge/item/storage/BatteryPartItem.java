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
import net.zagdrath.arcforge.block.multiblock.LithiumCellBlock;
import net.zagdrath.arcforge.block.multiblock.PowerRegulatorBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModDataComponents;

// A Lithium Cell or Power Regulator item: a cell shows the FE it holds (its share of the array it came out of) against its
// capacity, a regulator the transfer rate it adds to a Battery Array.
public class BatteryPartItem extends BlockItem {
    public BatteryPartItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        if (getBlock() instanceof LithiumCellBlock cell) {
            long stored = stack.getOrDefault(ModDataComponents.STORED_ENERGY.get(), 0L);
            builder.accept(Component.translatable("gui.arcforge.fe_stored", String.format("%,d", stored),
                    String.format("%,d", ArcforgeConfig.lithiumCellCapacity(cell.getTier()))).withStyle(ChatFormatting.GRAY));
        } else if (getBlock() instanceof PowerRegulatorBlock regulator) {
            builder.accept(Component.translatable("tooltip.arcforge.power_regulator.transfer",
                    String.format("%,d", ArcforgeConfig.powerRegulatorTransfer(regulator.getTier()))).withStyle(ChatFormatting.GRAY));
        }
    }
}
