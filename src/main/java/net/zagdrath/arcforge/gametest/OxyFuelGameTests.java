/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FuelBurnerBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.PressurizedCylinderBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;

// Oxy-fuel in the Firebox and Fuel Burner (default config: +300°C up to 1,600°C, x1.25 heat, 0.25 mB/t of oxygen).
public final class OxyFuelGameTests {
    private OxyFuelGameTests() {}

    private static FireboxBlockEntity firebox(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.FIREBOX.get());
        FireboxBlockEntity firebox = helper.getBlockEntity(pos, FireboxBlockEntity.class);
        try (Transaction tx = Transaction.openRoot()) {
            firebox.getItemHandler(null).insert(ItemResource.of(Items.COAL), 8, tx);
            tx.commit();
        }
        return firebox;
    }

    private static void oxygen(FireboxBlockEntity firebox, int amount) {
        firebox.getOxyFuel().getTank().set(0, FluidResource.of(ModFluids.OXYGEN.get()), amount);
    }

    // Without oxygen a Firebox burns as it always has: 1,100°C, 80 HU/t.
    static void offIsUnchanged(GameTestHelper helper) {
        FireboxBlockEntity firebox = firebox(helper, new BlockPos(1, 1, 1));
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(firebox.getHeat().getTemperature() == 1_100, "Burning at " + firebox.getHeat().getTemperature() + "°C, not 1,100");
                    helper.assertTrue(firebox.getOutputPerTick() == 80, "Making " + firebox.getOutputPerTick() + " HU/t, not 80");
                    helper.assertFalse(firebox.isOxyActive(), "On oxy-fuel without oxygen");
                })
                .thenSucceed();
    }

    // With oxygen it burns at 1,400°C and makes 100 HU/t (80 x 1.25), using 0.25 mB of oxygen a tick.
    static void firebox(GameTestHelper helper) {
        FireboxBlockEntity firebox = firebox(helper, new BlockPos(1, 1, 1));
        oxygen(firebox, 2_000);
        int[] before = new int[1];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> before[0] = firebox.getOxyFuel().getTank().getAmount())
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(firebox.isOxyActive(), "Not on oxy-fuel with oxygen");
                    helper.assertTrue(firebox.getHeat().getTemperature() == 1_400, "Burning at " + firebox.getHeat().getTemperature() + "°C, not 1,400");
                    helper.assertTrue(firebox.getOutputPerTick() == 100, "Making " + firebox.getOutputPerTick() + " HU/t, not 100");
                    int used = before[0] - firebox.getOxyFuel().getTank().getAmount();
                    helper.assertTrue(Math.abs(used - 10) <= 1, "Used " + used + " mB of oxygen in 40 ticks, not about 10");
                })
                .thenSucceed();
    }

    // Hydrogen burns at 1,400°C; with oxygen that would be 1,700, capped at 1,600.
    static void hydrogenCap(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.FUEL_BURNER.get());
        FuelBurnerBlockEntity burner = helper.getBlockEntity(pos, FuelBurnerBlockEntity.class);
        burner.getTank().set(0, FluidResource.of(ModFluids.HYDROGEN.get()), 8_000);
        burner.getOxyFuel().getTank().set(0, FluidResource.of(ModFluids.OXYGEN.get()), 2_000);
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(burner.getHeat().getTemperature() == 1_600, "Burning hydrogen on oxy-fuel at " + burner.getHeat().getTemperature() + "°C");
                    helper.assertTrue(burner.getStatus() == MachineStatus.OXY_FUEL, "Status is " + burner.getStatus());
                })
                .thenSucceed();
    }

    // An Oxygen face takes oxygen from a Pressurized Conduit, and refuses hydrogen.
    static void sideMode(GameTestHelper helper) {
        BlockPos oxygenBox = new BlockPos(1, 1, 1);
        BlockPos hydrogenBox = new BlockPos(3, 1, 1);
        FireboxBlockEntity fed = firebox(helper, oxygenBox);
        FireboxBlockEntity refused = firebox(helper, hydrogenBox);
        for (FireboxBlockEntity box : new FireboxBlockEntity[] { fed, refused }) {
            box.setSideMode(RelativeSide.TOP, SideMode.OXYGEN);
        }
        helper.setBlock(oxygenBox.above(2), ModBlocks.pressurizedCylinder(ConduitTier.WROUGHT).get());
        helper.setBlock(hydrogenBox.above(2), ModBlocks.pressurizedCylinder(ConduitTier.WROUGHT).get());
        helper.getBlockEntity(oxygenBox.above(2), PressurizedCylinderBlockEntity.class).getTank().set(0, FluidResource.of(ModFluids.OXYGEN.get()), 1_000);
        helper.getBlockEntity(hydrogenBox.above(2), PressurizedCylinderBlockEntity.class).getTank().set(0, FluidResource.of(ModFluids.HYDROGEN.get()), 1_000);
        for (BlockPos box : new BlockPos[] { oxygenBox, hydrogenBox }) {
            helper.setBlock(box.above(), ModBlocks.conduit(ConduitType.GAS, ConduitTier.WROUGHT).get());
            ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(box.above()));
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fed.getOxyFuel().getTank().getAmount() > 0, "No oxygen reached the Oxygen face"))
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(refused.getOxyFuel().getTank().getAmount() == 0, "The Oxygen face took hydrogen");
                    helper.assertTrue(helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                            helper.absolutePos(oxygenBox), Direction.NORTH) == null, "A face that isn't Oxygen takes fluids");
                })
                .thenSucceed();
    }
}
