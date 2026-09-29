/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.security;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

// A block entity with an owner (the player who placed it) and an optional security override; see SecurityRules.
// Every machine, multiblock controller and storage block is one, and the Security Terminal.
public interface Owned {
    Ownership ownership();

    @Nullable Level getLevel();

    BlockPos getBlockPos();

    default @Nullable UUID owner() {
        return ownership().owner();
    }

    default String ownerName() {
        return ownership().ownerName();
    }

    default Optional<SecurityMode> securityOverride() {
        return ownership().override();
    }

    default void setOwner(UUID owner, String name) {
        ownership().setOwner(owner, name);
    }

    default void setSecurityOverride(Optional<SecurityMode> override) {
        ownership().setOverride(override);
    }
}
