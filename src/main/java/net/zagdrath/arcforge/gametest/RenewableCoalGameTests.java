/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.machine.HydrothermalCarbonizerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.TreeCutterBlockEntity;
import net.zagdrath.arcforge.machine.CombustionFuel;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.tag.ModItemTags;

// Renewable coal: Bio-Coal working as coal (combustion fuel, furnace burn time, Carbonizer and Arc Crusher recipes), the
// biomass tag, the Hydrothermal Carbonizer making Bio-Coal only when hot enough and returning water, and the Tree Cutter
// planting a sapling and felling a whole tree into its inventory.
public final class RenewableCoalGameTests {
    private RenewableCoalGameTests() {}

    // Bio-Coal and its block against coal; the biomass tag; the recipes.
    static void recipes(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack bioCoal = new ItemStack(ModItems.BIO_COAL.get());
        ItemStack bioBlock = new ItemStack(ModItems.BIO_COAL_BLOCK.get());
        helper.assertTrue(CombustionFuel.isFuel(bioCoal) && CombustionFuel.isFuel(bioBlock), "Bio-Coal isn't combustion fuel");
        BlockPos furnacePos = new BlockPos(0, 1, 0);
        helper.setBlock(furnacePos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = helper.getBlockEntity(furnacePos, FurnaceBlockEntity.class);
        int coal = CombustionFuel.vanillaBurnTicks(level, furnace, new ItemStack(Items.COAL));
        int coalBlock = CombustionFuel.vanillaBurnTicks(level, furnace, new ItemStack(Items.COAL_BLOCK));
        helper.assertTrue(coal > 0 && CombustionFuel.vanillaBurnTicks(level, furnace, bioCoal) == coal,
                "Bio-Coal doesn't burn as long as coal (" + CombustionFuel.vanillaBurnTicks(level, furnace, bioCoal) + " vs " + coal + ")");
        helper.assertTrue(CombustionFuel.vanillaBurnTicks(level, furnace, bioBlock) == coalBlock, "A Block of Bio-Coal doesn't burn as long as a coal block");

        var coke = MachineRecipes.carbonizing(level, bioCoal);
        var coalCoke = MachineRecipes.carbonizing(level, new ItemStack(Items.COAL));
        helper.assertTrue(coke.isPresent() && coke.get().value().result().create().is(ModItems.COAL_COKE.get())
                && coke.get().value().byproduct().map(out -> out.fluid().value() == ModFluids.CREOSOTE.get()).orElse(false),
                "Bio-Coal doesn't carbonize into Coal Coke and Creosote");
        helper.assertTrue(coalCoke.isPresent() && coke.get().value().time() == coalCoke.get().value().time()
                && coke.get().value().byproduct().get().amount() == coalCoke.get().value().byproduct().map(out -> out.amount()).orElse(-1),
                "Bio-Coal carbonizes differently from coal");
        helper.assertTrue(MachineRecipes.carbonizing(level, bioBlock).isPresent(), "A Block of Bio-Coal doesn't carbonize");
        var dust = MachineRecipes.crushing(level, bioCoal);
        helper.assertTrue(dust.isPresent() && dust.get().value().result().map(r -> r.create().is(ModItems.CARBON_DUST.get())).orElse(false),
                "Bio-Coal doesn't crush into Carbon Dust");

        for (Item item : new Item[] { Items.WHEAT, Items.WHEAT_SEEDS, Items.OAK_LEAVES, Items.OAK_SAPLING, Items.STICK, Items.VINE, Items.KELP,
                ModItems.PRESS_CAKE.get(), ModItems.COMPOST.get() }) {
            ItemStack stack = new ItemStack(item);
            helper.assertTrue(stack.is(ModItemTags.BIOMASS), item + " isn't biomass");
            helper.assertTrue(MachineRecipes.isHydrothermalInput(level, stack), item + " doesn't go in the Hydrothermal Carbonizer");
        }
        helper.assertTrue(!new ItemStack(Items.COBBLESTONE).is(ModItemTags.BIOMASS), "Cobblestone is biomass");
        var bio = MachineRecipes.hydrothermalCarbonizing(level, new ItemStack(Items.STICK));
        helper.assertTrue(bio.isPresent() && bio.get().value().result().create().is(ModItems.BIO_COAL.get())
                && bio.get().value().waterReturn() < bio.get().value().water(), "Biomass doesn't make Bio-Coal, or returns all its water");
        helper.succeed();
    }

    private static int fill(HydrothermalCarbonizerBlockEntity machine, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = machine.getFluidHandler(Direction.UP).insert(FluidResource.of(Fluids.WATER), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    // Biomass and Water make nothing while cold; with a full buffer (hotter than 200°C) they make one Bio-Coal and some
    // water comes back into the second tank.
    static void hydrothermalMakesBioCoal(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.HYDROTHERMAL_CARBONIZER.get());
        HydrothermalCarbonizerBlockEntity machine = helper.getBlockEntity(pos, HydrothermalCarbonizerBlockEntity.class);
        var recipe = MachineRecipes.hydrothermalCarbonizing(helper.getLevel(), new ItemStack(Items.STICK)).orElseThrow().value();
        int count = recipe.inputCount();
        helper.assertTrue(CrushingGameTests.insert(machine.getItemHandler(Direction.UP), Items.STICK, count) == count, "It refused sticks");
        helper.assertTrue(CrushingGameTests.insert(machine.getItemHandler(Direction.UP), Items.COBBLESTONE, 1) == 0, "It took cobblestone");
        helper.assertTrue(fill(machine, recipe.water()) == recipe.water(), "It refused water");
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(machine.getStatus() == MachineStatus.TOO_COLD, "A cold Hydrothermal Carbonizer is " + machine.getStatus());
                    machine.getHeat().add(machine.getHeat().getCapacity());
                    helper.assertTrue(machine.getHeat().getTemperature() >= HydrothermalCarbonizerBlockEntity.minTemperature(),
                            "A full buffer isn't hot enough");
                })
                .thenWaitUntil(() -> helper.assertTrue(machine.getItems().getStack(HydrothermalCarbonizerBlockEntity.SLOT_OUTPUT).is(ModItems.BIO_COAL.get()),
                        "No Bio-Coal yet"))
                .thenExecute(() -> {
                    helper.assertTrue(machine.getItems().getStack(HydrothermalCarbonizerBlockEntity.SLOT_INPUT).isEmpty(), "It didn't use the biomass");
                    helper.assertTrue(machine.getWater().getAmount() == 0, "It didn't use the water");
                    helper.assertTrue(machine.getReturned().getAmount() == recipe.waterReturn(),
                            machine.getReturned().getAmount() + " mB returned, expected " + recipe.waterReturn());
                })
                .thenSucceed();
    }

