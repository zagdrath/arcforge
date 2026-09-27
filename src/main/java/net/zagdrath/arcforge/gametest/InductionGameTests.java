/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.multiblock.CubeCasingBlock;
import net.zagdrath.arcforge.blockentity.machine.ArcCrusherBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.InductionFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.InductionFurnaceArrayBlockEntity;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;

// The Induction Furnace, the Induction Furnace Array, auto-eject and creosote movement.
public final class InductionGameTests {
    private InductionGameTests() {}

    private static void charge(EnergyHandler energy, int amount) {
        for (int i = 0; i < 1_000 && amount > 0; i++) {
            try (Transaction tx = Transaction.openRoot()) {
                int inserted = energy.insert(amount, tx);
                tx.commit();
                if (inserted <= 0) {
                    return;
                }
                amount -= inserted;
            }
        }
    }

    // Iron dust smelts into an iron ingot in 100 ticks at 20 FE/t, and the recipe's XP is stored.
    static void furnaceSmelts(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.INDUCTION_FURNACE.get());
        InductionFurnaceBlockEntity furnace = helper.getBlockEntity(pos, InductionFurnaceBlockEntity.class);
        helper.assertTrue(!furnace.getItems().isValid(furnace.getItems().getFirstUpgradeSlot(), ItemResource.of(ModItems.HEAT_UPGRADE.get())),
                "Induction Furnace accepted a Heat upgrade");
        charge(furnace.getEnergy(), 20_000);
        furnace.getItems().setStack(InductionFurnaceBlockEntity.SLOT_INPUT, new ItemStack(ModItems.IRON_DUST.get(), 2));
        int[] before = new int[1];
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(furnace.getLane().getTotal() == 100, "Smelting takes " + furnace.getLane().getTotal() + " ticks, expected 100");
                    before[0] = furnace.getEnergy().getAmountAsInt();
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    int used = before[0] - furnace.getEnergy().getAmountAsInt();
                    helper.assertTrue(used == 200, "Used " + used + " FE in 10 ticks, expected 200");
                })
                .thenIdle(95)
                .thenExecute(() -> {
                    ItemStack out = furnace.getItems().getStack(InductionFurnaceBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(out.is(Items.IRON_INGOT) && out.getCount() == 1, "Made " + out + ", expected 1 iron ingot");
                    helper.assertTrue(furnace.getExperience().get() > 0, "No XP stored");
                    helper.assertTrue(helper.getBlockState(pos).getValue(net.zagdrath.arcforge.block.machine.MachineBlock.LIT), "Not lit while smelting");
                })
                .thenSucceed();
    }

    private static void buildArray(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(2, 3, 2))) {
            helper.setBlock(pos.immutable(), ModBlocks.INDUCTION_FURNACE_ARRAY_CASING.get());
        }
    }

    // Three lanes smelt in parallel at 50 ticks each, 16 FE/t a lane.
    static void arraySmelts(GameTestHelper helper) {
        buildArray(helper);
        BlockPos center = new BlockPos(1, 2, 1);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(center).getValue(CubeCasingBlock.PART) == CubeCasingBlock.Part.CENTER, "Array did not form");
                    InductionFurnaceArrayBlockEntity array = helper.getBlockEntity(center, InductionFurnaceArrayBlockEntity.class);
                    charge(array.getEnergy(), 100_000);
                    array.getItems().setStack(InductionFurnaceArrayBlockEntity.inputSlot(0), new ItemStack(ModItems.IRON_DUST.get()));
                    array.getItems().setStack(InductionFurnaceArrayBlockEntity.inputSlot(1), new ItemStack(Items.RAW_GOLD));
                    array.getItems().setStack(InductionFurnaceArrayBlockEntity.inputSlot(2), new ItemStack(Items.IRON_ORE));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    InductionFurnaceArrayBlockEntity array = helper.getBlockEntity(center, InductionFurnaceArrayBlockEntity.class);
                    helper.assertTrue(array.getLane(0).getTotal() == 50, "Lane takes " + array.getLane(0).getTotal() + " ticks, expected 50");
                    helper.assertTrue(array.energyPerTick() == 16, "Lane draws " + array.energyPerTick() + " FE/t, expected 16");
                })
                .thenIdle(52)
                .thenExecute(() -> {
                    InductionFurnaceArrayBlockEntity array = helper.getBlockEntity(center, InductionFurnaceArrayBlockEntity.class);
                    ItemStack[] expected = { new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.IRON_INGOT) };
                    for (int lane = 0; lane < 3; lane++) {
                        ItemStack out = array.getItems().getStack(InductionFurnaceArrayBlockEntity.outputSlot(lane));
                        helper.assertTrue(ItemStack.isSameItem(out, expected[lane]) && out.getCount() == 1,
                                "Lane " + lane + " made " + out + ", expected " + expected[lane]);
                    }
                })
                .thenSucceed();
    }

    // Induction and crusher casings never join: 26 crusher casings and one induction casing form nothing.
    static void arrayCasingsDontMix(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(2, 3, 2))) {
            helper.setBlock(pos.immutable(), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        }
        helper.setBlock(new BlockPos(2, 3, 2), ModBlocks.INDUCTION_FURNACE_ARRAY_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 1)).getValue(CubeCasingBlock.PART) == CubeCasingBlock.Part.NONE,
                            "Mixed casings formed a structure");
                    helper.assertTrue(helper.getBlockState(new BlockPos(2, 3, 2)).getValue(CubeCasingBlock.PART) == CubeCasingBlock.Part.NONE,
                            "Lone induction casing formed");
                })
                .thenSucceed();
    }

    // Auto-eject off: the output waits in the machine. On: it's pushed into the chest under the output face.
    static void autoEject(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        BlockPos chestPos = new BlockPos(1, 1, 1);
        helper.setBlock(chestPos, Blocks.CHEST);
        helper.setBlock(pos, ModBlocks.ARC_CRUSHER.get());
        ArcCrusherBlockEntity crusher = helper.getBlockEntity(pos, ArcCrusherBlockEntity.class);
        crusher.getItems().setStack(ArcCrusherBlockEntity.SLOT_OUTPUT, new ItemStack(ModItems.IRON_DUST.get(), 4));
        helper.assertTrue(!crusher.isAutoEject(), "Auto-eject is on by default");
        helper.startSequence()
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(chestPos, ChestBlockEntity.class).isEmpty(), "Output pushed with auto-eject off");
                    crusher.setAutoEject(true);
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT).isEmpty(), "Output not pushed with auto-eject on");
                    helper.assertTrue(helper.getBlockEntity(chestPos, ChestBlockEntity.class).countItem(ModItems.IRON_DUST.get()) == 4,
                            "Chest did not receive the iron dust");
                })
                .thenSucceed();
    }

    // An entity in creosote moves (it used to freeze in place): it sinks and drifts with its momentum.
    // An armor stand, as a living entity without AI that could swim or wander.
    static void creosoteMoves(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(4, 1, 4))) {
            helper.setBlock(pos.immutable(), Blocks.STONE);
        }
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 2, 1), new BlockPos(3, 3, 3))) {
            helper.setBlock(pos.immutable(), ModBlocks.CREOSOTE.get());
        }
        ArmorStand stand = helper.spawn(EntityTypes.ARMOR_STAND, new Vec3(1.5, 3.2, 2.5));
        Vec3 start = stand.position();
        stand.setDeltaMovement(0.3, 0.0, 0.0);
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    Vec3 now = stand.position();
                    helper.assertTrue(now.x - start.x > 0.1, "Armor stand did not drift through creosote: x " + start.x + " -> " + now.x);
                    helper.assertTrue(now.y < start.y - 0.1, "Armor stand did not sink in creosote: y " + start.y + " -> " + now.y);
                })
                .thenSucceed();
    }
}
