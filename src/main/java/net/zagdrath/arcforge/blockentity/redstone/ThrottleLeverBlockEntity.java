/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.block.redstone.ThrottleLeverBlock;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// Holds no saved data: the signal lives in the block state. On the client it eases the arm's angle toward
// the signal's angle so the lever swings instead of snapping.
public class ThrottleLeverBlockEntity extends BlockEntity {
    // Arm sweep: signal 0 leans 35 degrees back (toward the player / down on walls), 15 leans 35 degrees forward.
    public static final float MIN_ANGLE = -35.0F, MAX_ANGLE = 35.0F;
    private static final float MAX_STEP = 12.0F;           // degrees per tick

    private float angle = Float.NaN, previousAngle;

    public ThrottleLeverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.THROTTLE_LEVER.get(), pos, state);
    }

    public static float targetAngle(int power) {
        return MIN_ANGLE + (MAX_ANGLE - MIN_ANGLE) * power / 15.0F;
    }

    public void clientTick(BlockState state) {
        float target = targetAngle(state.getValue(ThrottleLeverBlock.POWER));
        if (Float.isNaN(angle)) {
            angle = previousAngle = target;
            return;
        }
        previousAngle = angle;
        angle += Math.clamp(target - angle, -MAX_STEP, MAX_STEP);
    }

    public float renderAngle(float partialTicks) {
        if (Float.isNaN(angle)) {
            return targetAngle(getBlockState().getValue(ThrottleLeverBlock.POWER));
        }
        return previousAngle + (angle - previousAngle) * partialTicks;
    }
}
