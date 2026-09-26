/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.config;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

// A machine face relative to its front. Left and right are as seen when looking at the front.
public enum RelativeSide {
    TOP("top"),
    BOTTOM("bottom"),
    LEFT("left"),
    RIGHT("right"),
    BACK("back"),
    FRONT("front");

    private final String name;

    RelativeSide(String name) {
        this.name = name;
    }

    public Direction toDirection(Direction facing) {
        return switch (this) {
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            case LEFT -> facing.getClockWise();
            case RIGHT -> facing.getCounterClockWise();
        };
    }

    public static RelativeSide fromDirection(Direction facing, Direction direction) {
        for (RelativeSide side : values()) {
            if (side.toDirection(facing) == direction) {
                return side;
            }
        }
        return FRONT;
    }

    public Component getDescription() {
        return Component.translatable("gui.arcforge.side." + name);
    }

    public static RelativeSide byId(int id) {
        RelativeSide[] values = values();
        return id >= 0 && id < values.length ? values[id] : FRONT;
    }
}
