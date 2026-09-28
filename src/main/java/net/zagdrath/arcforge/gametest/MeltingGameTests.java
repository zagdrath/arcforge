/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.machine.ArcMelterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;

// The Arc Melter: rock to lava, its tank, its upgrades, and feeding a Geothermal Plant.
public final class MeltingGameTests {
    private static final BlockPos POS = new BlockPos(0, 1, 0);

    private MeltingGameTests() {}

    // A melter whose lava stays in its tank (no output faces).
    private static ArcMelterBlockEntity place(GameTestHelper helper, BlockPos pos, boolean outputs) {
        helper.setBlock(pos, ModBlocks.ARC_MELTER.get());
        ArcMelterBlockEntity melter = helper.getBlockEntity(pos, ArcMelterBlockEntity.class);
        if (!outputs) {
            melter.setSideMode(RelativeSide.BOTTOM, SideMode.NONE);
        }
        return melter;
    }

    private static int lava(ArcMelterBlockEntity melter) {
        return melter.getTank().contains(Fluids.LAVA) ? melter.getTank().getAmount() : 0;
    }

    // Cobblestone makes 250 mB for 12,500 FE (125 ticks at 100 FE/t); then stone takes it to 500 mB.
    static void stone(GameTestHelper helper) {
        ArcMelterBlockEntity melter = place(helper, POS, false);
        CrushingGameTests.charge(melter.getEnergy(), 20_000);
        helper.assertTrue(CrushingGameTests.insert(melter.getItemHandler(Direction.UP), Items.COBBLESTONE, 1) == 1, "Top face refused cobblestone");
        helper.startSequence()
                .thenIdle(130)
                .thenExecute(() -> {
                    helper.assertTrue(lava(melter) == 250, "Tank holds " + lava(melter) + " mB after cobblestone, expected 250");
                    int used = 20_000 - melter.getEnergy().getAmountAsInt();
                    helper.assertTrue(used == 12_500, "Cobblestone used " + used + " FE, expected 12,500");
                    CrushingGameTests.charge(melter.getEnergy(), 20_000);
                    CrushingGameTests.insert(melter.getItemHandler(Direction.UP), Items.STONE, 1);
                })
                .thenIdle(130)
                .thenExecute(() -> helper.assertTrue(lava(melter) == 500, "Tank holds " + lava(melter) + " mB after stone, expected 500"))
                .thenSucceed();
    }

