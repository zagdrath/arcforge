/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.api.machine.settings;

/**
 * A face of a single-block machine, relative to the way the machine faces (its front). For a machine facing up or
 * down, left, right and back are fixed to west, east and south of a machine facing north.
 */
public enum MachineSide {
    /** The top face. */
    TOP,
    /** The bottom face. */
    BOTTOM,
    /** The face to the machine's left, seen from in front of it. */
    LEFT,
    /** The face to the machine's right, seen from in front of it. */
    RIGHT,
    /** The face opposite the front. */
    BACK,
    /** The front face. */
    FRONT
}
