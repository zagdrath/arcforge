/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */
package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.blockentity.machine.ChemicalReactorBlockEntity;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;

public final class GasVentGameTests {
    private GasVentGameTests() {}

    static void overflowAndSettings(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.CHEMICAL_REACTOR.get());
        var machine = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
        var tank = machine.getOutputTank();
        var gas = FluidResource.of(ModFluids.HYDROGEN.get());
        int capacity = tank.getCapacity();
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(tank.insert(0, gas, capacity + 100, tx) == capacity, "Closed vent discarded gas");
            tx.commit();
        }
        machine.gasVents.toggle(0);
        try (var tx = Transaction.openRoot()) {
            helper.assertTrue(tank.insert(0, gas, 100, tx) == 100, "Open vent blocked excess gas");
            tx.commit();
        }
        helper.assertTrue(tank.getAmount() == capacity, "Venting changed stored gas");
        helper.assertFalse(machine.getByproductTank().canVent(gas), "Vent changed another output");
        helper.assertFalse(tank.canVent(FluidResource.of(Fluids.WATER)), "Vent accepts liquid");
        var saved = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
        helper.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR);
        helper.setBlock(pos, ModBlocks.CHEMICAL_REACTOR.get());
        var restored = helper.getBlockEntity(pos, ChemicalReactorBlockEntity.class);
        restored.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        helper.assertTrue(restored.gasVents.isEnabled(0) && restored.getOutputTank().canVent(gas), "Vent setting did not persist");
        // A real refinery batch with a full unwanted gas output must complete only after its vent is enabled.
        BlockPos reformerPos = new BlockPos(3, 1, 1);
        helper.setBlock(reformerPos, ModBlocks.STEAM_METHANE_REFORMER.get());
        var reformer = helper.getBlockEntity(reformerPos, net.zagdrath.arcforge.blockentity.machine.RefiningMachineBlockEntity.class);
        var core = reformer.getCore();
        var recipe = net.zagdrath.arcforge.recipe.MachineRecipes.refining(helper.getLevel(),
                net.zagdrath.arcforge.recipe.RefiningProcess.STEAM_REFORMING,
                new net.zagdrath.arcforge.recipe.RefiningRecipe.Input(FluidResource.of(ModFluids.NATURAL_GAS.get()), 16000)).orElseThrow().value();
        try (var tx = Transaction.openRoot()) {
            core.feed().insert(0, FluidResource.of(ModFluids.NATURAL_GAS.get()), recipe.input().amount(), tx);
            core.secondFeed().insert(0, FluidResource.of(ModFluids.STEAM.get()), recipe.secondInput().orElseThrow().amount(), tx);
            core.product(1).insert(0, FluidResource.of(ModFluids.CARBON_DIOXIDE.get()), core.product(1).getCapacity(), tx);
            tx.commit();
        }
        core.heat().add(core.heat().storedAt(recipe.minTemp() + 100));
        for (int i = 0; i < recipe.ticks() + 2; i++) core.tick(helper.getLevel(), true, 0, 0);
        helper.assertTrue(core.product(0).getAmount() == 0, "Closed vent did not block batch");
        reformer.gasVents.toggle(1);
        for (int i = 0; i < recipe.ticks() + 2; i++) core.tick(helper.getLevel(), true, 0, 0);
        helper.assertTrue(core.product(0).getAmount() == recipe.fluids().get(0).amount(), "Open vent did not let batch produce hydrogen");
        helper.assertTrue(core.product(1).getAmount() == core.product(1).getCapacity(), "Vent drained stored carbon dioxide");
        helper.succeed();
    }
}

