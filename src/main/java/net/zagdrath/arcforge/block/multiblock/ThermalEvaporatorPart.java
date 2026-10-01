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
import net.zagdrath.arcforge.multiblock.ThermalEvaporatorStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;

// A block of the Thermal Evaporator Array's tower (see ThermalEvaporatorStructure): a Thermal Evaporator Casing or the
// controller. FORMED is set on every block of a formed tower, which then draws as one connected surface (see
// ConnectedModel, "thermal_evaporator").
public interface ThermalEvaporatorPart extends MultiblockPart {
    BooleanProperty FORMED = BooleanProperty.create("formed");

    @Override
    default @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return ThermalEvaporatorStructure.findController(level, pos);
    }

    @Override
    default InteractionResult useWrench(UseOnContext context) {
        return ThermalEvaporatorStructure.wrenchPart(context);
    }
}
