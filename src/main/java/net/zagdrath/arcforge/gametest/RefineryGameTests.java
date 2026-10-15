/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.machine.FlareStackBlock;
import net.zagdrath.arcforge.block.multiblock.OilfieldControllerBlock;
import net.zagdrath.arcforge.blockentity.machine.FlareStackBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.RefiningMachineBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CrackingUnitBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.DieselGeneratorBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.item.tool.JetpackFuel;
import net.zagdrath.arcforge.item.tool.JetpackItem;
import net.zagdrath.arcforge.machine.EngineFuel;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.refining.RefiningCore;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.multiblock.PlantKind;
import net.zagdrath.arcforge.recipe.CrackingMode;
import net.zagdrath.arcforge.recipe.DistillingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.RefiningProcess;
import net.zagdrath.arcforge.recipe.RefiningRecipe;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.steam.Lubricant;
import net.zagdrath.arcforge.tag.ModItemTags;

// The refinery's downstream units: the recipes; the Catalytic Reformer, Coker and Lubricant Blender running; the
// Cracking Unit forming and cracking with its catalyst, and hydrocracking in the mode set for it; the Steam Cracker on
// Naphtha and on Natural Gas; Light Oil on the crude chain and the sulfur sour crude gives; the Diesel Generator
// forming, spinning up and throttling when full; the Flare Stack burning gas; and the fuels, Lubricant, Petroleum Coke,
// Kerosene in the Jetpack and asphalt from Bitumen.
final class RefineryGameTests {
    private RefineryGameTests() {}

