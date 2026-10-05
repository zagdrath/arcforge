/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control.spec;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import net.zagdrath.arcforge.api.machine.resource.EnergyRole;
import net.zagdrath.arcforge.api.machine.resource.HeatRole;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;
import net.zagdrath.arcforge.blockentity.machine.BurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.CombustionPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ThermoelectricPlantBlockEntity;
import net.zagdrath.arcforge.machine.control.MachineControlSpec;
import net.zagdrath.arcforge.machine.control.MachineControls;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The machine control API (docs/API.md) for: Generators and heat producers.
public final class PowerMachineSpecs {
    private PowerMachineSpecs() {}

    public static void register() {
        MachineControls.register(ModBlockEntityTypes.COMBUSTION_PLANT.get(), MachineControlSpec.builder(CombustionPlantBlockEntity.class)
                .energy(EnergyRole.GENERATOR, CombustionPlantBlockEntity::getEnergy, CombustionPlantBlockEntity::getOutputPerTick)
                .slot(BurnerBlockEntity.SLOT_FUEL, SlotRole.FUEL).slot(BurnerBlockEntity.SLOT_ASH, SlotRole.OUTPUT)
                .items(CombustionPlantBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.OUTPUT, be -> be.getFlue().getTank())
                .progress(PowerMachineSpecs::burnDone, BurnerBlockEntity::getBurnTotal)
                .build());

        MachineControls.register(ModBlockEntityTypes.FIREBOX.get(), MachineControlSpec.builder(FireboxBlockEntity.class)
                .slot(BurnerBlockEntity.SLOT_FUEL, SlotRole.FUEL).slot(BurnerBlockEntity.SLOT_ASH, SlotRole.OUTPUT)
                .items(FireboxBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, be -> be.getOxyFuel().getTank())
                .tank(SlotRole.OUTPUT, be -> be.getFlue().getTank())
                // Unsided, pipes reach the oxygen tank (the flue is extract-only).
                .fluidAutomation(be -> be.getFluidHandler(null))
                .heat(HeatRole.PRODUCER, FireboxBlockEntity::getHeat, FireboxBlockEntity::getOutputPerTick, be -> be.getHeatHandler(null))
                .progress(PowerMachineSpecs::burnDone, BurnerBlockEntity::getBurnTotal)
                .build());

        // Continuous: no progress. No fluidAutomation: unsided pipes reach only the fuel tank, so a fill without a tank is
        // routed by each tank's own filter instead (fuel, oxygen).
        MachineControls.register(ModBlockEntityTypes.FUEL_BURNER.get(), MachineControlSpec.builder(FuelBurnerBlockEntity.class)
                .slot(FuelBurnerBlockEntity.SLOT_BUCKET_IN, SlotRole.INPUT).slot(FuelBurnerBlockEntity.SLOT_BUCKET_OUT, SlotRole.OUTPUT)
                .items(FuelBurnerBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.FUEL, FuelBurnerBlockEntity::getTank)
                .tank(SlotRole.INPUT, be -> be.getOxyFuel().getTank())
                .tank(SlotRole.OUTPUT, be -> be.getFlue().getTank())
                .heat(HeatRole.PRODUCER, FuelBurnerBlockEntity::getHeat, FuelBurnerBlockEntity::getHeatPerTick, be -> be.getHeatHandler(null))
                .build());

        // Progress: through the lava last taken from the tank (touching lava and magma heat it with no progress).
        MachineControls.register(ModBlockEntityTypes.GEOTHERMAL_PLANT.get(), MachineControlSpec.builder(GeothermalPlantBlockEntity.class)
                .slot(GeothermalPlantBlockEntity.SLOT_INPUT, SlotRole.INPUT).slot(GeothermalPlantBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .items(GeothermalPlantBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, GeothermalPlantBlockEntity::getLavaTank)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .heat(HeatRole.PRODUCER, GeothermalPlantBlockEntity::getHeat, GeothermalPlantBlockEntity::getHeatPerTick, be -> be.getHeatHandler(null))
                .progress(be -> be.getLavaBurning() > 0 && be.getLavaBurnTotal() > 0
                        ? OptionalDouble.of(Math.clamp(1.0 - be.getLavaBurning() / be.getLavaBurnTotal(), 0.0, 1.0)) : OptionalDouble.empty())
                .remaining(be -> be.getLavaBurning() > 0 ? OptionalInt.of(be.getLavaTicksLeft()) : OptionalInt.empty())
                .build());

        // No slots but the upgrades (read only). Heat in through what a conduit gets: up to its throughput a tick.
        MachineControls.register(ModBlockEntityTypes.THERMOELECTRIC_PLANT.get(), MachineControlSpec.builder(ThermoelectricPlantBlockEntity.class)
                .energy(EnergyRole.GENERATOR, ThermoelectricPlantBlockEntity::getEnergy, ThermoelectricPlantBlockEntity::getFePerTick)
                .items(ThermoelectricPlantBlockEntity::getItems, null)
                .heat(HeatRole.CONSUMER, ThermoelectricPlantBlockEntity::getHeat, ThermoelectricPlantBlockEntity::getHeatPerTick,
                        be -> be.getHeatHandler(null))
                .build());
    }

    // Ticks of the burning fuel item done (none between items).
    private static int burnDone(BurnerBlockEntity be) {
        return be.getBurnTime() > 0 ? be.getBurnTotal() - be.getBurnTime() : 0;
    }
}
