/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.api.machine.resource.EnergyRole;
import net.zagdrath.arcforge.api.machine.resource.HeatRole;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;
import net.zagdrath.arcforge.blockentity.farming.GreenhouseBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.BatteryArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.BiogasDigesterBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.FireboxArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SolarThermalArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ThermalEvaporatorBlockEntity;
import net.zagdrath.arcforge.machine.control.MachineControlSpec;
import net.zagdrath.arcforge.machine.control.MachineControls;
import net.zagdrath.arcforge.machine.control.StatusMapping;
import net.zagdrath.arcforge.recipe.ArcforgeSmeltingRecipe;
import net.zagdrath.arcforge.recipe.DigestingRecipe;
import net.zagdrath.arcforge.recipe.DistillingRecipe;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The machine control API (docs/API.md) for: Multiblocks with a controller block, the Arcforge Furnace and the Carbonizer.
public final class ControllerMultiblockSpecs {
    private ControllerMultiblockSpecs() {}

    public static void register() {
        // Lanes: progress is the lead lane's (the busy one nearest done, as its GUI's arrow).
        MachineControls.register(ModBlockEntityTypes.BIOGAS_DIGESTER.get(), MachineControlSpec.builder(BiogasDigesterBlockEntity.class)
                .slot(BiogasDigesterBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(BiogasDigesterBlockEntity.SLOT_DIGESTATE, SlotRole.OUTPUT)
                .slot(BiogasDigesterBlockEntity.SLOT_SULFUR, SlotRole.OUTPUT)
                .items(BiogasDigesterBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, BiogasDigesterBlockEntity::getWater)
                .tank(SlotRole.OUTPUT, BiogasDigesterBlockEntity::getBiogas)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .heat(HeatRole.CONSUMER, BiogasDigesterBlockEntity::getHeat, BiogasDigesterBlockEntity::getHeatUsage,
                        be -> be.heatHandlerAt(be.getBlockPos(), null))
                .progress(BiogasDigesterBlockEntity::getProgress, BiogasDigesterBlockEntity::getTotal)
                .fluidOutputs(be -> {
                    DigestingRecipe recipe = be.getLeadRecipe();
                    return recipe == null ? List.of() : List.of(recipe.result().create());
                })
                .build());

        // Progress: how much of the burning solid fuel item's heat is spent (liquid fuel burns continuously).
        MachineControls.register(ModBlockEntityTypes.FIREBOX_ARRAY.get(), MachineControlSpec.builder(FireboxArrayBlockEntity.class)
                .slot(FireboxArrayBlockEntity.SLOT_FUEL, SlotRole.FUEL)
                .items(FireboxArrayBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, FireboxArrayBlockEntity::getTank)
                .tank(SlotRole.INPUT, be -> be.getOxyFuel().getTank())
                .tank(SlotRole.OUTPUT, be -> be.getFlue().getTank())
                .heat(HeatRole.PRODUCER, FireboxArrayBlockEntity::getHeat, FireboxArrayBlockEntity::getHeatPerTick,
                        be -> be.heatHandlerAt(be.getBlockPos(), null))
                .progress(be -> be.getSolidTotal() > 0 && be.getSolidLeft() > 0
                        ? OptionalDouble.of(Math.clamp(1.0 - be.getSolidLeft() / be.getSolidTotal(), 0.0, 1.0)) : OptionalDouble.empty())
                .build());

        // Net flow: positive while charging. Its redstone pauses the output only; the enable switch stops both ways.
        MachineControls.register(ModBlockEntityTypes.BATTERY_ARRAY.get(), MachineControlSpec.builder(BatteryArrayBlockEntity.class)
                .energy(EnergyRole.STORAGE, BatteryArrayBlockEntity::getStored, BatteryArrayBlockEntity::getCapacity,
                        be -> be.getLastInput() - be.getLastOutput())
                .build());

        // Progress: mB evaporated towards the next result; it evaporates rate mB/t, so the time left follows from that.
        MachineControls.register(ModBlockEntityTypes.THERMAL_EVAPORATOR.get(), MachineControlSpec.builder(ThermalEvaporatorBlockEntity.class)
                .slot(ThermalEvaporatorBlockEntity.SLOT_SALT, SlotRole.OUTPUT)
                .items(ThermalEvaporatorBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, ThermalEvaporatorBlockEntity::getInput)
                .tank(SlotRole.OUTPUT, ThermalEvaporatorBlockEntity::getOutput)
                .tank(SlotRole.OUTPUT, ThermalEvaporatorBlockEntity::getWater)
                .tank(SlotRole.OUTPUT, ThermalEvaporatorBlockEntity::getByproduct)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .heat(HeatRole.CONSUMER, ThermalEvaporatorBlockEntity::getHeat, ThermalEvaporatorBlockEntity::getHeatUsage,
                        be -> be.heatHandlerAt(be.getBlockPos(), null))
                .progress(be -> {
                    int total = be.getTotal();
                    return total > 0 && be.getProgress() > 0 ? OptionalDouble.of(Math.min(1.0, be.getProgress() / total)) : OptionalDouble.empty();
                })
                .remaining(be -> {
                    int total = be.getTotal();
                    return total > 0 && be.getProgress() > 0 && be.getRate() > 0
                            ? OptionalInt.of((int) Math.ceil(Math.max(0.0, total - be.getProgress()) / be.getRate())) : OptionalInt.empty();
                })
                .outputs(be -> be.getCurrentRecipe().flatMap(recipe -> recipe.itemResult()).map(result -> List.of(result.create()))
                        .orElse(List.of()))
                .fluidOutputs(be -> be.getCurrentRecipe().map(recipe -> {
                    List<FluidStack> made = new ArrayList<>();
                    recipe.fluidResult().ifPresent(result -> made.add(result.create()));
                    recipe.byproduct().ifPresent(result -> made.add(result.create()));
                    return made;
                }).orElse(List.of()))
                .build());

        MachineControls.register(ModBlockEntityTypes.SOLAR_THERMAL_ARRAY.get(), MachineControlSpec.builder(SolarThermalArrayBlockEntity.class)
                .heat(HeatRole.PRODUCER, SolarThermalArrayBlockEntity::getHeat, SolarThermalArrayBlockEntity::getHeatPerTick,
                        be -> be.heatHandlerAt(be.getBlockPos(), null))
                .build());

        // Progress: mB of the batch through the column, at feedRate mB/t.
        MachineControls.register(ModBlockEntityTypes.DISTILLATION_ARRAY.get(), MachineControlSpec.builder(DistillationArrayBlockEntity.class)
                .slot(DistillationArrayBlockEntity.SLOT_PITCH, SlotRole.OUTPUT)
                .items(DistillationArrayBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, DistillationArrayBlockEntity::getFeed)
                .tank(SlotRole.INPUT, DistillationArrayBlockEntity::getSteam)
                .tank(SlotRole.OUTPUT, DistillationArrayBlockEntity::getNaphtha)
                .tank(SlotRole.OUTPUT, DistillationArrayBlockEntity::getLightOil)
                .tank(SlotRole.OUTPUT, DistillationArrayBlockEntity::getHeavyOil)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .heat(HeatRole.CONSUMER, DistillationArrayBlockEntity::getHeat, DistillationArrayBlockEntity::getHeatPerTick,
                        be -> be.heatHandlerAt(be.getBlockPos(), null))
                .progress(be -> {
                    DistillingRecipe recipe = be.getBatchRecipe();
                    return recipe != null && recipe.amount() > 0 && be.getProgress() > 0
                            ? OptionalDouble.of(Math.min(1.0, be.getProgress() / recipe.amount())) : OptionalDouble.empty();
                })
                .remaining(be -> {
                    DistillingRecipe recipe = be.getBatchRecipe();
                    return recipe != null && be.getProgress() > 0 && be.feedRate() > 0
                            ? OptionalInt.of((int) Math.ceil(Math.max(0.0, recipe.amount() - be.getProgress()) / be.feedRate())) : OptionalInt.empty();
                })
                .outputs(be -> {
                    DistillingRecipe recipe = be.getBatchRecipe();
                    int count = recipe == null ? 0 : recipe.items(be.getHeight());
                    return count > 0 ? List.of(new ItemStack(recipe.itemOutput(), count)) : List.of();
                })
                .fluidOutputs(be -> {
                    DistillingRecipe recipe = be.getBatchRecipe();
                    if (recipe == null) {
                        return List.of();
                    }
                    List<FluidStack> made = new ArrayList<>();
                    for (Map.Entry<net.minecraft.world.level.material.Fluid, Integer> entry : recipe.outputs(be.getHeight(), be.getBatchBonus()).entrySet()) {
                        made.add(new FluidStack(entry.getKey(), entry.getValue()));
                    }
                    return made;
                })
                .build());

        // A plain block entity: its status is the one it works out each tick. Its heat is a bare °C with no heat
        // buffer (no HU stored or capacity), so it has no heat view.
        MachineControls.register(ModBlockEntityTypes.ARCFORGE_FURNACE.get(), MachineControlSpec.builder(ArcforgeFurnaceBlockEntity.class)
                .report(be -> new MachineControlSpec.StatusReport(StatusMapping.toApi(be.getStatus()), be.getStatus().getDescription()))
                .slot(ArcforgeFurnaceBlockEntity.SLOT_METAL, SlotRole.INPUT)
                .slot(ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE, SlotRole.INPUT)
                .slot(ArcforgeFurnaceBlockEntity.SLOT_ADDITIVE_2, SlotRole.INPUT)
                .slot(ArcforgeFurnaceBlockEntity.SLOT_COKE, SlotRole.FUEL)
                .slot(ArcforgeFurnaceBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .slot(ArcforgeFurnaceBlockEntity.SLOT_BYPRODUCT, SlotRole.OUTPUT)
                .items(ArcforgeFurnaceBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, ArcforgeFurnaceBlockEntity::getOxygen)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .progress(ArcforgeFurnaceBlockEntity::getProgress, ArcforgeFurnaceBlockEntity::getProgressTotal)
                .outputs(be -> {
                    ArcforgeSmeltingRecipe recipe = be.getCurrentRecipe();
                    if (recipe == null || be.getProgress() <= 0) {
                        return List.of();
                    }
                    List<ItemStack> made = new ArrayList<>(List.of(recipe.result().create()));
                    recipe.byproduct().ifPresent(byproduct -> made.add(byproduct.create()));
                    return made;
                })
                .build());

        // Only the master holds the formation and the contents; the other blocks resolve to it (MachineControls).
        // Chambers bake in parallel: progress is the most advanced busy chamber's, as that is the next batch out.
        MachineControls.register(ModBlockEntityTypes.CARBONIZER.get(), MachineControlSpec.builder(CarbonizerBlockEntity.class)
                .primary(CarbonizerBlockEntity::isMaster)
                .report(be -> new MachineControlSpec.StatusReport(StatusMapping.toApi(be.getStatus()), be.getStatus().getDescription()))
                .slot(CarbonizerBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(CarbonizerBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .slot(CarbonizerBlockEntity.SLOT_BUCKET_IN, SlotRole.INPUT)
                .slot(CarbonizerBlockEntity.SLOT_BUCKET_OUT, SlotRole.OUTPUT)
                .items(CarbonizerBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.OUTPUT, CarbonizerBlockEntity::getTank)
                .progress(CarbonizerBlockEntity::getLeadProgress, CarbonizerBlockEntity::getLeadTime)
                .build());

        MachineControls.register(ModBlockEntityTypes.GREENHOUSE.get(), MachineControlSpec.builder(GreenhouseBlockEntity.class)
                .energy(EnergyRole.CONSUMER, GreenhouseBlockEntity::getEnergy, GreenhouseBlockEntity::getEnergyUsage)
                .slot(GreenhouseBlockEntity.SLOT_FERTILIZER, SlotRole.INPUT)
                .slots(GreenhouseBlockEntity.SLOT_OUTPUT_FIRST, GreenhouseBlockEntity.MACHINE_SLOTS, SlotRole.OUTPUT)
                .items(GreenhouseBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, GreenhouseBlockEntity::getWater)
                .tank(SlotRole.INPUT, GreenhouseBlockEntity::getNutrients)
                .tank(SlotRole.INPUT, GreenhouseBlockEntity::getCo2)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .heat(HeatRole.CONSUMER, GreenhouseBlockEntity::getHeat, GreenhouseBlockEntity::getHeatUsage,
                        be -> be.heatHandlerAt(be.getBlockPos(), null))
                .build());
    }
}
