/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.zagdrath.arcforge.registry.ModDamageTypes;

// Sulfuric Acid in the world: anything living that wades in takes 1 heart a second. It doesn't burn.
public class SulfuricAcidBlock extends LiquidBlock {
    private static final int INTERVAL = 20;
    private static final float DAMAGE = 2.0F;

    public SulfuricAcidBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level instanceof ServerLevel && entity instanceof LivingEntity && entity.tickCount % INTERVAL == 0) {
            entity.hurt(ModDamageTypes.source(level, ModDamageTypes.SULFURIC_ACID), DAMAGE);
        }
        super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
    }
}
