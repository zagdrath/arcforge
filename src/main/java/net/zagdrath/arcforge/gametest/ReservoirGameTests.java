/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.storage.ItemReservoirBlock;
import net.zagdrath.arcforge.blockentity.storage.ItemReservoirBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.ReservoirBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.ReservoirKind;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.steam.SteamGrade;

// The Gas Reservoir (one evenly filled vessel of any shape, gases only, apart from Fluid Reservoirs) and the Item
// Reservoir (blocks making a solid box form one store; anything else stays single blocks). The Fluid Reservoir's own
// tests are in ExtendedLogisticsGameTests.
final class ReservoirGameTests {
    private ReservoirGameTests() {}

    private static int insert(ResourceHandler<FluidResource> handler, Fluid fluid, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int moved = handler.insert(FluidResource.of(fluid), amount, tx);
            tx.commit();
            return moved;
        }
    }

    private static int insert(ResourceHandler<ItemResource> handler, ItemStack stack) {
        try (Transaction tx = Transaction.openRoot()) {
            int moved = handler.insert(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
            return moved;
        }
    }

    private static int extract(ResourceHandler<ItemResource> handler, ItemStack stack) {
        try (Transaction tx = Transaction.openRoot()) {
            int moved = handler.extract(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
            return moved;
        }
    }

    private static ReservoirBlockEntity tank(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, ReservoirBlockEntity.class);
    }

    private static ItemReservoirBlockEntity store(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, ItemReservoirBlockEntity.class);
    }

    // Three Gas Reservoirs in an L are one vessel that its steam fills evenly; it refuses water, and a Fluid Reservoir
    // beside it (which refuses steam) stays a tank of its own. A comparator reads the whole vessel, and a broken block keeps
    // its share.
    static void gasReservoir(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 1, 1), b = new BlockPos(2, 1, 1), c = new BlockPos(1, 2, 1), liquid = new BlockPos(0, 1, 1);
        for (BlockPos pos : List.of(a, b, c)) {
            helper.setBlock(pos, ModBlocks.GAS_RESERVOIR.get());
        }
        helper.setBlock(liquid, ModBlocks.FLUID_RESERVOIR.get());
        Fluid steam = SteamGrade.STEAM.fluid();
        int capacity = ReservoirKind.GAS.capacity();
        ResourceHandler<FluidResource> vessel = tank(helper, c).getFluidHandler();
        helper.assertTrue(insert(vessel, Fluids.WATER, 1_000) == 0, "The Gas Reservoir took water");
        helper.assertTrue(insert(tank(helper, liquid).getFluidHandler(), steam, 1_000) == 0, "The Fluid Reservoir took steam");
        helper.assertTrue(insert(vessel, steam, 30_000) == 30_000, "The vessel didn't take 30,000 mB");
        for (BlockPos pos : List.of(a, b, c)) {
            helper.assertTrue(tank(helper, pos).ownAmount() == 10_000, "Not filled evenly: " + pos + " holds " + tank(helper, pos).ownAmount());
        }
        helper.assertTrue(tank(helper, a).getGroup().size() == 3 && tank(helper, liquid).getGroup().size() == 1,
                "The vessel is " + tank(helper, a).getGroup().size() + " blocks, the liquid tank " + tank(helper, liquid).getGroup().size());
        helper.assertTrue(tank(helper, b).getComparatorSignal() == 15 * 30_000 / (3 * capacity), "Comparator reads " + tank(helper, b).getComparatorSignal());
        var level = helper.getLevel();
        List<ItemStack> drops = Block.getDrops(level.getBlockState(helper.absolutePos(b)), level, helper.absolutePos(b),
                level.getBlockEntity(helper.absolutePos(b)));
        FluidStack kept = drops.isEmpty() ? FluidStack.EMPTY
                : drops.getFirst().getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        helper.assertTrue(kept.getAmount() == 10_000 && kept.getFluid() == steam, "The broken block kept " + kept.getAmount() + " mB");
        helper.succeed();
    }

    // A 2x2x4 box along X is one store: every block formed along X, 16 blocks' slots as one inventory, so what goes in
    // one block comes out of another, and a comparator on any block reads it. Breaking a block spills its own slots,
    // and what's left (no longer a box) falls back to single blocks.
    static void itemReservoirStore(GameTestHelper helper) {
        BlockPos min = new BlockPos(1, 1, 1);
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 2; y++) {
                for (int z = 0; z < 2; z++) {
                    helper.setBlock(min.offset(x, y, z), ModBlocks.ITEM_RESERVOIR.get());
                }
            }
        }
        BlockPos first = min, last = min.offset(3, 1, 1), middle = min.offset(1, 0, 0);
        int slots = ArcforgeConfig.ITEM_RESERVOIR_SLOTS.getAsInt();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    for (int x = 0; x < 4; x++) {
                        var state = helper.getBlockState(min.offset(x, 1, 1));
                        helper.assertTrue(state.getValue(ItemReservoirBlock.FORMED) && state.getValue(ItemReservoirBlock.AXIS) == Direction.Axis.X,
                                "Block " + x + " isn't formed along X: " + state);
                    }
                    helper.assertTrue(store(helper, first).getStore().blocks() == 16 && store(helper, first).getStore().slots() == 16 * slots,
                            "The store is " + store(helper, first).getStore().blocks() + " blocks, " + store(helper, first).getStore().slots() + " slots");
                    helper.assertTrue(insert(store(helper, first).getItemHandler(), new ItemStack(Items.COBBLESTONE, 640)) == 640, "It didn't take 640 cobblestone");
                    helper.assertTrue(extract(store(helper, last).getItemHandler(), new ItemStack(Items.COBBLESTONE, 100)) == 100,
                            "The far block couldn't give what the near one took");
                    helper.assertTrue(store(helper, last).getComparatorSignal() > 0, "The comparator reads nothing");
                    store(helper, middle).items().setStack(0, new ItemStack(Items.DIAMOND, 7));
                    helper.getLevel().destroyBlock(helper.absolutePos(middle), true);
                    int diamonds = helper.getEntities(EntityTypes.ITEM, middle, 2.0).stream()
                            .filter(item -> item.getItem().is(Items.DIAMOND)).mapToInt(item -> item.getItem().getCount()).sum();
                    helper.assertTrue(diamonds == 7, "The broken block spilled " + diamonds + " diamonds");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(!helper.getBlockState(first).getValue(ItemReservoirBlock.FORMED), "What's left still counts as a box");
                    helper.assertTrue(store(helper, first).getStore().blocks() == 1, "A loose block is a store of " + store(helper, first).getStore().blocks());
                })
                .thenSucceed();
    }

    // Only a box of an allowed size forms: a 1x1x3 tower does (vertical), a 1x1x4 tower is too long for its width, and
    // an L of three isn't a box.
    static void itemReservoirShapes(GameTestHelper helper) {
        BlockPos tower = new BlockPos(1, 1, 1), tall = new BlockPos(3, 1, 1), corner = new BlockPos(5, 1, 1);
        for (int y = 0; y < 3; y++) {
            helper.setBlock(tower.above(y), ModBlocks.ITEM_RESERVOIR.get());
        }
        for (int y = 0; y < 4; y++) {
            helper.setBlock(tall.above(y), ModBlocks.ITEM_RESERVOIR.get());
        }
        for (BlockPos pos : List.of(corner, corner.east(), corner.above())) {
            helper.setBlock(pos, ModBlocks.ITEM_RESERVOIR.get());
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    var state = helper.getBlockState(tower);
                    helper.assertTrue(state.getValue(ItemReservoirBlock.FORMED) && state.getValue(ItemReservoirBlock.AXIS) == Direction.Axis.Y,
                            "The 1x1x3 tower isn't a vertical store: " + state);
                    helper.assertTrue(!helper.getBlockState(tall).getValue(ItemReservoirBlock.FORMED), "The 1x1x4 tower formed");
                    helper.assertTrue(!helper.getBlockState(corner).getValue(ItemReservoirBlock.FORMED), "The L formed");
                })
                .thenSucceed();
    }

    // A block picked up holding items can't go inside other storage (a Crate, a shulker box, another Item Reservoir); an
    // empty one can.
    static void itemReservoirNoNesting(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ITEM_RESERVOIR.get());
        ItemReservoirBlockEntity reservoir = store(helper, pos);
        ItemStack empty = new ItemStack(ModBlocks.ITEM_RESERVOIR.get());
        empty.applyComponents(reservoir.collectComponents());
        helper.assertTrue(empty.canFitInsideContainerItems(), "An empty Item Reservoir can't go in storage");
        reservoir.items().setStack(0, new ItemStack(Items.COBBLESTONE, 5));
        ItemStack full = new ItemStack(ModBlocks.ITEM_RESERVOIR.get());
        full.applyComponents(reservoir.collectComponents());
        helper.assertTrue(!full.canFitInsideContainerItems(), "A filled Item Reservoir fits in storage");
        helper.assertTrue(!ItemReservoirBlockEntity.accepts(ItemResource.of(full)), "An Item Reservoir accepts a filled one");
        helper.setBlock(pos, Blocks.AIR);
        helper.succeed();
    }
}
