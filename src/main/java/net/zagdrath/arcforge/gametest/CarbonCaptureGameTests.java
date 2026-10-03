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
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.blockentity.machine.BurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.CarbonReclaimerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FischerTropschReactorBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GasifierBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.heat.EnergyBalance;
import net.zagdrath.arcforge.heat.FlueGas;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.recipe.FischerTropschRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// Carbon capture: Syngas as a fuel and the carbon-fuel tags; the Gasifier and Fischer-Tropsch recipes (the oils hold
// less heat than the Syngas they came from); the Carbon Reclaimer always costing more FE than its Carbon Dust gives back,
// even fully upgraded; flue gas from a Firebox with a Flue Gas face (none without one, none from Hydrogen); the Gasifier
// making Syngas when hot; the Fischer-Tropsch Reactor stopping when too hot, then making its products and wearing its
// catalyst.
public final class CarbonCaptureGameTests {
    private CarbonCaptureGameTests() {}

    private static int fill(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    static void recipes(GameTestHelper helper) {
        var level = helper.getLevel();
        BurnerFuel syngas = BurnerFuel.of(ModFluids.SYNGAS.get());
        helper.assertTrue(syngas != null && syngas.burnTemperature().isPresent(), "Syngas isn't a burner fuel with its own temperature");
        helper.assertTrue(FlueGas.isCarbonFuel(Items.COAL) && FlueGas.isCarbonFuel(ModItems.BIO_COAL.get()) && FlueGas.isCarbonFuel(ModItems.COAL_COKE.get()),
                "Coal, Bio-Coal and Coal Coke aren't carbon fuels");
        helper.assertTrue(FlueGas.isCarbonFuel(ModFluids.SYNGAS.get()) && FlueGas.isCarbonFuel(ModFluids.NAPHTHA.get()), "Syngas and Naphtha aren't carbon fuels");
        helper.assertTrue(!FlueGas.isCarbonFuel(ModFluids.HYDROGEN.get()), "Hydrogen is a carbon fuel");
        for (ItemStack fuel : new ItemStack[] { new ItemStack(Items.COAL), new ItemStack(Items.CHARCOAL), new ItemStack(ModItems.BIO_COAL.get()),
                new ItemStack(ModItems.COAL_COKE.get()), new ItemStack(Items.WHEAT_SEEDS) }) {
            var gasified = MachineRecipes.gasifying(level, fuel);
            helper.assertTrue(gasified.isPresent() && gasified.get().value().result().fluid().value() == ModFluids.SYNGAS.get(), fuel + " doesn't gasify");
        }
        helper.assertTrue(MachineRecipes.gasifying(level, new ItemStack(Items.COBBLESTONE)).isEmpty(), "Cobblestone gasifies");
        var ft = MachineRecipes.fischerTropsch(level, new FischerTropschRecipe.Input(FluidResource.of(ModFluids.SYNGAS.get()), 16_000));
        helper.assertTrue(ft.isPresent(), "No Fischer-Tropsch recipe for Syngas");
        FischerTropschRecipe recipe = ft.get().value();
        Fluid[] products = { ModFluids.NAPHTHA.get(), ModFluids.LIGHT_OIL.get(), ModFluids.HEAVY_OIL.get() };
        double out = 0;
        for (int i = 0; i < products.length; i++) {
            out += recipe.products()[i] * BurnerFuel.of(products[i]).huPerMb();
        }
        double in = recipe.input().amount() * syngas.huPerMb();
        helper.assertTrue(out > 0 && out < in, "The oils hold " + out + " HU, the Syngas " + in);
        var reclaim = MachineRecipes.anyCarbonReclaiming(level);
        helper.assertTrue(reclaim.isPresent() && reclaim.get().value().result().create().is(ModItems.CARBON_DUST.get()), "No Carbon Reclaimer recipe");
        helper.succeed();
    }

    // The Carbon Reclaimer's cost is never below what its Carbon Dust can give back, with or without Energy upgrades.
    static void reclaimerBalance(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.CARBON_RECLAIMER.get());
        CarbonReclaimerBlockEntity reclaimer = helper.getBlockEntity(pos, CarbonReclaimerBlockEntity.class);
        var level = helper.getLevel();
        var holder = MachineRecipes.anyCarbonReclaiming(level).orElseThrow();
        ItemStack dust = holder.value().result().create();
        double back = dust.getCount() * EnergyBalance.recoverableFePerItem(level, reclaimer, dust);
        helper.assertTrue(back > 0, "Nothing burns Carbon Dust (or what it makes)");
        int floor = EnergyBalance.minEnergyFor(holder.value(), level, reclaimer);
        helper.assertTrue(floor > back, "The floor " + floor + " FE isn't above the " + back + " FE its dust gives back");
        helper.assertTrue(floor >= back * ArcforgeConfig.RECLAIMER_BALANCE_SAFETY_FACTOR.getAsDouble() - 1, "The floor misses the safety factor");
        helper.assertTrue(reclaimer.costFor(level, holder) >= floor, "It charges less than the floor");
        CrushingGameTests.install(reclaimer.getItems(), ModItems.ENERGY_UPGRADE.get(), 8);
        helper.assertTrue(reclaimer.costFor(level, holder) >= floor, "Energy upgrades take it under the floor");
        helper.succeed();
    }

