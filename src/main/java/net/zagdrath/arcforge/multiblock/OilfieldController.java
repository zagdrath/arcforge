/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.zagdrath.arcforge.heat.HeatHandler;

// The block entity of an oil or refinery fixed-size multiblock's controller (see OilfieldStructures).
public interface OilfieldController extends MultiblockController {
    // FE through the port on `side` of the block at pos (an unsided query sees its whole buffer); null where it has
    // none.
    default @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        return null;
    }

    // Heat the same way.
    default @Nullable HeatHandler heatHandlerAt(BlockPos pos, @Nullable Direction side) {
        return null;
    }

    // Recheck the structure on the next tick (a nearby part was placed or removed).
    void requestCheck();

    // Recheck it now.
    void checkNow();
}
