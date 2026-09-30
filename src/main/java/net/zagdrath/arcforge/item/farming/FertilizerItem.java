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

// Compost, Wood Ash, Basic Slag, Mixed Fertilizer, Seed Meal, Digestate and NPK Fertilizer: used on Loam Farmland (or on
// the crop growing on it), one adds its nutrients (config farming.fertilizers), up to 15. They do nothing on anything
// else. NPK Fertilizer also enriches the farmland (LoamFarmlandBlock.ENRICHED): until its nutrients run out, the crop on
// it grows at loamFarmland.npkGrowthMultiplier instead of growthMultiplier.
public class FertilizerItem extends Item {
    private final IntSupplier nutrients;
    private final boolean enriches;

    public FertilizerItem(IntSupplier nutrients, Item.Properties properties) {
        this(nutrients, false, properties);
    }

    public FertilizerItem(IntSupplier nutrients, boolean enriches, Item.Properties properties) {
        super(properties);
        this.nutrients = nutrients;
        this.enriches = enriches;
    }

    public int nutrients() {
        return nutrients.getAsInt();
    }

    public boolean enriches() {
        return enriches;
    }

    // Whether using it on this soil would change anything: it has room for nutrients, or this enriches soil that isn't.
    public boolean wouldHelp(BlockState soil) {
        if (!(soil.getBlock() instanceof LoamFarmlandBlock)) {
            return false;
        }
        return soil.getValue(LoamFarmlandBlock.NUTRIENTS) < LoamFarmlandBlock.MAX_NUTRIENTS && nutrients() > 0
                || enriches && !soil.getValue(LoamFarmlandBlock.ENRICHED);
    }

    // Fertilizes the Loam Farmland at pos with this item (and enriches it, if this enriches). Returns the new level.
    public int apply(Level level, BlockPos pos, BlockState soil) {
        return fertilize(level, pos, enriches ? soil.setValue(LoamFarmlandBlock.ENRICHED, true) : soil, nutrients());
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
        if (!wouldHelp(soil)) {
            if (!level.isClientSide() && player != null) {
                player.sendOverlayMessage(Component.translatable("message.arcforge.loam_farmland.full"));
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        apply(level, pos, soil);
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
