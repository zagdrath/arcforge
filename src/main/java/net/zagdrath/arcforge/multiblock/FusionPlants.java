/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.registry.ModBlocks;

// The fusion tier's plant vessels (see VesselStructure), all empty inside (the process runs in the space the walls
// close, so the bigger the box, the faster it goes):
//  - the Cryogenic Plant: a cold box of Cryogenic Plant Casing, 3 to 7 across and 3 to 7 tall; liquefies Nitrogen and
//    Helium (arcforge:liquefying) at a rate set by its inside's volume;
//  - the Cryogenic Distillation Column: Cryogenic Column Casing, always 3 across (square) and 6 to 16 tall; every block
//    of its inside's height is a stage (each running columnOperationsPerStage operations) (arcforge:cryogenic_distilling: air into Nitrogen, Oxygen and Argon, Helium-Rich
//    Natural Gas into Natural Gas and Helium);
//  - the Tritium Plant: Tritium Plant Casing, 3 to 7 across and 3 to 5 tall; separates Fusion Exhaust into Deuterium,
//    Tritium, Helium-3 and Helium at a rate set by its inside's volume.
public final class FusionPlants {
    public static final VesselStructure CRYOGENIC_PLANT = new VesselStructure(new VesselStructure.Spec("cryogenic_plant", 3, 7, 3, 7, false,
            state -> state.is(ModBlocks.CRYOGENIC_PLANT_CASING.get()), null, BlockState::isAir, (level, box) -> true));

    public static final VesselStructure CRYOGENIC_COLUMN = new VesselStructure(new VesselStructure.Spec("cryogenic_column", 3, 3, 6, 16, true,
            state -> state.is(ModBlocks.CRYOGENIC_COLUMN_CASING.get()), null, BlockState::isAir, (level, box) -> true));

    public static final VesselStructure TRITIUM_PLANT = new VesselStructure(new VesselStructure.Spec("tritium_plant", 3, 7, 3, 5, false,
            state -> state.is(ModBlocks.TRITIUM_PLANT_CASING.get()), null, BlockState::isAir, (level, box) -> true));

    private FusionPlants() {}
}
