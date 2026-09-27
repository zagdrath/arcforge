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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.multiblock.CarbonizerBlock;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnacePortBlock;
import net.zagdrath.arcforge.multiblock.ArcforgeFurnaceStructure;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;

// Carbonizer and Arcforge Furnace: formation, breaking, processing and structure-face capabilities.
public final class MultiblockGameTests {
    private MultiblockGameTests() {}

    // Carbonizer N slices wide along +X, facing north (front row at z=0, back rows behind it), bottom at y=1.
    private static void buildCarbonizer(GameTestHelper helper, int slices) {
        buildCarbonizer(helper, 0, slices, 2, 2);
    }

    private static void buildCarbonizer(GameTestHelper helper, int firstX, int slices, int height, int depth) {
        BlockState state = ModBlocks.CARBONIZER.get().defaultBlockState().setValue(CarbonizerBlock.FACING, Direction.NORTH);
        for (int x = firstX; x < firstX + slices; x++) {
            for (int y = 1; y <= height; y++) {
                for (int z = 0; z < depth; z++) {
                    helper.setBlock(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    private static CarbonizerBlock.Row row(GameTestHelper helper, int x, int y, int z) {
        return helper.getBlockState(new BlockPos(x, y, z)).getValue(CarbonizerBlock.ROW);
    }

    // Three slices form one structure: left/middle/right along the row (facing north, left is west),
    // with the master at the left, bottom, front block. Breaking one un-forms it; replacing it re-forms.
    public static void carbonizerForms(GameTestHelper helper) {
        buildCarbonizer(helper, 3);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(row(helper, 0, 1, 0) == CarbonizerBlock.Row.LEFT, "West slice is " + row(helper, 0, 1, 0));
                    helper.assertTrue(row(helper, 1, 2, 1) == CarbonizerBlock.Row.MIDDLE, "Middle slice is " + row(helper, 1, 2, 1));
                    helper.assertTrue(row(helper, 2, 1, 1) == CarbonizerBlock.Row.RIGHT, "East slice is " + row(helper, 2, 1, 1));
                    BlockState frontTop = helper.getBlockState(new BlockPos(1, 2, 0));
                    helper.assertTrue(frontTop.getValue(CarbonizerBlock.DEPTH) == CarbonizerBlock.Depth.FRONT, "z=0 is not the front row");
                    helper.assertTrue(frontTop.getValue(CarbonizerBlock.HALF) == CarbonizerBlock.Half.TOP, "y=2 is not the top");
                    CarbonizerBlockEntity master = helper.getBlockEntity(new BlockPos(0, 1, 0), CarbonizerBlockEntity.class);
                    helper.assertTrue(master.isFormed() && master.getSlices() == 3, "Master is not formed with 3 slices");
                    CarbonizerBlockEntity far = helper.getBlockEntity(new BlockPos(2, 2, 1), CarbonizerBlockEntity.class);
                    helper.assertTrue(far.getFormedMaster() == master, "Far block does not point at the master");
                })
                .thenExecute(() -> helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR))
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(row(helper, 0, 1, 0) == CarbonizerBlock.Row.NONE, "Still formed after a block was broken");
                    helper.assertTrue(row(helper, 2, 2, 1) == CarbonizerBlock.Row.NONE, "Far side still formed after a block was broken");
                })
                .thenExecute(() -> helper.setBlock(new BlockPos(1, 1, 1),
                        ModBlocks.CARBONIZER.get().defaultBlockState().setValue(CarbonizerBlock.FACING, Direction.NORTH)))
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(row(helper, 1, 1, 1) == CarbonizerBlock.Row.MIDDLE, "Did not re-form after rebuilding"))
                .thenSucceed();
    }

    // Coal goes in through the top (input), coke comes out of the bottom (output) and creosote out of
    // the back (by-product). The working slice lights up while its chamber runs.
    public static void carbonizerProcesses(GameTestHelper helper) {
        buildCarbonizer(helper, 1);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockPos top = helper.absolutePos(new BlockPos(0, 2, 1));
                    ResourceHandler<ItemResource> input = helper.getLevel().getCapability(Capabilities.Item.BLOCK, top, Direction.UP);
                    helper.assertTrue(input != null, "Top face does not accept items");
                    try (Transaction tx = Transaction.openRoot()) {
                        helper.assertTrue(input.insert(ItemResource.of(Items.COAL), 2, tx) == 2, "Top face refused coal");
                        tx.commit();
                    }
                    BlockPos side = helper.absolutePos(new BlockPos(0, 1, 0));
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, side, Direction.WEST) == null,
                            "Left face (none) exposes items");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(new BlockPos(0, 1, 0)).getValue(CarbonizerBlock.LIT), "Front of the working slice is not lit");
                })
                .thenIdle(600)
                .thenExecute(() -> {
                    BlockPos bottom = helper.absolutePos(new BlockPos(0, 1, 0));
                    ResourceHandler<ItemResource> output = helper.getLevel().getCapability(Capabilities.Item.BLOCK, bottom, Direction.DOWN);
                    helper.assertTrue(output != null, "Bottom face does not give items");
                    try (Transaction tx = Transaction.openRoot()) {
                        int coke = output.extract(ItemResource.of(ModItems.COAL_COKE.get()), 64, tx);
                        helper.assertTrue(coke >= 1, "No coal coke came out of the bottom face");
                    }
                    BlockPos back = helper.absolutePos(new BlockPos(0, 1, 1));
                    ResourceHandler<FluidResource> creosote = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, back, Direction.SOUTH);
                    helper.assertTrue(creosote != null, "Back face does not give creosote");
                    try (Transaction tx = Transaction.openRoot()) {
                        int drained = creosote.extract(FluidResource.of(ModFluids.CREOSOTE.get()), 1_000, tx);
                        helper.assertTrue(drained >= 250, "Only " + drained + " mB of creosote came out of the back face");
                    }
                })
                .thenSucceed();
    }

    // Three 3-tall, 3-deep slices form one structure; the middle layer and row are marked middle, and
    // the fronts use the tall door.
    public static void carbonizerBigForms(GameTestHelper helper) {
        buildCarbonizer(helper, 0, 3, 3, 3);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    CarbonizerBlockEntity master = helper.getBlockEntity(new BlockPos(0, 1, 0), CarbonizerBlockEntity.class);
                    helper.assertTrue(master.isFormed() && master.getSlices() == 3, "Master is not formed with 3 slices");
                    helper.assertTrue(master.getSliceHeight() == 3 && master.getSliceDepth() == 3,
                            "Slices are " + master.getSliceHeight() + "x" + master.getSliceDepth() + ", expected 3x3");
                    BlockState centre = helper.getBlockState(new BlockPos(1, 2, 1));
                    helper.assertTrue(centre.getValue(CarbonizerBlock.HALF) == CarbonizerBlock.Half.MIDDLE, "Centre is not the middle layer");
                    helper.assertTrue(centre.getValue(CarbonizerBlock.DEPTH) == CarbonizerBlock.Depth.MIDDLE, "Centre is not the middle row");
                    BlockState frontTop = helper.getBlockState(new BlockPos(2, 3, 0));
                    helper.assertTrue(frontTop.getValue(CarbonizerBlock.HALF) == CarbonizerBlock.Half.TOP
                            && frontTop.getValue(CarbonizerBlock.DEPTH) == CarbonizerBlock.Depth.FRONT
                            && frontTop.getValue(CarbonizerBlock.TALL), "Front top block is " + frontTop);
                    helper.assertTrue(helper.getBlockState(new BlockPos(0, 1, 2)).getValue(CarbonizerBlock.DEPTH) == CarbonizerBlock.Depth.BACK, "z=2 is not the back row");
                })
                .thenSucceed();
    }

    // A 3-tall slice next to a 2-tall one isn't a box, so nothing forms.
    public static void carbonizerMixedSizes(GameTestHelper helper) {
        buildCarbonizer(helper, 0, 1, 3, 2);
        buildCarbonizer(helper, 1, 1, 2, 2);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(row(helper, 0, 1, 0) == CarbonizerBlock.Row.NONE, "Mixed heights formed");
                    helper.assertTrue(row(helper, 1, 1, 0) == CarbonizerBlock.Row.NONE, "Mixed heights formed");
                })
                .thenSucceed();
    }

    // A 3x3 slice bakes 3 coal at once: 3 coke and 750 mB of creosote per cycle.
    public static void carbonizerBigBatch(GameTestHelper helper) {
        buildCarbonizer(helper, 0, 1, 3, 3);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    CarbonizerBlockEntity master = helper.getBlockEntity(new BlockPos(0, 1, 0), CarbonizerBlockEntity.class);
                    try (Transaction tx = Transaction.openRoot()) {
                        helper.assertTrue(master.getItemHandler(null).insert(ItemResource.of(Items.COAL), 5, tx) == 5, "Carbonizer refused coal");
                        tx.commit();
                    }
                })
                .thenIdle(605)
                .thenExecute(() -> {
                    CarbonizerBlockEntity master = helper.getBlockEntity(new BlockPos(0, 1, 0), CarbonizerBlockEntity.class);
                    ResourceHandler<ItemResource> items = master.getItemHandler(null);
                    try (Transaction tx = Transaction.openRoot()) {
                        int coke = items.extract(ItemResource.of(ModItems.COAL_COKE.get()), 64, tx);
                        helper.assertTrue(coke == 3, "One cycle made " + coke + " coke, expected 3");
                    }
                    ResourceHandler<FluidResource> creosote = master.getFluidHandler(null);
                    try (Transaction tx = Transaction.openRoot()) {
                        int drained = creosote.extract(FluidResource.of(ModFluids.CREOSOTE.get()), 10_000, tx);
                        helper.assertTrue(drained == 750, "One cycle made " + drained + " mB of creosote, expected 750");
                    }
                })
                .thenSucceed();
    }

    // Arcforge Furnace centred on (1, y, 1), front facing north (port at (1, 1, 0)), 6 layers from y=1.
    private static void buildFurnace(GameTestHelper helper) {
        for (int y = 1; y <= 6; y++) {
            for (int x = 0; x <= 2; x++) {
                for (int z = 0; z <= 2; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    boolean corner = x != 1 && z != 1;
                    if (x == 1 && z == 1) {
                        // A brick hearth at the bottom; above it the stack must be empty (the test
                        // area's barrier walls can reach into it).
                        helper.setBlock(pos, y == 1 ? ModBlocks.ARCFORGE_FURNACE_BRICKS.get().defaultBlockState() : Blocks.AIR.defaultBlockState());
                        continue;
                    }
                    if (y == 1 && x == 1 && z == 0) {
                        helper.setBlock(pos, ModBlocks.ARCFORGE_FURNACE_PORT.get().defaultBlockState().setValue(ArcforgeFurnacePortBlock.FACING, Direction.NORTH));
                    } else {
                        helper.setBlock(pos, corner ? ModBlocks.ARCFORGE_FURNACE_BRICK_WALL.get() : ModBlocks.ARCFORGE_FURNACE_BRICKS.get());
                    }
                }
            }
        }
    }

    // The cross pattern forms; a top brick then accepts items from above; losing a wall or the hearth breaks it.
    public static void furnaceForms(GameTestHelper helper) {
        buildFurnace(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcforgeFurnaceBlockEntity furnace = helper.getBlockEntity(new BlockPos(1, 1, 0), ArcforgeFurnaceBlockEntity.class);
                    BlockPos wrong = ArcforgeFurnaceStructure.firstMismatch(helper.getLevel(), furnace.getBlockPos(), furnace.getFacing());
                    helper.assertTrue(furnace.isFormed(), "Furnace did not form; first mismatch at "
                            + (wrong == null ? "none" : helper.relativePos(wrong) + " = " + helper.getLevel().getBlockState(wrong)));
                    BlockPos topBrick = helper.absolutePos(new BlockPos(0, 6, 1));
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, topBrick, Direction.UP) != null,
                            "Top brick does not accept items");
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, topBrick, Direction.EAST) == null,
                            "Inner face of a brick exposes items");
                })
                .thenExecute(() -> helper.setBlock(new BlockPos(2, 4, 2), Blocks.AIR))
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(!helper.getBlockEntity(new BlockPos(1, 1, 0), ArcforgeFurnaceBlockEntity.class).isFormed(),
                        "Furnace still formed without a corner wall"))
                .thenExecute(() -> {
                    helper.setBlock(new BlockPos(2, 4, 2), ModBlocks.ARCFORGE_FURNACE_BRICK_WALL.get());
                    helper.setBlock(new BlockPos(1, 1, 1), Blocks.AIR);
                })
                .thenIdle(25)
                .thenExecute(() -> helper.assertTrue(!helper.getBlockEntity(new BlockPos(1, 1, 0), ArcforgeFurnaceBlockEntity.class).isFormed(),
                        "Furnace formed without its hearth brick"))
                .thenExecute(() -> helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.ARCFORGE_FURNACE_BRICKS.get()))
                .thenIdle(25)
                .thenExecute(() -> helper.assertTrue(helper.getBlockEntity(new BlockPos(1, 1, 0), ArcforgeFurnaceBlockEntity.class).isFormed(),
                        "Furnace did not re-form with its hearth brick back"))
                .thenSucceed();
    }

    // Iron and coke go in; once hot enough the furnace makes steel and slag.
    public static void furnaceSmelts(GameTestHelper helper) {
        buildFurnace(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcforgeFurnaceBlockEntity furnace = helper.getBlockEntity(new BlockPos(1, 1, 0), ArcforgeFurnaceBlockEntity.class);
                    ResourceHandler<ItemResource> items = furnace.getItemHandler(null);
                    try (Transaction tx = Transaction.openRoot()) {
                        helper.assertTrue(items.insert(ItemResource.of(Items.IRON_INGOT), 1, tx) == 1, "Furnace refused iron");
                        helper.assertTrue(items.insert(ItemResource.of(ModItems.COAL_COKE.get()), 3, tx) == 3, "Furnace refused coal coke");
                        tx.commit();
                    }
                })
                .thenIdle(40)
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 0)).getValue(ArcforgeFurnacePortBlock.LIT),
                        "Port is not lit while burning"))
                .thenExecuteAfter(1_000, () -> {
                    ArcforgeFurnaceBlockEntity furnace = helper.getBlockEntity(new BlockPos(1, 1, 0), ArcforgeFurnaceBlockEntity.class);
                    ResourceHandler<ItemResource> items = furnace.getItemHandler(null);
                    try (Transaction tx = Transaction.openRoot()) {
                        helper.assertTrue(items.extract(ItemResource.of(ModItems.STEEL_INGOT.get()), 1, tx) == 1, "No steel ingot was made");
                        helper.assertTrue(items.extract(ItemResource.of(ModItems.SLAG.get()), 1, tx) == 1, "No slag was made");
                    }
                })
                .thenSucceed();
    }
}
