/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.config;

import java.util.Arrays;

import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// Per-face IO modes for a machine. The front face is locked to NONE.
public class SideConfig {
    private static final int BITS_PER_SIDE = 2;
    private static final int SIDE_MASK = (1 << BITS_PER_SIDE) - 1;

    // Player actions on a face, sent from the side-config tab.
    public static final int ACTION_NEXT = 0;
    public static final int ACTION_PREVIOUS = 1;
    public static final int ACTION_CLEAR = 2;
    public static final int ACTION_COUNT = 3;

    private final SideMode[] modes = new SideMode[RelativeSide.values().length];
    private final SideMode[] defaults;

    public SideConfig(SideMode top, SideMode bottom, SideMode left, SideMode right, SideMode back) {
        this.defaults = new SideMode[] { top, bottom, left, right, back, SideMode.NONE };
        reset();
    }

    public SideMode get(RelativeSide side) {
        return modes[side.ordinal()];
    }

    public SideMode get(Direction facing, Direction direction) {
        return get(RelativeSide.fromDirection(facing, direction));
    }

    public static boolean isLocked(RelativeSide side) {
        return side == RelativeSide.FRONT;
    }

    public void set(RelativeSide side, SideMode mode) {
        if (!isLocked(side)) {
            modes[side.ordinal()] = mode;
        }
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
            modes[side.ordinal()] = isLocked(side) ? SideMode.NONE : unpack(packed, side);
        }
    }

    public void serialize(ValueOutput output) {
        output.putInt("sides", pack());
    }

    public void deserialize(ValueInput input) {
        input.getInt("sides").ifPresentOrElse(this::load, this::reset);
    }

    @Override
    public String toString() {
        return "SideConfig" + Arrays.toString(modes);
    }
}
