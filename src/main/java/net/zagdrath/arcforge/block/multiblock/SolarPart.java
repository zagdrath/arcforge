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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.multiblock.SolarThermalStructure;

// A block of the Solar Thermal Array's tower (see SolarThermalStructure): a casing, the controller or a
// collector. FORMED is set on every block of a formed tower, which then draws as one model (the base, the
// mast and the trough) instead of as blocks.
public interface SolarPart extends MultiblockPart {
    BooleanProperty FORMED = BooleanProperty.create("formed");

    static boolean isFormed(BlockState state) {
        return state.getBlock() instanceof SolarPart && state.getValue(FORMED);
    }

    @Override
    default @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return SolarThermalStructure.findController(level, pos);
    }

    @Override
    default InteractionResult useWrench(UseOnContext context) {
        return SolarThermalStructure.wrenchPart(context);
    }
}
