/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.farming;

import java.util.List;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmlandBlock;
import net.zagdrath.arcforge.farming.CropHarvest;

// The Sickle (iron and steel) and the Scythe (steel). Used on a crop (or the farmland under it), it harvests every ripe
// crop in the square around it ((2 x radius + 1) across: sickleRadius, 3x3, or scytheRadius, 5x5) and replants each
// from its own drops, which fall on the ground (CropHarvest). A bearing Trellis has its cones picked. Each crop
// harvested costs 1 durability.
public class SickleItem extends Item {
    private final IntSupplier radius;

    public SickleItem(IntSupplier radius, Item.Properties properties) {
        super(properties);
        this.radius = radius;
    }

    public int radius() {
        return radius.getAsInt();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.getBlockState(pos).getBlock() instanceof FarmlandBlock) {
            pos = pos.above();
        }
        if (!CropHarvest.isCrop(level.getBlockState(pos))) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        int harvested = reap(serverLevel, pos, context.getItemInHand(), context.getPlayer(), context.getHand());
        if (harvested == 0) {
            return InteractionResult.PASS;
        }
        level.playSound(null, pos, SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 0.8F, 1.3F);
        return InteractionResult.SUCCESS;
    }

    // Harvests the ripe crops around centre (and the top of a two-high Trellis), dropping each harvest where it grew.
    // Returns how many.
    public int reap(ServerLevel level, BlockPos centre, ItemStack tool, @Nullable Player player, InteractionHand hand) {
        int harvested = 0;
        for (BlockPos column : CropHarvest.square(centre, radius())) {
            for (int dy = 1; dy >= -1; dy--) {
                if (tool.isEmpty()) {
                    return harvested;
                }
                BlockPos pos = column.above(dy);
                if (CropHarvest.harvest(level, pos, onGround(level, pos))) {
                    harvested++;
                    if (player != null) {
                        tool.hurtAndBreak(1, player, hand);
                    }
                }
            }
        }
        return harvested;
    }

    private static CropHarvest.Sink onGround(ServerLevel level, BlockPos pos) {
        return new CropHarvest.Sink() {
            @Override
            public boolean fits(List<ItemStack> stacks) {
                return true;
            }

            @Override
            public void put(ItemStack stack) {
                Block.popResource(level, pos, stack);
            }
        };
    }
}
