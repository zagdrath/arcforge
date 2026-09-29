/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.blockentity.machine.FermenterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.heat.BurnerFuel;
import net.zagdrath.arcforge.machine.MachineStatus;
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
}
