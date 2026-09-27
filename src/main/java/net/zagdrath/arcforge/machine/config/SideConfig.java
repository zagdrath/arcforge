/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.config;

import java.util.Arrays;

import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// Per-face IO modes for a machine, relative to its front.
public class SideConfig {
    private static final int BITS_PER_SIDE = 3;
    private static final int SIDE_MASK = (1 << BITS_PER_SIDE) - 1;
    // Saves from before BYPRODUCT existed packed each face into 2 bits under "sides".
    private static final int LEGACY_BITS_PER_SIDE = 2;

    // Player actions on a face, sent from the side-config tab.
    public static final int ACTION_NEXT = 0;
    public static final int ACTION_PREVIOUS = 1;
    public static final int ACTION_CLEAR = 2;
    public static final int ACTION_COUNT = 3;

    private final SideMode[] modes = new SideMode[RelativeSide.values().length];
    private final SideMode[] defaults;

    public SideConfig(SideMode top, SideMode bottom, SideMode left, SideMode right, SideMode back, SideMode front) {
        this.defaults = new SideMode[] { top, bottom, left, right, back, front };
        reset();
    }

    public SideMode get(RelativeSide side) {
        return modes[side.ordinal()];
    }

    public SideMode get(Direction facing, Direction direction) {
        return get(RelativeSide.fromDirection(facing, direction));
    }

    public void set(RelativeSide side, SideMode mode) {
        modes[side.ordinal()] = mode;
    }

    public void clear() {
        Arrays.fill(modes, SideMode.NONE);
    }

    public void reset() {
        System.arraycopy(defaults, 0, modes, 0, modes.length);
    }

    // Packs all faces into one int for menu syncing and saving.
    public int pack() {
        int packed = 0;
        for (RelativeSide side : RelativeSide.values()) {
            packed |= modes[side.ordinal()].ordinal() << (side.ordinal() * BITS_PER_SIDE);
        }
        return packed;
    }

    public static SideMode unpack(int packed, RelativeSide side) {
        return SideMode.byId((packed >>> (side.ordinal() * BITS_PER_SIDE)) & SIDE_MASK);
    }

    public void load(int packed) {
        for (RelativeSide side : RelativeSide.values()) {
            modes[side.ordinal()] = unpack(packed, side);
        }
    }

    private void loadLegacy(int packed) {
        int mask = (1 << LEGACY_BITS_PER_SIDE) - 1;
        for (RelativeSide side : RelativeSide.values()) {
            modes[side.ordinal()] = SideMode.byId((packed >>> (side.ordinal() * LEGACY_BITS_PER_SIDE)) & mask);
        }
    }

    public void serialize(ValueOutput output) {
        output.putInt("side_modes", pack());
    }

    public void deserialize(ValueInput input) {
        input.getInt("side_modes").ifPresentOrElse(this::load,
                () -> input.getInt("sides").ifPresentOrElse(this::loadLegacy, this::reset));
    }

    @Override
    public String toString() {
        return "SideConfig" + Arrays.toString(modes);
    }
}