    private static int fill(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static RefiningRecipe recipe(GameTestHelper helper, RefiningProcess process, Fluid feed) {
        var holder = MachineRecipes.refining(helper.getLevel(), process, new RefiningRecipe.Input(FluidResource.of(feed), 16_000));
        helper.assertTrue(holder.isPresent(), "No " + process.getSerializedName() + " recipe for " + feed);
        return holder.orElseThrow().value();
    }

    // Straight into the buffer: this hot.
    private static void heat(RefiningCore core, int celsius) {
        HeatBuffer heat = core.heat();
        if (heat != null) {
            heat.add(heat.storedAt(celsius));
        }
    }

    // A box plant of this kind, its controller at front facing south and the box running north from it. Its parts are
    // placed by world position: helper.relativePos doesn't undo absolutePos.
    static void buildPlant(GameTestHelper helper, PlantKind kind, BlockPos front) {
        buildPlant(helper, kind, front, OilfieldStructures.GENERATOR_LENGTH);
    }

    // length: a Diesel Generator's (5, 7 or 9).
    static void buildPlant(GameTestHelper helper, PlantKind kind, BlockPos front, int length) {
        OilfieldStructures.PlantBox box = OilfieldStructures.PlantBox.of(kind, helper.absolutePos(front), Direction.NORTH, length);
        Block casing = OilfieldStructures.casing(kind), controller = OilfieldStructures.controller(kind);
        for (BlockPos pos : box.parts()) {
            BlockState state = pos.equals(box.controller()) ? controller.defaultBlockState().setValue(OilfieldControllerBlock.FACING, Direction.SOUTH)
                    : casing.defaultBlockState();
            helper.getLevel().setBlock(pos, state, Block.UPDATE_ALL);
        }
    }

    // Each process has its recipe, and the products the handbook gives.
    static void recipesPresent(GameTestHelper helper) {
        RefiningRecipe cracking = recipe(helper, RefiningProcess.CRACKING, ModFluids.HEAVY_OIL.get());
        helper.assertTrue(cracking.usesCatalyst() && cracking.isCatalyst(new ItemStack(ModItems.CRACKING_CATALYST.get())),
                "Cracking doesn't wear the Cracking Catalyst");
        helper.assertTrue(cracking.fluids().size() == 4 && cracking.mode() == CrackingMode.CATALYTIC, "Cracking makes " + cracking.fluids().size() + " fluids");
        RefiningRecipe reforming = recipe(helper, RefiningProcess.REFORMING, ModFluids.NAPHTHA.get());
        helper.assertTrue(reforming.fluids().get(0).fluid().value() == ModFluids.GASOLINE.get(), "Reforming doesn't make Gasoline first");
        RefiningRecipe coking = recipe(helper, RefiningProcess.COKING, ModFluids.BITUMEN.get());
        helper.assertTrue(coking.itemResult().is(ModItems.PETROLEUM_COKE.get()), "Coking doesn't make Petroleum Coke");
        RefiningRecipe blending = recipe(helper, RefiningProcess.BLENDING, ModFluids.HEAVY_OIL.get());
        helper.assertTrue(blending.fluids().get(0).fluid().value() == ModFluids.LUBRICANT.get() && blending.energyPerTick() > 0,
                "Blending doesn't make Lubricant with FE");
        helper.assertTrue(MachineRecipes.refining(helper.getLevel(), RefiningProcess.REFORMING,
                new RefiningRecipe.Input(FluidResource.of(ModFluids.HEAVY_OIL.get()), 16_000)).isEmpty(), "The reformer takes Heavy Oil");
        helper.succeed();
    }

    // The Catalytic Reformer waits while cold, then turns Naphtha into Gasoline and Hydrogen.
    static void reformerReforms(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.CATALYTIC_REFORMER.get());
        RefiningMachineBlockEntity reformer = helper.getBlockEntity(pos, RefiningMachineBlockEntity.class);
        RefiningRecipe recipe = recipe(helper, RefiningProcess.REFORMING, ModFluids.NAPHTHA.get());
        RefiningCore core = reformer.getCore();
        helper.assertTrue(fill(core.feed(), ModFluids.NAPHTHA.get(), recipe.input().amount()) == recipe.input().amount(), "It refused Naphtha");
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(reformer.getStatus() == MachineStatus.TOO_COLD, "Cold, it's " + reformer.getStatus());
                    heat(core, recipe.minTemp() + 150);
                })
                .thenWaitUntil(() -> helper.assertTrue(core.product(0).getAmount() == recipe.fluids().get(0).amount(),
                        "No Gasoline yet (" + reformer.getStatus() + ")"))
                .thenExecute(() -> {
                    helper.assertTrue(core.product(0).getResource(0).getFluid() == ModFluids.GASOLINE.get(), "The first tank isn't Gasoline");
                    helper.assertTrue(core.product(1).getResource(0).getFluid() == ModFluids.HYDROGEN.get()
                            && core.product(1).getAmount() == recipe.fluids().get(1).amount(), "No Hydrogen");
                    helper.assertTrue(core.feed().getAmount() == 0, "It didn't use the Naphtha");
                })
                .thenSucceed();
    }

    // The Hydrotreater needs Hydrogen, heat and FE, then treats Sour Diesel into Diesel, giving off Hydrogen Sulfide.
    static void hydrotreaterTreats(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.HYDROTREATER.get());
        RefiningMachineBlockEntity unit = helper.getBlockEntity(pos, RefiningMachineBlockEntity.class);
        RefiningRecipe recipe = recipe(helper, RefiningProcess.HYDROTREATING, ModFluids.SOUR_DIESEL.get());
        RefiningCore core = unit.getCore();
        helper.assertTrue(fill(core.feed(), ModFluids.SOUR_DIESEL.get(), recipe.input().amount()) == recipe.input().amount(), "It refused Sour Diesel");
        helper.assertTrue(fill(core.feed(), ModFluids.DIESEL.get(), 100) == 0, "Its feed took clean Diesel");
        heat(core, recipe.minTemp() + 150);
        CrushingGameTests.charge(core.energy(), ArcforgeConfig.HYDROTREATER_ENERGY_CAPACITY.getAsInt());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(unit.getStatus() == MachineStatus.NO_HYDROGEN, "Without Hydrogen it's " + unit.getStatus());
                    int hydrogen = recipe.secondInput().orElseThrow().amount();
                    helper.assertTrue(fill(core.secondFeed(), ModFluids.HYDROGEN.get(), hydrogen) == hydrogen, "It refused Hydrogen");
                })
                .thenWaitUntil(() -> helper.assertTrue(core.product(0).getAmount() == recipe.fluids().get(0).amount(),
                        "No Diesel yet (" + unit.getStatus() + ")"))
                .thenExecute(() -> {
                    helper.assertTrue(core.product(0).getResource(0).getFluid() == ModFluids.DIESEL.get(), "The first tank isn't Diesel");
                    helper.assertTrue(core.product(1).getResource(0).getFluid() == ModFluids.HYDROGEN_SULFIDE.get()
                            && core.product(1).getAmount() == recipe.fluids().get(1).amount(), "No Hydrogen Sulfide");
                    helper.assertTrue(core.feed().getAmount() == 0 && core.secondFeed().getAmount() == 0, "It didn't use its feeds");
                })
                .thenSucceed();
    }

    // The Sulfur Recovery Unit burns Hydrogen Sulfide with Oxygen into Sulfur Dust (into its slot) and Steam.
    static void sulfurRecoveryRecovers(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.SULFUR_RECOVERY_UNIT.get());
        RefiningMachineBlockEntity unit = helper.getBlockEntity(pos, RefiningMachineBlockEntity.class);
        RefiningRecipe recipe = recipe(helper, RefiningProcess.SULFUR_RECOVERY, ModFluids.HYDROGEN_SULFIDE.get());
        RefiningCore core = unit.getCore();
        fill(core.feed(), ModFluids.HYDROGEN_SULFIDE.get(), recipe.input().amount());
        heat(core, recipe.minTemp() + 150);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(unit.getStatus() == MachineStatus.NO_OXYGEN, "Without Oxygen it's " + unit.getStatus());
                    fill(core.secondFeed(), ModFluids.OXYGEN.get(), recipe.secondInput().orElseThrow().amount());
                })
                .thenWaitUntil(() -> helper.assertTrue(unit.getItems().getStack(0).is(ModItems.SULFUR_DUST.get()),
                        "No Sulfur Dust yet (" + unit.getStatus() + ")"))
                .thenExecute(() -> helper.assertTrue(core.product(0).getResource(0).getFluid() == ModFluids.STEAM.get()
                        && core.product(0).getAmount() == recipe.fluids().get(0).amount(), "No Steam"))
                .thenSucceed();
    }

    // The Steam Methane Reformer needs 700°C, then makes Hydrogen and Carbon Dioxide from Natural Gas and Steam.
    static void steamReformerReforms(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.STEAM_METHANE_REFORMER.get());
        RefiningMachineBlockEntity unit = helper.getBlockEntity(pos, RefiningMachineBlockEntity.class);
        RefiningRecipe recipe = recipe(helper, RefiningProcess.STEAM_REFORMING, ModFluids.NATURAL_GAS.get());
        helper.assertTrue(recipe.minTemp() >= 700, "It reforms at " + recipe.minTemp() + "°C");
        RefiningCore core = unit.getCore();
        fill(core.feed(), ModFluids.NATURAL_GAS.get(), recipe.input().amount());
        fill(core.secondFeed(), ModFluids.STEAM.get(), recipe.secondInput().orElseThrow().amount());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(unit.getStatus() == MachineStatus.TOO_COLD, "Cold, it's " + unit.getStatus());
                    heat(core, recipe.minTemp() + 100);
                })
                .thenWaitUntil(() -> helper.assertTrue(core.product(0).getAmount() == recipe.fluids().get(0).amount(),
                        "No Hydrogen yet (" + unit.getStatus() + ")"))
                .thenExecute(() -> {
                    helper.assertTrue(core.product(0).getResource(0).getFluid() == ModFluids.HYDROGEN.get(), "The first tank isn't Hydrogen");
                    helper.assertTrue(core.product(1).getResource(0).getFluid() == ModFluids.CARBON_DIOXIDE.get()
                            && core.product(1).getAmount() == recipe.fluids().get(1).amount(), "No Carbon Dioxide");
                })
                .thenSucceed();
    }

    // The Coker turns Bitumen into Petroleum Coke (into its slot), Refinery Gas and a little Heavy Oil.
    static void cokerCokes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.COKER.get());
        RefiningMachineBlockEntity coker = helper.getBlockEntity(pos, RefiningMachineBlockEntity.class);
        RefiningRecipe recipe = recipe(helper, RefiningProcess.COKING, ModFluids.BITUMEN.get());
        RefiningCore core = coker.getCore();
        fill(core.feed(), ModFluids.BITUMEN.get(), recipe.input().amount());
        heat(core, recipe.minTemp() + 150);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(coker.getItems().getStack(0).is(ModItems.PETROLEUM_COKE.get()),
                        "No Petroleum Coke yet (" + coker.getStatus() + ")"))
                .thenExecute(() -> {
                    helper.assertTrue(core.product(0).getResource(0).getFluid() == ModFluids.REFINERY_GAS.get()
                            && core.product(0).getAmount() == recipe.fluids().get(0).amount(), "No Refinery Gas");
                    helper.assertTrue(core.product(1).getResource(0).getFluid() == ModFluids.HEAVY_OIL.get()
                            && core.product(1).getAmount() == recipe.fluids().get(1).amount(), "No Heavy Oil");
                })
                .thenSucceed();
    }

    // The Lubricant Blender needs FE, then blends Heavy Oil into Lubricant.
    static void blenderBlends(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.LUBRICANT_BLENDER.get());
        RefiningMachineBlockEntity blender = helper.getBlockEntity(pos, RefiningMachineBlockEntity.class);
        RefiningRecipe recipe = recipe(helper, RefiningProcess.BLENDING, ModFluids.HEAVY_OIL.get());
        RefiningCore core = blender.getCore();
        fill(core.feed(), ModFluids.HEAVY_OIL.get(), recipe.input().amount());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(blender.getStatus() == MachineStatus.NO_POWER, "Without FE it's " + blender.getStatus());
                    CrushingGameTests.charge(core.energy(), ArcforgeConfig.BLENDER_ENERGY_CAPACITY.getAsInt());
                })
                .thenWaitUntil(() -> helper.assertTrue(core.product(0).getAmount() == recipe.fluids().get(0).amount(),
                        "No Lubricant yet (" + blender.getStatus() + ")"))
                .thenExecute(() -> helper.assertTrue(core.product(0).getResource(0).getFluid() == ModFluids.LUBRICANT.get(), "It isn't Lubricant"))
                .thenSucceed();
    }

    // A 3x3x5 Cracking Unit forms; it won't crack without its catalyst, then cracks Heavy Oil into Naphtha, Diesel,
    // Refinery Gas and Light Oil and wears the catalyst by one batch.
    static void crackingUnitCracks(GameTestHelper helper) {
        BlockPos front = new BlockPos(1, 1, 3);
        buildPlant(helper, PlantKind.CRACKING_UNIT, front);
        RefiningRecipe recipe = recipe(helper, RefiningProcess.CRACKING, ModFluids.HEAVY_OIL.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    CrackingUnitBlockEntity cracker = helper.getBlockEntity(front, CrackingUnitBlockEntity.class);
                    helper.assertTrue(cracker.isFormed(), "The Cracking Unit didn't form");
                    helper.assertTrue(fill(cracker.getCore().feed(), ModFluids.HEAVY_OIL.get(), recipe.input().amount()) == recipe.input().amount(),
                            "It refused Heavy Oil");
                    heat(cracker.getCore(), recipe.minTemp() + 150);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    CrackingUnitBlockEntity cracker = helper.getBlockEntity(front, CrackingUnitBlockEntity.class);
                    helper.assertTrue(cracker.getStatus() == MachineStatus.NO_CATALYST, "Without a catalyst it's " + cracker.getStatus());
                    cracker.getItems().setStack(CrackingUnitBlockEntity.SLOT_CATALYST, new ItemStack(ModItems.CRACKING_CATALYST.get()));
                })
                .thenWaitUntil(() -> {
                    CrackingUnitBlockEntity cracker = helper.getBlockEntity(front, CrackingUnitBlockEntity.class);
                    helper.assertTrue(cracker.getCore().product(0).getAmount() == recipe.fluids().get(0).amount(),
                            "No Naphtha yet (" + cracker.getStatus() + ")");
                })
                .thenExecute(() -> {
                    RefiningCore core = helper.getBlockEntity(front, CrackingUnitBlockEntity.class).getCore();
                    for (int i = 0; i < recipe.fluids().size(); i++) {
                        helper.assertTrue(core.product(i).getResource(0).getFluid() == recipe.fluids().get(i).fluid().value()
                                && core.product(i).getAmount() == recipe.fluids().get(i).amount(), "Product " + i + " is wrong");
                    }
                    helper.assertTrue(core.catalystLeft() == recipe.catalystUses() - 1, "The catalyst has " + core.catalystLeft() + " batches left");
                    helper.setBlock(front.north(), net.minecraft.world.level.block.Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(!helper.getBlockEntity(front, CrackingUnitBlockEntity.class).isFormed(),
                        "It stayed formed with a casing gone"))
                .thenSucceed();
    }

    // A 3x3x5 Diesel Generator (a V8) forms, spins up on Diesel and makes FE; full, it throttles and burns nothing.
    static void dieselGeneratorGenerates(GameTestHelper helper) {
        BlockPos front = new BlockPos(1, 1, 5);
        buildPlant(helper, PlantKind.DIESEL_GENERATOR, front);
        int spinUp = ArcforgeConfig.DIESEL_GENERATOR_SPIN_UP.getAsInt();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    DieselGeneratorBlockEntity generator = helper.getBlockEntity(front, DieselGeneratorBlockEntity.class);
                    helper.assertTrue(generator.isFormed(), "The Diesel Generator didn't form");
                    helper.assertTrue(generator.getStatus() == MachineStatus.NO_FUEL, "Without fuel it's " + generator.getStatus());
                    helper.assertTrue(fill(generator.getFuel(), ModFluids.NAPHTHA.get(), 1_000) == 0, "It took Naphtha");
                    helper.assertTrue(fill(generator.getFuel(), ModFluids.DIESEL.get(), 4_000) == 4_000, "It refused Diesel");
                })
                .thenIdle(spinUp + 5)
                .thenExecute(() -> {
                    DieselGeneratorBlockEntity generator = helper.getBlockEntity(front, DieselGeneratorBlockEntity.class);
                    helper.assertTrue(generator.getStatus() == MachineStatus.GENERATING, "Spun up it's " + generator.getStatus());
                    helper.assertTrue(generator.getMade() == generator.maxOutput(), "It makes " + generator.getMade() + " FE/t");
                    helper.assertTrue(generator.getFuel().getAmount() < 4_000, "It burned no Diesel");
                    generator.getEnergy().generate(generator.getEnergy().getCapacityAsInt());
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    DieselGeneratorBlockEntity generator = helper.getBlockEntity(front, DieselGeneratorBlockEntity.class);
                    helper.assertTrue(generator.getStatus() == MachineStatus.THROTTLED, "Full, it's " + generator.getStatus());
                    helper.assertTrue(generator.getBurned() == 0.0, "Full, it burns " + generator.getBurned() + " mB/t");
                })
                .thenSucceed();
    }

    // Diesel Generators 5, 7 and 9 long form as a V8, V12 and V16: each makes its engine's output, the bigger ones a
    // little more FE per mB, with a bigger fuel tank and FE buffer.
    static void dieselEnginesScale(GameTestHelper helper) {
        net.zagdrath.arcforge.multiblock.DieselEngine[] engines = net.zagdrath.arcforge.multiblock.DieselEngine.values();
        BlockPos[] fronts = { new BlockPos(1, 1, 9), new BlockPos(5, 1, 9), new BlockPos(9, 1, 9) };
        for (int i = 0; i < engines.length; i++) {
            buildPlant(helper, PlantKind.DIESEL_GENERATOR, fronts[i], engines[i].length);
        }
        int spinUp = ArcforgeConfig.DIESEL_GENERATOR_SPIN_UP.getAsInt();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (int i = 0; i < engines.length; i++) {
                        DieselGeneratorBlockEntity generator = helper.getBlockEntity(fronts[i], DieselGeneratorBlockEntity.class);
                        helper.assertTrue(generator.isFormed() && generator.getEngine() == engines[i], "The " + engines[i].length + "-long generator is "
                                + generator.getEngine() + (generator.isFormed() ? "" : ", unformed"));
                        helper.assertTrue(generator.getFuel().getCapacity() == DieselGeneratorBlockEntity.tankCapacity(engines[i])
                                && generator.getEnergy().getCapacityAsInt() == DieselGeneratorBlockEntity.energyCapacity(engines[i]),
                                engines[i] + "'s tank or buffer is the wrong size");
                        helper.assertTrue(fill(generator.getFuel(), ModFluids.DIESEL.get(), 8_000) == 8_000, engines[i] + " refused Diesel");
                    }
                    var fuel = EngineFuel.of(ModFluids.DIESEL.get());
                    int v8 = DieselGeneratorBlockEntity.fePerMb(fuel, engines[0]), v16 = DieselGeneratorBlockEntity.fePerMb(fuel, engines[2]);
                    helper.assertTrue(DieselGeneratorBlockEntity.maxOutput(engines[2]) > DieselGeneratorBlockEntity.maxOutput(engines[1])
                            && DieselGeneratorBlockEntity.maxOutput(engines[1]) > DieselGeneratorBlockEntity.maxOutput(engines[0]), "Outputs don't grow");
                    helper.assertTrue(Math.abs(v16 - v8 * ArcforgeConfig.DIESEL_GENERATOR_EFFICIENCY_V16.getAsDouble()) <= 1.0,
                            "The V16 makes " + v16 + " FE/mB against the V8's " + v8);
                })
                .thenIdle(spinUp + 5)
                .thenExecute(() -> {
                    for (int i = 0; i < engines.length; i++) {
                        DieselGeneratorBlockEntity generator = helper.getBlockEntity(fronts[i], DieselGeneratorBlockEntity.class);
                        helper.assertTrue(generator.getMade() == DieselGeneratorBlockEntity.maxOutput(engines[i]), engines[i] + " makes "
                                + generator.getMade() + " FE/t");
                    }
                })
                .thenSucceed();
    }

    // The Steam Cracker forms, waits for steam and 800°C, then cracks Naphtha into Ethylene, Propylene, Hydrogen and a
    // little Heavy Oil.
    static void steamCrackerCracks(GameTestHelper helper) {
        BlockPos front = new BlockPos(1, 1, 3);
        buildPlant(helper, PlantKind.STEAM_CRACKER, front);
        RefiningRecipe recipe = recipe(helper, RefiningProcess.STEAM_CRACKING, ModFluids.NAPHTHA.get());
        helper.assertTrue(recipe.minTemp() >= 800 && recipe.fluids().size() == 4, "Steam cracking runs at " + recipe.minTemp() + "°C");
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    var cracker = helper.getBlockEntity(front, net.zagdrath.arcforge.blockentity.multiblock.SteamCrackerBlockEntity.class);
                    helper.assertTrue(cracker.isFormed(), "The Steam Cracker didn't form");
                    RefiningCore core = cracker.getCore();
                    helper.assertTrue(fill(core.feed(), ModFluids.NAPHTHA.get(), recipe.input().amount()) == recipe.input().amount(), "It refused Naphtha");
                    heat(core, recipe.minTemp() + 150);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    var cracker = helper.getBlockEntity(front, net.zagdrath.arcforge.blockentity.multiblock.SteamCrackerBlockEntity.class);
                    helper.assertTrue(cracker.getStatus() == MachineStatus.NO_STEAM, "Without steam it's " + cracker.getStatus());
                    fill(cracker.getCore().secondFeed(), ModFluids.STEAM.get(), recipe.secondInput().orElseThrow().amount());
                })
                .thenWaitUntil(() -> {
                    var cracker = helper.getBlockEntity(front, net.zagdrath.arcforge.blockentity.multiblock.SteamCrackerBlockEntity.class);
                    helper.assertTrue(cracker.getCore().product(0).getAmount() == recipe.fluids().get(0).amount(),
                            "No Ethylene yet (" + cracker.getStatus() + ")");
                })
                .thenExecute(() -> {
                    RefiningCore core = helper.getBlockEntity(front, net.zagdrath.arcforge.blockentity.multiblock.SteamCrackerBlockEntity.class).getCore();
                    for (int i = 0; i < 4; i++) {
                        helper.assertTrue(core.product(i).getResource(0).getFluid() == recipe.fluids().get(i).fluid().value()
                                && core.product(i).getAmount() == recipe.fluids().get(i).amount(), "Product " + i + " is wrong");
                    }
                    helper.assertTrue(core.product(1).getResource(0).is(ModFluids.PROPYLENE.get()), "The second product isn't Propylene");
                })
                .thenSucceed();
    }

    // The Cracking Unit cracks in the mode set for it, never the one its tanks suggest: in catalytic mode, Heavy Oil and
    // Hydrogen (in through a Hydrogen port) still wait for a catalyst. Switched to hydrocracking it needs no catalyst,
    // runs hotter and turns them into mostly Diesel and Kerosene (out of a Kerosene port), and the mode is saved.
    static void crackingModes(GameTestHelper helper) {
        BlockPos front = new BlockPos(1, 1, 3);
        buildPlant(helper, PlantKind.CRACKING_UNIT, front);
        RefiningRecipe catalytic = recipe(helper, RefiningProcess.CRACKING, ModFluids.HEAVY_OIL.get());
        RefiningRecipe hydro = hydrocracking(helper);
        helper.assertTrue(!hydro.usesCatalyst() && hydro.secondInput().map(second -> second.test(FluidResource.of(ModFluids.HYDROGEN.get()))).orElse(false),
                "Hydrocracking isn't Hydrogen without a catalyst");
        helper.assertTrue(hydro.minTemp() > catalytic.minTemp() && hydro.heatPerTick() > catalytic.heatPerTick(), "Hydrocracking doesn't need more heat");
        helper.assertTrue(amount(hydro, ModFluids.DIESEL.get()) > amount(catalytic, ModFluids.DIESEL.get())
                && amount(hydro, ModFluids.KEROSENE.get()) > 0 && amount(catalytic, ModFluids.KEROSENE.get()) == 0
                && amount(hydro, ModFluids.NAPHTHA.get()) < amount(catalytic, ModFluids.NAPHTHA.get())
                && amount(hydro, ModFluids.REFINERY_GAS.get()) < amount(catalytic, ModFluids.REFINERY_GAS.get()), "The modes' products don't differ as they should");
        int hydrogen = hydro.secondInput().orElseThrow().amount();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    CrackingUnitBlockEntity cracker = helper.getBlockEntity(front, CrackingUnitBlockEntity.class);
                    helper.assertTrue(cracker.isFormed() && cracker.getMode() == CrackingMode.CATALYTIC, "A new unit isn't catalytic");
                    helper.assertTrue(fill(cracker.getCore().feed(), ModFluids.HEAVY_OIL.get(), hydro.input().amount()) == hydro.input().amount(),
                            "It refused Heavy Oil");
                    helper.assertTrue(fill(cracker.getFluidHandler(SideMode.HYDROGEN), ModFluids.HYDROGEN.get(), hydrogen) == hydrogen,
                            "Its Hydrogen port refused Hydrogen");
                    heat(cracker.getCore(), hydro.minTemp() + 100);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    CrackingUnitBlockEntity cracker = helper.getBlockEntity(front, CrackingUnitBlockEntity.class);
                    helper.assertTrue(cracker.getStatus() == MachineStatus.NO_CATALYST, "With Hydrogen in catalytic mode it's " + cracker.getStatus());
                    cracker.setMode(CrackingMode.HYDROCRACKING);
                })
                .thenWaitUntil(() -> {
                    CrackingUnitBlockEntity cracker = helper.getBlockEntity(front, CrackingUnitBlockEntity.class);
                    helper.assertTrue(cracker.getCore().product(1).getAmount() == amount(hydro, ModFluids.DIESEL.get()),
                            "No Diesel yet (" + cracker.getStatus() + ")");
                })
                .thenExecute(() -> {
                    CrackingUnitBlockEntity cracker = helper.getBlockEntity(front, CrackingUnitBlockEntity.class);
                    RefiningCore core = cracker.getCore();
                    for (int i = 0; i < hydro.fluids().size(); i++) {
                        helper.assertTrue(core.product(i).getResource(0).getFluid() == hydro.fluids().get(i).fluid().value()
                                && core.product(i).getAmount() == hydro.fluids().get(i).amount(), "Product " + i + " is wrong");
                    }
                    helper.assertTrue(core.feed().getAmount() == 0 && core.secondFeed().getAmount() == 0, "It didn't use its Heavy Oil and Hydrogen");
                    try (Transaction tx = Transaction.openRoot()) {
                        helper.assertTrue(cracker.getFluidHandler(SideMode.KEROSENE).extract(FluidResource.of(ModFluids.KEROSENE.get()), 1_000, tx)
                                == amount(hydro, ModFluids.KEROSENE.get()), "The Kerosene port doesn't give the Kerosene");
                        helper.assertTrue(cracker.getFluidHandler(SideMode.REFINERY_GAS).extract(FluidResource.of(ModFluids.KEROSENE.get()), 1_000, tx) == 0,
                                "The Refinery Gas port gives Kerosene");
                    }
                    // Saved and loaded, it stays hydrocracking.
                    var registries = helper.getLevel().registryAccess();
                    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
                    core.save(output);
                    cracker.setMode(CrackingMode.CATALYTIC);
                    core.load(TagValueInput.create(ProblemReporter.DISCARDING, registries, output.buildResult()));
                    helper.assertTrue(cracker.getMode() == CrackingMode.HYDROCRACKING, "The mode wasn't saved");
                })
                .thenSucceed();
    }

    private static RefiningRecipe hydrocracking(GameTestHelper helper) {
        var holder = MachineRecipes.refining(helper.getLevel(), RefiningProcess.CRACKING,
                new RefiningRecipe.Input(FluidResource.of(ModFluids.HEAVY_OIL.get()), 16_000), CrackingMode.HYDROCRACKING);
        helper.assertTrue(holder.isPresent() && holder.get().value().mode() == CrackingMode.HYDROCRACKING, "No hydrocracking recipe for Heavy Oil");
        return holder.orElseThrow().value();
    }

    // What a recipe makes of this fluid, in mB.
    private static int amount(RefiningRecipe recipe, Fluid fluid) {
        return recipe.fluids().stream().filter(made -> made.fluid().value() == fluid).mapToInt(made -> made.amount()).sum();
    }

    // The Steam Cracker cracks Natural Gas too: less Ethylene than from Naphtha, more Hydrogen, hardly any Propylene.
    static void steamCrackerNaturalGas(GameTestHelper helper) {
        BlockPos front = new BlockPos(1, 1, 3);
        buildPlant(helper, PlantKind.STEAM_CRACKER, front);
        RefiningRecipe naphtha = recipe(helper, RefiningProcess.STEAM_CRACKING, ModFluids.NAPHTHA.get());
        RefiningRecipe gas = recipe(helper, RefiningProcess.STEAM_CRACKING, ModFluids.NATURAL_GAS.get());
        helper.assertTrue(amount(gas, ModFluids.ETHYLENE.get()) < amount(naphtha, ModFluids.ETHYLENE.get())
                && amount(gas, ModFluids.HYDROGEN.get()) > amount(naphtha, ModFluids.HYDROGEN.get())
                && amount(gas, ModFluids.PROPYLENE.get()) * 4 <= amount(naphtha, ModFluids.PROPYLENE.get()), "Natural Gas cracks like Naphtha");
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    var cracker = helper.getBlockEntity(front, net.zagdrath.arcforge.blockentity.multiblock.SteamCrackerBlockEntity.class);
                    helper.assertTrue(cracker.isFormed(), "The Steam Cracker didn't form");
                    RefiningCore core = cracker.getCore();
                    helper.assertTrue(fill(core.feed(), ModFluids.NATURAL_GAS.get(), gas.input().amount()) == gas.input().amount(), "It refused Natural Gas");
                    fill(core.secondFeed(), ModFluids.STEAM.get(), gas.secondInput().orElseThrow().amount());
                    heat(core, gas.minTemp() + 150);
                })
                .thenWaitUntil(() -> {
                    var cracker = helper.getBlockEntity(front, net.zagdrath.arcforge.blockentity.multiblock.SteamCrackerBlockEntity.class);
                    helper.assertTrue(cracker.getCore().product(0).getAmount() == amount(gas, ModFluids.ETHYLENE.get()),
                            "No Ethylene yet (" + cracker.getStatus() + ")");
                })
                .thenExecute(() -> {
                    RefiningCore core = helper.getBlockEntity(front, net.zagdrath.arcforge.blockentity.multiblock.SteamCrackerBlockEntity.class).getCore();
                    for (int i = 0; i < gas.fluids().size(); i++) {
                        helper.assertTrue(core.product(i).getResource(0).getFluid() == gas.fluids().get(i).fluid().value()
                                && core.product(i).getAmount() == gas.fluids().get(i).amount(), "Product " + i + " is wrong");
                    }
                    helper.assertTrue(core.feed().getAmount() == 0, "It didn't use the Natural Gas");
                })
                .thenSucceed();
    }

    // Light Oil is on the crude chain: Crude and Sour Crude give it 10 and 14 high (taken from the Diesel and Heavy Oil,
    // so each height makes as much as before), both cracking modes give a little, and the Chemical Reactor still makes
    // Plastic Sheet from it.
    static void lightOilOnCrudeChain(GameTestHelper helper) {
        int[][] expected = { { 10, 80, 950 }, { 14, 100, 970 } };
        for (Fluid crude : List.of(ModFluids.CRUDE_OIL.get(), ModFluids.SOUR_CRUDE_OIL.get())) {
            var holder = MachineRecipes.distilling(helper.getLevel(), FluidResource.of(crude));
            helper.assertTrue(holder.isPresent(), "No distilling recipe for " + crude);
            DistillingRecipe distilling = holder.orElseThrow().value();
            for (int[] row : expected) {
                var outputs = distilling.outputs(row[0], 0.0F);
                int light = outputs.getOrDefault(ModFluids.LIGHT_OIL.get(), 0);
                int total = outputs.values().stream().mapToInt(Integer::intValue).sum();
                helper.assertTrue(light == row[1] && total == row[2], crude + " " + row[0] + " high makes " + light + " Light Oil of " + total + " mB");
            }
            helper.assertTrue(!distilling.outputs(6, 0.0F).containsKey(ModFluids.LIGHT_OIL.get()), crude + " gives Light Oil 6 high");
        }
        RefiningRecipe catalytic = recipe(helper, RefiningProcess.CRACKING, ModFluids.HEAVY_OIL.get());
        helper.assertTrue(amount(catalytic, ModFluids.LIGHT_OIL.get()) > 0 && amount(hydrocracking(helper), ModFluids.LIGHT_OIL.get()) > 0,
                "Cracking gives no Light Oil");
        helper.assertTrue(MachineRecipes.isReactorFluid(helper.getLevel(), FluidResource.of(ModFluids.LIGHT_OIL.get())), "Nothing in the reactor takes Light Oil");
        helper.succeed();
    }

    // Sour Crude is a real sulfur source: 1,000 mB distilled 14 high, both sour cuts hydrotreated and the Hydrogen Sulfide
    // burnt in the Sulfur Recovery Unit, gives about one Sulfur Dust; the unit's Steam stays 1 mB per mB of feed.
    static void sourCrudeSulfurYield(GameTestHelper helper) {
        DistillingRecipe distilling = MachineRecipes.distilling(helper.getLevel(), FluidResource.of(ModFluids.SOUR_CRUDE_OIL.get())).orElseThrow().value();
        var cuts = distilling.outputs(14, 0.0F);
        double sulfide = 0;
        for (Fluid sour : List.of(ModFluids.SOUR_DIESEL.get(), ModFluids.SOUR_KEROSENE.get())) {
            int cut = cuts.getOrDefault(sour, 0);
            helper.assertTrue(cut > 0, "Sour Crude gives no " + sour + " 14 high");
            RefiningRecipe treating = recipe(helper, RefiningProcess.HYDROTREATING, sour);
            sulfide += cut * (double) amount(treating, ModFluids.HYDROGEN_SULFIDE.get()) / treating.input().amount();
        }
        RefiningRecipe recovery = recipe(helper, RefiningProcess.SULFUR_RECOVERY, ModFluids.HYDROGEN_SULFIDE.get());
        double dust = sulfide / recovery.input().amount() * recovery.itemResult().getCount();
        helper.assertTrue(recovery.itemResult().is(ModItems.SULFUR_DUST.get()) && dust >= 0.9 && dust <= 1.2,
                String.format(java.util.Locale.ROOT, "A bucket of Sour Crude gives %.2f Sulfur Dust", dust));
        helper.assertTrue(amount(recovery, ModFluids.STEAM.get()) == recovery.input().amount(), "The Steam isn't 1 mB per mB of Hydrogen Sulfide");
        helper.succeed();
    }

    // The Flare Stack takes gases (not liquids) through its sides and burns them, lit while it does.
    static void flareStackBurns(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.FLARE_STACK.get());
        FlareStackBlockEntity flare = helper.getBlockEntity(pos, FlareStackBlockEntity.class);
        var side = flare.getFluidHandler(Direction.NORTH);
        helper.assertTrue(side != null && flare.getFluidHandler(Direction.UP) == null, "Its faces are wrong");
        helper.assertTrue(fill(side, ModFluids.NAPHTHA.get(), 1_000) == 0, "It took a liquid");
        helper.assertTrue(fill(side, ModFluids.REFINERY_GAS.get(), 2_000) == 2_000, "It refused Refinery Gas");
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(pos).getValue(FlareStackBlock.LIT), "It isn't lit while flaring"))
                .thenWaitUntil(() -> helper.assertTrue(flare.getTank().getAmount() == 0, "It still holds " + flare.getTank().getAmount() + " mB"))
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(flare.getBurnt() == 0, "It burns with nothing in it"))
                .thenSucceed();
    }

    // The refinery fuels burn at their config values; Gasoline, Diesel, Biodiesel, Refinery Gas and Kerosene fuel the
    // generator;
    // Refinery Gas is a gas; Lubricant lubricates at the premium bonus.
    static void fuelsAndLubricant(GameTestHelper helper) {
        Object[][] fuels = { { ModFluids.GASOLINE.get(), ArcforgeConfig.GASOLINE_HU_PER_MB.getAsInt(), ArcforgeConfig.GASOLINE_BURN_TEMPERATURE.getAsInt() },
                { ModFluids.DIESEL.get(), ArcforgeConfig.DIESEL_HU_PER_MB.getAsInt(), ArcforgeConfig.DIESEL_BURN_TEMPERATURE.getAsInt() },
                { ModFluids.KEROSENE.get(), ArcforgeConfig.KEROSENE_HU_PER_MB.getAsInt(), ArcforgeConfig.KEROSENE_BURN_TEMPERATURE.getAsInt() },
                { ModFluids.REFINERY_GAS.get(), ArcforgeConfig.REFINERY_GAS_HU_PER_MB.getAsInt(),
                        ArcforgeConfig.REFINERY_GAS_BURN_TEMPERATURE.getAsInt() } };
        for (Object[] row : fuels) {
            BurnerFuel fuel = BurnerFuel.of((Fluid) row[0]);
            helper.assertTrue(fuel != null && fuel.huPerMb() == (int) row[1] && fuel.burnTemperature(Integer.MAX_VALUE) == (int) row[2] && fuel.gasTurbine(),
                    row[0] + " doesn't burn as configured");
        }
        for (Fluid fluid : List.of(ModFluids.DIESEL.get(), ModFluids.GASOLINE.get(), ModFluids.BIODIESEL.get(), ModFluids.REFINERY_GAS.get(),
                ModFluids.KEROSENE.get())) {
            helper.assertTrue(EngineFuel.of(fluid) != null, fluid + " doesn't fuel the Diesel Generator");
        }
        helper.assertTrue(EngineFuel.of(ModFluids.NAPHTHA.get()) == null, "Naphtha fuels the Diesel Generator");
        helper.assertTrue(Gases.isGas(ModFluids.REFINERY_GAS.get()) && !Gases.isGas(ModFluids.GASOLINE.get()), "Gas and liquid are mixed up");
        double premium = Lubricant.bonus(FluidResource.of(ModFluids.LUBRICANT.get()));
        helper.assertTrue(Math.abs(premium - (1.0 + ArcforgeConfig.LUBRICANT_PREMIUM_BONUS.getAsDouble())) < 1.0E-9,
                "Lubricant gives " + premium);
        helper.assertTrue(premium > Lubricant.bonus(FluidResource.of(ModFluids.HEAVY_OIL.get())), "Lubricant isn't better than Heavy Oil");
        helper.succeed();
    }

    // Petroleum Coke is coal coke (c:coal_coke) and a carbon fuel; Kerosene fuels the Jetpack, from a bucket.
    static void cokeAndKerosene(GameTestHelper helper) {
        ItemStack coke = new ItemStack(ModItems.PETROLEUM_COKE.get());
        helper.assertTrue(coke.is(ModItemTags.COAL_COKE) && new ItemStack(ModItems.COAL_COKE.get()).is(ModItemTags.COAL_COKE),
                "Petroleum Coke isn't in c:coal_coke");
        helper.assertTrue(coke.is(ModItemTags.CARBON_FUELS), "Petroleum Coke isn't a carbon fuel");
        helper.assertTrue(JetpackFuel.of(ModFluids.KEROSENE.get()) != null, "Kerosene isn't a Jetpack fuel");
        helper.assertTrue(JetpackItem.accepts(new ItemStack(ModItems.TEMPERED_JETPACK.get()), ModFluids.KEROSENE.get()), "The Jetpack refuses Kerosene");
        helper.succeed();
    }

    // 8 gravel round a Bitumen bucket make 8 Asphalt, leaving the bucket.
    static void asphaltFromBitumen(GameTestHelper helper) {
        RecipeHolder<?> holder = helper.getLevel().recipeAccess().recipeMap()
                .byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Arcforge.MODID, "crafting/asphalt_from_bitumen")));
        helper.assertTrue(holder != null && holder.value() instanceof CraftingRecipe, "No asphalt-from-Bitumen recipe");
        CraftingRecipe recipe = (CraftingRecipe) holder.value();
        ItemStack gravel = new ItemStack(Items.GRAVEL);
        CraftingInput grid = CraftingInput.of(3, 3, List.of(gravel, gravel, gravel, gravel, new ItemStack(ModItems.BITUMEN_BUCKET.get()), gravel,
                gravel, gravel, gravel));
        helper.assertTrue(recipe.matches(grid, helper.getLevel()), "Gravel and Bitumen don't match");
        ItemStack result = recipe.assemble(grid);
        helper.assertTrue(result.is(ModItems.ASPHALT.get()) && result.getCount() == 8, "It makes " + result);
        helper.assertTrue(recipe.getRemainingItems(grid).get(4).is(Items.BUCKET), "The bucket isn't left");
        helper.succeed();
    }
}
