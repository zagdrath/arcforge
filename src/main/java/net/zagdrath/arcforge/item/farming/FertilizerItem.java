/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.item.farming;

import java.util.function.IntSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;

// Compost, Wood Ash, Basic Slag and Mixed Fertilizer: used on Loam Farmland (or on the crop growing on it), one adds
// its nutrients (config farming.fertilizers), up to 15. They do nothing on anything else.
public class FertilizerItem extends Item {
    private final IntSupplier nutrients;

    public FertilizerItem(IntSupplier nutrients, Item.Properties properties) {
        super(properties);
        this.nutrients = nutrients;
    }

    public int nutrients() {
        return nutrients.getAsInt();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof LoamFarmlandBlock)) {
            pos = pos.below();
        }
        BlockState soil = level.getBlockState(pos);
        if (!(soil.getBlock() instanceof LoamFarmlandBlock)) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        int before = soil.getValue(LoamFarmlandBlock.NUTRIENTS);
        if (before >= LoamFarmlandBlock.MAX_NUTRIENTS || nutrients() <= 0) {
            if (!level.isClientSide() && player != null) {
                player.sendOverlayMessage(Component.translatable("message.arcforge.loam_farmland.full"));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        fertilize(level, pos, soil, nutrients());
        if (player == null || !player.hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    // Adds nutrients to the Loam Farmland at pos (capped at 15), with bone meal's particles and a soft thud. Returns
    // the new level.
    public static int fertilize(Level level, BlockPos pos, BlockState soil, int amount) {
        int after = Math.min(LoamFarmlandBlock.MAX_NUTRIENTS, soil.getValue(LoamFarmlandBlock.NUTRIENTS) + amount);
        level.setBlock(pos, soil.setValue(LoamFarmlandBlock.NUTRIENTS, after), Block.UPDATE_ALL);
        level.levelEvent(LevelEvent.PARTICLES_AND_SOUND_PLANT_GROWTH, pos.above(), 0);
        level.playSound(null, pos, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
        return after;
    }
}
