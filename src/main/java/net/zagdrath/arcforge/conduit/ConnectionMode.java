/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

// What a conduit side is connected to. The names are used by the blockstate files.
public enum ConnectionMode implements StringRepresentable {
    NONE("none"),
    // Connected to another conduit of the same type.
    PIPE("pipe"),
    // Wrench-configured: the conduit pushes into the neighbouring block.
    INPUT("input"),
    // Wrench-configured: the conduit pulls from the neighbouring block.
    OUTPUT("output");

    private final String name;

    ConnectionMode(String name) {
        this.name = name;
    }

    public boolean isPort() {
        return this == INPUT || this == OUTPUT;
    }

    // Wrench cycle for machine sides: none -> input -> output -> none.
    public ConnectionMode nextPort(boolean backwards) {
        return switch (this) {
            case NONE -> backwards ? OUTPUT : INPUT;
            case INPUT -> backwards ? NONE : OUTPUT;
            case OUTPUT -> backwards ? INPUT : NONE;
            case PIPE -> NONE;
        };
    }

    public Component getDisplayName() {
        return Component.translatable("conduit_mode.arcforge." + name);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
