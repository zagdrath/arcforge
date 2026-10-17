/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Equipment Hatch's middle: only there for its renderer (see EquipmentHatchRenderer), which swings the door open
// and shut over SWING_TICKS; the swing is the client's own (where it was last frame).
public class EquipmentHatchBlockEntity extends NuclearBlockEntity {
    public static final float SWING_TICKS = 30.0F;
    // Client side: how far open the door is drawn (0 shut, 1 open), and the game time it was last moved.
    public float swing = -1.0F;
    public double swungAt;

    public EquipmentHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.EQUIPMENT_HATCH.get(), pos, state);
    }
}
