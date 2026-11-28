/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.item.farming.FertilizerItem;
import net.zagdrath.arcforge.recipe.ChemicalReactingRecipe;
import net.zagdrath.arcforge.recipe.ChemicalReactorInput;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.SiftingRecipe;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModMinerals;
import net.zagdrath.arcforge.registry.ModStones;

// Stone chemistry (ModMinerals), one test a chain: Solvay, carbide to acetylene, lime scrubbing, Pidgeon magnesium,
// mineral carbonation and Kimberlite sifting. Recipe lookups, as SaltGameTests: the machines themselves have their own tests.
public final class StoneChemistryGameTests {
    private StoneChemistryGameTests() {}

    private static ItemStack stone(ModStones.Stone stone, boolean cobbled) {
        var family = ModStones.family(stone);
        return new ItemStack((cobbled ? family.cobbled() : family.natural()).block().get());
    }

    private static TagKey<Item> tag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(namespace, path));
    }

    private static boolean gives(java.util.Optional<net.minecraft.world.item.ItemStackTemplate> out, Item item, int count) {
        return out.map(o -> { ItemStack s = o.create(); return s.is(item) && s.getCount() == count; }).orElse(false);
    }

    private static boolean givesFluid(ChemicalReactingRecipe recipe, Fluid fluid, int amount) {
        return recipe.fluidOutput().map(o -> o.fluid().value() == fluid && o.amount() == amount).orElse(false);
    }

    // Limestone, Travertine and Dolomite with Brine and Ammonia make 2 Soda Ash and 1 Calcium Chloride, heated (200 °C);
    // Soda Ash fluxes sand into glass and dissolves into Lye; the Dolomite still calcines on its own without the fluids.
    static void solvay(GameTestHelper helper) {
        var level = helper.getLevel();
        for (ModStones.Stone s : new ModStones.Stone[] { ModStones.Stone.LIMESTONE, ModStones.Stone.TRAVERTINE, ModStones.Stone.DOLOMITE }) {
            var solvay = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(stone(s, false),
                    FluidResource.of(ModFluids.BRINE.get()), 1_000, FluidResource.of(ModFluids.AMMONIA.get()), 1_000));
            helper.assertTrue(solvay.isPresent(), s.id() + ", Brine and Ammonia don't react");
            var r = solvay.get().value();
            helper.assertTrue(gives(r.itemOutput(), ModMinerals.SODA_ASH.get(), 2) && gives(r.byproduct(), ModMinerals.CALCIUM_CHLORIDE.get(), 1),
                    s.id() + " doesn't give 2 Soda Ash and Calcium Chloride");
            helper.assertTrue(r.minTemperature() == 200 && r.heatPerTick() == 10, "Solvay isn't 10 HU/t at 200 °C");
        }
        var glass = MachineRecipes.arcforgeSmelting(level, new ItemStack(Items.SAND, 4), new ItemStack(ModMinerals.SODA_ASH.get()));
        helper.assertTrue(glass.isPresent() && glass.get().value().result().create().is(Items.GLASS)
                && glass.get().value().result().create().getCount() == 6 && glass.get().value().coke() == 0, "4 sand and Soda Ash don't make 6 glass");
        var lye = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(new ItemStack(ModMinerals.SODA_ASH.get()),
                FluidResource.of(Fluids.WATER), 1_000, FluidResource.EMPTY, 0));
        helper.assertTrue(lye.isPresent() && givesFluid(lye.get().value(), ModFluids.LYE.get(), 500), "Soda Ash and water don't make Lye");
        helper.assertTrue(new ItemStack(ModMinerals.SODA_ASH.get()).is(tag("c", "dusts/soda_ash")),
                "Soda Ash isn't #c:dusts/soda_ash");
        helper.succeed();
    }

    // Quicklime and Coke fuse into Calcium Carbide at the furnace's hottest; Carbide and water give Acetylene and Slaked
    // Lime; Acetylene burns hotter and richer than Hydrogen.
    static void carbideAcetylene(GameTestHelper helper) {
        var level = helper.getLevel();
        var carbide = MachineRecipes.arcforgeSmelting(level, new ItemStack(ModItems.QUICKLIME.get(), 2), ItemStack.EMPTY);
        helper.assertTrue(carbide.isPresent() && carbide.get().value().result().create().is(ModMinerals.CALCIUM_CARBIDE.get()),
                "Quicklime doesn't fuse into Calcium Carbide");
        helper.assertTrue(carbide.get().value().minHeat() >= 1_500 && carbide.get().value().minHeat() <= ArcforgeConfig.FURNACE_MAX_HEAT.getAsInt(),
                "Calcium Carbide isn't the furnace's hottest job: " + carbide.get().value().minHeat());
        var acetylene = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(new ItemStack(ModMinerals.CALCIUM_CARBIDE.get()),
                FluidResource.of(Fluids.WATER), 1_000, FluidResource.EMPTY, 0));
        helper.assertTrue(acetylene.isPresent() && givesFluid(acetylene.get().value(), ModFluids.ACETYLENE.get(), 500)
                && gives(acetylene.get().value().byproduct(), ModMinerals.SLAKED_LIME.get(), 1), "Carbide and water don't give Acetylene and Slaked Lime");
        BurnerFuel fuel = BurnerFuel.of(ModFluids.ACETYLENE.get());
        BurnerFuel hydrogen = BurnerFuel.of(ModFluids.HYDROGEN.get());
        helper.assertTrue(fuel != null && hydrogen != null, "Acetylene isn't a burner fuel");
        helper.assertTrue(fuel.huPerMb() == ArcforgeConfig.ACETYLENE_HU_PER_MB.getAsInt() && fuel.huPerMb() > hydrogen.huPerMb()
                && fuel.burnTemperature(10_000) > hydrogen.burnTemperature(10_000), "Acetylene doesn't burn above Hydrogen");
        helper.assertTrue(ModFluids.ACETYLENE_TYPE.get().isLighterThanAir(), "Acetylene isn't a gas");
        helper.succeed();
    }

    // Quicklime slakes; Slaked Lime scrubs Sulfur Dioxide into Gypsum, a fertilizer; Gypsum sets into Plaster.
    static void scrubbing(GameTestHelper helper) {
        var level = helper.getLevel();
        var slaked = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(new ItemStack(ModItems.QUICKLIME.get()),
                FluidResource.of(Fluids.WATER), 1_000, FluidResource.EMPTY, 0));
        helper.assertTrue(slaked.isPresent() && gives(slaked.get().value().itemOutput(), ModMinerals.SLAKED_LIME.get(), 1), "Quicklime doesn't slake");
        var gypsum = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(new ItemStack(ModMinerals.SLAKED_LIME.get()),
                FluidResource.of(ModFluids.SULFUR_DIOXIDE.get()), 1_000, FluidResource.EMPTY, 0));
        helper.assertTrue(gypsum.isPresent() && gives(gypsum.get().value().itemOutput(), ModMinerals.GYPSUM.get(), 1)
                && gypsum.get().value().fluidInputs().get(0).amount() == 250, "Slaked Lime doesn't scrub 250 mB Sulfur Dioxide into Gypsum");
        helper.assertTrue(ModMinerals.GYPSUM.get().nutrients() == ArcforgeConfig.GYPSUM_NUTRIENTS.getAsInt()
                && ModMinerals.VOLCANIC_ASH.get().nutrients() == ArcforgeConfig.VOLCANIC_ASH_NUTRIENTS.getAsInt()
                && ModMinerals.AGRICULTURAL_LIME.get() instanceof FertilizerItem, "The stone fertilizers don't follow their config");
        var ash = MachineRecipes.crushing(level, stone(ModStones.Stone.SCORIA, true));
        helper.assertTrue(ash.isPresent() && gives(ash.get().value().result(), ModMinerals.VOLCANIC_ASH.get(), 2)
                && ash.get().value().bonus().map(b -> b.create().is(ModItems.IRON_DUST.get())).orElse(false), "Scoria doesn't crush to Volcanic Ash (and Iron)");
        var aglime = MachineRecipes.crushing(level, stone(ModStones.Stone.LIMESTONE, false));
        helper.assertTrue(aglime.isPresent() && gives(aglime.get().value().result(), ModMinerals.AGRICULTURAL_LIME.get(), 2),
                "Limestone doesn't crush to Agricultural Lime");
        helper.succeed();
    }

    // Dolomite calcines to Magnesia and one Quicklime (and no longer by the general recipe); Magnesia and Ferrosilicon
    // make Magnesium in the Arcforge Furnace.
    static void pidgeon(GameTestHelper helper) {
        var level = helper.getLevel();
        var dolomite = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(stone(ModStones.Stone.DOLOMITE, true),
                FluidResource.EMPTY, 0, FluidResource.EMPTY, 0));
        helper.assertTrue(dolomite.isPresent() && gives(dolomite.get().value().itemOutput(), ModMinerals.MAGNESIA.get(), 2)
                && gives(dolomite.get().value().byproduct(), ModItems.QUICKLIME.get(), 1)
                && givesFluid(dolomite.get().value(), ModFluids.CARBON_DIOXIDE.get(), 250), "Dolomite doesn't calcine to Magnesia, Quicklime and CO2");
        var limestone = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(stone(ModStones.Stone.LIMESTONE, true),
                FluidResource.EMPTY, 0, FluidResource.EMPTY, 0));
        helper.assertTrue(limestone.isPresent() && gives(limestone.get().value().itemOutput(), ModItems.QUICKLIME.get(), 2),
                "Limestone doesn't calcine to 2 Quicklime");
        var magnesium = MachineRecipes.arcforgeSmelting(level, new ItemStack(ModMinerals.MAGNESIA.get(), 2), new ItemStack(ModItems.FERROSILICON.get()));
        helper.assertTrue(magnesium.isPresent() && magnesium.get().value().result().create().is(ModMinerals.MAGNESIUM_INGOT.get())
                && magnesium.get().value().result().create().getCount() == 2, "Magnesia and Ferrosilicon don't make 2 Magnesium");
        helper.assertTrue(new ItemStack(ModMinerals.MAGNESIUM_INGOT.get()).is(tag("c", "ingots/magnesium")),
                "Magnesium isn't #c:ingots/magnesium");
        helper.succeed();
    }

    // Serpentine and Olivine lock CO2 into Magnesite (heated, 150 °C); Magnesite calcines back to Magnesia and CO2.
    static void carbonation(GameTestHelper helper) {
        var level = helper.getLevel();
        for (ItemStack feed : new ItemStack[] { stone(ModStones.Stone.SERPENTINE, false), stone(ModStones.Stone.SERPENTINE, true),
                new ItemStack(ModMinerals.OLIVINE.get()) }) {
            var r = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(feed,
                    FluidResource.of(ModFluids.CARBON_DIOXIDE.get()), 1_000, FluidResource.EMPTY, 0));
            helper.assertTrue(r.isPresent() && gives(r.get().value().itemOutput(), ModMinerals.MAGNESITE.get(), 2)
                    && r.get().value().fluidInputs().get(0).amount() == 500 && r.get().value().minTemperature() == 150,
                    feed + " doesn't lock 500 mB CO2 into 2 Magnesite");
        }
        var back = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(new ItemStack(ModMinerals.MAGNESITE.get()),
                FluidResource.EMPTY, 0, FluidResource.EMPTY, 0));
        helper.assertTrue(back.isPresent() && gives(back.get().value().itemOutput(), ModMinerals.MAGNESIA.get(), 1)
                && givesFluid(back.get().value(), ModFluids.CARBON_DIOXIDE.get(), 250) && back.get().value().minTemperature() == 700,
                "Magnesite doesn't calcine to Magnesia at 700 °C");
        helper.succeed();
    }

    private static float chance(SiftingRecipe recipe, Item item) {
        return (float) recipe.outputs().stream().filter(o -> o.item().create().is(item)).mapToDouble(SiftingRecipe.Output::chance).sum();
    }

    // Cobbled Kimberlite keeps its four and gains Garnet and Olivine (six, the Sifter's slots); Slate gives Mica and
    // Serpentine Chromite.
    static void kimberliteSifting(GameTestHelper helper) {
        var level = helper.getLevel();
        var kimberlite = MachineRecipes.sifting(level, stone(ModStones.Stone.KIMBERLITE, true)).orElseThrow().value();
        helper.assertTrue(kimberlite.outputs().size() == 6, "Cobbled Kimberlite sifts " + kimberlite.outputs().size() + " outputs");
        helper.assertTrue(Math.abs(chance(kimberlite, Items.DIAMOND) - 0.03F) < 1e-4 && Math.abs(chance(kimberlite, ModMinerals.GARNET.get()) - 0.20F) < 1e-4
                && Math.abs(chance(kimberlite, ModMinerals.OLIVINE.get()) - 0.25F) < 1e-4, "Kimberlite's Diamond, Garnet or Olivine chance is off");
        var slate = MachineRecipes.sifting(level, stone(ModStones.Stone.SLATE, true)).orElseThrow().value();
        helper.assertTrue(Math.abs(chance(slate, ModMinerals.MICA.get()) - 0.30F) < 1e-4, "Cobbled Slate doesn't sift Mica");
        var serpentine = MachineRecipes.sifting(level, stone(ModStones.Stone.SERPENTINE, true)).orElseThrow().value();
        helper.assertTrue(Math.abs(chance(serpentine, ModMinerals.CHROMITE.get()) - 0.12F) < 1e-4, "Cobbled Serpentine doesn't sift Chromite");
        helper.assertTrue(new ItemStack(ModMinerals.GARNET.get()).is(tag("c", "gems/garnet"))
                && new ItemStack(ModMinerals.MICA.get()).is(tag("arcforge", "insulators")),
                "Garnet or Mica is missing its tag");
        var garnetMesh = net.zagdrath.arcforge.item.tool.SifterMeshItem.tierOf(new ItemStack(ModItems.GARNET_MESH.get()));
        helper.assertTrue(garnetMesh == net.zagdrath.arcforge.item.tool.SifterMeshItem.Tier.GARNET && garnetMesh.durability() == 768
                && garnetMesh.chanceMultiplier() == ArcforgeConfig.SIFTER_GARNET_CHANCE.getAsDouble()
                && garnetMesh.speedMultiplier() == ArcforgeConfig.SIFTER_GARNET_SPEED.getAsDouble(), "The Garnet Mesh isn't the Garnet tier");
        helper.succeed();
    }
}