    // A Firebox with a Flue Gas face gives off Carbon Dioxide burning coal; one without it gives off none; a Fuel Burner
    // burning Hydrogen gives off none even with the face.
    static void flueGas(GameTestHelper helper) {
        BlockPos flued = new BlockPos(0, 1, 0);
        BlockPos plain = new BlockPos(2, 1, 0);
        BlockPos hydrogen = new BlockPos(4, 1, 0);
        helper.setBlock(flued, ModBlocks.FIREBOX.get());
        helper.setBlock(plain, ModBlocks.FIREBOX.get());
        helper.setBlock(hydrogen, ModBlocks.FUEL_BURNER.get());
        FireboxBlockEntity withFlue = helper.getBlockEntity(flued, FireboxBlockEntity.class);
        FireboxBlockEntity without = helper.getBlockEntity(plain, FireboxBlockEntity.class);
        FuelBurnerBlockEntity burner = helper.getBlockEntity(hydrogen, FuelBurnerBlockEntity.class);
        withFlue.setSideMode(RelativeSide.LEFT, SideMode.GAS_OUTPUT);
        burner.setSideMode(RelativeSide.LEFT, SideMode.GAS_OUTPUT);
        withFlue.getItems().setStack(BurnerBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
        without.getItems().setStack(BurnerBlockEntity.SLOT_FUEL, new ItemStack(Items.COAL, 4));
        helper.assertTrue(fill(burner.getTank(), ModFluids.HYDROGEN.get(), 2_000) == 2_000, "The Fuel Burner refused Hydrogen");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(withFlue.getFlue().getTank().getAmount() >= 10, "No flue gas yet"))
                .thenExecute(() -> {
                    helper.assertTrue(withFlue.getFlue().getTank().contains(ModFluids.CARBON_DIOXIDE.get()), "The flue holds something else");
                    helper.assertTrue(without.getBurnTime() > 0 && without.getFlue().getTank().getAmount() == 0,
                            "A Firebox with no Flue Gas face gave off " + without.getFlue().getTank().getAmount() + " mB");
                    helper.assertTrue(burner.getHeatPerTick() > 0 || burner.getHeat().getStored() > 0, "The Fuel Burner didn't burn its Hydrogen");
                    helper.assertTrue(burner.getFlue().getTank().getAmount() == 0, "Hydrogen gave off Carbon Dioxide");
                    Direction left = RelativeSide.LEFT.toDirection(withFlue.getFacing());
                    var out = withFlue.getFluidHandler(left);
                    try (Transaction tx = Transaction.openRoot()) {
                        helper.assertTrue(out != null && out.extract(FluidResource.of(ModFluids.CARBON_DIOXIDE.get()), 5, tx) == 5,
                                "The Flue Gas face doesn't give out Carbon Dioxide");
                    }
                })
                .thenSucceed();
    }

