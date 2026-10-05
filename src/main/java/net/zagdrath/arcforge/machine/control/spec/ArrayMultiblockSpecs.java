/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control.spec;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

import net.zagdrath.arcforge.api.machine.resource.EnergyRole;
import net.zagdrath.arcforge.api.machine.resource.HeatRole;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CondenserArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.InductionFurnaceArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.MetalPressingArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamBoilerArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.machine.control.MachineControlSpec;
import net.zagdrath.arcforge.machine.control.MachineControls;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.BoilerPressure;

// The machine control API (docs/API.md) for: The cube and shell arrays. Every casing has a block entity of the
// array's type; only a cube's formed centre (its master) and a shell's master are the machine.
public final class ArrayMultiblockSpecs {
    private ArrayMultiblockSpecs() {}

    public static void register() {
        // --- Cube arrays: three FE lanes each ---

        MachineControls.register(ModBlockEntityTypes.ARC_CRUSHING_ARRAY.get(), MachineControlSpec.builder(ArcCrushingArrayBlockEntity.class)
                .primary(ArcCrushingArrayBlockEntity::isFormed)
                .energy(EnergyRole.CONSUMER, ArcCrushingArrayBlockEntity::getEnergy, ArcCrushingArrayBlockEntity::getUsage)
                .slots(0, ArcCrushingArrayBlockEntity.MACHINE_SLOTS, SlotRole.OUTPUT)
                .slot(ArcCrushingArrayBlockEntity.inputSlot(0), SlotRole.INPUT)
                .slot(ArcCrushingArrayBlockEntity.inputSlot(1), SlotRole.INPUT)
                .slot(ArcCrushingArrayBlockEntity.inputSlot(2), SlotRole.INPUT)
                .items(ArcCrushingArrayBlockEntity::getItems, be -> be.getItemHandler(null))
                .progress(lanes(ArcCrushingArrayBlockEntity.LANES, be -> lane -> be.getLane(lane).getProgress(), be -> lane -> be.getLane(lane).getTotal()))
                .remaining(lanesRemaining(ArcCrushingArrayBlockEntity.LANES, be -> lane -> be.getLane(lane).getProgress(), be -> lane -> be.getLane(lane).getTotal()))
                .build());

        MachineControls.register(ModBlockEntityTypes.INDUCTION_FURNACE_ARRAY.get(), MachineControlSpec.builder(InductionFurnaceArrayBlockEntity.class)
                .primary(InductionFurnaceArrayBlockEntity::isFormed)
                .energy(EnergyRole.CONSUMER, InductionFurnaceArrayBlockEntity::getEnergy, InductionFurnaceArrayBlockEntity::getUsage)
                .slots(0, InductionFurnaceArrayBlockEntity.MACHINE_SLOTS, SlotRole.OUTPUT)
                .slot(InductionFurnaceArrayBlockEntity.inputSlot(0), SlotRole.INPUT)
                .slot(InductionFurnaceArrayBlockEntity.inputSlot(1), SlotRole.INPUT)
                .slot(InductionFurnaceArrayBlockEntity.inputSlot(2), SlotRole.INPUT)
                .items(InductionFurnaceArrayBlockEntity::getItems, be -> be.getItemHandler(null))
                .progress(lanes(InductionFurnaceArrayBlockEntity.LANES, be -> lane -> be.getLane(lane).getProgress(), be -> lane -> be.getLane(lane).getTotal()))
                .remaining(lanesRemaining(InductionFurnaceArrayBlockEntity.LANES, be -> lane -> be.getLane(lane).getProgress(), be -> lane -> be.getLane(lane).getTotal()))
                .build());

        // The dies are never used up, and automation (so the API) can't put them in: players only.
        MachineControls.register(ModBlockEntityTypes.METAL_PRESSING_ARRAY.get(), MachineControlSpec.builder(MetalPressingArrayBlockEntity.class)
                .primary(MetalPressingArrayBlockEntity::isFormed)
                .energy(EnergyRole.CONSUMER, MetalPressingArrayBlockEntity::getEnergy, MetalPressingArrayBlockEntity::getUsage)
                .slot(MetalPressingArrayBlockEntity.dieSlot(0), SlotRole.CATALYST)
                .slot(MetalPressingArrayBlockEntity.inputSlot(0), SlotRole.INPUT)
                .slot(MetalPressingArrayBlockEntity.outputSlot(0), SlotRole.OUTPUT)
                .slot(MetalPressingArrayBlockEntity.dieSlot(1), SlotRole.CATALYST)
                .slot(MetalPressingArrayBlockEntity.inputSlot(1), SlotRole.INPUT)
                .slot(MetalPressingArrayBlockEntity.outputSlot(1), SlotRole.OUTPUT)
                .slot(MetalPressingArrayBlockEntity.dieSlot(2), SlotRole.CATALYST)
                .slot(MetalPressingArrayBlockEntity.inputSlot(2), SlotRole.INPUT)
                .slot(MetalPressingArrayBlockEntity.outputSlot(2), SlotRole.OUTPUT)
                .items(MetalPressingArrayBlockEntity::getItems, be -> be.getItemHandler(null))
                .progress(lanes(MetalPressingArrayBlockEntity.LANES, be -> lane -> be.getLane(lane).getProgress(), be -> lane -> be.getLane(lane).getTotal()))
                .remaining(lanesRemaining(MetalPressingArrayBlockEntity.LANES, be -> lane -> be.getLane(lane).getProgress(), be -> lane -> be.getLane(lane).getTotal()))
                .build());

        // --- Cube arrays: steam and heat, no slots or FE ---

        MachineControls.register(ModBlockEntityTypes.SUPERHEATER_ARRAY.get(), MachineControlSpec.builder(SuperheaterArrayBlockEntity.class)
                .primary(SuperheaterArrayBlockEntity::isFormed)
                .tank(SlotRole.INPUT, SuperheaterArrayBlockEntity::getSteamIn)
                .tank(SlotRole.OUTPUT, SuperheaterArrayBlockEntity::getSteamOut)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .heat(HeatRole.CONSUMER, SuperheaterArrayBlockEntity::getHeat, SuperheaterArrayBlockEntity::getHeatUsed,
                        be -> be.heatHandlerAt(be.getBlockPos(), null))
                .choiceOption("pressure", names(SuperheaterArrayBlockEntity.PRESSURES), be -> be.getPressure().getSerializedName(),
                        (be, value) -> be.setPressure(pressure(value)))
                .build());

        MachineControls.register(ModBlockEntityTypes.CONDENSER_ARRAY.get(), MachineControlSpec.builder(CondenserArrayBlockEntity.class)
                .primary(CondenserArrayBlockEntity::isFormed)
                .tank(SlotRole.INPUT, CondenserArrayBlockEntity::getExhaustIn)
                .tank(SlotRole.OUTPUT, CondenserArrayBlockEntity::getWaterOut)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .build());

        // --- Shell arrays: the master (the shell's minimum corner) is the machine ---

        MachineControls.register(ModBlockEntityTypes.STEAM_BOILER_ARRAY.get(), MachineControlSpec.builder(SteamBoilerArrayBlockEntity.class)
                .primary(SteamBoilerArrayBlockEntity::isMaster)
                .slot(SteamBoilerArrayBlockEntity.SLOT_BUCKET_IN, SlotRole.INPUT)
                .slot(SteamBoilerArrayBlockEntity.SLOT_BUCKET_OUT, SlotRole.OUTPUT)
                .items(SteamBoilerArrayBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, SteamBoilerArrayBlockEntity::getWater)
                .tank(SlotRole.OUTPUT, SteamBoilerArrayBlockEntity::getSteam)
                .fluidAutomation(be -> be.getFluidHandler(null))
                // HU/t boiled with, smoothed as its GUI shows it.
                .heat(HeatRole.CONSUMER, SteamBoilerArrayBlockEntity::getHeat, be -> be.getCore().getHeatUsed(),
                        be -> be.heatHandlerAt(be.getBlockPos(), null))
                .choiceOption("pressure", names(List.of(BoilerPressure.values())), be -> be.getCore().getPressure().getSerializedName(),
                        (be, value) -> be.setPressure(pressure(value)))
                .build());

        MachineControls.register(ModBlockEntityTypes.STEAM_TURBINE_ARRAY.get(), MachineControlSpec.builder(SteamTurbineArrayBlockEntity.class)
                .primary(SteamTurbineArrayBlockEntity::isMaster)
                .energy(EnergyRole.GENERATOR, SteamTurbineArrayBlockEntity::getEnergy, SteamTurbineArrayBlockEntity::getFePerTick)
                .tank(SlotRole.INPUT, SteamTurbineArrayBlockEntity::getSteam)
                .tank(SlotRole.INPUT, SteamTurbineArrayBlockEntity::getLubricant)
                .tank(SlotRole.OUTPUT, SteamTurbineArrayBlockEntity::getExhaust)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .build());

        MachineControls.register(ModBlockEntityTypes.GAS_TURBINE_ARRAY.get(), MachineControlSpec.builder(GasTurbineArrayBlockEntity.class)
                .primary(GasTurbineArrayBlockEntity::isMaster)
                .energy(EnergyRole.GENERATOR, GasTurbineArrayBlockEntity::getEnergy, GasTurbineArrayBlockEntity::getFePerTick)
                .tank(SlotRole.INPUT, GasTurbineArrayBlockEntity::getFuel)
                .tank(SlotRole.INPUT, GasTurbineArrayBlockEntity::getLubricant)
                .tank(SlotRole.OUTPUT, be -> be.getFlue().getTank())
                .fluidAutomation(be -> be.getFluidHandler(null))
                .heat(HeatRole.PRODUCER, GasTurbineArrayBlockEntity::getExhaust, GasTurbineArrayBlockEntity::getExhaustHu,
                        be -> be.heatHandlerAt(be.getBlockPos(), null))
                .build());
    }

