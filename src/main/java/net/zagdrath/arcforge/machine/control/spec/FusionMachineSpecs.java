/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control.spec;

import java.util.List;

import net.zagdrath.arcforge.api.machine.resource.EnergyRole;
import net.zagdrath.arcforge.api.machine.resource.HeatRole;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;
import net.zagdrath.arcforge.blockentity.fusion.FusionPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.fusion.FusionReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.fusion.LithiumIsotopeSeparatorBlockEntity;
import net.zagdrath.arcforge.blockentity.fusion.RemoteHandlingArmBlockEntity;
import net.zagdrath.arcforge.blockentity.fusion.TritiumExtractionUnitBlockEntity;
import net.zagdrath.arcforge.fusion.PlasmaSim;
import net.zagdrath.arcforge.machine.control.MachineControlSpec;
import net.zagdrath.arcforge.machine.control.MachineControls;
import net.zagdrath.arcforge.menu.fusion.FusionReactorMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The machine control API (docs/API.md) for the fusion tier: the Fusion Reactor (its tanks, its plasma's readings and
// its controls), the Cryogenic Plant, the Cryogenic Distillation Column, the Tritium Plant, and the Lithium Isotope
// Separator, the Tritium Extraction Unit and the Remote Handling Arm.
public final class FusionMachineSpecs {
    // The reactor's readings: option id and its FusionReactorMenu data index (the same fixed-point ints its GUI shows).
    private static final List<Reading> READINGS = List.of(
            new Reading("radius", FusionReactorMenu.DATA_RADIUS),
            new Reading("stellarator", FusionReactorMenu.DATA_STELLARATOR),
            new Reading("temperature", FusionReactorMenu.DATA_TEMPERATURE),
            new Reading("density", FusionReactorMenu.DATA_DENSITY),
            new Reading("plasma_current", FusionReactorMenu.DATA_CURRENT),
            new Reading("field", FusionReactorMenu.DATA_FIELD),
            new Reading("fusion_power", FusionReactorMenu.DATA_FUSION),
            new Reading("heating_power", FusionReactorMenu.DATA_HEATING),
            new Reading("gain", FusionReactorMenu.DATA_Q),
            new Reading("beta_n", FusionReactorMenu.DATA_BETA),
            new Reading("greenwald_fraction", FusionReactorMenu.DATA_GREENWALD),
            new Reading("q95", FusionReactorMenu.DATA_Q95),
            new Reading("pulse_left", FusionReactorMenu.DATA_PULSE_LEFT),
            new Reading("neutron_power", FusionReactorMenu.DATA_NEUTRON),
            new Reading("alarms", FusionReactorMenu.DATA_ALARMS));

    private record Reading(String id, int index) {}

    private FusionMachineSpecs() {}

    public static void register() {
        MachineControls.register(ModBlockEntityTypes.FUSION_REACTOR.get(), reactor());

        MachineControls.register(ModBlockEntityTypes.CRYOGENIC_PLANT.get(), plant(FusionPlantBlockEntity.Kind.CRYOGENIC_PLANT));
        MachineControls.register(ModBlockEntityTypes.CRYOGENIC_COLUMN.get(), plant(FusionPlantBlockEntity.Kind.CRYOGENIC_COLUMN));
        MachineControls.register(ModBlockEntityTypes.TRITIUM_PLANT.get(), plant(FusionPlantBlockEntity.Kind.TRITIUM_PLANT));

        MachineControls.register(ModBlockEntityTypes.LITHIUM_ISOTOPE_SEPARATOR.get(), MachineControlSpec.builder(LithiumIsotopeSeparatorBlockEntity.class)
                .energy(EnergyRole.CONSUMER, LithiumIsotopeSeparatorBlockEntity::getEnergy, LithiumIsotopeSeparatorBlockEntity::getUsage)
                .slot(LithiumIsotopeSeparatorBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(LithiumIsotopeSeparatorBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .items(LithiumIsotopeSeparatorBlockEntity::getItems, be -> be.getItemHandler(null))
                .progress(LithiumIsotopeSeparatorBlockEntity::getProgress, LithiumIsotopeSeparatorBlockEntity::getTotal)
                .build());

        MachineControls.register(ModBlockEntityTypes.TRITIUM_EXTRACTION_UNIT.get(), MachineControlSpec.builder(TritiumExtractionUnitBlockEntity.class)
                .slot(TritiumExtractionUnitBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(TritiumExtractionUnitBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .items(TritiumExtractionUnitBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.OUTPUT, TritiumExtractionUnitBlockEntity::getTritium)
                .tank(SlotRole.OUTPUT, TritiumExtractionUnitBlockEntity::getHelium)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .heat(HeatRole.CONSUMER, TritiumExtractionUnitBlockEntity::getHeat, TritiumExtractionUnitBlockEntity::getHeatUsage,
                        be -> be.getHeatHandler(null))
                .progress(TritiumExtractionUnitBlockEntity::getProgress, TritiumExtractionUnitBlockEntity::getTotal)
                .build());

        // The arm: spare parts in, Activated Components out; progress is the part it's fitting now.
        MachineControls.register(ModBlockEntityTypes.REMOTE_HANDLING_ARM.get(), MachineControlSpec.builder(RemoteHandlingArmBlockEntity.class)
                .energy(EnergyRole.CONSUMER, RemoteHandlingArmBlockEntity::getEnergy, be -> be.isWorking() ? RemoteHandlingArmBlockEntity.ENERGY_PER_TICK : 0)
                .slots(0, RemoteHandlingArmBlockEntity.INPUT_SLOTS, SlotRole.INPUT)
                .slots(RemoteHandlingArmBlockEntity.INPUT_SLOTS, RemoteHandlingArmBlockEntity.MACHINE_SLOTS, SlotRole.OUTPUT)
                .items(RemoteHandlingArmBlockEntity::getItems, be -> be.getItemHandler(null))
                .progress(RemoteHandlingArmBlockEntity::getProgress, be -> RemoteHandlingArmBlockEntity.SERVICE_TICKS)
                .reading("parts_replaced", RemoteHandlingArmBlockEntity::getReplaced)
                .build());
    }

    // The Fusion Reactor: its coolant and breeder loops, cryogens and FE; its plasma's readings; and its controls
    // (running, fast_shutdown, repeat, fuel, and the four setpoints as on its desk).
    private static MachineControlSpec<FusionReactorBlockEntity> reactor() {
        MachineControlSpec.Builder<FusionReactorBlockEntity> spec = MachineControlSpec.builder(FusionReactorBlockEntity.class)
                .primary(FusionReactorBlockEntity::isFormed)
                .energy(EnergyRole.CONSUMER, FusionReactorBlockEntity::getEnergy, be -> 0L)
                .tank(SlotRole.INPUT, FusionReactorBlockEntity::getWater)
                .tank(SlotRole.OUTPUT, FusionReactorBlockEntity::getHotCoolant)
                .tank(SlotRole.INPUT, FusionReactorBlockEntity::getLeadLithium)
                .tank(SlotRole.OUTPUT, FusionReactorBlockEntity::getHotLeadLithium)
                .tank(SlotRole.INPUT, FusionReactorBlockEntity::getLiquidHelium)
                .tank(SlotRole.INPUT, FusionReactorBlockEntity::getLiquidNitrogen)
                .fluidAutomation(be -> be.getFluidHandler(null));
        for (Reading reading : READINGS) {
            spec.reading(reading.id(), be -> be.dataValue(reading.index()));
        }
        return spec
                .booleanOption("running", FusionReactorBlockEntity::isRunning, FusionReactorBlockEntity::setRun)
                // On while a stopped reactor's plasma is still being shut down; setting it on starts a Fast Shutdown.
                .booleanOption("fast_shutdown", FusionMachineSpecs::shuttingDown, (be, on) -> {
                    if (on) {
                        be.fastShutdown();
                    }
                })
                .booleanOption("repeat", FusionReactorBlockEntity::isRepeat, FusionReactorBlockEntity::setRepeat)
                .choiceOption("fuel", List.of("dt", "dhe3"), be -> be.getFuelMode() == FusionReactorBlockEntity.FuelMode.DHE3 ? "dhe3" : "dt",
                        (be, value) -> be.setFuelMode(value.equals("dhe3") ? FusionReactorBlockEntity.FuelMode.DHE3 : FusionReactorBlockEntity.FuelMode.DT))
                .intOption("heating_setpoint", 0, 1_000, FusionReactorBlockEntity::getHeatingSetpoint, FusionReactorBlockEntity::setHeatingSetpoint)
                .intOption("density_setpoint", 0, 120, FusionReactorBlockEntity::getDensitySetpoint, FusionReactorBlockEntity::setDensitySetpoint)
                .intOption("current_setpoint", 0, 120, FusionReactorBlockEntity::getCurrentSetpoint, FusionReactorBlockEntity::setCurrentSetpoint)
                .intOption("field_setpoint", 0, 100, FusionReactorBlockEntity::getFieldSetpoint, FusionReactorBlockEntity::setFieldSetpoint)
                .build();
    }

    private static boolean shuttingDown(FusionReactorBlockEntity reactor) {
        PlasmaSim plasma = reactor.getSim();
        return !reactor.isRunning() && plasma != null && plasma.alive;
    }

    // A fusion plant: its input tank, its one to four product tanks (the Cryogenic Plant's a liquid), its FE, and its
    // size (the plants' inside volume, the column's stages).
    private static MachineControlSpec<FusionPlantBlockEntity> plant(FusionPlantBlockEntity.Kind kind) {
        MachineControlSpec.Builder<FusionPlantBlockEntity> spec = MachineControlSpec.builder(FusionPlantBlockEntity.class)
                .primary(FusionPlantBlockEntity::isFormed)
                .energy(EnergyRole.CONSUMER, FusionPlantBlockEntity::getEnergy, FusionPlantBlockEntity::getEnergyUse)
                .tank(SlotRole.INPUT, FusionPlantBlockEntity::getInput);
        for (int i = 0; i < kind.outputs(); i++) {
            int index = i;
            if (kind == FusionPlantBlockEntity.Kind.CRYOGENIC_PLANT) {
                spec.liquidTank(SlotRole.OUTPUT, be -> be.getOutput(index));
            } else {
                spec.tank(SlotRole.OUTPUT, be -> be.getOutput(index));
            }
        }
        return spec.fluidAutomation(be -> be.getFluidHandler(null))
                .reading("size", FusionPlantBlockEntity::size)
                .build();
    }
}
