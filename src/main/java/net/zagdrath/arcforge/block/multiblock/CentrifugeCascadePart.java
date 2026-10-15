/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.zagdrath.arcforge.multiblock.CentrifugeCascadeStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;

// A block of the Gas Centrifuge Cascade (see CentrifugeCascadeStructure): a floor casing, the controller or a Gas
// Centrifuge. FORMED is set on every one of a formed hall; they then draw nothing themselves (the floor its ports) and
// the controller's renderer draws the hall.
public interface CentrifugeCascadePart extends MultiblockPart {
    BooleanProperty FORMED = BooleanProperty.create("formed");

    @Override
    default @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return CentrifugeCascadeStructure.findController(level, pos);
    }

    @Override
    default InteractionResult useWrench(UseOnContext context) {
        return CentrifugeCascadeStructure.wrenchPart(context);
    }
}
