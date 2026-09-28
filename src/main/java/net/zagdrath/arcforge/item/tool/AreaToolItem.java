/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

// The Steel Hammer and Excavator: breaking a block also breaks the 3x3 around it, in the plane facing the side
// that was hit (see AreaToolEvents; AreaToolHighlight outlines them). The pickaxe or shovel behaviour itself,
// path-making included, comes from the item's components.
public class AreaToolItem extends Item {
    public AreaToolItem(Properties properties) {
        super(properties);
    }

    // The other blocks a break at `centre` takes with it: those in the 3x3 across `axis` that the tool is good
    // against, no harder than the centre, breakable and without a block entity. None if the tool isn't good
    // against the centre. The client outline and the server break share this, so they always agree.
    public static List<BlockPos> targets(Level level, BlockPos centre, BlockState centreState, Direction.Axis axis, ItemStack tool) {
        List<BlockPos> targets = new ArrayList<>();
        if (!isEffective(tool, centreState)) {
            return targets;
        }
        float hardness = centreState.getDestroySpeed(level, centre);
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                BlockPos pos = switch (axis) {
                    case X -> centre.offset(0, a, b);
                    case Y -> centre.offset(a, 0, b);
                    case Z -> centre.offset(a, b, 0);
                };
                BlockState state = level.getBlockState(pos);
                float speed = state.getDestroySpeed(level, pos);
                if (state.isAir() || speed < 0 || speed > hardness || level.getBlockEntity(pos) != null || !isEffective(tool, state)) {
                    continue;
                }
                targets.add(pos);
            }
        }
        return targets;
    }

    // Good against: it's the right tool for drops, or it at least mines the block faster than a hand.
    public static boolean isEffective(ItemStack tool, BlockState state) {
        return tool.isCorrectToolForDrops(state) || tool.getDestroySpeed(state) > 1.0F;
    }

    // The axis of the face the player is mining `pos` from: from their line of sight, or the way they face if
    // that doesn't land on it.
    public static Direction.Axis miningAxis(Player player, BlockPos pos) {
        if (player.pick(player.blockInteractionRange(), 1.0F, false) instanceof BlockHitResult hit
                && hit.getType() == BlockHitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) {
            return hit.getDirection().getAxis();
        }
        return Direction.orderedByNearest(player)[0].getAxis();
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.arcforge.area_tool").withStyle(ChatFormatting.GRAY));
        builder.accept(Component.translatable("tooltip.arcforge.area_tool.rules").withStyle(ChatFormatting.DARK_GRAY));
    }
}
