/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.storage.EnergyCellBlock;
import net.zagdrath.arcforge.block.storage.FluidTankBlock;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.StorageBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.item.tool.WrenchMode;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;

// Fluid tanks and energy cells: GUI slots, block state feedback, item form, and conduits.
final class StorageGameTests {
    private StorageGameTests() {}

    private static void fillTank(FluidTankBlockEntity tank, net.minecraft.world.level.material.Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            tank.getInteractionFluidHandler().insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
        }
    }

    // A lava bucket in the input slot empties into the tank and the empty bucket drops below; an empty
    // bucket is then filled back from the tank. Lava makes the tank glow.
    static void tankBuckets(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.fluidTank(ConduitTier.WROUGHT).get());
        FluidTankBlockEntity tank = helper.getBlockEntity(pos, FluidTankBlockEntity.class);
        tank.getItems().setStack(StorageBlockEntity.SLOT_IN, new ItemStack(Items.LAVA_BUCKET));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(tank.getFluid().getAmount() == 1_000, "Tank has " + tank.getFluid().getAmount() + " mB"))
                .thenExecute(() -> {
                    helper.assertTrue(tank.getItems().getStack(StorageBlockEntity.SLOT_OUT).is(Items.BUCKET), "No empty bucket in the output slot");
                    helper.assertTrue(tank.getItems().getStack(StorageBlockEntity.SLOT_IN).isEmpty(), "Input slot not emptied");
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(pos).getValue(FluidTankBlock.LIGHT) == 15, "Tank with lava does not glow"))
                .thenExecute(() -> {
                    tank.getItems().setStack(StorageBlockEntity.SLOT_OUT, ItemStack.EMPTY);
                    tank.getItems().setStack(StorageBlockEntity.SLOT_IN, new ItemStack(Items.BUCKET));
                })
                .thenWaitUntil(() -> helper.assertTrue(tank.getItems().getStack(StorageBlockEntity.SLOT_OUT).is(Items.LAVA_BUCKET), "Empty bucket was not filled"))
                .thenExecute(() -> helper.assertTrue(tank.getFluid().isEmpty(), "Tank still holds " + tank.getFluid().getAmount() + " mB"))
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(pos).getValue(FluidTankBlock.LIGHT) == 0, "Empty tank still glows"))
                .thenSucceed();
    }

    // Breaking a tank spills a source block of its fluid and drops the tank empty.
    static void tankSpills(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.fluidTank(ConduitTier.TEMPERED).get());
        fillTank(helper.getBlockEntity(pos, FluidTankBlockEntity.class), Fluids.WATER, 12_400);
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);

        var drops = helper.getEntities(EntityTypes.ITEM, pos, 2.0);
        helper.assertTrue(drops.size() == 1, "Expected one drop, got " + drops.size());
        helper.assertTrue(!drops.getFirst().getItem().has(ModDataComponents.FLUID_CONTENTS.get()), "Broken tank still carries its fluid");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(pos)).isSource()
                        && helper.getLevel().getFluidState(helper.absolutePos(pos)).is(Fluids.WATER), "No water source where the tank was"))
                .thenSucceed();
    }

    // Picking a tank up with the wrench keeps its fluid, and placing it again restores the fluid.
    static void tankWrenchKeepsFluid(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.fluidTank(ConduitTier.TEMPERED).get());
        fillTank(helper.getBlockEntity(pos, FluidTankBlockEntity.class), Fluids.WATER, 12_400);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        wrench.set(ModDataComponents.WRENCH_MODE.get(), WrenchMode.DISMANTLE);
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        BlockPos absolute = helper.absolutePos(pos);
        wrench.getItem().onItemUseFirst(wrench, new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));

        var drops = helper.getEntities(EntityTypes.ITEM, pos, 2.0);
        helper.assertTrue(drops.size() == 1, "Expected one drop, got " + drops.size());
        ItemStack dropped = drops.getFirst().getItem();
        var content = dropped.get(ModDataComponents.FLUID_CONTENTS.get());
        helper.assertTrue(content != null && content.copy().getAmount() == 12_400, "Dismantled tank does not carry its fluid");
        helper.assertTrue(helper.getLevel().getFluidState(absolute).isEmpty(), "Dismantled tank spilled its fluid");

        helper.setBlock(pos, ModBlocks.fluidTank(ConduitTier.TEMPERED).get());
        helper.getBlockEntity(pos, FluidTankBlockEntity.class).applyComponentsFromItemStack(dropped);
        int amount = helper.getBlockEntity(pos, FluidTankBlockEntity.class).getFluid().getAmount();
        helper.assertTrue(amount == 12_400, "Placed tank holds " + amount + " mB");
        helper.succeed();
    }

    // With the default sides (input on top, output on the bottom) a tank drains into the tank below it,
    // but only once auto-eject is on.
    static void tanksStack(GameTestHelper helper) {
        BlockPos lower = new BlockPos(1, 1, 1);
        BlockPos upper = new BlockPos(1, 2, 1);
        helper.setBlock(lower, ModBlocks.fluidTank(ConduitTier.WROUGHT).get());
        helper.setBlock(upper, ModBlocks.fluidTank(ConduitTier.WROUGHT).get());
        fillTank(helper.getBlockEntity(upper, FluidTankBlockEntity.class), Fluids.WATER, 5_000);

        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(lower, FluidTankBlockEntity.class).getFluid().isEmpty(), "Tank drained with auto-eject off");
                    helper.getBlockEntity(upper, FluidTankBlockEntity.class).setAutoEject(true);
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(lower, FluidTankBlockEntity.class).getFluid().getAmount() == 5_000,
                        "Lower tank has " + helper.getBlockEntity(lower, FluidTankBlockEntity.class).getFluid().getAmount() + " mB"))
                .thenExecute(() -> helper.assertTrue(helper.getBlockEntity(upper, FluidTankBlockEntity.class).getFluid().isEmpty(), "Upper tank not drained"))
                .thenSucceed();
    }

    // Breaking a machine (not with the wrench) drops the items in its slots as well as the block.
    static void machinesDropItems(GameTestHelper helper) {
        BlockPos plantPos = new BlockPos(0, 1, 0);
        BlockPos tankPos = new BlockPos(2, 1, 0);
        BlockPos cellPos = new BlockPos(4, 1, 0);
        helper.setBlock(plantPos, ModBlocks.GEOTHERMAL_PLANT.get());
        helper.setBlock(tankPos, ModBlocks.fluidTank(ConduitTier.WROUGHT).get());
        helper.setBlock(cellPos, ModBlocks.energyCell(ConduitTier.WROUGHT).get());
        var plant = helper.getBlockEntity(plantPos, net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity.class);
        try (Transaction tx = Transaction.openRoot()) {
            plant.getItemHandler(null).insert(net.neoforged.neoforge.transfer.item.ItemResource.of(Items.LAVA_BUCKET), 1, tx);
            tx.commit();
        }
        helper.getBlockEntity(tankPos, FluidTankBlockEntity.class).getItems().setStack(StorageBlockEntity.SLOT_OUT, new ItemStack(Items.BUCKET, 3));
        helper.getBlockEntity(cellPos, EnergyCellBlockEntity.class).getItems().setStack(StorageBlockEntity.SLOT_IN, new ItemStack(Items.DIAMOND, 2));

        var player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        // Keep the mock player out of pickup range of the drops.
        BlockPos away = helper.absolutePos(new BlockPos(2, 1, 12));
        player.snapTo(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
        for (BlockPos pos : new BlockPos[] { plantPos, tankPos, cellPos }) {
            BlockPos absolute = helper.absolutePos(pos);
            var state = helper.getLevel().getBlockState(absolute);
            var blockEntity = helper.getLevel().getBlockEntity(absolute);
            state.onDestroyedByPlayer(helper.getLevel(), absolute, player, player.getMainHandItem(), true, helper.getLevel().getFluidState(absolute));
            state.getBlock().playerDestroy(helper.getLevel(), player, absolute, state, blockEntity, player.getMainHandItem());
            helper.assertBlockNotPresent(state.getBlock(), pos);
        }

        java.util.Map<net.minecraft.world.item.Item, Integer> counts = new java.util.HashMap<>();
        for (var entity : helper.getEntities(EntityTypes.ITEM, new BlockPos(2, 1, 0), 6.0)) {
            counts.merge(entity.getItem().getItem(), entity.getItem().getCount(), Integer::sum);
        }
        helper.assertTrue(counts.getOrDefault(Items.LAVA_BUCKET, 0) == 1, "Plant dropped " + counts.getOrDefault(Items.LAVA_BUCKET, 0) + " lava buckets; drops " + counts);
        helper.assertTrue(counts.getOrDefault(Items.BUCKET, 0) == 3, "Tank dropped " + counts.getOrDefault(Items.BUCKET, 0) + " buckets; drops " + counts);
        helper.assertTrue(counts.getOrDefault(Items.DIAMOND, 0) == 2, "Cell dropped " + counts.getOrDefault(Items.DIAMOND, 0) + " diamonds; drops " + counts);
        helper.succeed();
    }

    // The cell is dark when empty, lights one segment for 1 FE and all four when full, and keeps
    // its charge as an item.
    static void cellCharge(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.energyCell(ConduitTier.WROUGHT).get());
        EnergyCellBlockEntity cell = helper.getBlockEntity(pos, EnergyCellBlockEntity.class);
        var handler = cell.getEnergyHandler(null);

        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(pos).getValue(EnergyCellBlock.CHARGE) == 0, "Empty cell is lit"))
                .thenExecute(() -> {
                    try (Transaction tx = Transaction.openRoot()) {
                        handler.insert(1, tx);
                        tx.commit();
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(pos).getValue(EnergyCellBlock.CHARGE) == 1, "1 FE did not light one segment"))
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(pos).getLightEmission(helper.getLevel(), helper.absolutePos(pos)) > 0, "Charged cell gives no light"))
                .thenExecute(() -> {
                    for (int i = 0; i < 2_000 && cell.getEnergy() < cell.getCapacity(); i++) {
                        try (Transaction tx = Transaction.openRoot()) {
                            handler.insert(Integer.MAX_VALUE, tx);
                            tx.commit();
                        }
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockState(pos).getValue(EnergyCellBlock.CHARGE) == 4, "Full cell does not light all segments"))
                .thenExecute(() -> {
                    helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
                    var drops = helper.getEntities(EntityTypes.ITEM, pos, 2.0);
                    helper.assertTrue(drops.size() == 1, "Expected one drop, got " + drops.size());
                    Integer stored = drops.getFirst().getItem().get(ModDataComponents.ENERGY.get());
                    helper.assertTrue(stored != null && stored == ConduitTier.WROUGHT.cellCapacity(), "Dropped cell carries " + stored + " FE");
                })
                .thenSucceed();
    }

    // A cell pushes FE out of its front (output) face into a neighbour, and an energy conduit placed
    // against its side connects as an input without the wrench.
    static void cellOutputAndConduit(GameTestHelper helper) {
        BlockPos cellPos = new BlockPos(1, 1, 1);
        BlockPos sink = new BlockPos(1, 1, 0);
        TestFixtures.reset(helper.absolutePos(sink));
        helper.setBlock(cellPos, ModBlocks.energyCell(ConduitTier.WROUGHT).get());
        EnergyCellBlockEntity cell = helper.getBlockEntity(cellPos, EnergyCellBlockEntity.class);
        // Placed without a player the cell faces north, so its front (output) is the north face.
        helper.setBlock(sink, Blocks.LODESTONE);
        try (Transaction tx = Transaction.openRoot()) {
            cell.getEnergyHandler(null).insert(500, tx);
            tx.commit();
        }
        var to = TestFixtures.energy(helper.absolutePos(sink));

        BlockPos conduit = new BlockPos(2, 1, 1);
        helper.setBlock(conduit, ModBlocks.conduit(ConduitType.ENERGY, ConduitTier.WROUGHT).get());
        ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(conduit));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(to.getAmountAsInt() == 500, "Neighbour has " + to.getAmountAsInt() + " FE"))
                .thenExecute(() -> helper.assertTrue(ConduitBlock.mode(helper.getBlockState(conduit), Direction.WEST) == ConnectionMode.INPUT,
                        "Conduit beside the cell is " + ConduitBlock.mode(helper.getBlockState(conduit), Direction.WEST)))
                .thenSucceed();
    }
}
