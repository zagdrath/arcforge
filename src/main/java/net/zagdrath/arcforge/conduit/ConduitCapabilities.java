/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.zagdrath.arcforge.registry.ModCapabilities;

// The block capability each conduit type connects to.
public final class ConduitCapabilities {
    private ConduitCapabilities() {}

    public static BlockCapability<?, @Nullable Direction> forType(ConduitType type) {
        return switch (type) {
            case ENERGY -> Capabilities.Energy.BLOCK;
            case ITEM -> Capabilities.Item.BLOCK;
            case LIQUID -> Capabilities.Fluid.BLOCK;
            case THERMAL -> ModCapabilities.HEAT;
        };
    }

    // Whether the block next to a conduit side exposes the matching capability on the face touching the conduit.
    public static boolean canConnect(Level level, BlockPos conduitPos, Direction side, ConduitType type) {
        BlockPos target = conduitPos.relative(side);
        return level.isLoaded(target) && level.getCapability(forType(type), target, side.getOpposite()) != null;
    }
}
