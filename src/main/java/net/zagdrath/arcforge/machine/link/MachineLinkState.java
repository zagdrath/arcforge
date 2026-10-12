/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.link;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.arcforge.machine.config.SideMode;

// What a LinkableMachine keeps: its link (saved), and the mode a Wired Machine Link is asking for right now (not saved).
public final class MachineLinkState {
    private @Nullable MachineLink link;
    private @Nullable SideMode forced;

    public @Nullable MachineLink link() {
        return link;
    }

    void set(@Nullable MachineLink link) {
        this.link = link;
    }

    public @Nullable SideMode forced() {
        return forced;
    }

    void force(@Nullable SideMode mode) {
        this.forced = mode;
    }

    public void load(ValueInput input) {
        link = input.read("machine_link", MachineLink.CODEC).orElse(null);
    }

    public void save(ValueOutput output) {
        if (link != null) {
            output.store("machine_link", MachineLink.CODEC, link);
        }
    }
}
