/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming.greenhouse;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.zagdrath.arcforge.multiblock.GreenhouseStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;

// A block of the Greenhouse Array (see GreenhouseStructure): a Greenhouse Frame, the controller, a Planting Bed or a Grow
// Lamp. FORMED is set on the frames and controller of a formed greenhouse (the beds and lamps have no such state), which
// then draw as one connected frame (see ConnectedModel, "greenhouse").
public interface GreenhousePart extends MultiblockPart {
    BooleanProperty FORMED = BooleanProperty.create("formed");

    @Override
    default @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return GreenhouseStructure.findController(level, pos);
    }

    @Override
    default InteractionResult useWrench(UseOnContext context) {
        return GreenhouseStructure.wrenchPart(context);
    }
}
