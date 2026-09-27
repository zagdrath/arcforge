/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.function.Consumer;

import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.machine.interaction.Dismantleable;
import net.zagdrath.arcforge.machine.interaction.WrenchableMachine;

// Configures conduit sides, rotates machines, and picks up conduits and machines.
// Runs from onItemUseFirst so it acts before the block's own interaction (such as opening a GUI).
public class WrenchItem extends Item {
    public WrenchItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        boolean conduit = state.getBlock() instanceof ConduitBlock;
        boolean machine = state.getBlock() instanceof WrenchableMachine;
        if (!conduit && !machine) {
            return InteractionResult.PASS;
        }
        // The server does the work; the client only swings.
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (conduit) {
            return ((ConduitBlock) state.getBlock()).useWrench(context);
        }
        Player player = context.getPlayer();
        return player != null && player.isSecondaryUseActive()
                ? dismantle(level, context.getClickedPos(), state, player)
                : rotate(level, context.getClickedPos(), state);
    }

    private static InteractionResult rotate(Level level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            return InteractionResult.PASS;
        }
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        level.setBlock(pos, state.setValue(HorizontalDirectionalBlock.FACING, facing.getClockWise()), Block.UPDATE_ALL);
        // Side configuration is relative to the front, so every face's capabilities just changed.
        level.invalidateCapabilities(pos);
        ConduitBlock.refreshAround(level, pos);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.6F);
        return InteractionResult.SUCCESS;
    }

    // Drops the machine as an item carrying its block entity data, so it can be placed back unchanged.
    private static InteractionResult dismantle(Level level, BlockPos pos, BlockState state, Player player) {
        ItemStack drop = new ItemStack(state.getBlock());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null) {
            try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LogUtils.getLogger())) {
                TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
                blockEntity.saveCustomOnly(output);
                blockEntity.removeComponentsFromTag(output);
                BlockItem.setBlockEntityData(drop, blockEntity.getType(), output);
            }
            drop.applyComponents(blockEntity.collectComponents());
            if (blockEntity instanceof Dismantleable dismantleable) {
                dismantleable.markDismantled();
            }
        }
        level.removeBlock(pos, false);
        Block.popResource(level, pos, drop);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5F, 1.2F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.arcforge.wrench.conduit").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.arcforge.wrench.pick_up").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.arcforge.wrench.machine").withStyle(ChatFormatting.GRAY));
    }
}
