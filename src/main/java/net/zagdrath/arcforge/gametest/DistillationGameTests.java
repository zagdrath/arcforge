/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.multiblock.ColumnPart;
import net.zagdrath.arcforge.block.multiblock.DistillationArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.TrayLevelCasingBlock;
import net.zagdrath.arcforge.block.multiblock.TrayLevelCasingBlock.Fraction;
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.SteamTurbineBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.steam.SteamTank;

// The Distillation Array, burn temperatures, turbine lubricant and the pitch products.
final class DistillationGameTests {
    private static final FluidResource CREOSOTE = FluidResource.of(ModFluids.CREOSOTE.get());
    private static final FluidResource NAPHTHA = FluidResource.of(ModFluids.NAPHTHA.get());
    private static final FluidResource HEAVY_OIL = FluidResource.of(ModFluids.HEAVY_OIL.get());

    private DistillationGameTests() {}

    // A 2x2 column: casings top and bottom, the controller at min + (0, controllerLayer, 0), trays elsewhere.
    private static void buildColumn(GameTestHelper helper, BlockPos min, int height, int controllerLayer, Direction controllerFacing) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    BlockPos pos = min.offset(x, y, z);
                    if (y == controllerLayer && x == 0 && z == 0) {
                        helper.setBlock(pos, ModBlocks.DISTILLATION_ARRAY_CONTROLLER.get().defaultBlockState()
                                .setValue(DistillationArrayControllerBlock.FACING, controllerFacing));
                    } else {
                        helper.setBlock(pos, y == 0 || y == height - 1 ? ModBlocks.DISTILLATION_ARRAY_CASING.get() : ModBlocks.TRAY_LEVEL_CASING.get());
                    }
                }
            }
        }
    }

    private static DistillationArrayBlockEntity controller(GameTestHelper helper, BlockPos min, int controllerLayer) {
        return helper.getBlockEntity(min.above(controllerLayer), DistillationArrayBlockEntity.class);
    }

    // Only 2x2 columns 4, 6 or 8 tall with the controller off the end layers form. The trays show the fractions
    // that column makes, the controller turns to face out, and taking a block away breaks it.
    static void columnForms(GameTestHelper helper) {
        BlockPos four = new BlockPos(0, 1, 0), eight = new BlockPos(3, 1, 0), five = new BlockPos(6, 1, 0), low = new BlockPos(9, 1, 0);
        buildColumn(helper, four, 4, 1, Direction.NORTH);
        buildColumn(helper, eight, 8, 1, Direction.SOUTH);
        buildColumn(helper, five, 5, 1, Direction.NORTH);
        buildColumn(helper, low, 4, 0, Direction.NORTH);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(controller(helper, four, 1).isFormed() && controller(helper, four, 1).getHeight() == 4, "The 4-high column didn't form");
                    DistillationArrayBlockEntity tall = controller(helper, eight, 1);
                    helper.assertTrue(tall.isFormed() && tall.getHeight() == 8, "The 8-high column didn't form");
                    helper.assertTrue(!controller(helper, five, 1).isFormed(), "A 5-high column formed");
                    helper.assertTrue(!controller(helper, low, 0).isFormed(), "A column with its controller in the bottom layer formed");
                    helper.assertTrue(controller(helper, five, 1).getStatus() == MachineStatus.NOT_FORMED, "An incomplete column is " + controller(helper, five, 1).getStatus());
                    // The controller at the north-west corner can't face south into the column.
                    helper.assertTrue(helper.getBlockState(eight.above()).getValue(DistillationArrayControllerBlock.FACING) == Direction.NORTH,
                            "The controller faces " + helper.getBlockState(eight.above()).getValue(DistillationArrayControllerBlock.FACING));
                    Fraction[] expected = { null, Fraction.HEAVY, Fraction.LIGHT, Fraction.LIGHT, Fraction.NAPHTHA, Fraction.NAPHTHA, Fraction.VAPOR };
                    for (int y = 1; y < 7; y++) {
                        BlockState tray = helper.getBlockState(eight.offset(1, y, 1));
                        helper.assertTrue(tray.getValue(ColumnPart.FORMED) && tray.getValue(TrayLevelCasingBlock.FRACTION) == expected[y],
                                "Tray " + y + " of 8 shows " + tray.getValue(TrayLevelCasingBlock.FRACTION));
                    }
                    helper.assertTrue(helper.getBlockState(four.offset(1, 1, 1)).getValue(TrayLevelCasingBlock.FRACTION) == Fraction.NAPHTHA
                            && helper.getBlockState(four.offset(1, 2, 1)).getValue(TrayLevelCasingBlock.FRACTION) == Fraction.VAPOR,
                            "A 4-high column's trays don't show naphtha then vapour");
                    helper.setBlock(eight.offset(1, 7, 1), Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(!controller(helper, eight, 1).isFormed(), "The column stayed formed without a block");
                    helper.assertTrue(!helper.getBlockState(eight.offset(1, 3, 1)).getValue(ColumnPart.FORMED), "A tray stayed formed");
                })
                .thenSucceed();
    }

    // Each height makes its own fractions from a 1,000 mB batch of creosote: 4 high Naphtha 250 and 2 Pitch;
    // 8 high Naphtha 250, Light Oil 300, Heavy Oil 300 and 1 Pitch. Without heat nothing happens.
    static void columnMakesFractions(GameTestHelper helper) {
        BlockPos four = new BlockPos(0, 1, 0), eight = new BlockPos(3, 1, 0), cold = new BlockPos(6, 1, 0);
        buildColumn(helper, four, 4, 1, Direction.NORTH);
        buildColumn(helper, eight, 8, 1, Direction.NORTH);
        buildColumn(helper, cold, 4, 1, Direction.NORTH);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    for (BlockPos min : new BlockPos[] { four, eight, cold }) {
                        DistillationArrayBlockEntity column = controller(helper, min, 1);
                        helper.assertTrue(SteamGameTests.fill(column.getFluidHandler(null), CREOSOTE, 2_000) == 2_000, "The column didn't take creosote");
                        if (min != cold) {
                            column.getHeat().add(column.getHeat().getCapacity());
                        }
                    }
                    helper.assertTrue(SteamGameTests.fill(controller(helper, four, 1).getFluidHandler(null), NAPHTHA, 100) == 0,
                            "The column took naphtha in");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    DistillationArrayBlockEntity tall = controller(helper, eight, 1);
                    helper.assertTrue(tall.getNaphtha().getAmount() == 250 && tall.getLightOil().getAmount() == 300 && tall.getHeavyOil().getAmount() == 300,
                            "8 high made " + tall.getNaphtha().getAmount() + " / " + tall.getLightOil().getAmount() + " / " + tall.getHeavyOil().getAmount());
                    helper.assertTrue(tall.getItems().getStack(DistillationArrayBlockEntity.SLOT_PITCH).is(ModItems.PITCH.get())
                            && tall.getItems().getStack(DistillationArrayBlockEntity.SLOT_PITCH).getCount() == 1, "8 high didn't make one pitch");
                    helper.assertTrue(helper.getBlockState(eight.offset(1, 3, 1)).getValue(ColumnPart.LIT), "The trays aren't lit while running");
                    DistillationArrayBlockEntity cooled = controller(helper, cold, 1);
                    helper.assertTrue(cooled.getStatus() == MachineStatus.HEATING && cooled.getNaphtha().getAmount() == 0,
                            "A cold column is " + cooled.getStatus() + " with " + cooled.getNaphtha().getAmount() + " mB naphtha");
                })
                .thenIdle(50)
                .thenExecute(() -> {
                    DistillationArrayBlockEntity small = controller(helper, four, 1);
                    helper.assertTrue(small.getNaphtha().getAmount() == 250 && small.getHeavyOil().getAmount() == 0 && small.getLightOil().getAmount() == 0,
                            "4 high made " + small.getNaphtha().getAmount() + " mB naphtha");
                    helper.assertTrue(small.getItems().getStack(DistillationArrayBlockEntity.SLOT_PITCH).getCount() == 2, "4 high didn't make two pitch");
                })
                .thenSucceed();
    }

    // Superheated steam raises naphtha by 50%, out of the heavy oil (6 high: 375 / 325), and uses 100 mB a batch.
    static void steamStripping(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        buildColumn(helper, min, 6, 1, Direction.NORTH);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    DistillationArrayBlockEntity column = controller(helper, min, 1);
                    SteamGameTests.fill(column.getFluidHandler(null), CREOSOTE, 1_000);
                    helper.assertTrue(SteamGameTests.fill(column.getFluidHandler(null), SteamGrade.SUPERHEATED.resource(), 1_000) == 1_000,
                            "The column didn't take steam");
                    column.getHeat().add(column.getHeat().getCapacity());
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    DistillationArrayBlockEntity column = controller(helper, min, 1);
                    helper.assertTrue(column.getNaphtha().getAmount() == 375 && column.getHeavyOil().getAmount() == 325,
                            "With superheated steam: " + column.getNaphtha().getAmount() + " naphtha, " + column.getHeavyOil().getAmount() + " heavy oil");
                    helper.assertTrue(column.getSteam().getAmount() == 900, "Steam left: " + column.getSteam().getAmount());
                })
                .thenSucceed();
    }

    // A steam tank holds one grade: a lower grade arriving turns the stored steam down to it.
    static void steamTankMixesDown(GameTestHelper helper) {
        SteamTank tank = new SteamTank(8_000, () -> {});
        SteamGameTests.fill(tank, SteamGrade.SUPERHEATED.resource(), 1_000);
        helper.assertTrue(SteamGameTests.fill(tank, SteamGrade.HIGH_PRESSURE.resource(), 500) == 500, "The tank didn't take a lower grade");
        helper.assertTrue(tank.getResource(0).equals(SteamGrade.HIGH_PRESSURE.resource()) && tank.getAmount() == 1_500,
                "Mixed steam is " + tank.getResource(0) + " x " + tank.getAmount());
        SteamGameTests.fill(tank, SteamGrade.SUPERHEATED.resource(), 500);
        helper.assertTrue(tank.getResource(0).equals(SteamGrade.HIGH_PRESSURE.resource()) && tank.getAmount() == 2_000,
                "A higher grade changed the tank to " + tank.getResource(0));
        helper.succeed();
    }

    // A Fuel Burner gets no hotter than its fuel burns: creosote stops at 850°C, naphtha goes on to 1,200°C.
    static void burnTemperature(GameTestHelper helper) {
        helper.assertTrue(BurnerFuel.of(ModFluids.CREOSOTE.get()).burnTemperature(1_200) == 850, "Creosote doesn't burn at 850°C");
        helper.assertTrue(BurnerFuel.of(ModFluids.NAPHTHA.get()).burnTemperature(1_200) == 1_200, "Naphtha doesn't burn at 1,200°C");
        BlockPos creosotePos = new BlockPos(0, 1, 0), naphthaPos = new BlockPos(2, 1, 0);
        helper.setBlock(creosotePos, ModBlocks.FUEL_BURNER.get());
        helper.setBlock(naphthaPos, ModBlocks.FUEL_BURNER.get());
        FuelBurnerBlockEntity creosote = helper.getBlockEntity(creosotePos, FuelBurnerBlockEntity.class);
        FuelBurnerBlockEntity naphtha = helper.getBlockEntity(naphthaPos, FuelBurnerBlockEntity.class);
        SteamGameTests.fill(creosote.getInteractionFluidHandler(), CREOSOTE, 1_000);
        SteamGameTests.fill(naphtha.getInteractionFluidHandler(), NAPHTHA, 1_000);
        for (FuelBurnerBlockEntity burner : new FuelBurnerBlockEntity[] { creosote, naphtha }) {
            burner.getHeat().add(burner.getHeat().storedAt(1_000));
        }
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(creosote.getStatus() == MachineStatus.FULL && creosote.getTank().getAmount() == 1_000,
                            "A creosote burner at 1,000°C is " + creosote.getStatus() + " with " + creosote.getTank().getAmount() + " mB");
                    helper.assertTrue(naphtha.getStatus() == MachineStatus.BURNING && naphtha.getHeatPerTick() == 200,
                            "A naphtha burner at 1,000°C is " + naphtha.getStatus() + " making " + naphtha.getHeatPerTick() + " HU/t");
                })
                .thenSucceed();
    }

    // Heavy Oil lubricates a Steam Turbine: +8% (Superheated: 238 FE/t), 1 mB every 100 ticks. It takes nothing else.
    static void turbineLubricant(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.STEAM_TURBINE.get());
        SteamTurbineBlockEntity turbine = helper.getBlockEntity(pos, SteamTurbineBlockEntity.class);
        helper.assertTrue(SteamGameTests.fill(turbine.getFluidHandler(null), CREOSOTE, 100) == 0, "The turbine took creosote");
        SteamGameTests.fill(turbine.getSteam(), SteamGrade.SUPERHEATED.resource(), 8_000);
        helper.assertTrue(SteamGameTests.fill(turbine.getFluidHandler(null), HEAVY_OIL, 100) == 100, "The turbine didn't take heavy oil");
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(turbine.getFePerTick() == 238, "A lubricated turbine makes " + turbine.getFePerTick() + " FE/t"))
                .thenIdle(110)
                .thenExecute(() -> helper.assertTrue(turbine.getLubricant().getAmount() == 99, "Lubricant left: " + turbine.getLubricant().getAmount()))
                .thenSucceed();
    }

    // A lubricated Steam Turbine Array spins up twice as fast.
    static void turbineArrayLubricant(GameTestHelper helper) {
        BlockPos dry = new BlockPos(0, 1, 0), oiled = new BlockPos(0, 1, 4);
        SteamGameTests.buildShell(helper, dry, Direction.Axis.X, 5, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        SteamGameTests.buildShell(helper, oiled, Direction.Axis.X, 5, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (BlockPos min : new BlockPos[] { dry, oiled }) {
                        helper.getBlockEntity(min, SteamTurbineArrayBlockEntity.class).getSteam().set(0, SteamGrade.SUPERHEATED.resource(), 60_000);
                    }
                    SteamTurbineArrayBlockEntity lubricated = helper.getBlockEntity(oiled, SteamTurbineArrayBlockEntity.class);
                    helper.assertTrue(SteamGameTests.fill(lubricated.getFluidHandler(null), HEAVY_OIL, 1_000) == 1_000, "The array didn't take heavy oil");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    double plain = helper.getBlockEntity(dry, SteamTurbineArrayBlockEntity.class).getRpm();
                    double fast = helper.getBlockEntity(oiled, SteamTurbineArrayBlockEntity.class).getRpm();
                    helper.assertTrue(fast > plain * 1.5, "Lubricated " + fast + " RPM against " + plain);
                })
                .thenSucceed();
    }

    // Pitch spins into Carbon Fiber, creosote is a distilling feed, and asphalt speeds you up.
    static void pitchProducts(GameTestHelper helper) {
        helper.assertTrue(MachineRecipes.isFiberizerInput(helper.getLevel(), new ItemStack(ModItems.PITCH.get())), "The Fiberizer doesn't take pitch");
        helper.assertTrue(ModBlocks.ASPHALT.get().getSpeedFactor() > 1.0F && ModBlocks.ASPHALT_SLAB.get().getSpeedFactor() > 1.0F
                && ModBlocks.ASPHALT_STAIRS.get().getSpeedFactor() > 1.0F, "Asphalt doesn't speed things up");
        helper.assertTrue(MachineRecipes.isDistillingFeed(helper.getLevel(), CREOSOTE), "Creosote isn't a distilling feed");
        helper.succeed();
    }
}
