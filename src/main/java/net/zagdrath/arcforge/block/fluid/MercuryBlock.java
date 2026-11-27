/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.nuclear.HazmatSuit;

// Mercury in the world: anything living that wades in without all four pieces of a Hazmat Suit is poisoned (Poison for
// chemistry.mercury.poisonSeconds, renewed while it stays in). It doesn't burn and it doesn't hurt otherwise; entities
// float on it (ModFluids.MercuryType).
public class MercuryBlock extends LiquidBlock {
    private static final int INTERVAL = 20;

    public MercuryBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level instanceof ServerLevel && entity instanceof LivingEntity living && entity.tickCount % INTERVAL == 0 && !protectedFrom(living)) {
            living.addEffect(new MobEffectInstance(MobEffects.POISON, ArcforgeConfig.MERCURY_POISON_SECONDS.getAsInt() * 20,
                    ArcforgeConfig.MERCURY_POISON_LEVEL.getAsInt() - 1));
        }
        super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
    }

    // A full Hazmat Suit keeps it off the skin.
    public static boolean protectedFrom(LivingEntity entity) {
        return HazmatSuit.pieces(entity) >= 4;
    }
}
