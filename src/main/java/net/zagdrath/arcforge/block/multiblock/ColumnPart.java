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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.zagdrath.arcforge.multiblock.DistillationStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;

// A block of the Distillation Array's column (see DistillationStructure): a casing, a tray level casing or
// the controller. FORMED is set on every block of a formed column; LIT (trays and the controller) while
// it runs.
public interface ColumnPart extends MultiblockPart {
    BooleanProperty FORMED = BooleanProperty.create("formed");
    BooleanProperty LIT = BlockStateProperties.LIT;

    @Override
    default @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return DistillationStructure.findController(level, pos);
    }

    @Override
    default InteractionResult useWrench(UseOnContext context) {
        return DistillationStructure.wrenchPart(context);
    }
}
