/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear.safety;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

// The containments of the loaded reactors, by level and by their reactor's position (each reactor puts its own here as
// it scans, and takes it out when it's removed or unloaded). What other things ask: whether a place is inside a sealed
// containment (a failing Spent Fuel Rack's fission products, a Hydrogen Recombiner's work), and the dose from a
// containment's radioactive air there (see RadiationField).
public final class Containments {
    private static final Map<Level, Map<BlockPos, Containment>> BY_LEVEL = new WeakHashMap<>();

    private Containments() {}

    public static void put(Level level, Containment containment) {
        BY_LEVEL.computeIfAbsent(level, key -> new HashMap<>()).put(containment.owner, containment);
    }

    public static void remove(Level level, BlockPos owner) {
        Map<BlockPos, Containment> map = BY_LEVEL.get(level);
        if (map != null) {
            map.remove(owner);
        }
    }

    public static Collection<Containment> all(Level level) {
        Map<BlockPos, Containment> map = BY_LEVEL.get(level);
        return map != null ? map.values() : List.of();
    }

    // The sealed containment pos is in, if any.
    public static @Nullable Containment at(Level level, BlockPos pos) {
        Map<BlockPos, Containment> map = BY_LEVEL.get(level);
        if (map == null) {
            return null;
        }
        for (Containment containment : map.values()) {
            if (containment.sealed() && containment.contains(pos)) {
                return containment;
            }
        }
        return null;
    }

    // The dose rate (mSv/h) from radioactive air at pos: its containment's, or nothing outside one.
    public static double airDose(Level level, BlockPos pos) {
        Containment containment = at(level, pos);
        return containment != null ? containment.activity : 0.0;
    }

    public static void forget(Level level) {
        BY_LEVEL.remove(level);
    }
}
