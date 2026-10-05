/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.zagdrath.arcforge.api.machine.resource.EnergyRole;
import net.zagdrath.arcforge.api.machine.resource.HeatRole;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;
import net.zagdrath.arcforge.blockentity.machine.AirSeparatorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.CarbonReclaimerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ElectricPumpBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ElectrolyzerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FermenterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FischerTropschReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GasifierBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.HaberReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.HydrothermalCarbonizerBlockEntity;
import net.zagdrath.arcforge.machine.control.MachineControlSpec;
import net.zagdrath.arcforge.machine.control.MachineControls;
import net.zagdrath.arcforge.recipe.CarbonReclaimingRecipe;
import net.zagdrath.arcforge.recipe.ElectrolyzingRecipe;
import net.zagdrath.arcforge.recipe.FischerTropschRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;

// The machine control API (docs/API.md) for: Chemical, gas and fluid machines.
public final class ChemicalMachineSpecs {
    private ChemicalMachineSpecs() {}

    public static void register() {
        // No machine slots, only upgrades (and no item handler), so items are read only.
        MachineControls.register(ModBlockEntityTypes.AIR_SEPARATOR.get(), MachineControlSpec.builder(AirSeparatorBlockEntity.class)
                .energy(EnergyRole.CONSUMER, AirSeparatorBlockEntity::getEnergy, AirSeparatorBlockEntity::getUsage)
                .items(AirSeparatorBlockEntity::getItems, null)
                .tank(SlotRole.OUTPUT, AirSeparatorBlockEntity::getNitrogen)
                .tank(SlotRole.OUTPUT, AirSeparatorBlockEntity::getOxygen)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .progress(AirSeparatorBlockEntity::getProgress, AirSeparatorBlockEntity::getTotal)
                .fluidOutputs(be -> {
                    var recipe = be.getShownRecipe();
                    if (be.getProgress() <= 0 || recipe == null) {
                        return List.of();
                    }
                    List<FluidStack> made = new ArrayList<>();
                    made.add(recipe.primary().create());
                    recipe.secondary().ifPresent(secondary -> made.add(secondary.create()));
                    return made;
                })
                .build());

        // Progress is FE paid of the operation's FE cost; it draws energyPerTick a tick.
        MachineControls.register(ModBlockEntityTypes.CARBON_RECLAIMER.get(), MachineControlSpec.builder(CarbonReclaimerBlockEntity.class)
                .energy(EnergyRole.CONSUMER, CarbonReclaimerBlockEntity::getEnergy, CarbonReclaimerBlockEntity::getUsage)
                .slot(CarbonReclaimerBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .items(CarbonReclaimerBlockEntity::getItems, be -> be.getItemHandler(null))
                // Both gas tanks through the router, so a fill keeps each gas in a tank of its own as input faces do.
                .tank(SlotRole.INPUT, CarbonReclaimerBlockEntity::getRouter, 0)
                .tank(SlotRole.INPUT, CarbonReclaimerBlockEntity::getRouter, 1)
                .tank(SlotRole.OUTPUT, CarbonReclaimerBlockEntity::getWater)
                .fluidAutomation(CarbonReclaimerBlockEntity::getRouter)
                .progress(CarbonReclaimerBlockEntity::getSpent, CarbonReclaimerBlockEntity::getCost)
                .remaining(be -> energyRemaining(be.getSpent(), be.getCost(), be.energyPerTick()))
                .outputs(be -> {
                    if (be.getSpent() <= 0) {
                        return List.of();
                    }
                    return reclaiming(be).map(recipe -> List.of(recipe.result().create())).orElse(List.of());
                })
                .fluidOutputs(be -> {
                    if (be.getSpent() <= 0) {
                        return List.of();
                    }
                    return reclaiming(be).filter(recipe -> recipe.waterAmount() > 0)
                            .map(recipe -> List.of(new FluidStack(Fluids.WATER, recipe.waterAmount()))).orElse(List.of());
                })
                .build());

        // Item inputs go through PairedItemInput and fluid inputs through the router, as from input faces.
        MachineControls.register(ModBlockEntityTypes.CHEMICAL_REACTOR.get(), MachineControlSpec.builder(ChemicalReactorBlockEntity.class)
                .energy(EnergyRole.CONSUMER, ChemicalReactorBlockEntity::getEnergy, ChemicalReactorBlockEntity::getUsage)
                .slot(ChemicalReactorBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(ChemicalReactorBlockEntity.SLOT_INPUT_B, SlotRole.INPUT)
                .slot(ChemicalReactorBlockEntity.SLOT_INPUT_C, SlotRole.INPUT)
                .slot(ChemicalReactorBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .slot(ChemicalReactorBlockEntity.SLOT_BYPRODUCT, SlotRole.OUTPUT)
                .items(ChemicalReactorBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, ChemicalReactorBlockEntity::getRouter, 0)
                .tank(SlotRole.INPUT, ChemicalReactorBlockEntity::getRouter, 1)
                .tank(SlotRole.INPUT, ChemicalReactorBlockEntity::getRouter, 2)
                .tank(SlotRole.OUTPUT, ChemicalReactorBlockEntity::getOutputTank)
                .tank(SlotRole.OUTPUT, ChemicalReactorBlockEntity::getByproductTank)
                .fluidAutomation(ChemicalReactorBlockEntity::getRouter)
                .progress(ChemicalReactorBlockEntity::getProgress, ChemicalReactorBlockEntity::getTotal)
                // The chance by-product and the liquid by-product aren't cached, so only the main products.
                .outputs(be -> be.getProgress() > 0 ? List.of(be.getMakingItem()) : List.of())
                .fluidOutputs(be -> be.getProgress() > 0 ? List.of(be.getMakingFluid()) : List.of())
                .build());

        // No machine slots, only upgrades (and no item handler), so items are read only. Progress is FE paid of the cost.
        MachineControls.register(ModBlockEntityTypes.ELECTROLYZER.get(), MachineControlSpec.builder(ElectrolyzerBlockEntity.class)
                .energy(EnergyRole.CONSUMER, ElectrolyzerBlockEntity::getEnergy, ElectrolyzerBlockEntity::getUsage)
                .items(ElectrolyzerBlockEntity::getItems, null)
                .tank(SlotRole.INPUT, ElectrolyzerBlockEntity::getWater)
                .tank(SlotRole.OUTPUT, ElectrolyzerBlockEntity::getHydrogen)
                .tank(SlotRole.OUTPUT, ElectrolyzerBlockEntity::getOxygen)
                .tank(SlotRole.OUTPUT, ElectrolyzerBlockEntity::getLiquid)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .progress(ElectrolyzerBlockEntity::getSpent, ElectrolyzerBlockEntity::getCost)
                .remaining(be -> energyRemaining(be.getSpent(), be.getCost(), be.energyPerTick()))
                .fluidOutputs(be -> {
                    ElectrolyzingRecipe recipe = be.getShownRecipe();
                    if (be.getSpent() <= 0 || recipe == null) {
                        return List.of();
                    }
                    List<FluidStack> made = new ArrayList<>();
                    made.add(recipe.primary().create());
                    recipe.secondary().ifPresent(secondary -> made.add(secondary.create()));
                    recipe.tertiary().ifPresent(tertiary -> made.add(tertiary.create()));
                    return made;
                })
                .booleanOption("vent_hydrogen", ElectrolyzerBlockEntity::isVentingHydrogen, (be, on) -> be.setVenting(true, on))
                .booleanOption("vent_oxygen", ElectrolyzerBlockEntity::isVentingOxygen, (be, on) -> be.setVenting(false, on))
                .build());

        // Its unsided item handler joins the input and output views end to end (twice the slots), so writes go through
        // the slots' own filters instead.
        MachineControls.register(ModBlockEntityTypes.FERMENTER.get(), MachineControlSpec.builder(FermenterBlockEntity.class)
                .energy(EnergyRole.CONSUMER, FermenterBlockEntity::getEnergy, FermenterBlockEntity::getUsage)
                .slot(FermenterBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(FermenterBlockEntity.SLOT_INPUT_2, SlotRole.INPUT)
                .slot(FermenterBlockEntity.SLOT_BYPRODUCT, SlotRole.OUTPUT)
                // Dried Hops (one lasts several operations) or a starter culture (never used up).
                .slot(FermenterBlockEntity.SLOT_ADDITIVE, SlotRole.CATALYST)
                .items(FermenterBlockEntity::getItems, null)
                .tank(SlotRole.INPUT, FermenterBlockEntity::getWater)
                .liquidTank(SlotRole.OUTPUT, FermenterBlockEntity::getEthanol)
                .tank(SlotRole.OUTPUT, FermenterBlockEntity::getCarbonDioxide)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .progress(FermenterBlockEntity::getProgress, FermenterBlockEntity::getTotal)
                // A culture's result; fermenting's Bone Meal is only a chance.
                .outputs(be -> be.getProgress() > 0
                        ? be.culture(be.getLevel()).map(holder -> List.of(holder.value().result().create())).orElse(List.of())
                        : List.of())
                .fluidOutputs(be -> {
                    if (be.getProgress() <= 0 || be.culture(be.getLevel()).isPresent()) {
                        return List.of();
                    }
                    ItemStack input = be.getItems().getStack(FermenterBlockEntity.SLOT_INPUT);
                    return MachineRecipes.fermenting(be.getLevel(), input).map(holder -> {
                        int ethanol = be.ethanolFor(holder.value());
                        int gas = FermenterBlockEntity.carbonDioxideFor(ethanol);
                        FluidStack made = holder.value().result().create().copyWithAmount(ethanol);
                        return gas > 0 ? List.of(made, new FluidStack(ModFluids.CARBON_DIOXIDE.get(), gas))
                                : List.of(made);
                    }).orElse(List.of());
                })
                .build());

        // Syngas is the only fill (the unsided fluid handler is the raw tanks, which would take Water into its product
        // tank), so fills without a tank go through the tanks' roles.
        MachineControls.register(ModBlockEntityTypes.FISCHER_TROPSCH_REACTOR.get(), MachineControlSpec.builder(FischerTropschReactorBlockEntity.class)
                .energy(EnergyRole.CONSUMER, FischerTropschReactorBlockEntity::getEnergy, FischerTropschReactorBlockEntity::getUsage)
                .slot(FischerTropschReactorBlockEntity.SLOT_CATALYST, SlotRole.CATALYST)
                .items(FischerTropschReactorBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, FischerTropschReactorBlockEntity::getSyngas)
                .tank(SlotRole.OUTPUT, be -> be.getProduct(FischerTropschReactorBlockEntity.TANK_NAPHTHA))
                .tank(SlotRole.OUTPUT, be -> be.getProduct(FischerTropschReactorBlockEntity.TANK_LIGHT_OIL))
                .tank(SlotRole.OUTPUT, be -> be.getProduct(FischerTropschReactorBlockEntity.TANK_HEAVY_OIL))
                .tank(SlotRole.OUTPUT, be -> be.getProduct(FischerTropschReactorBlockEntity.TANK_WATER))
                .heat(HeatRole.CONSUMER, FischerTropschReactorBlockEntity::getHeat, FischerTropschReactorBlockEntity::getHeatUsage,
                        be -> be.getHeatHandler(null))
                .progress(FischerTropschReactorBlockEntity::getProgress, FischerTropschReactorBlockEntity::getTotal)
                .fluidOutputs(be -> {
                    if (be.getProgress() <= 0 || be.getSyngas().getAmount() <= 0) {
                        return List.of();
                    }
                    var input = new FischerTropschRecipe.Input(be.getSyngas().getResource(0), be.getSyngas().getAmount());
                    return MachineRecipes.fischerTropsch(be.getLevel(), input).map(holder -> {
                        int[] made = holder.value().products();
                        List<FluidStack> fluids = new ArrayList<>();
                        for (int i = 0; i < made.length; i++) {
                            if (made[i] > 0) {
                                fluids.add(new FluidStack(FischerTropschReactorBlockEntity.fluidOf(i), made[i]));
                            }
                        }
                        return fluids;
                    }).orElse(List.of());
                })
                .build());

        MachineControls.register(ModBlockEntityTypes.GASIFIER.get(), MachineControlSpec.builder(GasifierBlockEntity.class)
                .slot(GasifierBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(GasifierBlockEntity.SLOT_ASH, SlotRole.OUTPUT)
                .items(GasifierBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, GasifierBlockEntity::getSteam)
                .tank(SlotRole.OUTPUT, GasifierBlockEntity::getSyngas)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .heat(HeatRole.CONSUMER, GasifierBlockEntity::getHeat, GasifierBlockEntity::getHeatUsage, be -> be.getHeatHandler(null))
                .progress(GasifierBlockEntity::getProgress, GasifierBlockEntity::getTotal)
                // The ash is only a chance.
                .fluidOutputs(be -> be.getProgress() > 0
                        ? MachineRecipes.gasifying(be.getLevel(), be.getItems().getStack(GasifierBlockEntity.SLOT_INPUT))
                                .map(holder -> List.of(holder.value().result().create())).orElse(List.of())
                        : List.of())
                .build());

        // No machine slots, only upgrades (and no item handler), so items are read only.
        MachineControls.register(ModBlockEntityTypes.HABER_REACTOR.get(), MachineControlSpec.builder(HaberReactorBlockEntity.class)
                .energy(EnergyRole.CONSUMER, HaberReactorBlockEntity::getEnergy, HaberReactorBlockEntity::getUsage)
                .items(HaberReactorBlockEntity::getItems, null)
                .tank(SlotRole.INPUT, HaberReactorBlockEntity::getRouter, 0)
                .tank(SlotRole.INPUT, HaberReactorBlockEntity::getRouter, 1)
                .tank(SlotRole.OUTPUT, HaberReactorBlockEntity::getOutputTank)
                .fluidAutomation(HaberReactorBlockEntity::getRouter)
                .heat(HeatRole.CONSUMER, HaberReactorBlockEntity::getHeat, HaberReactorBlockEntity::getHeatUsage, be -> be.getHeatHandler(null))
                .progress(HaberReactorBlockEntity::getProgress, HaberReactorBlockEntity::getTotal)
                .fluidOutputs(be -> be.getProgress() > 0 ? List.of(be.getMaking()) : List.of())
                .build());

        MachineControls.register(ModBlockEntityTypes.HYDROTHERMAL_CARBONIZER.get(), MachineControlSpec.builder(HydrothermalCarbonizerBlockEntity.class)
                .slot(HydrothermalCarbonizerBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(HydrothermalCarbonizerBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .items(HydrothermalCarbonizerBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, HydrothermalCarbonizerBlockEntity::getWater)
                .tank(SlotRole.OUTPUT, HydrothermalCarbonizerBlockEntity::getReturned)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .heat(HeatRole.CONSUMER, HydrothermalCarbonizerBlockEntity::getHeat, HydrothermalCarbonizerBlockEntity::getHeatUsage,
                        be -> be.getHeatHandler(null))
                .progress(HydrothermalCarbonizerBlockEntity::getProgress, HydrothermalCarbonizerBlockEntity::getTotal)
                .outputs(be -> be.getProgress() > 0
                        ? MachineRecipes.hydrothermalCarbonizing(be.getLevel(), be.getItems().getStack(HydrothermalCarbonizerBlockEntity.SLOT_INPUT))
                                .map(holder -> List.of(holder.value().result().create())).orElse(List.of())
                        : List.of())
                // The water given back, unless it's thrown away (what doesn't fit in the tank is lost too).
                .fluidOutputs(be -> be.getProgress() > 0 && !be.isDiscardingWater()
                        ? MachineRecipes.hydrothermalCarbonizing(be.getLevel(), be.getItems().getStack(HydrothermalCarbonizerBlockEntity.SLOT_INPUT))
                                .filter(holder -> holder.value().waterReturn() > 0)
                                .map(holder -> List.of(new FluidStack(Fluids.WATER, holder.value().waterReturn()))).orElse(List.of())
                        : List.of())
                .booleanOption("discard_water", HydrothermalCarbonizerBlockEntity::isDiscardingWater,
                        HydrothermalCarbonizerBlockEntity::setDiscardingWater)
                .build());

        // Nothing goes into its tank, so no fluid automation.
        MachineControls.register(ModBlockEntityTypes.ELECTRIC_PUMP.get(), MachineControlSpec.builder(ElectricPumpBlockEntity.class)
                .energy(EnergyRole.CONSUMER, ElectricPumpBlockEntity::getEnergy, ElectricPumpBlockEntity::getUsage)
                .slot(ElectricPumpBlockEntity.SLOT_BUCKET_IN, SlotRole.INPUT)
                .slot(ElectricPumpBlockEntity.SLOT_BUCKET_OUT, SlotRole.OUTPUT)
                .items(ElectricPumpBlockEntity::getItems, be -> be.getItemHandler(null))
                .liquidTank(SlotRole.OUTPUT, ElectricPumpBlockEntity::getTank)
                .progress(ElectricPumpBlockEntity::getProgress, ElectricPumpBlockEntity::getTotal)
                .fluidOutputs(be -> {
                    Fluid source = be.getSourceFluid();
                    return be.getProgress() > 0 && source != null ? List.of(new FluidStack(source, FluidType.BUCKET_VOLUME)) : List.of();
                })
                .build());
    }

    // Ticks left of an operation paid for in FE, at perTick FE a tick.
    private static OptionalInt energyRemaining(int spent, int cost, int perTick) {
        return cost > 0 && spent > 0 ? OptionalInt.of((int) Math.ceil((double) Math.max(0, cost - spent) / Math.max(1, perTick))) : OptionalInt.empty();
    }

    // The recipe the Carbon Reclaimer's tanks make now (the lookup it makes each tick).
    private static Optional<CarbonReclaimingRecipe> reclaiming(CarbonReclaimerBlockEntity be) {
        var input = new CarbonReclaimingRecipe.Input(be.getCarbonDioxide().getAmount(), be.getHydrogen().getAmount());
        return MachineRecipes.carbonReclaiming(be.getLevel(), input).map(holder -> holder.value());
    }
}
