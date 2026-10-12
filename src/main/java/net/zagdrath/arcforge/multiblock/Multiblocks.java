/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;

// Finding the structure any block belongs to, whatever kind of block it is.
public final class Multiblocks {
    private Multiblocks() {}

    // The controller of the multiblock the block at pos is part of (formed or not), or null: a part's own controller, or
    // for a pane of Pressure Glass, the array, Greenhouse, Thermal Evaporator, Firebox Array or Battery Array it's in.
    public static @Nullable MultiblockController controllerAt(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        MultiblockController controller = null;
        if (block instanceof MultiblockPart part) {
            controller = part.findController(level, pos);
        } else if (block instanceof PressureGlassBlock) {
            controller = PressureGlassBlock.findMaster(level, pos);
            if (controller == null) {
                controller = GreenhouseStructure.findController(level, pos);
            }
            if (controller == null) {
                controller = ThermalEvaporatorStructure.findController(level, pos);
            }
            if (controller == null) {
                controller = FireboxArrayStructure.findController(level, pos);
            }
            if (controller == null) {
                controller = BatteryArrayStructure.findController(level, pos);
            }
        }
        return controller;
    }
}