    // A Tree Cutter facing south over a 5x5 patch of dirt plants a sapling, then fells a hand-built oak (four logs under
    // natural leaves) into its inventory, leaves and all.
    static void treeCutterPlantsAndFells(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 0);
        // The area: z 1..5, x 0..4, on the machine's level.
        for (int x = 0; x <= 4; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
            }
        }
        helper.setBlock(pos, ModBlocks.TREE_CUTTER.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.SOUTH));
        TreeCutterBlockEntity cutter = helper.getBlockEntity(pos, TreeCutterBlockEntity.class);
        CrushingGameTests.charge(cutter.getEnergy(), 50_000);
        cutter.getItems().setStack(0, new ItemStack(Items.OAK_SAPLING, 1));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    boolean planted = false;
                    for (int x = 0; x <= 4; x++) {
                        for (int z = 1; z <= 5; z++) {
                            planted |= helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.OAK_SAPLING);
                        }
                    }
                    helper.assertTrue(planted, "No sapling planted yet");
                })
                .thenExecute(() -> {
                    helper.assertTrue(cutter.getItems().getStack(0).isEmpty(), "It didn't use the sapling");
                    // Take the planted sapling away again, so it can't grow into a second tree during the test.
                    for (int x = 0; x <= 4; x++) {
                        for (int z = 1; z <= 5; z++) {
                            if (helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.OAK_SAPLING)) {
                                helper.setBlock(new BlockPos(x, 1, z), Blocks.AIR);
                            }
                        }
                    }
                    // A grown tree at the far corner of the area (a planting spot, so nothing is planted there first).
                    BlockPos base = new BlockPos(4, 1, 5);
                    // Leaves with their natural distance to the trunk (the cutter takes the leaves nearest its tree).
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            for (int dy = 3; dy <= 4; dy++) {
                                if (dx == 0 && dz == 0 && dy == 3) {
                                    continue;
                                }
                                int distance = Math.abs(dx) + Math.abs(dz) + (dy - 3);
                                helper.setBlock(base.offset(dx, dy, dz), Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.DISTANCE, distance));
                            }
                        }
                    }
                    for (int y = 0; y < 4; y++) {
                        helper.setBlock(base.above(y), Blocks.OAK_LOG);
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(cutter.getFelled() >= 1, "No tree felled yet"))
                .thenExecute(() -> {
                    helper.assertTrue(cutter.getLastLogs() == 4, cutter.getLastLogs() + " logs in the last tree, expected 4");
                    int logs = 0;
                    int leafBlocks = 0;
                    for (int i = 0; i < TreeCutterBlockEntity.OUTPUT_SLOTS; i++) {
                        ItemStack stack = cutter.getItems().getStack(TreeCutterBlockEntity.FIRST_OUTPUT + i);
                        logs += stack.is(Items.OAK_LOG) ? stack.getCount() : 0;
                        leafBlocks += stack.is(Items.OAK_LEAVES) ? stack.getCount() : 0;
                    }
                    helper.assertTrue(logs == 4, logs + " logs collected, expected 4");
                    helper.assertTrue(leafBlocks > 0, "No leaves collected");
                    for (int y = 1; y <= 5; y++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                var state = helper.getBlockState(new BlockPos(4 + dx, y, 5 + dz));
                                helper.assertTrue(!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES), "Part of the tree is still standing");
                            }
                        }
                    }
                })
                .thenSucceed();
    }
}
