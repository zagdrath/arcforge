/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gas;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.api.gas.Gas;
import net.zagdrath.arcforge.api.gas.GasCapabilities;
import net.zagdrath.arcforge.api.gas.GasHandler;
import net.zagdrath.arcforge.api.gas.GasRegistry;
import net.zagdrath.arcforge.machine.control.MachineControls;

// The gas API (docs/API.md): GasCapabilities.BLOCK on every Arcforge block. A machine, or any block of a formed
// multiblock, gives its machine's gas tanks (MachineControls); anything else its fluid handler with no side, if that
// holds a gas (a Pressurized Cylinder). Items are registered in ModCapabilities.
public final class GasHandlers {
    // Every gas, for checking which tanks can hold one. Rebuilt when tags change, since #arcforge:gases makes gases.
    private static volatile @Nullable List<Gas> gases;

    private GasHandlers() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(GasHandlers::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(TagsUpdatedEvent.class, event -> gases = null);
    }

    static List<Gas> gases() {
        List<Gas> all = gases;
        if (all == null) {
            all = GasRegistry.all();
            gases = all;
        }
        return all;
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        Block[] blocks = BuiltInRegistries.BLOCK.stream()
                .filter(block -> Arcforge.MODID.equals(BuiltInRegistries.BLOCK.getKey(block).getNamespace()))
                .toArray(Block[]::new);
        event.registerBlock(GasCapabilities.BLOCK, (level, pos, state, blockEntity, context) -> find(level, pos, state, blockEntity), blocks);
    }

    private static @Nullable GasHandler find(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        if (level.isClientSide()) {
            return null;
        }
        BlockEntity machine = MachineControls.resolve(level, pos, state, blockEntity);
        if (machine != null) {
            return MachineControls.gases(machine);
        }
        return blockEntity == null ? null : FluidGasHandler.of(level.getCapability(Capabilities.Fluid.BLOCK, pos, state, blockEntity, null));
    }
}
