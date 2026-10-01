/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.blockentity.farming.ClocheBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.HydroponicCellBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.farming.CropHarvest;
import net.zagdrath.arcforge.farming.CropRotation;
import net.zagdrath.arcforge.farming.LoamGrowth;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.tag.ModItemTags;

// Soybeans and crop rotation: Soybeans plant, drop and press (more Seed Oil than other seeds), compost and are legumes;
// on Loam Farmland a legume puts nutrients back (up to 15) instead of using them; harvesting a legume marks the farmland
// and the next non-legume grows rotationMultiplier times as fast until it's harvested; and the Hydroponic Cell grows
// legumes on FE alone, at the fertilized rate.
public final class LegumeGameTests {
    private static final BlockPos SOIL = new BlockPos(1, 1, 1);

    private LegumeGameTests() {}

    private static void loam(GameTestHelper helper, BlockPos pos, int nutrients, boolean afterLegume) {
        helper.setBlock(pos, ModBlocks.LOAM_FARMLAND.get().defaultBlockState().setValue(LoamFarmlandBlock.NUTRIENTS, nutrients)
                .setValue(LoamFarmlandBlock.AFTER_LEGUME, afterLegume));
    }

    // Soybeans plant their crop, are a legume and a compostable, drop more of themselves grown, and press into more Seed
    // Oil than Rapeseeds.
    static void soybeans(GameTestHelper helper) {
        ItemStack beans = new ItemStack(ModItems.SOYBEANS.get());
        helper.assertTrue(beans.is(ModItemTags.LEGUMES) && CropRotation.isLegumeSeed(beans), "Soybeans aren't a legume");
        helper.assertTrue(CropRotation.isLegume(ModBlocks.SOYBEANS.get().defaultBlockState()), "The soybean crop isn't a legume");
        helper.assertTrue(!CropRotation.isLegume(Blocks.WHEAT.defaultBlockState()) && !new ItemStack(Items.WHEAT_SEEDS).is(ModItemTags.LEGUMES),
                "Wheat is a legume");
        helper.assertTrue(beans.has(DataComponents.COMPOSTABLE), "Soybeans don't compost");
        helper.assertTrue(ModBlocks.SOYBEANS.get().asItem() == ModItems.SOYBEANS.get(), "The crop's item isn't Soybeans");
        List<ItemStack> grown = Block.getDrops(ModBlocks.SOYBEANS.get().getStateForAge(7), helper.getLevel(), helper.absolutePos(SOIL.above()), null);
        int count = grown.stream().filter(stack -> stack.is(ModItems.SOYBEANS.get())).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(count >= 2, "Grown soybeans drop " + grown);
        var soy = MachineRecipes.oilPressing(helper.getLevel(), beans);
        var rape = MachineRecipes.oilPressing(helper.getLevel(), new ItemStack(ModItems.RAPESEEDS.get()));
        helper.assertTrue(soy.isPresent() && rape.isPresent() && soy.get().value().result().amount() > rape.get().value().result().amount(),
                "Soybeans don't press into more Seed Oil than Rapeseeds");
        helper.succeed();
    }

    // A legume's stage adds nutrients instead of using them, capped at 15, and it grows even on empty farmland.
    static void legumesAddNutrients(GameTestHelper helper) {
        int per = ArcforgeConfig.LOAM_LEGUME_NUTRIENTS_PER_STAGE.getAsInt();
        loam(helper, SOIL, 3, false);
        helper.setBlock(SOIL.above(), ModBlocks.SOYBEANS.get());
        int stages = LoamGrowth.onCropGrew(helper.getLevel(), helper.absolutePos(SOIL.above()), RandomSource.create(1));
        int after = LoamFarmlandBlock.nutrients(helper.getBlockState(SOIL));
        helper.assertTrue(stages >= 1 && after == Math.min(15, 3 + per * stages), stages + " stages took 3 nutrients to " + after);

        BlockPos full = SOIL.east(2);
        loam(helper, full, 15, false);
        helper.setBlock(full.above(), ModBlocks.SOYBEANS.get());
        LoamGrowth.onCropGrew(helper.getLevel(), helper.absolutePos(full.above()), RandomSource.create(1));
        helper.assertTrue(LoamFarmlandBlock.nutrients(helper.getBlockState(full)) == 15, "Nutrients went past 15");

        BlockPos empty = SOIL.east(4);
        loam(helper, empty, 0, false);
        helper.setBlock(empty.above(), ModBlocks.SOYBEANS.get());
        int grew = LoamGrowth.onCropGrew(helper.getLevel(), helper.absolutePos(empty.above()), RandomSource.create(1));
        helper.assertTrue(grew >= 1 && LoamFarmlandBlock.nutrients(helper.getBlockState(empty)) >= per, "Soybeans on empty Loam didn't feed it");
        helper.succeed();
    }

