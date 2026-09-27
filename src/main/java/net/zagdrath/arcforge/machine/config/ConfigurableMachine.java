/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.config;

import java.util.List;

// A block entity whose sides (and optionally redstone mode) players configure from its GUI tabs.
public interface ConfigurableMachine {
    SideMode getSideMode(RelativeSide side);

    void setSideMode(RelativeSide side, SideMode mode);

    void clearSideModes();

    // The modes the side-config tab cycles through, in order.
    default List<SideMode> getAllowedSideModes() {
        return List.of(SideMode.values());
    }

    default void setRedstoneMode(RedstoneMode mode) {}
}