    // 500 mB after 250 ticks, and not before.
    static void netherrack(GameTestHelper helper) {
        ArcMelterBlockEntity melter = place(helper, POS, false);
        CrushingGameTests.charge(melter.getEnergy(), 40_000);
        CrushingGameTests.insert(melter.getItemHandler(Direction.UP), Items.NETHERRACK, 1);
        helper.startSequence()
                .thenIdle(245)
                .thenExecute(() -> helper.assertTrue(lava(melter) == 0, "Netherrack melted early"))
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(lava(melter) == 500, "Netherrack gave " + lava(melter) + " mB, expected 500"))
                .thenSucceed();
    }

    // 1,000 mB after 500 ticks. That's 50,000 FE, more than the buffer holds, so it's topped up halfway.
    static void magmaBlock(GameTestHelper helper) {
        ArcMelterBlockEntity melter = place(helper, POS, false);
        CrushingGameTests.charge(melter.getEnergy(), 40_000);
        CrushingGameTests.insert(melter.getItemHandler(Direction.UP), Items.MAGMA_BLOCK, 1);
        helper.startSequence()
                .thenIdle(250)
                .thenExecute(() -> CrushingGameTests.charge(melter.getEnergy(), 40_000))
                .thenIdle(245)
                .thenExecute(() -> helper.assertTrue(lava(melter) == 0, "Magma block melted early"))
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(lava(melter) == 1_000, "Magma block gave " + lava(melter) + " mB, expected 1,000"))
                .thenSucceed();
    }

    // Only things that melt go in, and only through input faces.
    static void rejectsNonMelting(GameTestHelper helper) {
        ArcMelterBlockEntity melter = place(helper, POS, true);
        helper.assertTrue(CrushingGameTests.insert(melter.getItemHandler(Direction.UP), Items.DIRT, 1) == 0, "Took dirt");
        helper.assertTrue(CrushingGameTests.insert(melter.getItemHandler(Direction.UP), Items.NETHERRACK, 1) == 1, "Refused netherrack");
        helper.assertTrue(melter.getItemHandler(melter.getFacing()) == null, "Front face takes items");
        helper.succeed();
    }

    // With no room for 250 mB it waits: nothing melts, no FE is used and the cobblestone stays.
    static void tankFullWaits(GameTestHelper helper) {
        ArcMelterBlockEntity melter = place(helper, POS, false);
        try (Transaction tx = Transaction.openRoot()) {
            melter.getTank().insert(0, FluidResource.of(Fluids.LAVA), 3_900, tx);
            tx.commit();
        }
        CrushingGameTests.charge(melter.getEnergy(), 20_000);
        CrushingGameTests.insert(melter.getItemHandler(Direction.UP), Items.COBBLESTONE, 1);
        helper.startSequence()
                .thenIdle(140)
                .thenExecute(() -> {
                    helper.assertTrue(melter.getStatus() == MachineStatus.TANK_FULL, "Status is " + melter.getStatus());
                    helper.assertTrue(lava(melter) == 3_900, "Tank holds " + lava(melter) + " mB");
                    helper.assertTrue(melter.getItems().getStack(ArcMelterBlockEntity.SLOT_INPUT).is(Items.COBBLESTONE), "Cobblestone was used up");
                    helper.assertTrue(melter.getEnergy().getAmountAsInt() == 20_000, "Used FE while waiting");
                })
                .thenSucceed();
    }

    // The melter feeds the Geothermal Plant at plantPos: the plant gets lava and lights up, and the melter's
    // tank never holds more than one melt.
    private static void feedsGeothermal(GameTestHelper helper, ArcMelterBlockEntity melter, BlockPos plantPos) {
        CrushingGameTests.charge(melter.getEnergy(), 40_000);
        CrushingGameTests.insert(melter.getItemHandler(null), Items.COBBLESTONE, 8);
        GeothermalPlantBlockEntity plant = helper.getBlockEntity(plantPos, GeothermalPlantBlockEntity.class);
        ResourceHandler<FluidResource> plantTank = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(plantPos), null);
        helper.assertTrue(plantTank != null, "Geothermal Plant has no tank");
        helper.onEachTick(() -> helper.assertTrue(lava(melter) <= 250, "Melter tank holds " + lava(melter) + " mB: the lava isn't leaving"));
        helper.startSequence()
                .thenIdle(300)
                .thenExecute(() -> {
                    helper.assertTrue(plantTank.getAmountAsLong(0) > 0 || plant.getHeat().getStored() > 0, "Geothermal Plant got no lava");
                    helper.assertTrue(helper.getBlockState(plantPos).getValue(MachineBlock.LIT), "Geothermal Plant isn't lit");
                })
                .thenSucceed();
    }

    // Straight down with the default sides: the melter's bottom is an output, the plant's top an input.
    static void feedsGeothermalBelow(GameTestHelper helper) {
        BlockPos plantPos = new BlockPos(0, 1, 0);
        helper.setBlock(plantPos, ModBlocks.GEOTHERMAL_PLANT.get());
        feedsGeothermal(helper, place(helper, plantPos.above(), true), plantPos);
    }

    // Side by side: the melter's right face set to output, the plant's face against it to input.
    static void feedsGeothermalSide(GameTestHelper helper) {
        ArcMelterBlockEntity melter = place(helper, POS, false);
        Direction right = RelativeSide.RIGHT.toDirection(melter.getFacing());
        BlockPos plantPos = POS.relative(right);
        helper.setBlock(plantPos, ModBlocks.GEOTHERMAL_PLANT.get());
        melter.setSideMode(RelativeSide.RIGHT, SideMode.OUTPUT);
        GeothermalPlantBlockEntity plant = helper.getBlockEntity(plantPos, GeothermalPlantBlockEntity.class);
        plant.setSideMode(RelativeSide.fromDirection(plant.getFacing(), right.getOpposite()), SideMode.INPUT);
        feedsGeothermal(helper, melter, plantPos);
    }

    // 100 FE/t base; 8 Energy upgrades take it to 17 FE/t or less; 8 Speed upgrades melt cobblestone in 8 ticks.
    static void upgrades(GameTestHelper helper) {
        ArcMelterBlockEntity melter = place(helper, POS, false);
        int base = melter.energyPerTick();
        CrushingGameTests.install(melter.getItems(), ModItems.ENERGY_UPGRADE.get(), 8);
        int efficient = melter.energyPerTick();
        helper.assertTrue(base == 100 && efficient <= 17, "FE/t is " + base + " base, " + efficient + " with 8 Energy upgrades");
        CrushingGameTests.install(melter.getItems(), ModItems.SPEED_UPGRADE.get(), 8);
        CrushingGameTests.charge(melter.getEnergy(), 40_000);
        CrushingGameTests.insert(melter.getItemHandler(null), Items.COBBLESTONE, 2);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(melter.getTotal() == 8, "Cobblestone takes " + melter.getTotal() + " ticks, expected 8"))
                .thenIdle(15)
                .thenExecute(() -> helper.assertTrue(lava(melter) == 500, "Tank holds " + lava(melter) + " mB after two upgraded melts"))
                .thenSucceed();
    }

    // In HIGH mode with no signal it doesn't melt or use FE; a redstone block starts it.
    static void redstone(GameTestHelper helper) {
        ArcMelterBlockEntity melter = place(helper, POS, false);
        melter.setRedstoneMode(RedstoneMode.HIGH);
        CrushingGameTests.charge(melter.getEnergy(), 20_000);
        CrushingGameTests.insert(melter.getItemHandler(null), Items.COBBLESTONE, 1);
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(melter.getStatus() == MachineStatus.DISABLED, "Status is " + melter.getStatus());
                    helper.assertTrue(melter.getProgress() == 0 && melter.getEnergy().getAmountAsInt() == 20_000, "Ran without a signal");
                    helper.setBlock(POS.east(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(melter.getStatus() == MachineStatus.MELTING, "Status with a signal is " + melter.getStatus()))
                .thenSucceed();
    }
}