    // A lane array's progress is its lane nearest to finishing (lanes advance one tick a tick).
    private static <T> Function<T, OptionalDouble> lanes(int count, Function<T, IntUnaryOperator> progress, Function<T, IntUnaryOperator> total) {
        return be -> {
            int lane = busiestLane(count, progress.apply(be), total.apply(be));
            return lane < 0 ? OptionalDouble.empty()
                    : OptionalDouble.of(Math.min(1.0, (double) progress.apply(be).applyAsInt(lane) / total.apply(be).applyAsInt(lane)));
        };
    }

    private static <T> Function<T, OptionalInt> lanesRemaining(int count, Function<T, IntUnaryOperator> progress, Function<T, IntUnaryOperator> total) {
        return be -> {
            int lane = busiestLane(count, progress.apply(be), total.apply(be));
            return lane < 0 ? OptionalInt.empty()
                    : OptionalInt.of(Math.max(0, total.apply(be).applyAsInt(lane) - progress.apply(be).applyAsInt(lane)));
        };
    }

    // The lane in an operation with the largest share done, or -1 if none is.
    private static int busiestLane(int count, IntUnaryOperator progress, IntUnaryOperator total) {
        int best = -1;
        double bestShare = -1;
        for (int lane = 0; lane < count; lane++) {
            int done = progress.applyAsInt(lane);
            int needed = total.applyAsInt(lane);
            if (done > 0 && needed > 0 && (double) done / needed > bestShare) {
                best = lane;
                bestShare = (double) done / needed;
            }
        }
        return best;
    }

    private static List<String> names(List<BoilerPressure> pressures) {
        return pressures.stream().map(BoilerPressure::getSerializedName).toList();
    }

    // Only called with one of the option's choices.
    private static BoilerPressure pressure(String name) {
        return Arrays.stream(BoilerPressure.values()).filter(pressure -> pressure.getSerializedName().equals(name)).findFirst().orElse(BoilerPressure.AUTO);
    }
}
