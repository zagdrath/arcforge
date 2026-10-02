/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.quantum;

import net.minecraft.core.Direction;

// A Quantum Tunnel's side configuration packed into one long: for each face (by Direction.get3DDataValue) and each
// resource, two bits: NONE, INPUT (the face takes that resource into the frequency) or OUTPUT (it gives it out, and pushes
// it into the block there). Faces start as NONE.
public final class QuantumFaces {
    public static final int NONE = 0, INPUT = 1, OUTPUT = 2;
    private static final int RESOURCES = QuantumResource.values().length;

    private QuantumFaces() {}

    private static int shift(Direction face, QuantumResource resource) {
        return (face.get3DDataValue() * RESOURCES + resource.ordinal()) * 2;
    }

    public static int get(long faces, Direction face, QuantumResource resource) {
        int mode = (int) (faces >>> shift(face, resource)) & 3;
        return mode > OUTPUT ? NONE : mode;
    }

    public static long with(long faces, Direction face, QuantumResource resource, int mode) {
        int shift = shift(face, resource);
        return (faces & ~(3L << shift)) | ((long) (mode & 3) << shift);
    }

    // NONE -> INPUT -> OUTPUT -> NONE.
    public static int next(int mode) {
        return (mode + 1) % 3;
    }

    public static boolean any(long faces, Direction face, int mode) {
        for (QuantumResource resource : QuantumResource.values()) {
            if (get(faces, face, resource) == mode) {
                return true;
            }
        }
        return false;
    }

    // For the menu: the long split into two ints.
    public static int low(long faces) {
        return (int) faces;
    }

    public static int high(long faces) {
        return (int) (faces >>> 32);
    }

    public static long join(int low, int high) {
        return (low & 0xFFFFFFFFL) | ((long) high << 32);
    }
}