    // Coal and Steam make nothing while cold; hot, they make 1,000 mB Syngas.
    static void gasifierMakesSyngas(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.GASIFIER.get());
        GasifierBlockEntity gasifier = helper.getBlockEntity(pos, GasifierBlockEntity.class);
        var recipe = MachineRecipes.gasifying(helper.getLevel(), new ItemStack(Items.COAL)).orElseThrow().value();
        helper.assertTrue(CrushingGameTests.insert(gasifier.getItemHandler(Direction.UP), Items.COAL, 1) == 1, "It refused coal");
        helper.assertTrue(CrushingGameTests.insert(gasifier.getItemHandler(Direction.UP), Items.COBBLESTONE, 1) == 0, "It took cobblestone");
        helper.assertTrue(fill(gasifier.getFluidHandler(Direction.UP), ModFluids.STEAM.get(), recipe.steamAmount()) == recipe.steamAmount(), "It refused Steam");
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(gasifier.getStatus() == MachineStatus.TOO_COLD, "A cold Gasifier is " + gasifier.getStatus());
                    gasifier.getHeat().add(gasifier.getHeat().getCapacity());
                    helper.assertTrue(gasifier.getHeat().getTemperature() >= GasifierBlockEntity.minTemperature(), "A full buffer isn't 800°C");
                })
                .thenWaitUntil(() -> helper.assertTrue(gasifier.getSyngas().getAmount() == recipe.result().amount(),
                        gasifier.getSyngas().getAmount() + " mB Syngas"))
                .thenExecute(() -> {
                    helper.assertTrue(gasifier.getItems().getStack(GasifierBlockEntity.SLOT_INPUT).isEmpty(), "It didn't use the coal");
                    helper.assertTrue(gasifier.getSteam().getAmount() == 0, "It didn't use the Steam");
                })
                .thenSucceed();
    }

    // Heated past its window it stops and cools; back in it, a batch of Syngas becomes the four products and wears the
    // Iron Dust catalyst by one batch.
    static void fischerTropschWindow(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.FISCHER_TROPSCH_REACTOR.get());
        FischerTropschReactorBlockEntity reactor = helper.getBlockEntity(pos, FischerTropschReactorBlockEntity.class);
        CrushingGameTests.charge(reactor.getEnergy(), 100_000);
        var recipe = MachineRecipes.fischerTropsch(helper.getLevel(), new FischerTropschRecipe.Input(FluidResource.of(ModFluids.SYNGAS.get()), 16_000))
                .orElseThrow().value();
        int batch = recipe.input().amount();
        helper.assertTrue(fill(reactor.getSyngas(), ModFluids.SYNGAS.get(), batch) == batch, "It refused Syngas");
        reactor.getItems().setStack(FischerTropschReactorBlockEntity.SLOT_CATALYST, new ItemStack(ModItems.IRON_DUST.get(), 2));
        // Straight into the buffer, past the thermostat: 500°C.
        reactor.getHeat().add(reactor.getHeat().storedAt(500));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(reactor.getStatus() == MachineStatus.TOO_HOT, "At 500°C it's " + reactor.getStatus()))
                .thenWaitUntil(() -> helper.assertTrue(reactor.getProduct(FischerTropschReactorBlockEntity.TANK_NAPHTHA).getAmount() == recipe.naphtha(),
                        "No Naphtha yet (" + reactor.getStatus() + ", " + reactor.getHeat().getTemperature() + "°C)"))
                .thenExecute(() -> {
                    helper.assertTrue(reactor.getProduct(FischerTropschReactorBlockEntity.TANK_LIGHT_OIL).getAmount() == recipe.lightOil()
                            && reactor.getProduct(FischerTropschReactorBlockEntity.TANK_HEAVY_OIL).getAmount() == recipe.heavyOil()
                            && reactor.getProduct(FischerTropschReactorBlockEntity.TANK_WATER).getAmount() == recipe.water(), "The products are wrong");
                    helper.assertTrue(reactor.getSyngas().getAmount() == 0, "It didn't use the Syngas");
                    helper.assertTrue(reactor.catalystLeft() == ArcforgeConfig.FT_IRON_CATALYST_OPERATIONS.getAsInt() - 1,
                            "The catalyst has " + reactor.catalystLeft() + " batches left");
                    helper.assertTrue(reactor.getHeat().getTemperature() <= FischerTropschReactorBlockEntity.maxTemperature(), "It didn't cool into its window");
                })
                .thenSucceed();
    }
}
