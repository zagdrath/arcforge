/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// The machines loaded on each server level. At the end of every level tick each one's MachineControlState counts its
// loaded and running time and tells listeners about a status change. Machines add themselves when they load and leave
// when removed or unloaded.
public final class MachineControlTracker {
    private static final Map<Level, Set<BlockEntity>> LOADED = new WeakHashMap<>();

    private MachineControlTracker() {}

    public static void add(BlockEntity blockEntity) {
        if (blockEntity.getLevel() instanceof ServerLevel level) {
            LOADED.computeIfAbsent(level, key -> new LinkedHashSet<>()).add(blockEntity);
        }
    }

    public static void remove(BlockEntity blockEntity) {
        Level level = blockEntity.getLevel();
        Set<BlockEntity> loaded = level == null ? null : LOADED.get(level);
        if (loaded != null) {
            loaded.remove(blockEntity);
        }
    }

    static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Set<BlockEntity> loaded = LOADED.get(level);
        if (loaded == null || loaded.isEmpty()) {
            return;
        }
        // A copy: a listener may break or place blocks.
        for (BlockEntity blockEntity : loaded.toArray(BlockEntity[]::new)) {
            if (blockEntity.isRemoved()) {
                loaded.remove(blockEntity);
            } else if (MachineControls.of(blockEntity) instanceof SpecMachineControl<?> control) {
                control.tick();
            }
        }
    }
}
