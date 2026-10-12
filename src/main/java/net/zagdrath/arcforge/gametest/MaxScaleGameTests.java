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
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.multiblock.CarbonizerBlock;
import net.zagdrath.arcforge.block.multiblock.DistillationArrayControllerBlock;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.DistillationArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.WaterIntakeBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// What max-size multiblocks need: Arcforged FE rates, the Industrial Water Intake, and Speed (and Heat) Upgrades in the
// Carbonizer and the Distillation Array. (The Condenser Array's 7x7x7 rates are in SteamCycleGameTests.condenserClimate.)
public final class MaxScaleGameTests {
    private MaxScaleGameTests() {}

    // The Arcforged Energy Conduit and Energy Cell move 262,144 FE/t; the other tiers are unchanged.
    static void energyRates(GameTestHelper helper) {
        int[] conduit = { 256, 1_024, 8_192, 262_144 };
        int[] cell = { 512, 2_048, 16_384, 262_144 };
        for (ConduitTier tier : ConduitTier.values()) {
            helper.assertTrue(tier.energyPerTick() == conduit[tier.ordinal()], tier + " energy conduit carries " + tier.energyPerTick() + " FE/t");
            helper.assertTrue(tier.cellRate() == cell[tier.ordinal()], tier + " energy cell moves " + tier.cellRate() + " FE/t");
        }
        // A 7x7x19 Steam Turbine Array (103.4 sections) on Superheated Steam with lubricant fits down one Arcforged
        // line; a full-size 7x7x27 (147 sections) needs two.
        double perSection = ArcforgeConfig.TURBINE_ARRAY_FLOW_PER_SECTION.getAsInt() * 56 * net.zagdrath.arcforge.steam.Lubricant.bonus();
        int nineteen = (int) Math.round(49.0 / 9.0 * 19 * perSection), full = (int) Math.round(147 * perSection);
        helper.assertTrue(ConduitTier.ARCFORGED.energyPerTick() >= nineteen, "One Arcforged conduit can't carry " + nineteen + " FE/t");
        helper.assertTrue(ConduitTier.ARCFORGED.energyPerTick() < full && 2 * ConduitTier.ARCFORGED.energyPerTick() >= full,
                "A full-size turbine's " + full + " FE/t doesn't take two Arcforged conduits");
        helper.succeed();
    }

    // --- Industrial Water Intake ---

    // A pool from (0,1,0) to (size-1, depth, size-1) inside a glass rim, with the floor of the test under it.
    private static void pool(GameTestHelper helper, int size, int depth) {
        // A stone floor under it all, so no water runs out under the rim.
        for (int x = -1; x <= size; x++) {
            for (int z = -1; z <= size; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        for (int y = 1; y <= depth; y++) {
            for (int x = -1; x <= size; x++) {
                for (int z = -1; z <= size; z++) {
                    boolean rim = x < 0 || z < 0 || x >= size || z >= size;
                    helper.setBlock(new BlockPos(x, y, z), rim ? Blocks.GLASS : Blocks.WATER);
                }
            }
        }
    }

    // A cube standing on a 12x12x3 pool (432 sources) pumps 8,000 mB/t (x1.25 if the test biome counts as open water)
    // for 0.25 FE per mB, and its ports' push keeps pace.
    static void waterIntakePumps(GameTestHelper helper) {
        pool(helper, 12, 3);
        BlockPos min = new BlockPos(4, 4, 4);
        MultiblockTestHelpers.buildCube(helper, min, ModBlocks.WATER_INTAKE_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    WaterIntakeBlockEntity intake = MultiblockTestHelpers.centre(helper, min, WaterIntakeBlockEntity.class);
                    helper.assertTrue(intake.isFormed(), "The intake didn't form");
                    try (Transaction tx = Transaction.openRoot()) {
                        intake.getEnergy().insert(100_000, tx);
                        tx.commit();
                    }
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    WaterIntakeBlockEntity intake = MultiblockTestHelpers.centre(helper, min, WaterIntakeBlockEntity.class);
                    helper.assertTrue(intake.getWaterBlocks() >= ArcforgeConfig.WATER_INTAKE_MIN_WATER.getAsInt(),
                            "It counted " + intake.getWaterBlocks() + " water sources");
                    int expected = intake.isOpenWater() ? 10_000 : 8_000;
                    helper.assertTrue(intake.rate() == expected, "It pumps " + intake.rate() + " mB/t, expected " + expected);
                    helper.assertTrue(intake.getPumped() == expected, "It pumped " + intake.getPumped() + " mB last tick");
                    int water = intake.getWater().getAmount();
                    int used = 100_000 - intake.getEnergy().getAmountAsInt();
                    helper.assertTrue(water > 0 && Math.abs(used - water / 4) <= 5, "Pumped " + water + " mB for " + used + " FE");
                    helper.assertTrue(intake.getStatus() == MachineStatus.PUMPING, "Status " + intake.getStatus());
                })
                .thenSucceed();
    }

    // On a 3x3 pond it forms but won't pump: not enough connected water.
    static void waterIntakeNeedsAWaterBody(GameTestHelper helper) {
        pool(helper, 3, 1);
        BlockPos min = new BlockPos(0, 2, 0);
        MultiblockTestHelpers.buildCube(helper, min, ModBlocks.WATER_INTAKE_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    WaterIntakeBlockEntity intake = MultiblockTestHelpers.centre(helper, min, WaterIntakeBlockEntity.class);
                    try (Transaction tx = Transaction.openRoot()) {
                        intake.getEnergy().insert(100_000, tx);
                        tx.commit();
                    }
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    WaterIntakeBlockEntity intake = MultiblockTestHelpers.centre(helper, min, WaterIntakeBlockEntity.class);
                    helper.assertTrue(intake.isFormed() && intake.getWaterBlocks() == 9, "It counted " + intake.getWaterBlocks() + " sources");
                    helper.assertTrue(intake.rate() == 0 && intake.getWater().getAmount() == 0, "It pumped from a pond");
                    helper.assertTrue(intake.getStatus() == MachineStatus.NO_WATER, "Status " + intake.getStatus());
                    helper.assertTrue(WaterIntakeBlockEntity.countWater(helper.getLevel(), helper.absolutePos(new BlockPos(5, 5, 5)), 100) == 0,
                            "Air counted as water");
                })
                .thenSucceed();
    }