    // Harvesting ripe Soybeans marks the farmland; harvesting the ripe wheat after them clears it. Young crops don't count.
    static void rotationMarksFarmland(GameTestHelper helper) {
        var level = helper.getLevel();
        loam(helper, SOIL, 0, false);
        helper.setBlock(SOIL.above(), ModBlocks.SOYBEANS.get().getStateForAge(3));
        CropRotation.onHarvested(level, helper.absolutePos(SOIL.above()), helper.getBlockState(SOIL.above()), null);
        helper.assertTrue(!LoamFarmlandBlock.isAfterLegume(helper.getBlockState(SOIL)), "Young soybeans marked the farmland");
        // Through the Harvester's path: the crop replants itself and the farmland remembers.
        helper.setBlock(SOIL.above(), ModBlocks.SOYBEANS.get().getStateForAge(7));
        List<ItemStack> got = new ArrayList<>();
        boolean harvested = CropHarvest.harvest(level, helper.absolutePos(SOIL.above()), new CropHarvest.Sink() {
            @Override
            public boolean fits(List<ItemStack> stacks) {
                return true;
            }

            @Override
            public void put(ItemStack stack) {
                got.add(stack);
            }
        });
        helper.assertTrue(harvested && !got.isEmpty(), "Ripe soybeans weren't harvested");
        helper.assertTrue(LoamFarmlandBlock.isAfterLegume(helper.getBlockState(SOIL)), "Harvesting soybeans didn't mark the farmland");
        // Wheat next: ripe and harvested, it clears the mark.
        helper.setBlock(SOIL.above(), ((CropBlock) Blocks.WHEAT).getStateForAge(7));
        CropRotation.onHarvested(level, helper.absolutePos(SOIL.above()), helper.getBlockState(SOIL.above()), null);
        helper.assertTrue(!LoamFarmlandBlock.isAfterLegume(helper.getBlockState(SOIL)), "Harvesting wheat didn't clear the mark");
        helper.succeed();
    }

    // On farmland a legume left, wheat with no nutrients grows a stage more about (rotationMultiplier - 1) of the time;
    // without the mark it gets nothing; Soybeans after Soybeans get no rotation bonus (only their own nutrients).
    static void rotationSpeedsNextCrop(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos crop = SOIL.above();
        RandomSource random = RandomSource.create(42);
        int trials = 400, extra = 0;
        loam(helper, SOIL, 0, true);
        for (int i = 0; i < trials; i++) {
            helper.setBlock(crop, Blocks.WHEAT);
            extra += LoamGrowth.onCropGrew(level, helper.absolutePos(crop), random);
            helper.assertTrue(LoamFarmlandBlock.nutrients(helper.getBlockState(SOIL)) == 0, "The rotation bonus changed the nutrients");
        }
        double share = extra / (double) trials, expected = ArcforgeConfig.LOAM_ROTATION_MULTIPLIER.getAsDouble() - 1.0;
        helper.assertTrue(Math.abs(share - expected) < 0.1, "Wheat after a legume grew an extra stage " + share + " of the time, expected " + expected);
        loam(helper, SOIL, 0, false);
        helper.setBlock(crop, Blocks.WHEAT);
        helper.assertTrue(LoamGrowth.onCropGrew(level, helper.absolutePos(crop), random) == 0, "Wheat on bare Loam got a bonus");
        helper.assertTrue(CropRotation.multiplier(ModBlocks.SOYBEANS.get().defaultBlockState(), ModBlocks.LOAM_FARMLAND.get().defaultBlockState()
                .setValue(LoamFarmlandBlock.AFTER_LEGUME, true)) == 1.0, "Soybeans after soybeans got the rotation bonus");
        helper.succeed();
    }

    // The Hydroponic Cell grows Soybeans with no Nutrient Solution, at the fertilized rate, leaving its fertilizer alone.
    static void hydroponicLegumesNeedNoNutrients(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.HYDROPONIC_CELL.get());
        HydroponicCellBlockEntity cell = helper.getBlockEntity(pos, HydroponicCellBlockEntity.class);
        CrushingGameTests.charge(cell.getEnergy(), 80_000);
        helper.assertTrue(SteamGameTests.fill(cell.getFluidHandler(null), FluidResource.of(Fluids.WATER), 1_000) == 0, "It took water");
        cell.getItems().setStack(ClocheBlockEntity.SLOT_SEED, new ItemStack(ModItems.SOYBEANS.get()));
        cell.getItems().setStack(ClocheBlockEntity.SLOT_FERTILIZER, new ItemStack(ModItems.COMPOST.get()));
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(cell.getStatus() == MachineStatus.GROWING, "Soybeans with no Nutrient Solution are " + cell.getStatus());
                    double expected = ArcforgeConfig.HYDROPONIC_CELL_SPEED.getAsDouble() * ArcforgeConfig.CLOCHE_FERTILIZER_BONUS.getAsDouble();
                    helper.assertTrue(Math.abs(cell.getRate() - expected) < 0.01, "They grow at " + cell.getRate() + ", not " + expected);
                    helper.assertTrue(cell.getItems().getStack(ClocheBlockEntity.SLOT_FERTILIZER).is(ModItems.COMPOST.get()), "The Compost was used");
                    helper.assertTrue(cell.getTank().getAmount() == 0, "The tank changed");
                })
                .thenWaitUntil(() -> {
                    int beans = 0;
                    for (int i = 0; i < ClocheBlockEntity.OUTPUT_SLOTS; i++) {
                        ItemStack stack = cell.getItems().getStack(ClocheBlockEntity.SLOT_OUTPUT_FIRST + i);
                        beans += stack.is(ModItems.SOYBEANS.get()) ? stack.getCount() : 0;
                    }
                    helper.assertTrue(beans >= 1, "No soybeans yet");
                })
                .thenSucceed();
    }
}
