/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.FermenterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// The Fermenter and Ethanol.
public final class FermenterGameTests {
    private static final FluidResource WATER = FluidResource.of(Fluids.WATER);
    private static final FluidResource ETHANOL = FluidResource.of(ModFluids.ETHANOL.get());

    private FermenterGameTests() {}

    // 10 wheat and 1,000 mB water make 500 mB Ethanol, using all the water, with 0-10 bone meal. (Four Speed
    // upgrades make each batch 50 ticks; the FE per batch stays 2,000.)
    static void makesEthanol(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.FERMENTER.get());
        FermenterBlockEntity fermenter = helper.getBlockEntity(pos, FermenterBlockEntity.class);
        CrushingGameTests.charge(fermenter.getEnergy(), 20_000);
        SteamGameTests.fill(fermenter.getInteractionFluidHandler(), WATER, 1_000);
        fermenter.getItems().setStack(FermenterBlockEntity.SLOT_INPUT, new ItemStack(Items.WHEAT, 10));
        int first = fermenter.getItems().getFirstUpgradeSlot();
        fermenter.getItems().setStack(first, new ItemStack(ModItems.SPEED_UPGRADE.get(), 4));
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> helper.assertTrue(fermenter.getStatus() == MachineStatus.FERMENTING, "The fermenter is " + fermenter.getStatus()))
                .thenWaitUntil(() -> helper.assertTrue(fermenter.getItems().getStack(FermenterBlockEntity.SLOT_INPUT).isEmpty(), "Wheat left"))
                .thenExecute(() -> {
                    helper.assertTrue(fermenter.getEthanol().getAmount() == 500, "Made " + fermenter.getEthanol().getAmount() + " mB Ethanol, not 500");
                    helper.assertTrue(fermenter.getWater().getAmount() == 0, fermenter.getWater().getAmount() + " mB water left");
                    int boneMeal = fermenter.getItems().getStack(FermenterBlockEntity.SLOT_BYPRODUCT).getCount();
                    helper.assertTrue(boneMeal >= 0 && boneMeal <= 10, boneMeal + " bone meal");
                })
                .thenSucceed();
    }

    // Ethanol burns in a Fuel Burner at 300 HU/mB and 0.5 mB/t (150 HU/t), up to 900°C.
    static void burnsInFuelBurner(GameTestHelper helper) {
        BurnerFuel fuel = BurnerFuel.of(ModFluids.ETHANOL.get());
        helper.assertTrue(fuel != null && fuel.burnTemperature(1_400) == 900, "Ethanol doesn't burn at 900°C");
        helper.assertTrue(fuel.gasTurbine(), "Ethanol isn't a Gas Turbine fuel");
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.FUEL_BURNER.get());
        FuelBurnerBlockEntity burner = helper.getBlockEntity(pos, FuelBurnerBlockEntity.class);
        SteamGameTests.fill(burner.getInteractionFluidHandler(), ETHANOL, 1_000);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(burner.getStatus() == MachineStatus.BURNING && burner.getHeatPerTick() == 150,
                        "An ethanol burner is " + burner.getStatus() + " making " + burner.getHeatPerTick() + " HU/t"))
                .thenSucceed();
    }

    // --- Slimeballs ---

    // 2 Pine Resin and a Kelp craft into a Slimeball (shapeless), and 250 mB of Latex and a Lime Dye react into 2 in the
    // Chemical Reactor.
    static void slimeBallRecipes(GameTestHelper helper) {
        var level = helper.getLevel();
        CraftingInput grid = CraftingInput.of(3, 1, List.of(new ItemStack(Items.KELP), new ItemStack(ModItems.PINE_RESIN.get()),
                new ItemStack(ModItems.PINE_RESIN.get())));
        var crafted = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid, level);
        helper.assertTrue(crafted.isPresent() && crafted.get().value().assemble(grid).is(Items.SLIME_BALL), "Pine Resin and Kelp don't craft a Slimeball");
        CraftingInput oneResin = CraftingInput.of(2, 1, List.of(new ItemStack(Items.KELP), new ItemStack(ModItems.PINE_RESIN.get())));
        helper.assertTrue(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, oneResin, level).isEmpty(), "One Pine Resin is enough");

        RecipeHolder<?> holder = level.recipeAccess().recipeMap().byKey(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(Arcforge.MODID, "chemical_reacting/slime_ball_from_latex")));
        helper.assertTrue(holder != null && holder.value() instanceof ChemicalReactingRecipe, "No Chemical Reactor Slimeball recipe");
        ChemicalReactingRecipe reacting = (ChemicalReactingRecipe) holder.value();
        helper.assertTrue(reacting.itemInput().map(input -> input.test(new ItemStack(Items.LIME_DYE))).orElse(false), "It doesn't take Lime Dye");
        helper.assertTrue(reacting.fluidInputs().size() == 1 && reacting.fluidInputs().getFirst().test(FluidResource.of(ModFluids.LATEX.get()), 250),
                "It doesn't take 250 mB of Latex");
        helper.assertTrue(reacting.itemOutput().map(out -> out.create().is(Items.SLIME_BALL) && out.create().getCount() == 2).orElse(false),
                "It doesn't make 2 Slimeballs");
        helper.succeed();
    }

    // With a Slimeball starter, Sugar and Kelp (either way round) and water grow Slimeballs into the output slot: the
    // starter stays and no Ethanol or Carbon Dioxide is made. Slower than
    // Ethanol. (Eight Speed upgrades keep the test short; one batch is what the FE buffer holds at that rate.)
    static void growsSlimeBalls(GameTestHelper helper) {
        var culture = MachineRecipes.culturing(helper.getLevel(), new ItemStack(Items.KELP), new ItemStack(Items.SUGAR), new ItemStack(Items.SLIME_BALL));
        helper.assertTrue(culture.isPresent(), "Kelp, Sugar and a Slimeball make no culture");
        int wheatTime = MachineRecipes.fermenting(helper.getLevel(), new ItemStack(Items.WHEAT)).orElseThrow().value().time();
        helper.assertTrue(culture.get().value().time() > wheatTime, "A culture is no slower than fermenting Ethanol");

        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.FERMENTER.get());
        FermenterBlockEntity fermenter = helper.getBlockEntity(pos, FermenterBlockEntity.class);
        CrushingGameTests.charge(fermenter.getEnergy(), 100_000);
        SteamGameTests.fill(fermenter.getInteractionFluidHandler(), WATER, 1_000);
        var items = fermenter.getItems();
        helper.assertTrue(items.isValid(FermenterBlockEntity.SLOT_INPUT_2, net.neoforged.neoforge.transfer.item.ItemResource.of(Items.SUGAR)),
                "The second input slot refuses Sugar");
        helper.assertFalse(items.isValid(FermenterBlockEntity.SLOT_INPUT_2, net.neoforged.neoforge.transfer.item.ItemResource.of(Items.WHEAT)),
                "The second input slot takes Wheat");
        helper.assertTrue(items.isValid(FermenterBlockEntity.SLOT_ADDITIVE, net.neoforged.neoforge.transfer.item.ItemResource.of(Items.SLIME_BALL)),
                "The additive slot refuses a Slimeball starter");
        items.setStack(FermenterBlockEntity.SLOT_INPUT, new ItemStack(Items.KELP));
        items.setStack(FermenterBlockEntity.SLOT_INPUT_2, new ItemStack(Items.SUGAR));
        items.setStack(FermenterBlockEntity.SLOT_ADDITIVE, new ItemStack(Items.SLIME_BALL));
        items.setStack(items.getFirstUpgradeSlot(), new ItemStack(ModItems.SPEED_UPGRADE.get(), 8));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(items.getStack(FermenterBlockEntity.SLOT_INPUT).isEmpty(), "Kelp left: " + fermenter.getStatus()
                        + ", " + fermenter.getEnergy().getAmountAsInt() + " FE, progress " + fermenter.getProgress() + "/" + fermenter.getTotal() + ", out " + items.getStack(FermenterBlockEntity.SLOT_BYPRODUCT)))
                .thenExecute(() -> {
                    ItemStack grown = items.getStack(FermenterBlockEntity.SLOT_BYPRODUCT);
                    helper.assertTrue(grown.is(Items.SLIME_BALL) && grown.getCount() == 1, "Grew " + grown + ", not a Slimeball");
                    helper.assertTrue(items.getStack(FermenterBlockEntity.SLOT_INPUT_2).isEmpty(), "Sugar left");
                    helper.assertTrue(items.getStack(FermenterBlockEntity.SLOT_ADDITIVE).is(Items.SLIME_BALL)
                            && items.getStack(FermenterBlockEntity.SLOT_ADDITIVE).getCount() == 1, "The starter was used up");
                    helper.assertTrue(fermenter.getEthanol().getAmount() == 0, "A culture made Ethanol");
                    helper.assertTrue(fermenter.getCarbonDioxide().getAmount() == 0, "A culture made Carbon Dioxide");
                    helper.assertTrue(fermenter.getWater().getAmount() == 1_000 - culture.get().value().fluidInput().amount(),
                            fermenter.getWater().getAmount() + " mB of water left");
                })
                .thenSucceed();
    }

    // A Fermenter saved before the second input slot (layout 2: input, byproduct, additive, upgrades) loads with its
    // upgrades moved up past the new slot, which is empty.
    static void secondInputMigration(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.FERMENTER.get());
        FermenterBlockEntity fermenter = helper.getBlockEntity(pos, FermenterBlockEntity.class);
        var level = helper.getLevel();
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(() -> "secondInputMigration", Arcforge.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            ItemStacksResourceHandler old = new ItemStacksResourceHandler(7);
            ItemStack[] stacks = { new ItemStack(Items.WHEAT, 5), new ItemStack(Items.BONE_MEAL, 2), new ItemStack(ModItems.DRIED_HOPS.get(), 3),
                    new ItemStack(ModItems.SPEED_UPGRADE.get(), 4) };
            for (int i = 0; i < stacks.length; i++) {
                old.set(i, net.neoforged.neoforge.transfer.item.ItemResource.of(stacks[i]), stacks[i].getCount());
            }
            old.serialize(output.child("items"));
            output.putInt("slot_layout", 2);
            fermenter.loadCustomOnly(TagValueInput.create(reporter, level.registryAccess(), output.buildResult()));
        }
        var items = fermenter.getItems();
        helper.assertTrue(items.getStack(FermenterBlockEntity.SLOT_INPUT).is(Items.WHEAT), "The crop moved");
        helper.assertTrue(items.getStack(FermenterBlockEntity.SLOT_BYPRODUCT).is(Items.BONE_MEAL), "The output moved");
        helper.assertTrue(items.getStack(FermenterBlockEntity.SLOT_ADDITIVE).is(ModItems.DRIED_HOPS.get()), "The additive moved");
        helper.assertTrue(items.getStack(FermenterBlockEntity.SLOT_INPUT_2).isEmpty(), "The new second input slot isn't empty");
        helper.assertTrue(fermenter.upgrades(net.zagdrath.arcforge.upgrade.UpgradeType.SPEED) == 4, "The Speed Upgrades were lost");
        helper.succeed();
    }
}
