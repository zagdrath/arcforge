/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.config;

import java.util.Arrays;

import net.minecraft.core.Direction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// Per-face IO modes for a machine, relative to its front, and whether its output faces push (auto-eject).
public class SideConfig {
    private static final int BITS_PER_SIDE = 4;
    private static final int SIDE_MASK = (1 << BITS_PER_SIDE) - 1;
    // Packed after the six faces.
    private static final int AUTO_EJECT_BIT = 1 << (6 * BITS_PER_SIDE);
    // Older saves packed each face into fewer bits: 2 under "sides" (before BYPRODUCT), then 3 under
    // "side_modes" (before the distillation modes).
    private static final int LEGACY_BITS_PER_SIDE = 2;
    private static final int OLD_BITS_PER_SIDE = 3;

    // Player actions on a face, sent from the side-config tab.
    public static final int ACTION_NEXT = 0;
    public static final int ACTION_PREVIOUS = 1;
    public static final int ACTION_CLEAR = 2;
    public static final int ACTION_COUNT = 3;

    private final SideMode[] modes = new SideMode[RelativeSide.values().length];
    private final SideMode[] defaults;
    // Off by default: outputs wait to be pulled out.
    private boolean autoEject;

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

    public boolean isAutoEject() {
        return autoEject;
    }

    public void setAutoEject(boolean autoEject) {
        this.autoEject = autoEject;
    }

    // Clears every face; auto-eject has its own button and stays as it is.
    public void clear() {
        Arrays.fill(modes, SideMode.NONE);
    }

    public void reset() {
        System.arraycopy(defaults, 0, modes, 0, modes.length);
        autoEject = false;
    }

    // Packs all faces and the auto-eject flag into one int for menu syncing and saving.
    public int pack() {
        int packed = autoEject ? AUTO_EJECT_BIT : 0;
        for (RelativeSide side : RelativeSide.values()) {
            packed |= modes[side.ordinal()].ordinal() << (side.ordinal() * BITS_PER_SIDE);
        }
        return packed;
    }

    public static SideMode unpack(int packed, RelativeSide side) {
        return SideMode.byId((packed >>> (side.ordinal() * BITS_PER_SIDE)) & SIDE_MASK);
    }

    public static boolean unpackAutoEject(int packed) {
        return (packed & AUTO_EJECT_BIT) != 0;
    }

    public void load(int packed) {
        for (RelativeSide side : RelativeSide.values()) {
            modes[side.ordinal()] = unpack(packed, side);
        }
        autoEject = unpackAutoEject(packed);
    }

    private void loadLegacy(int packed) {
        loadNarrow(packed, LEGACY_BITS_PER_SIDE);
        autoEject = false;
    }

    private void loadOld(int packed) {
        loadNarrow(packed, OLD_BITS_PER_SIDE);
        autoEject = (packed & 1 << (6 * OLD_BITS_PER_SIDE)) != 0;
    }

    private void loadNarrow(int packed, int bits) {
        int mask = (1 << bits) - 1;
        for (RelativeSide side : RelativeSide.values()) {
            modes[side.ordinal()] = SideMode.byId((packed >>> (side.ordinal() * bits)) & mask);
        }
    }

    public void serialize(ValueOutput output) {
        output.putInt("side_config", pack());
    }

    public void deserialize(ValueInput input) {
        input.getInt("side_config").ifPresentOrElse(this::load,
                () -> input.getInt("side_modes").ifPresentOrElse(this::loadOld,
                        () -> input.getInt("sides").ifPresentOrElse(this::loadLegacy, this::reset)));
    }

    @Override
    public String toString() {
        return "SideConfig" + Arrays.toString(modes) + (autoEject ? " auto-eject" : "");
    }
}
