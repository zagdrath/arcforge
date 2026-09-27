/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModCapabilities;

// Pushes a machine's FE or heat straight into the blocks touching its ENERGY or HEAT faces.
// One instance per machine; it caches the neighbouring capabilities.
public class MachineOutputs {
    private final Map<Direction, BlockCapabilityCache<EnergyHandler, @Nullable Direction>> energyTargets = new EnumMap<>(Direction.class);
    private final Map<Direction, BlockCapabilityCache<HeatHandler, @Nullable Direction>> heatTargets = new EnumMap<>(Direction.class);

    // Moves up to max FE out of ENERGY faces (shared across them); returns how much moved.
    public int pushEnergy(ServerLevel level, BlockPos pos, Direction facing, SideConfig sides, EnergyHandler source, int max) {
        int budget = Math.min(source.getAmountAsInt(), max);
        int moved = 0;
        for (Direction direction : Direction.values()) {
            if (budget <= 0) {
                break;
            }
            if (sides.get(facing, direction) != SideMode.ENERGY) {
                continue;
            }
            int amount = EnergyHandlerUtil.move(source, target(energyTargets, Capabilities.Energy.BLOCK, level, pos, direction), budget, null);
            budget -= amount;
            moved += amount;
        }
        return moved;
    }

    // Moves heat out of HEAT faces into touching heat handlers, up to ratePerFace each, and only while
    // the source is hotter than the block it touches. Returns how much moved.
    public int pushHeat(ServerLevel level, BlockPos pos, Direction facing, SideConfig sides, HeatBuffer source, int ratePerFace) {
        int moved = 0;
        for (Direction direction : Direction.values()) {
            if (sides.get(facing, direction) != SideMode.HEAT) {
                continue;
            }
            HeatHandler target = target(heatTargets, ModCapabilities.HEAT, level, pos, direction);
            if (target != null) {
                moved += moveHeat(source, target, ratePerFace);
            }
        }
        return moved;
    }

    // min(rate, what the source holds, what the target can take), if the source is hotter.
    public static int moveHeat(HeatBuffer source, HeatHandler target, int rate) {
        if (rate <= 0 || source.getTemperature() <= target.getTemperature()) {
            return 0;
        }
        int amount = target.receiveHeat(Math.min(rate, source.getStored()), false);
        return source.remove(amount);
    }

    private static <H> @Nullable H target(Map<Direction, BlockCapabilityCache<H, @Nullable Direction>> caches,
            BlockCapability<H, @Nullable Direction> capability, ServerLevel level, BlockPos pos, Direction direction) {
        return caches.computeIfAbsent(direction, dir -> BlockCapabilityCache.create(capability, level, pos.relative(dir), dir.getOpposite()))
                .getCapability();
    }
}
