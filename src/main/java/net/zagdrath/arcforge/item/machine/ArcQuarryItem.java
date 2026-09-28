/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.machine;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.machine.ArcQuarryBlock;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBoundingBlockEntity;
import net.zagdrath.arcforge.machine.quarry.QuarrySettings;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;

// Places an Arc Quarry: a 3x3x3 standing on the clicked-against face, all of it replaceable (and inside the world
// border and build height) or nothing is placed. The centre becomes the main block (facing the player), the rest
// its bounding parts, and the item's settings and name go to the main block.
public class ArcQuarryItem extends BlockItem {
    public ArcQuarryItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!context.canPlace()) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        BlockPos main = context.getClickedPos().above();
        for (BlockPos pos : ArcQuarryBlock.positions(main)) {
            if (!level.getBlockState(pos).canBeReplaced(context) || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)) {
                Player player = context.getPlayer();
                if (player != null && !level.isClientSide()) {
                    player.sendOverlayMessage(Component.translatable("message.arcforge.quarry.no_room").append(" ")
                            .append(Component.translatable("message.arcforge.quarry.no_room_at", pos.toShortString())));
                }
                return InteractionResult.FAIL;
            }
        }
        if (!level.isClientSide()) {
            ItemStack stack = context.getItemInHand();
            BlockState state = getBlock().defaultBlockState().setValue(MachineBlock.FACING, context.getHorizontalDirection().getOpposite());
            level.setBlock(main, state, Block.UPDATE_ALL);
            if (level.getBlockEntity(main) instanceof ArcQuarryBlockEntity quarry) {
                quarry.applyComponentsFromItemStack(stack);
                quarry.setChanged();
            }
            BlockState part = ModBlocks.ARC_QUARRY_BOUNDING.get().defaultBlockState();
            for (BlockPos pos : ArcQuarryBlock.positions(main)) {
                if (pos.equals(main)) {
                    continue;
                }
                level.setBlock(pos, part, Block.UPDATE_ALL | Block.UPDATE_KNOWN_SHAPE);
                if (level.getBlockEntity(pos) instanceof ArcQuarryBoundingBlockEntity bounding) {
                    bounding.setMain(main);
                }
            }
            // Neighbours (conduits) look again now the machine is whole.
            for (BlockPos pos : ArcQuarryBlock.positions(main)) {
                level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock());
            }
            level.playSound(null, main, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        Player player = context.getPlayer();
        context.getItemInHand().consume(1, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        QuarrySettings settings = stack.get(ModDataComponents.QUARRY_SETTINGS.get());
        if (settings != null) {
            builder.accept(Component.translatable("tooltip.arcforge.quarry.area", settings.radius(), settings.minY(), settings.maxY())
                    .withStyle(ChatFormatting.GRAY));
            builder.accept(Component.translatable("tooltip.arcforge.quarry.filter", settings.count(),
                    Component.translatable(settings.deny() ? "gui.arcforge.conduit_filter.denylist" : "gui.arcforge.conduit_filter.allowlist"))
                    .withStyle(ChatFormatting.GRAY));
        }
        super.appendHoverText(stack, context, display, builder, flag);
    }
}
