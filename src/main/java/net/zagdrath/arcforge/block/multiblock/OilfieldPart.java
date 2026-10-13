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
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;

// A block of an Oil Derrick or a Pumpjack (see OilfieldStructures): a Derrick Casing, Derrick Frame or Pumpjack Casing,
// or either controller. FORMED is set on every block of a formed rig or pumpjack.
public interface OilfieldPart extends MultiblockPart {
    BooleanProperty FORMED = BooleanProperty.create("formed");

    @Override
    default @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return OilfieldStructures.findController(level, pos);
    }

    @Override
    default InteractionResult useWrench(UseOnContext context) {
        return OilfieldStructures.wrenchPart(context);
    }
}
