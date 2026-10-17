/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.registry.ModBlocks;

// The nuclear tier's vessels (see VesselStructure):
//  - the Fission Reactor: walls of Reactor Casing and Reactor Glass, 3 to 7 across and 3 to 9 tall; inside, only Fuel
//    Channels, Control Rod Channels, Graphite Moderator, Steel Reflector and air (coolant), with at least one Fuel
//    Channel or Graphite Moderator (a molten salt core's salt runs through channels in its graphite);
//  - the Steam Generator: Steam Generator Casing, 3 to 7 across (square) and 4 to 12 tall; inside, Steam Generator
//    Tubes and air, with at least one Tubes block;
//  - the Heavy Water Tower: Heavy Water Tower Casing, 3 to 5 across (square) and 4 to 8 tall; inside, Exchange Trays
//    and air, with at least one tray;
//  - the PUREX Plant: a hot cell of Hot Cell Casing with lead-glass Hot Cell Windows, 3 to 9 across and 3 to 6 tall,
//    empty inside (its walls stay in view: the renderer draws the cell's insides through its windows).
public final class VesselStructures {
    public static final VesselStructure REACTOR = new VesselStructure(new VesselStructure.Spec("fission_reactor", 3, 7, 3, 9, false,
            state -> state.is(ModBlocks.REACTOR_CASING.get()), state -> state.is(ModBlocks.REACTOR_GLASS.get()), VesselStructures::reactorFill,
            (level, box) -> any(level, box, ModBlocks.FUEL_CHANNEL.get().defaultBlockState()) || any(level, box, ModBlocks.GRAPHITE_MODERATOR.get().defaultBlockState())));

    public static final VesselStructure STEAM_GENERATOR = new VesselStructure(new VesselStructure.Spec("steam_generator", 3, 7, 4, 12, true,
            state -> state.is(ModBlocks.STEAM_GENERATOR_CASING.get()), null,
            state -> state.isAir() || state.is(ModBlocks.STEAM_GENERATOR_TUBES.get()),
            (level, box) -> any(level, box, ModBlocks.STEAM_GENERATOR_TUBES.get().defaultBlockState())));

    public static final VesselStructure HEAVY_WATER_TOWER = new VesselStructure(new VesselStructure.Spec("heavy_water_tower", 3, 5, 4, 8, true,
            state -> state.is(ModBlocks.HEAVY_WATER_TOWER_CASING.get()), null,
            state -> state.isAir() || state.is(ModBlocks.EXCHANGE_TRAY.get()),
            (level, box) -> any(level, box, ModBlocks.EXCHANGE_TRAY.get().defaultBlockState())));

    public static final VesselStructure PUREX = new VesselStructure(new VesselStructure.Spec("purex_plant", 3, 9, 3, 6, false,
            state -> state.is(ModBlocks.HOT_CELL_CASING.get()), state -> state.is(ModBlocks.HOT_CELL_WINDOW.get()), BlockState::isAir,
            (level, box) -> true));

    private VesselStructures() {}

    private static boolean reactorFill(BlockState state) {
        return state.isAir() || state.is(ModBlocks.FUEL_CHANNEL.get()) || state.is(ModBlocks.CONTROL_ROD_CHANNEL.get())
                || state.is(ModBlocks.GRAPHITE_MODERATOR.get()) || state.is(ModBlocks.STEEL_REFLECTOR.get());
    }

    private static boolean any(net.minecraft.world.level.Level level, VesselStructure.Box box, BlockState wanted) {
        for (BlockPos pos : box.interior()) {
            if (level.getBlockState(pos).is(wanted.getBlock())) {
                return true;
            }
        }
        return false;
    }

    // How many of a block are inside.
    public static int count(net.minecraft.world.level.BlockGetter level, VesselStructure.Box box, net.minecraft.world.level.block.Block block) {
        int found = 0;
        for (BlockPos pos : box.interior()) {
            if (level.getBlockState(pos).is(block)) {
                found++;
            }
        }
        return found;
    }
}
