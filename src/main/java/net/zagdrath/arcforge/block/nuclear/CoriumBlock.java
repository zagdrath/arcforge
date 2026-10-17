/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.safety.Fallout;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDamageTypes;
import net.zagdrath.arcforge.tag.ModBlockTags;

// Corium: a melted core, a glowing orange-red molten mix of fuel, cladding and steel, extremely radioactive. It runs
// like slow lava and burns whatever wades into it. Its sources (random ticks):
//  - melt down through what's under them (nuclear.failures.meltdownMeltsBlocks), a block at a time, up to
//    coriumMeltDepth blocks (DEPTH counts them), but never through #arcforge:corium_resistant (obsidian, bedrock,
//    Containment Concrete, a Core Catcher, Solidified Corium);
//  - cool a step at a time (COOLING: a darkening crust; coriumCooling a tick, four times as fast with water beside it,
//    three times on a Core Catcher, which spreads it thin), and at the last step set into Solidified Corium, which
//    stays radioactive. A crusted source no longer melts anything.
public class CoriumBlock extends LiquidBlock {
    public static final IntegerProperty COOLING = IntegerProperty.create("cooling", 0, 3);
    public static final IntegerProperty DEPTH = IntegerProperty.create("depth", 0, 15);

    public CoriumBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
        registerDefaultState(defaultBlockState().setValue(COOLING, 0).setValue(DEPTH, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COOLING, DEPTH);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(LEVEL) == 0;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LEVEL) != 0) {
            return;
        }
        // Loose in the world it contaminates the ground round it (see Fallout.seep).
        Fallout.seep(level, pos, ArcforgeConfig.CORIUM_SEEP.getAsDouble());
        int cooling = state.getValue(COOLING);
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        double chance = ArcforgeConfig.CORIUM_COOLING.getAsDouble();
        if (touchesWater(level, pos)) {
            chance *= 4.0;
        }
        if (under.is(ModBlocks.CORE_CATCHER.get())) {
            chance *= 3.0;
        }
        if (random.nextDouble() < chance) {
            if (cooling >= 3) {
                level.setBlock(pos, ModBlocks.SOLIDIFIED_CORIUM.get().defaultBlockState(), Block.UPDATE_ALL);
            } else {
                level.setBlock(pos, state.setValue(COOLING, cooling + 1), Block.UPDATE_CLIENTS);
            }
            return;
        }
        int depth = state.getValue(DEPTH);
        if (cooling < 2 && ArcforgeConfig.MELTDOWN_MELTS_BLOCKS.getAsBoolean() && depth < Math.min(15, ArcforgeConfig.CORIUM_MELT_DEPTH.getAsInt())
                && canMelt(level, below, under)) {
            level.destroyBlock(below, false);
            level.setBlock(below, state.setValue(DEPTH, depth + 1), Block.UPDATE_ALL);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    // What a source can melt into: anything solid but what resists it (it flows into air and liquids by itself).
    public static boolean canMelt(Level level, BlockPos pos, BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.is(ModBlockTags.CORIUM_RESISTANT) || state.getBlock() instanceof CoriumBlock) {
            return false;
        }
        return state.getDestroySpeed(level, pos) >= 0.0F && pos.getY() > level.getMinY();
    }

    private static boolean touchesWater(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getFluidState(pos.relative(direction)).is(Fluids.WATER)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity && entity.tickCount % 10 == 0) {
            entity.igniteForSeconds(15.0F);
            entity.hurtServer(server, ModDamageTypes.source(level, ModDamageTypes.CORIUM), 6.0F);
        }
        super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
    }
}
