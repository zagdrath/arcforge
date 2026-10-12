/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.conduit;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModDataComponents;

// A Conduit Facade: a panel that covers one side of a conduit. It carries the block whose look it copies
// (arcforge:facade_target); without one it's blank. Mounting it is the conduit's job (ConduitBlock).
public class ConduitFacadeItem extends Item {
    public ConduitFacadeItem(Item.Properties properties) {
        super(properties);
    }

    public static @Nullable BlockState target(ItemStack stack) {
        return stack.get(ModDataComponents.FACADE_TARGET.get());
    }

    public static ItemStack of(Item facade, BlockState target) {
        ItemStack stack = new ItemStack(facade);
        stack.set(ModDataComponents.FACADE_TARGET.get(), target);
        return stack;
    }

    // "Stone Conduit Facade" once it copies a block.
    @Override
    public Component getName(ItemStack stack) {
        BlockState target = target(stack);
        return target != null ? Component.translatable("item.arcforge.conduit_facade.with", target.getBlock().getName()) : super.getName(stack);
    }

    // A facade always says what it looks like.
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        BlockState target = target(stack);
        builder.accept((target != null ? Component.translatable("tooltip.arcforge.conduit_facade.target", target.getBlock().getName())
                : Component.translatable("tooltip.arcforge.conduit_facade.blank")).withStyle(ChatFormatting.GRAY));
    }

    // Whether a facade can copy a block: a full, non-translucent cube drawn from its model, without a block entity, and not
    // on the config's blocklist (logistics.conduitFacades.blocklist).
    public static boolean canCopy(BlockState state) {
        if (state.hasBlockEntity() || state.getRenderShape() != RenderShape.MODEL || !state.canOcclude()
                || !Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
            return false;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return !ArcforgeConfig.SPEC.isLoaded() || !ArcforgeConfig.FACADE_BLOCKLIST.get().contains(id);
    }

    // The block an item would dress a facade in, or null if it can't.
    public static @Nullable BlockState targetFor(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            return canCopy(state) ? state : null;
        }
        return null;
    }
}
