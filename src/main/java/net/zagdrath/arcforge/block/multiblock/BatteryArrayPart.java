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
import net.zagdrath.arcforge.multiblock.BatteryArrayStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;

// A block of the Battery Array (see BatteryArrayStructure): a casing, the controller, a Lithium Cell or a Power Regulator.
// FORMED is set on every block of a formed box; the casings and controller then draw as one connected skin (see
// ConnectedModel, "battery_array"), and the regulators light their status lamp.
public interface BatteryArrayPart extends MultiblockPart {
    BooleanProperty FORMED = BooleanProperty.create("formed");

    @Override
    default @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return BatteryArrayStructure.findController(level, pos);
    }

    @Override
    default InteractionResult useWrench(UseOnContext context) {
        return BatteryArrayStructure.wrenchPart(context);
    }
}
