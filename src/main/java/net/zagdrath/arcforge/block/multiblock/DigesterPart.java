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
import net.zagdrath.arcforge.multiblock.BiogasDigesterStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;

// A block of the Biogas Digester's tank (see BiogasDigesterStructure): a Biogas Digester Casing or the controller. FORMED is set
// on every block of a formed tank, which then draws as one connected surface (see ConnectedModel, "digester").
public interface DigesterPart extends MultiblockPart {
    BooleanProperty FORMED = BooleanProperty.create("formed");

    @Override
    default @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return BiogasDigesterStructure.findController(level, pos);
    }

    @Override
    default InteractionResult useWrench(UseOnContext context) {
        return BiogasDigesterStructure.wrenchPart(context);
    }
}
