/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

// What a conduit carries. Conduits only connect to conduits of the same type.
public enum ConduitType implements StringRepresentable {
    ENERGY("energy", true),
    ITEM("item", false),
    FLUID("fluid", false),
    THERMAL("thermal", true);

    private final String name;
    // Energy and thermal conduits glow while moving their resource; item and fluid conduits are glass.
    private final boolean hasActiveState;

    ConduitType(String name, boolean hasActiveState) {
        this.name = name;
        this.hasActiveState = hasActiveState;
    }

    public boolean hasActiveState() {
        return hasActiveState;
    }

    // Glass conduits render their contents with a block entity renderer.
    public boolean isTransparent() {
        return !hasActiveState;
    }

    public Component getDisplayName() {
        return Component.translatable("conduit_type.arcforge." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
