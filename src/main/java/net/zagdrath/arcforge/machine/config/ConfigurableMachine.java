/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.config;

import java.util.List;

import org.jspecify.annotations.Nullable;

// A block entity whose sides (and optionally redstone mode) players configure from its GUI tabs.
public interface ConfigurableMachine {
    SideMode getSideMode(RelativeSide side);

    void setSideMode(RelativeSide side, SideMode mode);

    void clearSideModes();

    // The modes the side-config tab cycles through, in order.
    default List<SideMode> getAllowedSideModes() {
        return List.of(SideMode.values());
    }

    // The current redstone mode, or null for a block without one.
    default @Nullable RedstoneMode getRedstoneMode() {
        return null;
    }

    default void setRedstoneMode(RedstoneMode mode) {}

    // The redstone modes its Redstone tab offers; the menu refuses any other.
    default List<RedstoneMode> getAllowedRedstoneModes() {
        return RedstoneMode.STANDARD;
    }

    // Auto-eject: whether output faces push items and fluids into neighbours (off: they wait to be
    // pulled). Machines without item or fluid outputs have no button and never push.
    default boolean isAutoEject() {
        return false;
    }

    default void setAutoEject(boolean autoEject) {}
}
