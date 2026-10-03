/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.block.multiblock.BiogasDigesterControllerBlock;
import net.zagdrath.arcforge.block.multiblock.DigesterPart;
import net.zagdrath.arcforge.blockentity.machine.AirSeparatorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.HaberReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.BiogasDigesterBlockEntity;
import net.zagdrath.arcforge.farming.LoamGrowth;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.item.farming.FertilizerItem;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.recipe.ChemicalReactorInput;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.SynthesizingRecipe;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModRecipes;
import net.zagdrath.arcforge.steam.Gases;

// Farm chemistry: the Air Separator makes Nitrogen and Oxygen 4:1 (none in the End) and vents what doesn't fit; the
// Haber Reactor waits for 450°C, then makes Ammonia from Hydrogen and Nitrogen; the Chemical Reactor sorts two items
// into its two slots and makes NPK Fertilizer from Basic Slag, Wood Ash and Ammonia; NPK Fertilizer fills and enriches
// Loam Farmland, doubling growth until the nutrients run out, and Digestate adds 4; the Biogas Digester forms (turning
// its controller outward), breaks, and digests wheat into Biogas while warm; the new fuels and gases; and the recipes.
public final class FarmChemistryGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private FarmChemistryGameTests() {}

    private static FluidResource fluid(net.minecraft.world.level.material.Fluid fluid) {
        return FluidResource.of(fluid);
    }

    // 80 mB of Nitrogen and 20 of Oxygen an operation, in the Overworld and the Nether but not the End.
    static void airSeparatorMakesGases(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(MachineRecipes.airSeparating(level, Level.OVERWORLD).isPresent(), "No air to separate in the Overworld");
        helper.assertTrue(MachineRecipes.airSeparating(level, Level.NETHER).isPresent(), "No air to separate in the Nether");
        helper.assertTrue(MachineRecipes.airSeparating(level, Level.END).isEmpty(), "The Air Separator works in the End");
        helper.setBlock(POS, ModBlocks.AIR_SEPARATOR.get());
        AirSeparatorBlockEntity separator = helper.getBlockEntity(POS, AirSeparatorBlockEntity.class);
        CrushingGameTests.charge(separator.getEnergy(), 20_000);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(separator.getNitrogen().getAmount() >= 80, "No Nitrogen yet"))
                .thenExecute(() -> {
                    helper.assertTrue(separator.getNitrogen().getResource(0).is(ModFluids.NITROGEN.get()), "The primary tank holds " + separator.getNitrogen().getResource(0));
                    helper.assertTrue(separator.getOxygen().getResource(0).is(ModFluids.OXYGEN.get()), "The secondary tank holds " + separator.getOxygen().getResource(0));
                    helper.assertTrue(separator.getNitrogen().getAmount() == 4 * separator.getOxygen().getAmount(),
                            separator.getNitrogen().getAmount() + " mB Nitrogen to " + separator.getOxygen().getAmount() + " mB Oxygen, not 4:1");
                    helper.assertTrue(Gases.isGas(fluid(ModFluids.NITROGEN.get())), "Nitrogen isn't a gas");
                })
                .thenSucceed();
    }

    // With the Nitrogen tank full it still makes Oxygen (the Nitrogen goes back into the air); with both full it stops.
    static void airSeparatorVents(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.AIR_SEPARATOR.get());
        AirSeparatorBlockEntity separator = helper.getBlockEntity(POS, AirSeparatorBlockEntity.class);
        CrushingGameTests.charge(separator.getEnergy(), 40_000);
        SteamGameTests.fill(separator.getNitrogen(), fluid(ModFluids.NITROGEN.get()), separator.getNitrogen().getCapacity());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(separator.getOxygen().getAmount() >= 40, "No Oxygen with the Nitrogen full"))
                .thenExecute(() -> {
                    helper.assertTrue(separator.getNitrogen().getAmount() == separator.getNitrogen().getCapacity(), "The Nitrogen overflowed its tank");
                    SteamGameTests.fill(separator.getOxygen(), fluid(ModFluids.OXYGEN.get()), separator.getOxygen().getCapacity());
                })
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(separator.getStatus() == MachineStatus.OUTPUT_FULL, "Both full, it's " + separator.getStatus()))
                .thenSucceed();
    }

    // Too cold below 450°C; once hot, 60 mB of Hydrogen and 20 of Nitrogen make 40 mB of Ammonia.
    static void haberNeedsHeat(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.HABER_REACTOR.get());
        HaberReactorBlockEntity reactor = helper.getBlockEntity(POS, HaberReactorBlockEntity.class);
        CrushingGameTests.charge(reactor.getEnergy(), 20_000);
        SteamGameTests.fill(reactor.getRouter(), fluid(ModFluids.HYDROGEN.get()), 600);
        SteamGameTests.fill(reactor.getRouter(), fluid(ModFluids.NITROGEN.get()), 200);
        helper.assertTrue(reactor.getInputA().getAmount() > 0 && reactor.getInputB().getAmount() > 0, "The gases didn't get a tank each");
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(reactor.getStatus() == MachineStatus.TOO_COLD, "A cold reactor is " + reactor.getStatus());
                    helper.assertTrue(reactor.getOutputTank().getAmount() == 0, "A cold reactor made Ammonia");
                    reactor.getHeat().add(reactor.getHeat().minStoredAt(600));
                    helper.assertTrue(reactor.getHeat().getTemperature() >= 450, "It isn't 450°C yet");
                })
                .thenWaitUntil(() -> helper.assertTrue(reactor.getOutputTank().getAmount() >= 40, "No Ammonia yet"))
                .thenExecute(() -> {
                    helper.assertTrue(reactor.getOutputTank().getResource(0).is(ModFluids.AMMONIA.get()), "It made " + reactor.getOutputTank().getResource(0));
                    int made = reactor.getOutputTank().getAmount() / 40;
                    int hydrogen = reactor.getInputA().getResource(0).is(ModFluids.HYDROGEN.get()) ? reactor.getInputA().getAmount() : reactor.getInputB().getAmount();
                    helper.assertTrue(hydrogen == 600 - 60 * made, made + " operations left " + hydrogen + " mB of Hydrogen");
                })
                .thenSucceed();
    }

    // Basic Slag and Wood Ash fed through the same face each get a slot; with Ammonia they make NPK Fertilizer. The
    // recipe takes its items from either slot.
    static void reactorMakesNpk(GameTestHelper helper) {
        var level = helper.getLevel();
        ChemicalReactingRecipe npk = level.recipeAccess().recipeMap().byType(ModRecipes.CHEMICAL_REACTING.get()).stream()
                .map(holder -> holder.value())
                .filter(recipe -> recipe.itemOutput().isPresent() && recipe.itemOutput().get().create().is(ModItems.NPK_FERTILIZER.get()))
                .findFirst().orElse(null);
        helper.assertTrue(npk != null, "No NPK Fertilizer recipe");
        FluidResource ammonia = fluid(ModFluids.AMMONIA.get());
        helper.assertTrue(npk.slotsFor(new ChemicalReactorInput(new ItemStack(ModItems.WOOD_ASH.get()), new ItemStack(ModItems.BASIC_SLAG.get()),
                ammonia, 1_000, FluidResource.EMPTY, 0)) != null, "The recipe doesn't take its items the other way round");
        helper.assertTrue(npk.slotsFor(new ChemicalReactorInput(new ItemStack(ModItems.BASIC_SLAG.get()), ItemStack.EMPTY,
                ammonia, 1_000, FluidResource.EMPTY, 0)) == null, "The recipe ran on Basic Slag alone");

        helper.setBlock(POS, ModBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(POS, ChemicalReactorBlockEntity.class);
        CrushingGameTests.charge(reactor.getEnergy(), 40_000);
        var input = reactor.getItemHandler(null);
        helper.assertTrue(FiberGameTests.insert(input, ModItems.BASIC_SLAG.get(), 4) == 4, "The reactor refused Basic Slag");
        helper.assertTrue(FiberGameTests.insert(input, ModItems.WOOD_ASH.get(), 4) == 4, "The reactor refused Wood Ash");
        helper.assertTrue(FiberGameTests.insert(input, ModItems.SULFUR_DUST.get(), 1) == 1, "A third item didn't go into the third slot");
        helper.assertTrue(FiberGameTests.insert(input, ModItems.SALT.get(), 1) == 0, "A fourth item went in while all three slots were in use");
        ItemStack a = reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_INPUT);
        ItemStack b = reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_INPUT_B);
        helper.assertTrue(a.is(ModItems.BASIC_SLAG.get()) && b.is(ModItems.WOOD_ASH.get()), "The slots hold " + a + " and " + b);
        SteamGameTests.fill(reactor.getRouter(), ammonia, 1_000);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_OUTPUT).getCount() >= 2, "No NPK Fertilizer yet"))
                .thenExecute(() -> {
                    helper.assertTrue(reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_OUTPUT).is(ModItems.NPK_FERTILIZER.get()), "It made "
                            + reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_OUTPUT));
                    helper.assertTrue(reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_INPUT).getCount() < 4
                            && reactor.getItems().getStack(ChemicalReactorBlockEntity.SLOT_INPUT_B).getCount() < 4, "It didn't use both items");
                })
                .thenSucceed();
    }

    // NPK fills the farmland to 15 and enriches it: every growth stage brings one more. The enrichment goes when the
    // nutrients run out. Digestate adds 4 and doesn't enrich.
    static void npkEnrichesLoam(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos soil = new BlockPos(1, 1, 1);
        helper.setBlock(soil, ModBlocks.LOAM_FARMLAND.get().defaultBlockState().setValue(LoamFarmlandBlock.NUTRIENTS, 3));
        FertilizerItem npk = ModItems.NPK_FERTILIZER.get();
        FertilizerItem digestate = ModItems.DIGESTATE.get();
        helper.assertTrue(npk.enriches() && !digestate.enriches(), "Only NPK Fertilizer should enrich");
        BlockPos abs = helper.absolutePos(soil);
        npk.apply(level, abs, helper.getBlockState(soil));
        BlockState state = helper.getBlockState(soil);
        helper.assertTrue(LoamFarmlandBlock.nutrients(state) == 15 && state.getValue(LoamFarmlandBlock.ENRICHED), "NPK left " + state);
        helper.assertTrue(npk.wouldHelp(ModBlocks.LOAM_FARMLAND.get().defaultBlockState().setValue(LoamFarmlandBlock.NUTRIENTS, 15)),
                "NPK can't enrich full farmland");
        helper.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState());
        int stages = LoamGrowth.onCropGrew(level, abs.above(), level.getRandom());
        helper.assertTrue(stages == 2, "An enriched stage grew " + stages + " stages, not 2");
        helper.assertTrue(helper.getBlockState(soil.above()).getValue(CropBlock.AGE) == 1, "The wheat is at age " + helper.getBlockState(soil.above()).getValue(CropBlock.AGE));
        helper.setBlock(soil, ModBlocks.LOAM_FARMLAND.get().defaultBlockState().setValue(LoamFarmlandBlock.NUTRIENTS, 1).setValue(LoamFarmlandBlock.ENRICHED, true));
        helper.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState());
        LoamGrowth.onCropGrew(level, abs.above(), level.getRandom());
        state = helper.getBlockState(soil);
        helper.assertTrue(LoamFarmlandBlock.nutrients(state) == 0 && !state.getValue(LoamFarmlandBlock.ENRICHED), "Used up, the soil is " + state);
        digestate.apply(level, abs, state);
        state = helper.getBlockState(soil);
        helper.assertTrue(LoamFarmlandBlock.nutrients(state) == 4 && !state.getValue(LoamFarmlandBlock.ENRICHED), "Digestate left " + state);
        helper.succeed();
    }

    // Builds the 3x3x3 with its minimum corner at min and the controller in the middle of the south side, facing.
    private static BlockPos buildDigester(GameTestHelper helper, BlockPos min, Direction facing) {
        BlockPos controller = min.offset(1, 1, 2);
        for (BlockPos pos : BlockPos.betweenClosed(min, min.offset(2, 2, 2))) {
            if (pos.equals(controller)) {
                helper.setBlock(controller, ModBlocks.BIOGAS_DIGESTER_CONTROLLER.get().defaultBlockState().setValue(BiogasDigesterControllerBlock.FACING, facing));
            } else {
                helper.setBlock(pos.immutable(), ModBlocks.DIGESTER_CASING.get());
            }
        }
        return controller;
    }

    // A full tank forms, even with its controller placed facing in (it turns out); one missing casing breaks it; and
    // eight casings round a controller with a gap don't form.
    static void digesterForms(GameTestHelper helper) {
        BlockPos good = buildDigester(helper, new BlockPos(0, 1, 0), Direction.NORTH);
        BlockPos gap = buildDigester(helper, new BlockPos(5, 1, 0), Direction.SOUTH);
        helper.setBlock(new BlockPos(5, 3, 0), Blocks.AIR);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    BiogasDigesterBlockEntity digester = helper.getBlockEntity(good, BiogasDigesterBlockEntity.class);
                    helper.assertTrue(digester.isFormed(), "The full tank didn't form");
                    helper.assertTrue(helper.getBlockState(good).getValue(BiogasDigesterControllerBlock.FACING) == Direction.SOUTH,
                            "The controller faces " + helper.getBlockState(good).getValue(BiogasDigesterControllerBlock.FACING));
                    helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 1)).getValue(DigesterPart.FORMED), "The core casing isn't formed");
                    BiogasDigesterBlockEntity broken = helper.getBlockEntity(gap, BiogasDigesterBlockEntity.class);
                    helper.assertTrue(!broken.isFormed() && broken.getStatus() == MachineStatus.NOT_FORMED, "A tank with a gap formed");
                    helper.setBlock(new BlockPos(0, 1, 0), Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(!helper.getBlockEntity(good, BiogasDigesterBlockEntity.class).isFormed(), "The tank stayed formed without a casing");
                    helper.assertTrue(!helper.getBlockState(new BlockPos(2, 3, 2)).getValue(DigesterPart.FORMED), "A casing stayed formed");
                })
                .thenSucceed();
    }

    // Cold, it waits; at 35°C or hotter it digests four wheat at once, each into 60 mB of Biogas for 100 mB of water.
    static void digesterDigests(GameTestHelper helper) {
        BlockPos controller = buildDigester(helper, new BlockPos(0, 1, 0), Direction.SOUTH);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    BiogasDigesterBlockEntity digester = helper.getBlockEntity(controller, BiogasDigesterBlockEntity.class);
                    helper.assertTrue(digester.isFormed(), "The digester didn't form");
                    SteamGameTests.fill(digester.getWater(), FluidResource.of(Fluids.WATER), 1_000);
                    digester.getItems().setStack(BiogasDigesterBlockEntity.SLOT_INPUT, new ItemStack(Items.WHEAT, 4));
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    BiogasDigesterBlockEntity digester = helper.getBlockEntity(controller, BiogasDigesterBlockEntity.class);
                    helper.assertTrue(digester.getStatus() == MachineStatus.TOO_COLD, "A cold digester is " + digester.getStatus());
                    digester.getHeat().add(digester.getHeat().minStoredAt(50));
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    BiogasDigesterBlockEntity digester = helper.getBlockEntity(controller, BiogasDigesterBlockEntity.class);
                    helper.assertTrue(digester.busyLanes() == 4, digester.busyLanes() + " lanes busy, not 4");
                    helper.assertTrue(digester.getWater().getAmount() == 600, digester.getWater().getAmount() + " mB of water left, not 600");
                    helper.assertTrue(digester.getItems().getStack(BiogasDigesterBlockEntity.SLOT_INPUT).isEmpty(), "Wheat left over");
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(controller, BiogasDigesterBlockEntity.class).getBiogas().getAmount() >= 240,
                        "Not all the Biogas yet"))
                .thenExecute(() -> {
                    BiogasDigesterBlockEntity digester = helper.getBlockEntity(controller, BiogasDigesterBlockEntity.class);
                    helper.assertTrue(digester.getBiogas().getResource(0).is(ModFluids.BIOGAS.get()), "It made " + digester.getBiogas().getResource(0));
                    ItemStack digestate = digester.getItems().getStack(BiogasDigesterBlockEntity.SLOT_DIGESTATE);
                    helper.assertTrue(digestate.isEmpty() || digestate.is(ModItems.DIGESTATE.get()), "The by-product is " + digestate);
                })
                .thenSucceed();
    }

    // Biodiesel burns in the Fuel Burner but not the turbine; Biogas in both. The new gases are gases; the liquids aren't.
    static void fuelsAndGases(GameTestHelper helper) {
        BurnerFuel biodiesel = BurnerFuel.of(ModFluids.BIODIESEL.get());
        helper.assertTrue(biodiesel != null && biodiesel.huPerMb() == 320 && biodiesel.burnTemperature(1_600) == 1_000 && !biodiesel.gasTurbine(),
                "Biodiesel's fuel value is " + biodiesel);
        BurnerFuel biogas = BurnerFuel.of(ModFluids.BIOGAS.get());
        helper.assertTrue(biogas != null && biogas.huPerMb() == 45 && biogas.burnTemperature(1_600) == 1_100 && biogas.gasTurbine(),
                "Biogas's fuel value is " + biogas);
        for (var gas : List.of(ModFluids.NITROGEN.get(), ModFluids.AMMONIA.get(), ModFluids.BIOGAS.get())) {
            helper.assertTrue(Gases.isGas(fluid(gas)), gas + " isn't a gas");
        }
        for (var liquid : List.of(ModFluids.NUTRIENT_SOLUTION.get(), ModFluids.BIODIESEL.get())) {
            helper.assertTrue(!Gases.isGas(fluid(liquid)), liquid + " is a gas");
        }
        helper.assertTrue(BurnerFuel.of(ModFluids.NITROGEN.get()) == null && BurnerFuel.of(ModFluids.AMMONIA.get()) == null, "Nitrogen or Ammonia burns");
        helper.succeed();
    }

    // The recipes load: Ammonia (either tank order), Nutrient Solution, Biodiesel, and digesting for everything the
    // digester takes.
    static void recipes(GameTestHelper helper) {
        var level = helper.getLevel();
        FluidResource hydrogen = fluid(ModFluids.HYDROGEN.get()), nitrogen = fluid(ModFluids.NITROGEN.get());
        var ammonia = MachineRecipes.synthesizing(level, new SynthesizingRecipe.Input(nitrogen, 1_000, hydrogen, 1_000));
        helper.assertTrue(ammonia.isPresent() && ammonia.get().value().output().create().getFluid() == ModFluids.AMMONIA.get(), "No Ammonia recipe");
        helper.assertTrue(ammonia.get().value().minTemperatureOrDefault() == 450, "Ammonia needs " + ammonia.get().value().minTemperatureOrDefault() + "°C");
        helper.assertTrue(MachineRecipes.synthesizing(level, new SynthesizingRecipe.Input(hydrogen, 1_000, FluidResource.EMPTY, 0)).isEmpty(),
                "Hydrogen alone makes something");
        ChemicalReactorInput diesel = new ChemicalReactorInput(ItemStack.EMPTY, fluid(ModFluids.ETHANOL.get()), 1_000, fluid(ModFluids.SEED_OIL.get()), 1_000);
        var biodiesel = MachineRecipes.chemicalReacting(level, diesel);
        helper.assertTrue(biodiesel.isPresent() && biodiesel.get().value().fluidOutput().get().create().getFluid() == ModFluids.BIODIESEL.get(),
                "No Biodiesel recipe");
        ChemicalReactorInput solution = new ChemicalReactorInput(new ItemStack(ModItems.NPK_FERTILIZER.get()), FluidResource.of(Fluids.WATER), 1_000,
                FluidResource.EMPTY, 0);
        var nutrient = MachineRecipes.chemicalReacting(level, solution);
        helper.assertTrue(nutrient.isPresent() && nutrient.get().value().fluidOutput().get().create().getFluid() == ModFluids.NUTRIENT_SOLUTION.get(),
                "No Nutrient Solution recipe");
        for (ItemStack stack : List.of(new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.OAK_LEAVES),
                new ItemStack(ModItems.PRESS_CAKE.get()), new ItemStack(ModItems.COMPOST.get()))) {
            helper.assertTrue(MachineRecipes.isDigesterInput(level, stack), stack + " doesn't digest");
        }
        helper.assertTrue(!MachineRecipes.isDigesterInput(level, new ItemStack(Items.COBBLESTONE)), "Cobblestone digests");
        helper.succeed();
    }
}
