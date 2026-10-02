/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.quantum;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.zagdrath.arcforge.conduit.ConduitType;

// What a Quantum Tunnel carries; each face is set for each one separately (see QuantumFaces).
public enum QuantumResource implements StringRepresentable {
    ENERGY("energy", ConduitType.ENERGY),
    HEAT("heat", ConduitType.THERMAL),
    FLUID("fluid", ConduitType.FLUID),
    GAS("gas", ConduitType.GAS),
    ITEMS("items", ConduitType.ITEM);

    private final String name;
    private final ConduitType conduit;

    QuantumResource(String name, ConduitType conduit) {
        this.name = name;
        this.conduit = conduit;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public ConduitType conduit() {
        return conduit;
    }

    public Component displayName() {
        return Component.translatable("gui.arcforge.quantum_tunnel.resource." + name);
    }

    public static QuantumResource of(ConduitType type) {
        for (QuantumResource resource : values()) {
            if (resource.conduit == type) {
                return resource;
            }
        }
        return ITEMS;
    }
}
