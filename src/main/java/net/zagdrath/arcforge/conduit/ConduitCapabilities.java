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
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.heat.HeatHandler;
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

    // The connection an "auto" conduit side makes with the block next to it.
    //  - Arcforge machines decide from their side configuration (see ConduitConnectable).
    //  - Other blocks are pushed into, except energy/heat blocks that can only give, which are pulled from.
    //    Use the wrench to force a side to output to pull from another mod's storage.
    public static ConnectionMode autoMode(Level level, BlockPos conduitPos, Direction side, ConduitType type) {
        if (!canConnect(level, conduitPos, side, type)) {
            return ConnectionMode.NONE;
        }
        BlockPos target = conduitPos.relative(side);
        Direction face = side.getOpposite();
        if (level.getBlockEntity(target) instanceof ConduitConnectable connectable) {
            return connectable.getConduitConnection(face, type);
        }
        return switch (type) {
            case ENERGY -> energyMode(level.getCapability(Capabilities.Energy.BLOCK, target, face));
            case THERMAL -> heatMode(level.getCapability(ModCapabilities.HEAT, target, face));
            case ITEM, LIQUID -> ConnectionMode.INPUT;
        };
    }

    private static ConnectionMode energyMode(@Nullable EnergyHandler handler) {
        if (handler == null) {
            return ConnectionMode.NONE;
        }
        int accepted;
        int given;
        try (Transaction simulation = Transaction.openRoot()) {
            accepted = handler.insert(1, simulation);
        }
        try (Transaction simulation = Transaction.openRoot()) {
            given = handler.extract(1, simulation);
        }
        return accepted == 0 && given > 0 ? ConnectionMode.OUTPUT : ConnectionMode.INPUT;
    }

    private static ConnectionMode heatMode(@Nullable HeatHandler handler) {
        if (handler == null) {
            return ConnectionMode.NONE;
        }
        return handler.receiveHeat(1, true) == 0 && handler.extractHeat(1, true) > 0 ? ConnectionMode.OUTPUT : ConnectionMode.INPUT;
    }
}
