/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

// Dev-only storage blocks for GameTests: lodestone acts as a plain FE buffer and a target block as a
// plain 16,000 mB tank, so conduits can be tested against simple sources and sinks.
// Only registered when GameTests are enabled (dev runs), never in a normal game.
public final class TestFixtures {
    public static final int TANK_CAPACITY = 16_000;
    public static final int ENERGY_CAPACITY = 1_000_000;

    private static final Map<BlockPos, SimpleEnergyHandler> ENERGY = new HashMap<>();
    private static final Map<BlockPos, FluidStacksResourceHandler> TANKS = new HashMap<>();

    private TestFixtures() {}

    public static boolean enabled() {
        return System.getProperty("neoforge.enabledGameTestNamespaces") != null;
    }

    public static void register(IEventBus modEventBus) {
        if (enabled()) {
            modEventBus.addListener(TestFixtures::registerCapabilities);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.Energy.BLOCK, (level, pos, state, blockEntity, side) -> energy(pos), Blocks.LODESTONE);
        event.registerBlock(Capabilities.Fluid.BLOCK, (level, pos, state, blockEntity, side) -> tank(pos), Blocks.TARGET);
    }

    public static SimpleEnergyHandler energy(BlockPos pos) {
        return ENERGY.computeIfAbsent(pos.immutable(), p -> new SimpleEnergyHandler(ENERGY_CAPACITY));
    }

    public static FluidStacksResourceHandler tank(BlockPos pos) {
        return TANKS.computeIfAbsent(pos.immutable(), p -> new FluidStacksResourceHandler(1, TANK_CAPACITY));
    }

    public static void reset(BlockPos pos) {
        ENERGY.remove(pos);
        TANKS.remove(pos);
    }
}
