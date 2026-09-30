/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.farming.greenhouse.GreenhouseControllerBlock;
import net.zagdrath.arcforge.block.farming.greenhouse.GreenhousePart;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.blockentity.farming.GreenhouseBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.PlantingBedBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// The Greenhouse Array: a 5x5x4 forms (frames, glass and controller marked formed, nine beds and a lamp found) and breaks
// with a pane gone; stone inside or a lamp off the top layer stop it forming; a bed takes a soil and then a seed, and only
// grows what fits; the temperature factor, and heat warming the air; no water, no growth; and fully fed, it grows wheat at
// base x 2 (Nutrient Solution) x 1.3 (Carbon Dioxide) and harvests it into its output slots, keeping the seeds.
public final class GreenhouseGameTests {
    private static final int SIZE = 5, HEIGHT = 4;

    private GreenhouseGameTests() {}

    // A 5x5x4 greenhouse at origin (relative): frame edges, glass walls and roof, beds in the floor, a lamp in the middle
    // under the roof, and the controller in the middle of the south wall. Returns the controller's position.
    static BlockPos build(GameTestHelper helper, BlockPos origin) {
        BlockPos controller = origin.offset(SIZE / 2, 1, SIZE - 1);
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    BlockPos pos = origin.offset(x, y, z);
                    int boundaries = (x == 0 || x == SIZE - 1 ? 1 : 0) + (y == 0 || y == HEIGHT - 1 ? 1 : 0) + (z == 0 || z == SIZE - 1 ? 1 : 0);
                    BlockState state;
                    if (boundaries >= 2) {
                        state = ModBlocks.GREENHOUSE_FRAME.get().defaultBlockState();
                    } else if (boundaries == 1) {
                        state = y == 0 ? ModBlocks.PLANTING_BED.get().defaultBlockState()
                                : pos.equals(controller) ? ModBlocks.GREENHOUSE_CONTROLLER.get().defaultBlockState()
                                        .setValue(GreenhouseControllerBlock.FACING, Direction.SOUTH)
                                : ModBlocks.PRESSURE_GLASS.get().defaultBlockState();
                    } else {
                        state = x == SIZE / 2 && z == SIZE / 2 && y == HEIGHT - 2 ? ModBlocks.GROW_LAMP.get().defaultBlockState()
                                : Blocks.AIR.defaultBlockState();
                    }
                    helper.setBlock(pos, state);
                }
            }
        }
        return controller;
    }

    private static GreenhouseBlockEntity greenhouse(GameTestHelper helper, BlockPos controller) {
        return helper.getBlockEntity(controller, GreenhouseBlockEntity.class);
    }

    // Soil and seed in every bed of the greenhouse at origin.
    private static void plantAll(GameTestHelper helper, BlockPos origin, ItemStack soil, ItemStack seed) {
        for (int z = 1; z < SIZE - 1; z++) {
            for (int x = 1; x < SIZE - 1; x++) {
                PlantingBedBlockEntity bed = helper.getBlockEntity(origin.offset(x, 0, z), PlantingBedBlockEntity.class);
                bed.setSoil(soil);
                bed.setSeed(seed);
            }
        }
    }

    static void forms(GameTestHelper helper) {
        BlockPos origin = new BlockPos(0, 1, 0);
        BlockPos controller = build(helper, origin);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    GreenhouseBlockEntity greenhouse = greenhouse(helper, controller);
                    helper.assertTrue(greenhouse.isFormed(), "The greenhouse didn't form");
                    helper.assertTrue(helper.getBlockState(origin).getValue(GreenhousePart.FORMED), "A corner frame isn't formed");
                    helper.assertTrue(PressureGlassBlock.isFormed(helper.getBlockState(origin.offset(1, 3, 1))), "The roof isn't formed");
                    helper.assertTrue(greenhouse.getBeds().size() == 9, greenhouse.getBeds().size() + " beds, not 9");
                    helper.assertTrue(greenhouse.getLamps().size() == 1, greenhouse.getLamps().size() + " lamps, not 1");
                    helper.assertTrue(greenhouse.getStatus() == MachineStatus.NO_CROPS, "Empty, it's " + greenhouse.getStatus());
                    helper.setBlock(origin.offset(0, 2, 2), Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(!greenhouse(helper, controller).isFormed(), "It stayed formed with a pane gone");
                    helper.assertTrue(!PressureGlassBlock.isFormed(helper.getBlockState(origin.offset(1, 3, 1))), "The roof stayed formed");
                    helper.assertTrue(!helper.getBlockState(origin).getValue(GreenhousePart.FORMED), "A frame stayed formed");
                })
                .thenSucceed();
    }

    // Stone inside, or a lamp standing on the floor, and it doesn't form.
    static void rejectsClutter(GameTestHelper helper) {
        BlockPos stone = new BlockPos(0, 1, 0);
        BlockPos lamp = new BlockPos(7, 1, 0);
        BlockPos withStone = build(helper, stone);
        BlockPos withLamp = build(helper, lamp);
        helper.setBlock(stone.offset(1, 1, 1), Blocks.STONE);
        helper.setBlock(lamp.offset(1, 1, 1), ModBlocks.GROW_LAMP.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(!greenhouse(helper, withStone).isFormed(), "It formed with stone inside");
                    helper.assertTrue(!greenhouse(helper, withLamp).isFormed(), "It formed with a lamp on the floor");
                    helper.assertTrue(greenhouse(helper, withLamp).getStatus() == MachineStatus.NOT_FORMED, "Unformed, it's "
                            + greenhouse(helper, withLamp).getStatus());
                })
                .thenSucceed();
    }

    // A bed grows only a seed that fits its soil: wheat in dirt, nether wart in soul sand; not wheat in sand, nor a sapling.
    static void bedsTakeSoilAndSeed(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.PLANTING_BED.get());
        PlantingBedBlockEntity bed = helper.getBlockEntity(pos, PlantingBedBlockEntity.class);
        bed.setSeed(new ItemStack(Items.WHEAT_SEEDS));
        helper.assertTrue(bed.plant() == null, "Wheat grows without soil");
        bed.setSoil(new ItemStack(Items.DIRT, 5));
        helper.assertTrue(bed.getSoil().getCount() == 1, "The bed took " + bed.getSoil().getCount() + " dirt");
        helper.assertTrue(bed.plant() != null, "Wheat doesn't grow in dirt");
        bed.setSoil(new ItemStack(Items.SAND));
        helper.assertTrue(bed.plant() == null, "Wheat grows in sand");
        bed.setSoil(new ItemStack(Items.SOUL_SAND));
        bed.setSeed(new ItemStack(Items.NETHER_WART));
        helper.assertTrue(bed.plant() != null, "Nether wart doesn't grow in soul sand");
        bed.setSoil(new ItemStack(ModItems.LOAM.get()));
        bed.setSeed(new ItemStack(Items.OAK_SAPLING));
        helper.assertTrue(bed.plant() == null, "A sapling grows in a bed (it's hydroponic-only)");
        helper.assertTrue(bed.soilGrowth() > 1.0, "Loam isn't faster");
        helper.succeed();
    }

    // Full speed from 18 to 30°C, losing 5% a degree outside; heat in the buffer warms the air.
    static void temperature(GameTestHelper helper) {
        helper.assertTrue(GreenhouseBlockEntity.temperatureFactor(24) == 1.0, "24°C isn't ideal");
        helper.assertTrue(Math.abs(GreenhouseBlockEntity.temperatureFactor(8) - 0.5) < 1.0E-6, "8°C gives " + GreenhouseBlockEntity.temperatureFactor(8));
        helper.assertTrue(Math.abs(GreenhouseBlockEntity.temperatureFactor(40) - 0.5) < 1.0E-6, "40°C gives " + GreenhouseBlockEntity.temperatureFactor(40));
        helper.assertTrue(GreenhouseBlockEntity.temperatureFactor(-5) == 0.0, "-5°C still grows");
        BlockPos controller = build(helper, new BlockPos(0, 1, 0));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    GreenhouseBlockEntity greenhouse = greenhouse(helper, controller);
                    helper.assertTrue(greenhouse.isFormed(), "The greenhouse didn't form");
                    greenhouse.setTemperature(-10);
                    greenhouse.getHeat().add(greenhouse.getHeat().getCapacity());
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    GreenhouseBlockEntity greenhouse = greenhouse(helper, controller);
                    helper.assertTrue(greenhouse.getHeatState() == GreenhouseBlockEntity.SystemState.ON, "Heat is " + greenhouse.getHeatState());
                    helper.assertTrue(greenhouse.getTemperature() > -9.0, "Heat didn't warm it: " + greenhouse.getTemperature() + "°C");
                    helper.assertTrue(greenhouse.getHeat().getStored() < greenhouse.getHeat().getCapacity(), "It used no heat");
                })
                .thenSucceed();
    }

    // Planted but dry, nothing grows; with water it grows.
    static void needsWater(GameTestHelper helper) {
        BlockPos origin = new BlockPos(0, 1, 0);
        BlockPos controller = build(helper, origin);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    GreenhouseBlockEntity greenhouse = greenhouse(helper, controller);
                    CrushingGameTests.charge(greenhouse.getEnergy(), 100_000);
                    plantAll(helper, origin, new ItemStack(Items.DIRT), new ItemStack(Items.WHEAT_SEEDS));
                    greenhouse.setTemperature(24);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    GreenhouseBlockEntity greenhouse = greenhouse(helper, controller);
                    helper.assertTrue(greenhouse.getStatus() == MachineStatus.NO_WATER, "Dry, it's " + greenhouse.getStatus());
                    helper.assertTrue(greenhouse.getWaterState() == GreenhouseBlockEntity.SystemState.BLOCKED, "Water is " + greenhouse.getWaterState());
                    SteamGameTests.fill(greenhouse.getWater(), FluidResource.of(Fluids.WATER), 4_000);
                    greenhouse.setTemperature(24);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    GreenhouseBlockEntity greenhouse = greenhouse(helper, controller);
                    helper.assertTrue(greenhouse.getStatus() == MachineStatus.GROWING, "Watered, it's " + greenhouse.getStatus());
                    helper.assertTrue(greenhouse.getWater().getAmount() == 4_000 - 9 * 50, "Nine harvests took "
                            + (4_000 - greenhouse.getWater().getAmount()) + " mB, not 450");
                })
                .thenSucceed();
    }

    // Warm, lit, with Nutrient Solution and Carbon Dioxide: wheat grows at 2.6x and is harvested, and the seeds stay.
    static void fullyFedHarvests(GameTestHelper helper) {
        BlockPos origin = new BlockPos(0, 1, 0);
        BlockPos controller = build(helper, origin);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    GreenhouseBlockEntity greenhouse = greenhouse(helper, controller);
                    helper.assertTrue(greenhouse.isFormed(), "The greenhouse didn't form");
                    CrushingGameTests.charge(greenhouse.getEnergy(), 100_000);
                    SteamGameTests.fill(greenhouse.getWater(), FluidResource.of(Fluids.WATER), 4_000);
                    SteamGameTests.fill(greenhouse.getNutrients(), FluidResource.of(ModFluids.NUTRIENT_SOLUTION.get()), 1_000);
                    SteamGameTests.fill(greenhouse.getCo2(), FluidResource.of(ModFluids.CARBON_DIOXIDE.get()), 1_000);
                    helper.assertTrue(greenhouse.getWater().getAmount() == 4_000 && greenhouse.getNutrients().getAmount() == 1_000
                            && greenhouse.getCo2().getAmount() == 1_000, "The tanks didn't fill");
                    greenhouse.setTemperature(24);
                    helper.assertTrue(Math.abs(greenhouse.currentSpeed() - 2.6) < 0.01, "Fully fed, it grows at " + greenhouse.currentSpeed());
                    plantAll(helper, origin, new ItemStack(Items.DIRT), new ItemStack(Items.WHEAT_SEEDS));
                })
                .thenWaitUntil(() -> {
                    GreenhouseBlockEntity greenhouse = greenhouse(helper, controller);
                    greenhouse.setTemperature(24);
                    int wheat = 0;
                    for (int i = 0; i < GreenhouseBlockEntity.OUTPUT_SLOTS; i++) {
                        ItemStack stack = greenhouse.getItems().getStack(GreenhouseBlockEntity.SLOT_OUTPUT_FIRST + i);
                        if (stack.is(Items.WHEAT)) {
                            wheat += stack.getCount();
                        }
                    }
                    helper.assertTrue(wheat >= 9, "Only " + wheat + " wheat so far");
                })
                .thenExecute(() -> {
                    PlantingBedBlockEntity bed = helper.getBlockEntity(origin.offset(2, 0, 2), PlantingBedBlockEntity.class);
                    helper.assertTrue(bed.getSeed().is(Items.WHEAT_SEEDS), "The seed was used up");
                    helper.assertTrue(greenhouse(helper, controller).getNutrients().getAmount() < 1_000, "No Nutrient Solution was used");
                })
                .thenSucceed();
    }
}
