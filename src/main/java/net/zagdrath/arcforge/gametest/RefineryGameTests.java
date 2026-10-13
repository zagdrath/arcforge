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
import net.zagdrath.arcforge.machine.refining.RefiningCore;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.multiblock.PlantKind;
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
// Cracking Unit forming and cracking with its catalyst; the Diesel Generator forming, spinning up and throttling when
// full; the Flare Stack burning gas; and the fuels, Lubricant, Petroleum Coke, Kerosene in the Jetpack and asphalt from
// Bitumen.
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

    // A fixed box plant of this kind, its controller at front facing south and the box running north from it. Its parts
    // are placed by world position: helper.relativePos doesn't undo absolutePos.
    static void buildPlant(GameTestHelper helper, PlantKind kind, BlockPos front) {
        OilfieldStructures.PlantBox box = OilfieldStructures.PlantBox.of(kind, helper.absolutePos(front), Direction.NORTH);
        boolean cracker = kind == PlantKind.CRACKING_UNIT;
        Block casing = cracker ? ModBlocks.CRACKING_UNIT_CASING.get() : ModBlocks.DIESEL_GENERATOR_CASING.get();
        Block controller = cracker ? ModBlocks.CRACKING_UNIT_CONTROLLER.get() : ModBlocks.DIESEL_GENERATOR_CONTROLLER.get();
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
        helper.assertTrue(cracking.fluids().size() == 3, "Cracking makes " + cracking.fluids().size() + " fluids");
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

    // The Coker turns Bitumen into Petroleum Coke (into its slot) and Refinery Gas.
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
                .thenExecute(() -> helper.assertTrue(core.product(0).getResource(0).getFluid() == ModFluids.REFINERY_GAS.get()
                        && core.product(0).getAmount() == recipe.fluids().get(0).amount(), "No Refinery Gas"))
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

    // A 3x3x5 Cracking Unit forms; it won't crack without its catalyst, then cracks Heavy Oil into Naphtha, Diesel and
    // Refinery Gas and wears the catalyst by one batch.
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
                    for (int i = 0; i < 3; i++) {
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

    // A 3x2x5 Diesel Generator forms, spins up on Diesel and makes FE; full, it throttles and burns nothing.
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
                    helper.assertTrue(generator.getMade() == DieselGeneratorBlockEntity.maxOutput(), "It makes " + generator.getMade() + " FE/t");
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

    // The refinery fuels burn at their config values; Gasoline, Diesel, Biodiesel and Refinery Gas fuel the generator;
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
        for (Fluid fluid : List.of(ModFluids.DIESEL.get(), ModFluids.GASOLINE.get(), ModFluids.BIODIESEL.get(), ModFluids.REFINERY_GAS.get())) {
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
