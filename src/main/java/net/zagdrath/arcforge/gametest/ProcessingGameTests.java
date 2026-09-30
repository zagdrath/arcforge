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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.farming.CompostBinBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.MillstoneBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FermenterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GrainDryerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.MillBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.OilPressBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.SeedExtractorBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.steam.Lubricant;

// Farm processing: the Millstone turns by hand (with its cooldown) and by redstone pulse and mills wheat into Flour;
// the Mill spreads its input over three lanes and mills them all; the Oil Press makes Seed Oil and Press Cake (which a
// Compost Bin takes); Seed Oil lubricates turbines; the Seed Extractor threshes seeds; the Grain Dryer waits for 60°C
// and then dries Hop Cones; Dried Hops give the Fermenter 20% more Ethanol and it gives off Carbon Dioxide through a
// Gas Output face; Flour smelts into Bread and dried crops ferment faster; and the recipes exist.
public final class ProcessingGameTests {
    private static final FluidResource WATER = FluidResource.of(Fluids.WATER);
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private ProcessingGameTests() {}

    private static int count(ItemStack stack, net.minecraft.world.item.Item item) {
        return stack.is(item) ? stack.getCount() : 0;
    }

    // Two turns mill a wheat; a second turn by hand in the same tick is refused; a redstone pulse turns it once, and
    // holding the signal doesn't turn it again. Sneak-use hands the Flour over.
    static void millstoneGrinds(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.MILLSTONE.get());
        MillstoneBlockEntity millstone = helper.getBlockEntity(POS, MillstoneBlockEntity.class);
        var level = helper.getLevel();
        helper.assertTrue(millstone.insertByHand(new ItemStack(Items.DIRT)) == 0, "The millstone took dirt");
        helper.assertTrue(millstone.insertByHand(new ItemStack(Items.WHEAT, 2)) == 2, "The millstone refused wheat");
        helper.assertTrue(millstone.turnsNeeded() == 2, "Wheat needs " + millstone.turnsNeeded() + " turns, not 2");
        helper.assertTrue(millstone.turnByHand(level), "The first turn by hand didn't turn it");
        helper.assertTrue(!millstone.turnByHand(level), "A second turn by hand in the same tick turned it");
        helper.assertTrue(millstone.grind(level), "The second turn didn't turn it");
        ItemStack flour = millstone.getItems().getStack(MillstoneBlockEntity.SLOT_OUTPUT);
        helper.assertTrue(count(flour, ModItems.FLOUR.get()) >= 1, "Two turns made " + flour + ", not Flour");
        helper.assertTrue(millstone.getItems().getStack(MillstoneBlockEntity.SLOT_INPUT).getCount() == 1, "The wheat wasn't used up");
        BlockPos abs = helper.absolutePos(POS);
        helper.setBlock(POS.west(), Blocks.REDSTONE_BLOCK);
        millstone.serverTick(level, abs, helper.getBlockState(POS));
        helper.assertTrue(millstone.getTurns() == 1, "A pulse gave " + millstone.getTurns() + " turns, not 1");
        millstone.serverTick(level, abs, helper.getBlockState(POS));
        helper.assertTrue(millstone.getTurns() == 1, "Holding the signal turned it again");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(millstone.takeByHand(level, player) && player.getInventory().countItem(ModItems.FLOUR.get()) >= 1,
                "Taking by hand didn't give the Flour");
        helper.assertTrue(MachineRecipes.milling(level, new ItemStack(Items.BONE)).map(holder -> holder.value().result().count()).orElse(0) > 3,
                "Milling a bone doesn't beat crafting it (3 Bone Meal)");
        helper.succeed();
    }

    // Three wheat put in through the top go one to each lane, and all three are milled.
    static void millRunsThreeLanes(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.MILL.get());
        MillBlockEntity mill = helper.getBlockEntity(POS, MillBlockEntity.class);
        CrushingGameTests.charge(mill.getEnergy(), 20_000);
        var top = mill.getItemHandler(Direction.UP);
        for (int i = 0; i < 3; i++) {
            try (Transaction tx = Transaction.openRoot()) {
                helper.assertTrue(top.insert(ItemResource.of(Items.WHEAT), 1, tx) == 1, "The mill refused wheat");
                tx.commit();
            }
        }
        for (int lane = 0; lane < MillBlockEntity.LANES; lane++) {
            helper.assertTrue(mill.getItems().getStack(MillBlockEntity.inputSlot(lane)).getCount() == 1, "Lane " + lane + " didn't get one wheat");
        }
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(mill.getStatus() == MachineStatus.MILLING, "The mill is " + mill.getStatus()))
                .thenWaitUntil(() -> {
                    for (int lane = 0; lane < MillBlockEntity.LANES; lane++) {
                        helper.assertTrue(mill.getItems().getStack(MillBlockEntity.outputSlot(lane)).is(ModItems.FLOUR.get()), "Lane " + lane + " has no Flour yet");
                    }
                })
                .thenSucceed();
    }

    // Two Rapeseeds make 250 mB of Seed Oil and two Press Cake; a Compost Bin takes the cake.
    static void oilPressMakesOil(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.OIL_PRESS.get());
        OilPressBlockEntity press = helper.getBlockEntity(POS, OilPressBlockEntity.class);
        CrushingGameTests.charge(press.getEnergy(), 20_000);
        press.getItems().setStack(OilPressBlockEntity.SLOT_INPUT, new ItemStack(ModItems.RAPESEEDS.get(), 2));
        helper.assertTrue(CompostBinBlockEntity.compostable(new ItemStack(ModItems.PRESS_CAKE.get())), "The Compost Bin doesn't take Press Cake");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(press.getItems().getStack(OilPressBlockEntity.SLOT_INPUT).isEmpty(), "Rapeseeds left"))
                .thenExecute(() -> {
                    helper.assertTrue(press.getOil().getAmount() == 250 && press.getOil().getResource(0).is(ModFluids.SEED_OIL.get()),
                            "Pressed " + press.getOil().getAmount() + " mB, not 250 mB of Seed Oil");
                    helper.assertTrue(count(press.getItems().getStack(OilPressBlockEntity.SLOT_CAKE), ModItems.PRESS_CAKE.get()) == 2, "Not two Press Cake");
                })
                .thenSucceed();
    }

    // Seed Oil and Heavy Oil lubricate turbines; Ethanol doesn't.
    static void seedOilLubricates(GameTestHelper helper) {
        helper.assertTrue(Lubricant.isLubricant(FluidResource.of(ModFluids.SEED_OIL.get())), "Seed Oil isn't a lubricant");
        helper.assertTrue(Lubricant.isLubricant(FluidResource.of(ModFluids.HEAVY_OIL.get())), "Heavy Oil isn't a lubricant");
        helper.assertTrue(!Lubricant.isLubricant(FluidResource.of(ModFluids.ETHANOL.get())), "Ethanol is a lubricant");
        helper.succeed();
    }

    // A wheat threshes into at least two Wheat Seeds.
    static void seedExtractorThreshes(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.SEED_EXTRACTOR.get());
        SeedExtractorBlockEntity extractor = helper.getBlockEntity(POS, SeedExtractorBlockEntity.class);
        CrushingGameTests.charge(extractor.getEnergy(), 20_000);
        extractor.getItems().setStack(SeedExtractorBlockEntity.SLOT_INPUT, new ItemStack(Items.WHEAT));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(extractor.getItems().getStack(SeedExtractorBlockEntity.SLOT_INPUT).isEmpty(), "Wheat left"))
                .thenExecute(() -> helper.assertTrue(count(extractor.getItems().getStack(SeedExtractorBlockEntity.SLOT_OUTPUT), Items.WHEAT_SEEDS) >= 2,
                        "Threshed " + extractor.getItems().getStack(SeedExtractorBlockEntity.SLOT_OUTPUT)))
                .thenSucceed();
    }

    // Cold it waits; with its buffer half full (well over 60°C) it dries Hop Cones into Dried Hops.
    static void grainDryerNeedsHeat(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.GRAIN_DRYER.get());
        GrainDryerBlockEntity dryer = helper.getBlockEntity(POS, GrainDryerBlockEntity.class);
        dryer.getItems().setStack(GrainDryerBlockEntity.SLOT_INPUT, new ItemStack(ModItems.HOP_CONES.get()));
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(dryer.getStatus() == MachineStatus.TOO_COLD, "A cold dryer is " + dryer.getStatus());
                    dryer.getHeat().add(dryer.getHeat().getCapacity() / 2);
                    helper.assertTrue(dryer.getHeat().getTemperature() > GrainDryerBlockEntity.minTemperature(), "Half full isn't over 60°C");
                })
                .thenWaitUntil(() -> helper.assertTrue(dryer.getItems().getStack(GrainDryerBlockEntity.SLOT_OUTPUT).is(ModItems.DRIED_HOPS.get()), "No Dried Hops yet"))
                .thenSucceed();
    }

    // With Dried Hops, a wheat makes 60 mB of Ethanol instead of 50, leaving 3 boosted operations; it gives off 60 mB of
    // Carbon Dioxide, a gas, which a Gas Output face gives out.
    static void fermenterHopsAndCarbonDioxide(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.FERMENTER.get());
        FermenterBlockEntity fermenter = helper.getBlockEntity(POS, FermenterBlockEntity.class);
        CrushingGameTests.charge(fermenter.getEnergy(), 20_000);
        SteamGameTests.fill(fermenter.getInteractionFluidHandler(), WATER, 1_000);
        fermenter.getItems().setStack(FermenterBlockEntity.SLOT_INPUT, new ItemStack(Items.WHEAT));
        fermenter.getItems().setStack(FermenterBlockEntity.SLOT_ADDITIVE, new ItemStack(ModItems.DRIED_HOPS.get()));
        helper.assertTrue(Gases.isGas(FluidResource.of(ModFluids.CARBON_DIOXIDE.get())), "Carbon Dioxide isn't a gas");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fermenter.getItems().getStack(FermenterBlockEntity.SLOT_INPUT).isEmpty(), "Wheat left"))
                .thenExecute(() -> {
                    helper.assertTrue(fermenter.getEthanol().getAmount() == 60, "Made " + fermenter.getEthanol().getAmount() + " mB Ethanol, not 60");
                    helper.assertTrue(fermenter.getItems().getStack(FermenterBlockEntity.SLOT_ADDITIVE).isEmpty(), "The Dried Hops weren't taken");
                    helper.assertTrue(fermenter.getAdditiveLeft() == 3, fermenter.getAdditiveLeft() + " boosted operations left, not 3");
                    helper.assertTrue(fermenter.getCarbonDioxide().getAmount() == 60, "Gave off " + fermenter.getCarbonDioxide().getAmount() + " mB CO2, not 60");
                    fermenter.setSideMode(RelativeSide.LEFT, SideMode.GAS_OUTPUT);
                    Direction left = RelativeSide.LEFT.toDirection(fermenter.getFacing());
                    var gas = fermenter.getFluidHandler(left);
                    try (Transaction tx = Transaction.openRoot()) {
                        helper.assertTrue(gas != null && gas.extract(FluidResource.of(ModFluids.CARBON_DIOXIDE.get()), 60, tx) == 60,
                                "The Gas Output face doesn't give out Carbon Dioxide");
                    }
                })
                .thenSucceed();
    }

    // GAS_OUTPUT is the last side mode that fits SideConfig's four bits a face, and survives packing.
    static void gasOutputSideMode(GameTestHelper helper) {
        helper.assertTrue(SideMode.GAS_OUTPUT.ordinal() == 15, "GAS_OUTPUT is ordinal " + SideMode.GAS_OUTPUT.ordinal());
        SideConfig config = new SideConfig(SideMode.GAS_OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.GAS_OUTPUT);
        int packed = config.pack();
        helper.assertTrue(SideConfig.unpack(packed, RelativeSide.TOP) == SideMode.GAS_OUTPUT && SideConfig.unpack(packed, RelativeSide.FRONT) == SideMode.GAS_OUTPUT,
                "GAS_OUTPUT doesn't survive packing");
        helper.succeed();
    }

    // Flour smelts into Bread; Dried Grain and Dried Sorghum ferment faster than what they're dried from, for the same Ethanol.
    static void flourAndDriedCrops(GameTestHelper helper) {
        var level = helper.getLevel();
        var bread = level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(ModItems.FLOUR.get())), level);
        helper.assertTrue(bread.isPresent() && bread.get().value().assemble(new SingleRecipeInput(new ItemStack(ModItems.FLOUR.get()))).is(Items.BREAD),
                "Flour doesn't smelt into Bread");
        for (var pair : List.of(List.of(new ItemStack(ModItems.DRIED_GRAIN.get()), new ItemStack(Items.WHEAT)),
                List.of(new ItemStack(ModItems.DRIED_SORGHUM.get()), new ItemStack(ModItems.SORGHUM_STALKS.get())))) {
            var dried = MachineRecipes.fermenting(level, pair.get(0)).orElseThrow().value();
            var fresh = MachineRecipes.fermenting(level, pair.get(1)).orElseThrow().value();
            helper.assertTrue(dried.time() < fresh.time() && dried.result().amount() == fresh.result().amount(),
                    pair.get(0).getHoverName().getString() + " doesn't ferment faster for the same Ethanol");
        }
        helper.succeed();
    }

    static void recipes(GameTestHelper helper) {
        var recipes = helper.getLevel().recipeAccess().recipeMap();
        for (String name : List.of("millstone", "mill", "oil_press", "seed_extractor", "grain_dryer")) {
            helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, Identifier.fromNamespaceAndPath(Arcforge.MODID, "crafting/" + name))) != null,
                    "No recipe for " + name);
        }
        helper.succeed();
    }
}
