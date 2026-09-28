/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.sound;

import java.util.function.Consumer;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;

// Machines' running loops. Their blocks tick on clients too, handing the block entity to the hook, which keeps
// its loop going while the machine runs. The client sets the hook (to MachineLoopSound::keepPlaying), so the
// blocks never load client code on a server.
public final class MachineSounds {
    public static Consumer<BlockEntity> clientHook = machine -> {};

    private MachineSounds() {}

    public static <T extends BlockEntity> BlockEntityTicker<T> clientTicker() {
        return (level, pos, state, machine) -> clientHook.accept(machine);
    }
}
