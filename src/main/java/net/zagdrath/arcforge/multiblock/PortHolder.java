/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import net.minecraft.world.level.block.state.BlockState;

// A multiblock block that can be one of its structure's ports (see MultiblockPorts).
public interface PortHolder {
    // Whether this block can hold ports (a subclass that can't says no).
    default boolean holdsPorts() {
        return true;
    }

    // Whether this block in this state can hold ports (the Gas Turbine Array's intake can't).
    default boolean holdsPorts(BlockState state) {
        return holdsPorts();
    }
}