    // --- Fuel production ---

    // Speed Upgrades in the Carbonizer's upgrade slot shorten each new batch by the usual 2^(n/2).
    static void carbonizerSpeedUpgrades(GameTestHelper helper) {
        BlockState state = ModBlocks.CARBONIZER.get().defaultBlockState().setValue(CarbonizerBlock.FACING, Direction.NORTH);
        for (int y = 1; y <= 2; y++) {
            for (int z = 0; z < 2; z++) {
                helper.setBlock(new BlockPos(0, y, z), state);
            }
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    CarbonizerBlockEntity master = helper.getBlockEntity(new BlockPos(0, 1, 0), CarbonizerBlockEntity.class);
                    helper.assertTrue(master.isFormed(), "The Carbonizer didn't form");
                    helper.assertTrue(master.getItems().isValid(CarbonizerBlockEntity.SLOT_UPGRADE,
                            net.neoforged.neoforge.transfer.item.ItemResource.of(ModItems.SPEED_UPGRADE.get())), "It refuses Speed Upgrades");
                    master.getItems().setStack(CarbonizerBlockEntity.SLOT_UPGRADE, new ItemStack(ModItems.SPEED_UPGRADE.get(), 8));
                    master.getItems().setStack(CarbonizerBlockEntity.SLOT_INPUT, new ItemStack(Items.COAL, 1));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    CarbonizerBlockEntity master = helper.getBlockEntity(new BlockPos(0, 1, 0), CarbonizerBlockEntity.class);
                    int base = MachineRecipes.carbonizing(helper.getLevel(), new ItemStack(Items.COAL)).orElseThrow().value().time();
                    int expected = UpgradeType.time(base, 8);
                    helper.assertTrue(master.speedUpgrades() == 8, master.speedUpgrades() + " Speed Upgrades counted");
                    helper.assertTrue(master.getChamberTime(0) == expected, "The batch takes " + master.getChamberTime(0) + " ticks, expected " + expected);
                })
                .thenSucceed();
    }

    // Two Speed Upgrades double the Distillation Array's feed; it takes Heat Upgrades too.
    static void distillationUpgrades(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 2; x++) {
                for (int z = 0; z < 2; z++) {
                    BlockPos pos = min.offset(x, y, z);
                    if (y == 1 && x == 0 && z == 0) {
                        helper.setBlock(pos, ModBlocks.DISTILLATION_ARRAY_CONTROLLER.get().defaultBlockState()
                                .setValue(DistillationArrayControllerBlock.FACING, Direction.NORTH));
                    } else {
                        helper.setBlock(pos, y == 0 || y == 3 ? ModBlocks.DISTILLATION_ARRAY_CASING.get() : ModBlocks.TRAY_LEVEL_CASING.get());
                    }
                }
            }
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    DistillationArrayBlockEntity column = helper.getBlockEntity(min.above(1), DistillationArrayBlockEntity.class);
                    helper.assertTrue(column.isFormed(), "The column didn't form");
                    int base = column.feedRate();
                    helper.assertTrue(base == ArcforgeConfig.DISTILLATION_FEED_RATE.getAsInt(), "A 4-high column feeds " + base + " mB/t");
                    CrushingGameTests.install(column.getItems(), ModItems.SPEED_UPGRADE.get(), 2);
                    CrushingGameTests.install(column.getItems(), ModItems.HEAT_UPGRADE.get(), 8);
                    helper.assertTrue(column.feedRate() == base * 2, "Two Speed Upgrades feed " + column.feedRate() + " mB/t");
                    helper.assertTrue(column.upgrades(UpgradeType.HEAT) == 8, "It holds " + column.upgrades(UpgradeType.HEAT) + " Heat Upgrades");
                })
                .thenSucceed();
    }
}
