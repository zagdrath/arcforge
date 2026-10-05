/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control.spec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.api.machine.resource.EnergyRole;
import net.zagdrath.arcforge.api.machine.resource.HeatRole;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;
import net.zagdrath.arcforge.blockentity.machine.ArcCrusherBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcMelterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.DiamondPressBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FiberizerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GrainDryerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.InductionFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.InfuserBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MetalPressBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MillBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.OilPressBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.SeedExtractorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.SifterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VulcanizerBlockEntity;
import net.zagdrath.arcforge.machine.ItemLane;
import net.zagdrath.arcforge.machine.control.MachineControlSpec;
import net.zagdrath.arcforge.machine.control.MachineControls;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The machine control API (docs/API.md) for: Item and energy processing machines.
// Current outputs are the guaranteed ones only (chance bonuses aren't known until they're rolled), looked up the way the
// machine does each tick, and only while an operation is under way.
public final class ProcessingMachineSpecs {
    private ProcessingMachineSpecs() {}

    public static void register() {
        MachineControls.register(ModBlockEntityTypes.ARC_CRUSHER.get(), MachineControlSpec.builder(ArcCrusherBlockEntity.class)
                .energy(EnergyRole.CONSUMER, ArcCrusherBlockEntity::getEnergy, ArcCrusherBlockEntity::getUsage)
                .slot(ArcCrusherBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(ArcCrusherBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .slot(ArcCrusherBlockEntity.SLOT_BONUS, SlotRole.OUTPUT)
                .items(ArcCrusherBlockEntity::getItems, be -> be.getItemHandler(null))
                .progress(be -> be.getLane().getProgress(), be -> be.getLane().getTotal())
                .outputs(be -> current(be, be.getLane().getProgress(), level -> MachineRecipes
                        .crushing(level, be.getItems().getStack(ArcCrusherBlockEntity.SLOT_INPUT))
                        .flatMap(holder -> holder.value().result()).map(template -> template.create())))
                .build());

        MachineControls.register(ModBlockEntityTypes.INDUCTION_FURNACE.get(), MachineControlSpec.builder(InductionFurnaceBlockEntity.class)
                .energy(EnergyRole.CONSUMER, InductionFurnaceBlockEntity::getEnergy, InductionFurnaceBlockEntity::getUsage)
                .slot(InductionFurnaceBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(InductionFurnaceBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .items(InductionFurnaceBlockEntity::getItems, be -> be.getItemHandler(null))
                .progress(be -> be.getLane().getProgress(), be -> be.getLane().getTotal())
                .outputs(be -> current(be, be.getLane().getProgress(), level -> {
                    ItemStack input = be.getItems().getStack(InductionFurnaceBlockEntity.SLOT_INPUT);
                    return MachineRecipes.smelting(level, input).map(holder -> holder.value().assemble(new SingleRecipeInput(input)));
                }))
                .build());

        MachineControls.register(ModBlockEntityTypes.METAL_PRESS.get(), MachineControlSpec.builder(MetalPressBlockEntity.class)
                .energy(EnergyRole.CONSUMER, MetalPressBlockEntity::getEnergy, MetalPressBlockEntity::getUsage)
                .slot(MetalPressBlockEntity.SLOT_DIE, SlotRole.CATALYST)
                .slot(MetalPressBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(MetalPressBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .items(MetalPressBlockEntity::getItems, be -> be.getItemHandler(null))
                .progress(be -> be.getLane().getProgress(), be -> be.getLane().getTotal())
                .outputs(be -> current(be, be.getLane().getProgress(), level -> MachineRecipes
                        .pressing(level, be.getItems().getStack(MetalPressBlockEntity.SLOT_DIE), be.getItems().getStack(MetalPressBlockEntity.SLOT_INPUT))
                        .map(holder -> holder.value().result().create())))
                .build());

        // The unsided item handler combines the input and output views (its own indices), so writes use the slots' filters.
        MachineControls.register(ModBlockEntityTypes.MILL.get(), MachineControlSpec.builder(MillBlockEntity.class)
                .energy(EnergyRole.CONSUMER, MillBlockEntity::getEnergy, MillBlockEntity::getUsage)
                .slots(0, MillBlockEntity.LANES, SlotRole.INPUT)
                .slots(MillBlockEntity.LANES, MillBlockEntity.MACHINE_SLOTS, SlotRole.OUTPUT)
                .items(MillBlockEntity::getItems, null)
                // Progress: the furthest-along lane.
                .progress(be -> furthest(be).getProgress(), be -> furthest(be).getTotal())
                .outputs(be -> {
                    List<ItemStack> outputs = new ArrayList<>();
                    for (int lane = 0; lane < MillBlockEntity.LANES; lane++) {
                        ItemStack input = be.getItems().getStack(MillBlockEntity.inputSlot(lane));
                        outputs.addAll(current(be, be.getLane(lane).getProgress(), level -> MachineRecipes.milling(level, input)
                                .map(holder -> holder.value().result().create())));
                    }
                    return outputs;
                })
                .build());

        MachineControls.register(ModBlockEntityTypes.SEED_EXTRACTOR.get(), MachineControlSpec.builder(SeedExtractorBlockEntity.class)
                .energy(EnergyRole.CONSUMER, SeedExtractorBlockEntity::getEnergy, SeedExtractorBlockEntity::getUsage)
                .slot(SeedExtractorBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(SeedExtractorBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .slot(SeedExtractorBlockEntity.SLOT_BONUS, SlotRole.OUTPUT)
                .items(SeedExtractorBlockEntity::getItems, be -> be.getItemHandler(null))
                .progress(be -> be.getLane().getProgress(), be -> be.getLane().getTotal())
                .outputs(be -> current(be, be.getLane().getProgress(), level -> MachineRecipes
                        .seedExtracting(level, be.getItems().getStack(SeedExtractorBlockEntity.SLOT_INPUT))
                        .map(holder -> holder.value().result().create())))
                .build());

        // Every sifting output is a chance roll, so no current outputs.
        MachineControls.register(ModBlockEntityTypes.SIFTER.get(), MachineControlSpec.builder(SifterBlockEntity.class)
                .energy(EnergyRole.CONSUMER, SifterBlockEntity::getEnergy, SifterBlockEntity::getUsage)
                .slot(SifterBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(SifterBlockEntity.SLOT_MESH, SlotRole.CATALYST)
                .slots(SifterBlockEntity.FIRST_OUTPUT, SifterBlockEntity.MACHINE_SLOTS, SlotRole.OUTPUT)
                .items(SifterBlockEntity::getItems, be -> be.getItemHandler(null))
                .progress(SifterBlockEntity::getProgress, SifterBlockEntity::getTotal)
                .build());

        MachineControls.register(ModBlockEntityTypes.DIAMOND_PRESS.get(), MachineControlSpec.builder(DiamondPressBlockEntity.class)
                .energy(EnergyRole.CONSUMER, DiamondPressBlockEntity::getEnergy, DiamondPressBlockEntity::getUsage)
                .slot(DiamondPressBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(DiamondPressBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .items(DiamondPressBlockEntity::getItems, be -> be.getItemHandler(null))
                .heat(HeatRole.CONSUMER, DiamondPressBlockEntity::getHeat, DiamondPressBlockEntity::getHeatUsage, be -> be.getHeatHandler(null))
                .progress(DiamondPressBlockEntity::getProgress, DiamondPressBlockEntity::getTotal)
                .outputs(be -> current(be, be.getProgress(), level -> MachineRecipes
                        .diamondPressing(level, be.getItems().getStack(DiamondPressBlockEntity.SLOT_INPUT))
                        .map(holder -> holder.value().result().create())))
                .build());

        MachineControls.register(ModBlockEntityTypes.FIBERIZER.get(), MachineControlSpec.builder(FiberizerBlockEntity.class)
                .energy(EnergyRole.CONSUMER, FiberizerBlockEntity::getEnergy, FiberizerBlockEntity::getUsage)
                .slot(FiberizerBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(FiberizerBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .items(FiberizerBlockEntity::getItems, be -> be.getItemHandler(null))
                .heat(HeatRole.CONSUMER, FiberizerBlockEntity::getHeat, FiberizerBlockEntity::getHeatUsage, be -> be.getHeatHandler(null))
                .progress(FiberizerBlockEntity::getProgress, FiberizerBlockEntity::getTotal)
                .outputs(be -> current(be, be.getProgress(), level -> {
                    ItemStack input = be.getItems().getStack(FiberizerBlockEntity.SLOT_INPUT);
                    return MachineRecipes.fiberizing(level, input).map(holder -> holder.value().assemble(new SingleRecipeInput(input)));
                }))
                .build());

        // The unsided item handler combines the input and output views (its own indices), so writes use the slots' filters.
        // The cake is a chance by-product, so only the oil is a current output.
        MachineControls.register(ModBlockEntityTypes.OIL_PRESS.get(), MachineControlSpec.builder(OilPressBlockEntity.class)
                .energy(EnergyRole.CONSUMER, OilPressBlockEntity::getEnergy, OilPressBlockEntity::getUsage)
                .slot(OilPressBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(OilPressBlockEntity.SLOT_CAKE, SlotRole.OUTPUT)
                .items(OilPressBlockEntity::getItems, null)
                .liquidTank(SlotRole.OUTPUT, OilPressBlockEntity::getOil)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .progress(OilPressBlockEntity::getProgress, OilPressBlockEntity::getTotal)
                .fluidOutputs(be -> currentFluid(be, be.getProgress(), level -> MachineRecipes
                        .oilPressing(level, be.getItems().getStack(OilPressBlockEntity.SLOT_INPUT))
                        .map(holder -> holder.value().result().create())))
                .build());

        MachineControls.register(ModBlockEntityTypes.GRAIN_DRYER.get(), MachineControlSpec.builder(GrainDryerBlockEntity.class)
                .slot(GrainDryerBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(GrainDryerBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .items(GrainDryerBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, GrainDryerBlockEntity::getTank)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .heat(HeatRole.CONSUMER, GrainDryerBlockEntity::getHeat, GrainDryerBlockEntity::getHeatUsage, be -> be.getHeatHandler(null))
                .progress(GrainDryerBlockEntity::getProgress, GrainDryerBlockEntity::getTotal)
                // The slot's recipe, else the tank's (the dryer's own order; the tank's amount was checked when it started).
                .outputs(be -> current(be, be.getProgress(), level -> MachineRecipes
                        .drying(level, be.getItems().getStack(GrainDryerBlockEntity.SLOT_INPUT))
                        .or(() -> be.getTank().getAmount() > 0 ? MachineRecipes.dryingFluid(level, be.getTank().getResource(0)) : Optional.empty())
                        .map(holder -> holder.value().result().create())))
                .build());

        MachineControls.register(ModBlockEntityTypes.VULCANIZER.get(), MachineControlSpec.builder(VulcanizerBlockEntity.class)
                .slot(VulcanizerBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(VulcanizerBlockEntity.SLOT_INPUT_B, SlotRole.INPUT)
                .slot(VulcanizerBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .items(VulcanizerBlockEntity::getItems, be -> be.getItemHandler(null))
                .heat(HeatRole.CONSUMER, VulcanizerBlockEntity::getHeat, VulcanizerBlockEntity::getHeatUsage, be -> be.getHeatHandler(null))
                .progress(VulcanizerBlockEntity::getProgress, VulcanizerBlockEntity::getTotal)
                .outputs(be -> current(be, be.getProgress(), level -> MachineRecipes
                        .vulcanizing(level, be.getItems().getStack(VulcanizerBlockEntity.SLOT_INPUT), be.getItems().getStack(VulcanizerBlockEntity.SLOT_INPUT_B))
                        .map(holder -> holder.value().result().create())))
                .build());

        MachineControls.register(ModBlockEntityTypes.INFUSER.get(), MachineControlSpec.builder(InfuserBlockEntity.class)
                .energy(EnergyRole.CONSUMER, InfuserBlockEntity::getEnergy, InfuserBlockEntity::getUsage)
                .slot(InfuserBlockEntity.SLOT_BUCKET_IN, SlotRole.INPUT)
                .slot(InfuserBlockEntity.SLOT_BUCKET_OUT, SlotRole.OUTPUT)
                .slot(InfuserBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .slot(InfuserBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
                .slot(InfuserBlockEntity.SLOT_ADDITIVE, SlotRole.INPUT)
                .items(InfuserBlockEntity::getItems, be -> be.getItemHandler(null))
                .tank(SlotRole.INPUT, InfuserBlockEntity::getTank)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .progress(InfuserBlockEntity::getProgress, InfuserBlockEntity::getTotal)
                .outputs(be -> current(be, be.getProgress(), level -> {
                    ItemStack input = be.getItems().getStack(InfuserBlockEntity.SLOT_INPUT);
                    return MachineRecipes.infusing(level, input, be.getTank().getResource(0), be.getItems().getStack(InfuserBlockEntity.SLOT_ADDITIVE))
                            .map(holder -> holder.value().assemble(new SingleRecipeInput(input)));
                }))
                .build());

        MachineControls.register(ModBlockEntityTypes.ARC_MELTER.get(), MachineControlSpec.builder(ArcMelterBlockEntity.class)
                .energy(EnergyRole.CONSUMER, ArcMelterBlockEntity::getEnergy, ArcMelterBlockEntity::getUsage)
                .slot(ArcMelterBlockEntity.SLOT_INPUT, SlotRole.INPUT)
                .items(ArcMelterBlockEntity::getItems, be -> be.getItemHandler(null))
                .liquidTank(SlotRole.OUTPUT, ArcMelterBlockEntity::getTank)
                .fluidAutomation(be -> be.getFluidHandler(null))
                .progress(ArcMelterBlockEntity::getProgress, ArcMelterBlockEntity::getTotal)
                .fluidOutputs(be -> currentFluid(be, be.getProgress(), level -> MachineRecipes
                        .melting(level, be.getItems().getStack(ArcMelterBlockEntity.SLOT_INPUT))
                        .map(holder -> holder.value().result().create())))
                .build());
    }

    // The Mill lane furthest through its item.
    private static ItemLane furthest(MillBlockEntity be) {
        ItemLane best = be.getLane(0);
        for (int lane = 1; lane < MillBlockEntity.LANES; lane++) {
            if (fraction(be.getLane(lane)) > fraction(best)) {
                best = be.getLane(lane);
            }
        }
        return best;
    }

    private static double fraction(ItemLane lane) {
        return lane.getTotal() > 0 ? (double) lane.getProgress() / lane.getTotal() : 0;
    }

    // The current operation's result: looked up on the server only while one is under way.
    private static List<ItemStack> current(BlockEntity be, int progress, Function<ServerLevel, Optional<ItemStack>> result) {
        return progress > 0 && be.getLevel() instanceof ServerLevel level ? result.apply(level).stream().toList() : List.of();
    }

    private static List<FluidStack> currentFluid(BlockEntity be, int progress, Function<ServerLevel, Optional<FluidStack>> result) {
        return progress > 0 && be.getLevel() instanceof ServerLevel level ? result.apply(level).stream().toList() : List.of();
    }
}
