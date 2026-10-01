/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.blockentity.farming.ClocheBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.GlassClocheBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.GrowChamberBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.HydroponicCellBlockEntity;
import net.zagdrath.arcforge.farming.ClochePlants;
import net.zagdrath.arcforge.farming.ClocheSoil;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.ClocheRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModRecipes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// Automated farms: the Glass Cloche waits for a soil and water, then grows (taking a harvest's water), and drops its
// harvest into the chest under it; the Grow Chamber harvests wheat with FE, water and NPK Fertilizer, keeping its seed;
// the Hydroponic Cell grows an oak sapling into logs with Nutrient Solution and Carbon Dioxide, which the soil farms
// can't; a crop no recipe names falls back to its loot table; and the recipes and soils.
public final class ClocheGameTests {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private ClocheGameTests() {}

    private static void put(MachineItemHandler items, int slot, ItemStack stack) {
        items.setStack(slot, stack);
    }

    private static int outputCount(ClocheBlockEntity farm, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int i = 0; i < ClocheBlockEntity.OUTPUT_SLOTS; i++) {
            ItemStack stack = farm.getItems().getStack(ClocheBlockEntity.SLOT_OUTPUT_FIRST + i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    // No soil, then no water; with both it grows, taking 100 mB for the harvest. The slots only take what goes there.
    static void glassClocheNeedsSoilAndWater(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.GLASS_CLOCHE.get());
        GlassClocheBlockEntity cloche = helper.getBlockEntity(POS, GlassClocheBlockEntity.class);
        MachineItemHandler items = cloche.getItems();
        var level = helper.getLevel();
        helper.assertTrue(!ClocheBlockEntity.isItemValid(ClocheBlockEntity.Kind.GLASS_CLOCHE, level, ClocheBlockEntity.SLOT_SOIL,
                net.neoforged.neoforge.transfer.item.ItemResource.of(Items.STONE)), "Stone went in the soil slot");
        helper.assertTrue(!ClocheBlockEntity.isItemValid(ClocheBlockEntity.Kind.GLASS_CLOCHE, level, ClocheBlockEntity.SLOT_SEED,
                net.neoforged.neoforge.transfer.item.ItemResource.of(Items.STICK)), "A stick went in the seed slot");
        helper.assertTrue(!ClocheBlockEntity.isItemValid(ClocheBlockEntity.Kind.HYDROPONIC_CELL, level, ClocheBlockEntity.SLOT_SOIL,
                net.neoforged.neoforge.transfer.item.ItemResource.of(Items.DIRT)), "The Hydroponic Cell took a soil");
        put(items, ClocheBlockEntity.SLOT_SEED, new ItemStack(Items.WHEAT_SEEDS));
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(cloche.getStatus() == MachineStatus.NO_SOIL, "Without soil it's " + cloche.getStatus());
                    put(items, ClocheBlockEntity.SLOT_SOIL, new ItemStack(Items.DIRT));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(cloche.getStatus() == MachineStatus.NO_WATER, "Without water it's " + cloche.getStatus());
                    SteamGameTests.fill(cloche.getTank(), FluidResource.of(Fluids.WATER), 1_000);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(cloche.getStatus() == MachineStatus.GROWING, "With soil and water it's " + cloche.getStatus());
                    helper.assertTrue(cloche.getTank().getAmount() == 900, "The harvest took " + (1_000 - cloche.getTank().getAmount()) + " mB, not 100");
                    helper.assertTrue(cloche.growth() > 0, "Nothing grew");
                    helper.assertTrue(cloche.plant() != null && cloche.plant().recipe() != null, "Wheat isn't grown by its recipe");
                })
                .thenSucceed();
    }

    // What's in its output slots goes down into the chest under it.
    static void glassClochePushesDown(GameTestHelper helper) {
        BlockPos chest = POS.below();
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(POS, ModBlocks.GLASS_CLOCHE.get());
        GlassClocheBlockEntity cloche = helper.getBlockEntity(POS, GlassClocheBlockEntity.class);
        put(cloche.getItems(), ClocheBlockEntity.SLOT_OUTPUT_FIRST, new ItemStack(Items.WHEAT, 3));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(chest, ChestBlockEntity.class).getItem(0).is(Items.WHEAT),
                        "The harvest didn't reach the chest"))
                .thenExecute(() -> helper.assertTrue(outputCount(cloche, Items.WHEAT) == 0, "Wheat stayed in the cloche"))
                .thenSucceed();
    }

    // Wheat at 3x, doubled by NPK Fertilizer (one point a harvest), comes out as wheat; the seed stays planted.
    static void growChamberHarvests(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.GROW_CHAMBER.get());
        GrowChamberBlockEntity chamber = helper.getBlockEntity(POS, GrowChamberBlockEntity.class);
        CrushingGameTests.charge(chamber.getEnergy(), 40_000);
        SteamGameTests.fill(chamber.getTank(), FluidResource.of(Fluids.WATER), 1_000);
        MachineItemHandler items = chamber.getItems();
        put(items, ClocheBlockEntity.SLOT_SEED, new ItemStack(Items.WHEAT_SEEDS));
        put(items, ClocheBlockEntity.SLOT_SOIL, new ItemStack(ModItems.LOAM.get()));
        put(items, ClocheBlockEntity.SLOT_FERTILIZER, new ItemStack(ModItems.NPK_FERTILIZER.get()));
        int npk = ClocheBlockEntity.fertilizerPoints(new ItemStack(ModItems.NPK_FERTILIZER.get()));
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(chamber.getStatus() == MachineStatus.GROWING, "It's " + chamber.getStatus());
                    helper.assertTrue(items.getStack(ClocheBlockEntity.SLOT_FERTILIZER).isEmpty(), "The fertilizer wasn't opened");
                    helper.assertTrue(chamber.getFertilizer() == npk - 1, chamber.getFertilizer() + " points left of " + npk);
                    helper.assertTrue(chamber.isEnriched(), "NPK Fertilizer didn't enrich it");
                    // Loam 1.25 x enriched 2 x the chamber's 3.
                    helper.assertTrue(Math.abs(chamber.getRate() - 7.5) < 0.01, "It grows at " + chamber.getRate() + ", not 7.5");
                    helper.assertTrue(chamber.getEnergy().getAmountAsInt() < 40_000, "It used no FE");
                })
                .thenWaitUntil(() -> helper.assertTrue(outputCount(chamber, Items.WHEAT) >= 1, "No wheat yet"))
                .thenExecute(() -> helper.assertTrue(items.getStack(ClocheBlockEntity.SLOT_SEED).is(Items.WHEAT_SEEDS), "The seed was used up"))
                .thenSucceed();
    }

    // An oak sapling can't grow in the Grow Chamber; in the Hydroponic Cell it grows into oak logs, faster with Carbon
    // Dioxide, which it uses.
    static void hydroponicCellGrowsTrees(GameTestHelper helper) {
        helper.setBlock(POS.east(2), ModBlocks.GROW_CHAMBER.get());
        GrowChamberBlockEntity chamber = helper.getBlockEntity(POS.east(2), GrowChamberBlockEntity.class);
        CrushingGameTests.charge(chamber.getEnergy(), 40_000);
        SteamGameTests.fill(chamber.getTank(), FluidResource.of(Fluids.WATER), 1_000);
        put(chamber.getItems(), ClocheBlockEntity.SLOT_SEED, new ItemStack(Items.OAK_SAPLING));
        put(chamber.getItems(), ClocheBlockEntity.SLOT_SOIL, new ItemStack(Items.DIRT));

        helper.setBlock(POS, ModBlocks.HYDROPONIC_CELL.get());
        HydroponicCellBlockEntity cell = helper.getBlockEntity(POS, HydroponicCellBlockEntity.class);
        CrushingGameTests.charge(cell.getEnergy(), 80_000);
        SteamGameTests.fill(cell.getTank(), FluidResource.of(ModFluids.NUTRIENT_SOLUTION.get()), 1_000);
        SteamGameTests.fill(cell.getCo2Tank(), FluidResource.of(ModFluids.CARBON_DIOXIDE.get()), 4_000);
        helper.assertTrue(cell.getTank().getAmount() == 1_000 && cell.getCo2Tank().getAmount() == 4_000, "The tanks didn't fill");
        helper.assertTrue(SteamGameTests.fill(cell.getFluidHandler(null), FluidResource.of(Fluids.WATER), 1_000) == 0, "It took water");
        put(cell.getItems(), ClocheBlockEntity.SLOT_SEED, new ItemStack(Items.OAK_SAPLING));
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(chamber.getStatus() == MachineStatus.CANT_GROW, "A sapling in the Grow Chamber is " + chamber.getStatus());
                    helper.assertTrue(cell.getStatus() == MachineStatus.GROWING, "The Hydroponic Cell is " + cell.getStatus());
                    helper.assertTrue(cell.isBoosted() && Math.abs(cell.getRate() - 7.5) < 0.01, "With CO2 it grows at " + cell.getRate() + ", not 7.5");
                    helper.assertTrue(cell.getCo2Tank().getAmount() < 4_000, "It used no Carbon Dioxide");
                    helper.assertTrue(cell.getTank().getAmount() == 950, "A harvest took " + (1_000 - cell.getTank().getAmount()) + " mB, not 50");
                })
                .thenWaitUntil(() -> helper.assertTrue(outputCount(cell, Items.OAK_LOG) >= 6, "No oak logs yet"))
                .thenSucceed();
    }

    // A crop no recipe names grows from its loot table (here wheat's own, asked for directly); recipes win.
    static void fallbackCrop(GameTestHelper helper) {
        var level = helper.getLevel();
        helper.assertTrue(ClochePlants.isFallbackCrop(new ItemStack(Items.WHEAT_SEEDS)), "Wheat isn't a vanilla-style crop");
        helper.assertTrue(!ClochePlants.isFallbackCrop(new ItemStack(Items.OAK_SAPLING)), "A sapling is a crop");
        ClochePlants.Plant fallback = new ClochePlants.Plant(null, Items.WHEAT_SEEDS, 2_400, ClocheRecipe.Render.DEFAULT);
        List<ItemStack> harvest = fallback.harvest(level, helper.absolutePos(POS), level.getRandom());
        helper.assertTrue(harvest.stream().anyMatch(stack -> stack.is(Items.WHEAT)), "A grown wheat crop's drops were " + harvest);
        helper.assertTrue(fallback.stateAt(1.0F) != null && fallback.stateAt(1.0F).is(Blocks.WHEAT), "The fallback draws " + fallback.stateAt(1.0F));
        ClochePlants.Plant recipe = ClochePlants.find(level, new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.DIRT), false);
        helper.assertTrue(recipe != null && recipe.recipe() != null, "The wheat recipe lost to the fallback");
        helper.assertTrue(ClochePlants.find(level, new ItemStack(Items.WHEAT_SEEDS), new ItemStack(Items.SAND), false) == null, "Wheat grows in sand");
        helper.succeed();
    }

    // Every recipe draws a block and names a seed; hydroponic-only ones have no soil; the soils all look like something.
    static void recipes(GameTestHelper helper) {
        var level = helper.getLevel();
        List<RecipeHolder<ClocheRecipe>> all = level.recipeAccess().recipeMap().byType(ModRecipes.CLOCHE.get()).stream().toList();
        helper.assertTrue(all.size() == 28, all.size() + " cloche recipes, not 28");
        for (RecipeHolder<ClocheRecipe> holder : all) {
            ClocheRecipe recipe = holder.value();
            helper.assertTrue(recipe.seed().items().findAny().isPresent(), holder.id() + " has no seed");
            helper.assertTrue(!recipe.hydroponicOnly() || recipe.soil().isEmpty(), holder.id() + " is hydroponic-only with a soil");
            ItemStack seed = new ItemStack(recipe.seed().items().findFirst().get(), 1);
            ClochePlants.Plant plant = ClochePlants.find(level, seed, ItemStack.EMPTY, true);
            helper.assertTrue(plant != null && plant.stateAt(0.5F) != null && !plant.stateAt(0.5F).isAir(), holder.id() + " draws nothing");
        }
        for (var item : List.of(Items.DIRT, Items.GRASS_BLOCK, ModItems.LOAM.get(), Items.SAND, Items.SOUL_SAND)) {
            ClocheSoil soil = ClocheSoil.of(new ItemStack(item));
            helper.assertTrue(soil != null && !soil.renderState().isAir(), item + " isn't a soil");
        }
        helper.assertTrue(ClocheSoil.of(new ItemStack(ModItems.LOAM.get())).growth() > 1.0F, "Loam isn't faster");
        helper.assertTrue(MachineRecipes.cloche(level, new ItemStack(Items.NETHER_WART), new ItemStack(Items.SOUL_SAND), false).isPresent(),
                "Nether wart doesn't grow in soul sand");
        helper.assertTrue(MachineRecipes.cloche(level, new ItemStack(Items.CACTUS), new ItemStack(Items.DIRT), false).isEmpty(), "Cactus grows in dirt");
        helper.assertTrue(MachineRecipes.cloche(level, new ItemStack(ModItems.HOP_SEEDS.get()), new ItemStack(Items.DIRT), false).isEmpty(),
                "Hops grow outside the Hydroponic Cell");
        var flowers = MachineRecipes.cloche(level, new ItemStack(Items.POPPY), ItemStack.EMPTY, true);
        helper.assertTrue(flowers.isPresent() && flowers.get().value().seedOutput() == 2, "A poppy doesn't give 2 poppies");
        helper.succeed();
    }
}
