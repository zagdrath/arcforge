/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.config;

import java.util.List;

import net.minecraft.network.chat.Component;

// How a machine reacts to a redstone signal.
public enum RedstoneMode {
    IGNORE("ignore"),
    HIGH("high"),
    LOW("low"),
    // Once per rising edge: the machine does one operation for each redstone pulse (see
    // MachineBlockEntity.redstoneAllows). canRun alone never runs it.
    PULSE("pulse"),
    // The signal strength sets how hard the machine runs (the Gas Turbine Array reads it itself); any signal
    // lets it run.
    THROTTLE("throttle");

    // What most machines offer; the Block Breaker and Block Placer add PULSE, the Gas Turbine Array THROTTLE.
    public static final List<RedstoneMode> STANDARD = List.of(IGNORE, HIGH, LOW);
    public static final List<RedstoneMode> WITH_PULSE = List.of(IGNORE, HIGH, LOW, PULSE);
    public static final List<RedstoneMode> THROTTLED = List.of(IGNORE, HIGH, LOW, THROTTLE);

    private final String name;

    RedstoneMode(String name) {
        this.name = name;
    }

    public boolean canRun(boolean powered) {
        return switch (this) {
            case IGNORE -> true;
            case HIGH -> powered;
            case LOW -> !powered;
            case PULSE -> false;
            case THROTTLE -> powered;
        };
    }

    public String getSerializedName() {
        return name;
    }

    public Component getDescription() {
        return Component.translatable("gui.arcforge.redstone." + name);
    }

    public static RedstoneMode byId(int id) {
        RedstoneMode[] values = values();
        return id >= 0 && id < values.length ? values[id] : IGNORE;
    }
}
