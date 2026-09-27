/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

// A block that can be part of a multiblock. Capabilities, conduit connections and the GUI of any part
// are served by the structure's controller.
public interface MultiblockPart {
    // The controller of the formed structure this block belongs to, or null if it isn't part of one.
    @Nullable MultiblockController findController(Level level, BlockPos pos);

    // Server side: a Wrench click in Configure mode. Rechecks the structure (and may turn it).
    InteractionResult useWrench(UseOnContext context);
}
