/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModBlocks;

// Loam Farmland: tilled Loam. Like vanilla farmland it's moistened by water within 4 blocks (or rain), but it dries
// only dryingChance as often, so it stays moist about twice as long, and it can't be trampled (FarmingEvents cancels
// that). It holds NUTRIENTS (0-15), added with fertilizers (FertilizerItem); while it has any, the crop on it grows
// faster and each growth stage uses some (FarmingEvents). NPK Fertilizer also sets ENRICHED, for a stronger growth bonus
// until the nutrients run out (LoamGrowth). AFTER_LEGUME remembers that the last crop harvested from it was a legume,
// for crop rotation (CropRotation). Dry with nothing growing on it, or covered by a solid block,
// it turns back into Loam, and its nutrients go with it.
// Crops plant and grow on it through #minecraft:supports_crops and #minecraft:grows_crops; like any FarmlandBlock it
// counts as fertile (the moist-farmland growth rate) while it's moist.
// The irrigated kind has a copper water channel: it's always moist, needs no water, and never turns back.
public class LoamFarmlandBlock extends FarmlandBlock {
    public static final int MAX_NUTRIENTS = 15;
    public static final IntegerProperty NUTRIENTS = IntegerProperty.create("nutrients", 0, MAX_NUTRIENTS);
    public static final BooleanProperty ENRICHED = BooleanProperty.create("enriched");
    // Crop rotation: the last crop harvested from it was a legume, so the next non-legume grows faster (CropRotation).
    public static final BooleanProperty AFTER_LEGUME = BooleanProperty.create("after_legume");
    private static final int WET = 7;

    private final Block loam;
    private final boolean irrigated;

    // loam is what it turns back into (26.1's FarmlandBlock always turns into dirt, so this block does it itself).
    public LoamFarmlandBlock(Block loam, boolean irrigated, BlockBehaviour.Properties properties) {
        super(properties);
        this.loam = loam;
        this.irrigated = irrigated;
        registerDefaultState(stateDefinition.any().setValue(MOISTURE, irrigated ? WET : 0).setValue(NUTRIENTS, 0).setValue(ENRICHED, false)
                .setValue(AFTER_LEGUME, false));
    }

    public boolean isIrrigated() {
        return irrigated;
    }

    public static int nutrients(BlockState state) {
        return state.getBlock() instanceof LoamFarmlandBlock ? state.getValue(NUTRIENTS) : 0;
    }

    public static boolean isEnriched(BlockState state) {
        return state.getBlock() instanceof LoamFarmlandBlock && state.getValue(ENRICHED);
    }

    public static boolean isAfterLegume(BlockState state) {
        return state.getBlock() instanceof LoamFarmlandBlock && state.getValue(AFTER_LEGUME);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NUTRIENTS, ENRICHED, AFTER_LEGUME);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        return irrigated || state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : ModBlocks.LOAM.get().defaultBlockState();
    }

    // Irrigated farmland keeps its channel whatever is put on it.
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return irrigated || super.canSurvive(state, level, pos);
    }

    // Scheduled by FarmlandBlock when a solid block lands on top.
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            turnToBaseBlock(null, state, level, pos);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int moisture = state.getValue(MOISTURE);
        if (irrigated || isNearWater(level, pos) || level.isRainingAt(pos.above())) {
            if (moisture < WET) {
                level.setBlock(pos, state.setValue(MOISTURE, WET), Block.UPDATE_CLIENTS);
            }
        } else if (random.nextDouble() < ArcforgeConfig.LOAM_DRYING_CHANCE.getAsDouble()) {
            if (moisture > 0) {
                level.setBlock(pos, state.setValue(MOISTURE, moisture - 1), Block.UPDATE_CLIENTS);
            } else if (!level.getBlockState(pos.above()).is(BlockTags.MAINTAINS_FARMLAND)) {
                turnToBaseBlock(null, state, level, pos);
            }
        }
    }

    // Water within 4 blocks across and on its level or the one above, as for vanilla farmland.
    private static boolean isNearWater(LevelReader level, BlockPos pos) {
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            if (level.getFluidState(near).is(FluidTags.WATER)) {
                return true;
            }
        }
        return false;
    }

    // It can't be trampled: only the fall damage, never FarmlandBlock's trampling into dirt. (On 26.1 the trample event
    // carries the dirt it would become, so FarmingEvents can't tell Loam Farmland from other farmland.)
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        entity.causeFallDamage(fallDistance, 1.0F, entity.damageSources().fall());
    }

    // Back into its loam. Irrigated farmland never turns back.
    public void turnToBaseBlock(@Nullable Entity sourceEntity, BlockState state, Level level, BlockPos pos) {
        if (!irrigated) {
            BlockState newState = pushEntitiesUp(state, loam.defaultBlockState(), level, pos);
            level.setBlockAndUpdate(pos, newState);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(sourceEntity, newState));
        }
    }
}
