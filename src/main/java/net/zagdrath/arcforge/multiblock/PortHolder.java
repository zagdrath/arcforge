/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

// A multiblock block that can be one of its structure's ports (see MultiblockPorts).
public interface PortHolder {
    // Whether this block can hold ports (a subclass that can't says no).
    default boolean holdsPorts() {
        return true;
    }
}
