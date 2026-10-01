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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ElectrolyzerBlockEntity;
import net.zagdrath.arcforge.chemistry.OreSlurry;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.ChemicalReactorInput;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// Salt and chlor-alkali: the recipes; Brine from Salt and water; the Electrolyzer splitting Brine into Hydrogen, Chlorine
// and Lye; Hydrochloric Acid from Chlorine and Hydrogen, and leaching with it; the Lye biodiesel; and Salt's tags. The
// Thermal Evaporator Array has its own tests (ThermalEvaporatorGameTests), as do Halite and Seawater.
public final class SaltGameTests {
    private static int fill(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    // The recipes: the Thermal Evaporator boils Seawater into Brine and Brine into Salt, returning water; Salt + water
    // make Brine; Brine splits into three; Chlorine + Hydrogen make Hydrochloric Acid, which leaches like Sulfuric Acid;
    // Seed Oil, Ethanol and Lye pick the Lye biodiesel (150 mB).
    static void recipes(GameTestHelper helper) {
        var level = helper.getLevel();
        var seawater = MachineRecipes.evaporating(level, FluidResource.of(ModFluids.SEAWATER.get()));
        helper.assertTrue(seawater.isPresent() && seawater.get().value().fluidResult().map(out -> out.fluid().value() == ModFluids.BRINE.get()).orElse(false)
                && seawater.get().value().water() > 0, "Seawater doesn't boil down to Brine and water");
        var toSalt = MachineRecipes.evaporating(level, FluidResource.of(ModFluids.BRINE.get()));
        helper.assertTrue(toSalt.isPresent() && toSalt.get().value().itemResult().map(out -> out.create().is(ModItems.SALT.get())).orElse(false)
                && toSalt.get().value().water() > 0, "Brine doesn't boil down to Salt and water");
        helper.assertTrue(MachineRecipes.evaporating(level, FluidResource.of(Fluids.WATER)).isEmpty(), "Plain water evaporates");
        var brine = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(new ItemStack(ModItems.SALT.get()),
                FluidResource.of(Fluids.WATER), 1_000, FluidResource.EMPTY, 0));
        helper.assertTrue(brine.isPresent() && brine.get().value().fluidOutput().map(out -> out.fluid().value() == ModFluids.BRINE.get()).orElse(false),
                "Salt and water don't make Brine");
        var split = MachineRecipes.electrolyzing(level, FluidResource.of(ModFluids.BRINE.get()));
        helper.assertTrue(split.isPresent() && split.get().value().tertiary().map(out -> out.fluid().value() == ModFluids.LYE.get()).orElse(false),
                "Brine doesn't split to Lye");
        var hcl = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(ItemStack.EMPTY,
                FluidResource.of(ModFluids.CHLORINE.get()), 1_000, FluidResource.of(ModFluids.HYDROGEN.get()), 1_000));
        helper.assertTrue(hcl.isPresent() && hcl.get().value().fluidOutput().map(out -> out.fluid().value() == ModFluids.HYDROCHLORIC_ACID.get()).orElse(false),
                "Chlorine and Hydrogen don't make Hydrochloric Acid");
        var leach = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(new ItemStack(Items.RAW_IRON),
                FluidResource.of(ModFluids.HYDROCHLORIC_ACID.get()), 1_000, FluidResource.EMPTY, 0));
        helper.assertTrue(leach.isPresent() && "leaching".equals(leach.get().value().category()), "Hydrochloric Acid doesn't leach raw iron");
        var lye = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(ItemStack.EMPTY, ItemStack.EMPTY,
                FluidResource.of(ModFluids.SEED_OIL.get()), 1_000, FluidResource.of(ModFluids.ETHANOL.get()), 1_000,
                FluidResource.of(ModFluids.LYE.get()), 1_000));
        helper.assertTrue(lye.isPresent() && lye.get().value().fluidInputs().size() == 3
                && lye.get().value().fluidOutput().map(out -> out.amount() == 150).orElse(false), "Lye doesn't pick the 150 mB biodiesel");
        var plain = MachineRecipes.chemicalReacting(level, new ChemicalReactorInput(ItemStack.EMPTY,
                FluidResource.of(ModFluids.SEED_OIL.get()), 1_000, FluidResource.of(ModFluids.ETHANOL.get()), 1_000));
        helper.assertTrue(plain.isPresent() && plain.get().value().fluidOutput().map(out -> out.amount() == 100).orElse(false),
                "The plain biodiesel is gone");
        helper.succeed();
    }

    // 1 Salt + 250 mB water make 250 mB Brine in the reactor.
    static void reactorMakesBrine(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
        CrushingGameTests.charge(reactor.getEnergy(), 40_000);
        helper.assertTrue(CrushingGameTests.insert(reactor.getItemHandler(Direction.UP), ModItems.SALT.get(), 1) == 1, "It refused Salt");
        fill(reactor.getRouter(), Fluids.WATER, 250);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(reactor.getOutputTank().contains(ModFluids.BRINE.get())
                        && reactor.getOutputTank().getAmount() == 250, "No Brine yet"))
                .thenSucceed();
    }

    // 1,000 mB Brine split into 500 mB Hydrogen, 500 mB Chlorine and 1,000 mB Lye, each in its own tank.
    static void electrolyzerSplitsBrine(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.ELECTROLYZER.get());
        ElectrolyzerBlockEntity machine = helper.getBlockEntity(pos, ElectrolyzerBlockEntity.class);
        CrushingGameTests.install(machine.getItems(), ModItems.SPEED_UPGRADE.get(), 8);
        helper.assertTrue(fill(machine.getFluidHandler(Direction.UP), ModFluids.BRINE.get(), 1_000) == 1_000, "It refused Brine");
        helper.onEachTick(() -> CrushingGameTests.charge(machine.getEnergy(), machine.getEnergy().getCapacityAsInt()));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(machine.getWater().getAmount() == 0 && machine.getStatus() == MachineStatus.IDLE,
                        "Still " + machine.getWater().getAmount() + " mB of Brine"))
                .thenExecute(() -> {
                    helper.assertTrue(machine.getHydrogen().contains(ModFluids.HYDROGEN.get()) && machine.getHydrogen().getAmount() == 500,
                            "Hydrogen tank holds " + machine.getHydrogen().getAmount() + " mB");
                    helper.assertTrue(machine.getOxygen().contains(ModFluids.CHLORINE.get()) && machine.getOxygen().getAmount() == 500,
                            "Second tank holds " + machine.getOxygen().getAmount() + " mB of " + machine.getOxygen().getResource(0));
                    helper.assertTrue(machine.getLiquid().contains(ModFluids.LYE.get()) && machine.getLiquid().getAmount() == 1_000,
                            "Liquid tank holds " + machine.getLiquid().getAmount() + " mB");
                })
                .thenSucceed();
    }

    // 1 raw iron + 250 mB Hydrochloric Acid make 300 mB Iron Slurry, as Sulfuric Acid does.
    static void hydrochloricAcidLeaches(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.CHEMICAL_REACTOR.get());
        ChemicalReactorBlockEntity reactor = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
        CrushingGameTests.charge(reactor.getEnergy(), 40_000);
        CrushingGameTests.insert(reactor.getItemHandler(null), Items.RAW_IRON, 1);
        helper.assertTrue(fill(reactor.getRouter(), ModFluids.HYDROCHLORIC_ACID.get(), 250) == 250, "It refused Hydrochloric Acid");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(reactor.getOutputTank().contains(OreSlurry.IRON.fluid())
                        && reactor.getOutputTank().getAmount() == 300, "No Iron Slurry yet"))
                .thenSucceed();
    }

    // Salt is #c:dusts/salt (what the Brine recipe takes) and the Salt Block #c:storage_blocks/salt.
    static void saltTags(GameTestHelper helper) {
        var dusts = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "dusts/salt"));
        var blocks = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                net.minecraft.resources.Identifier.fromNamespaceAndPath("c", "storage_blocks/salt"));
        helper.assertTrue(new ItemStack(ModItems.SALT.get()).is(dusts), "Salt isn't #c:dusts/salt");
        helper.assertTrue(new ItemStack(ModItems.SALT_BLOCK.get()).is(blocks), "The Salt Block isn't #c:storage_blocks/salt");
        helper.succeed();
    }
}
