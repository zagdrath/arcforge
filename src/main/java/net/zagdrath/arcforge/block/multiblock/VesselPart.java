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
import net.zagdrath.arcforge.multiblock.VesselStructure;

// A wall block of a vessel (see VesselStructure): a casing, a controller or a pane of glass. FORMED is set on every one
// of a formed vessel; casings and the controller then draw nothing but their ports, and the controller's renderer draws
// the vessel.
public interface VesselPart extends MultiblockPart {
    BooleanProperty FORMED = BooleanProperty.create("formed");

    VesselStructure structure();

    @Override
    default @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return structure().findController(level, pos);
    }

    @Override
    default InteractionResult useWrench(UseOnContext context) {
        return structure().wrenchPart(context);
    }
}
