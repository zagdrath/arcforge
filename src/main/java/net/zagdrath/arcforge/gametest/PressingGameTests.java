/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.machine.MetalPressBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.MetalPressingArrayBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Metal Press and Metal Pressing Array, their dies and the steel parts they make. Machines are
// placed facing north.
public final class PressingGameTests {
    private PressingGameTests() {}

    private static ItemStack steel(int count) {
        return new ItemStack(ModItems.STEEL_INGOT.get(), count);
    }

    private static int extract(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = handler.extract(ItemResource.of(item), count, tx);
            tx.commit();
            return extracted;
        }
    }

    // Plate: 1 ingot -> 1 plate. Gear: 4 ingots -> 1 gear (3 aren't enough). Rod: 1 ingot -> 2 rods.
    // The die slot takes dies only; automation never reaches it, and only feeds what the die presses.
    static void pressSlotsAndRecipes(GameTestHelper helper) {
        var level = helper.getLevel();
        Object[][] cases = {
                { ModItems.PLATE_DIE.get(), 1, ModItems.STEEL_PLATE.get(), 1 },
                { ModItems.GEAR_DIE.get(), 4, ModItems.STEEL_GEAR.get(), 1 },
                { ModItems.ROD_DIE.get(), 1, ModItems.STEEL_ROD.get(), 2 },
        };
        for (Object[] c : cases) {
            ItemStack die = new ItemStack((Item) c[0]);
            var recipe = MachineRecipes.pressing(level, die, steel((int) c[1])).orElse(null);
            helper.assertTrue(recipe != null, "No recipe for " + die);
            ItemStack result = recipe.value().result().create();
            helper.assertTrue(recipe.value().count() == (int) c[1], die + " uses " + recipe.value().count() + " ingots");
            helper.assertTrue(result.is((Item) c[2]) && result.getCount() == (int) c[3], die + " makes " + result);
        }
        helper.assertTrue(MachineRecipes.pressing(level, new ItemStack(ModItems.GEAR_DIE.get()), steel(3)).isEmpty(), "Three ingots made a gear");
        helper.assertTrue(MachineRecipes.pressing(level, ItemStack.EMPTY, steel(1)).isEmpty(), "Pressed without a die");

        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.METAL_PRESS.get());
        MetalPressBlockEntity press = helper.getBlockEntity(pos, MetalPressBlockEntity.class);
        var items = press.getItems();
        helper.assertTrue(items.isValid(MetalPressBlockEntity.SLOT_DIE, ItemResource.of(ModItems.ROD_DIE.get())), "Die slot refuses a die");
        helper.assertTrue(!items.isValid(MetalPressBlockEntity.SLOT_DIE, ItemResource.of(ModItems.STEEL_INGOT.get())), "Die slot takes an ingot");
        helper.assertTrue(items.isValid(MetalPressBlockEntity.SLOT_INPUT, ItemResource.of(ModItems.STEEL_INGOT.get())), "Input refuses steel");
        helper.assertTrue(!items.isValid(MetalPressBlockEntity.SLOT_INPUT, ItemResource.of(Items.STONE)), "Input takes stone");
        helper.assertTrue(new ItemStack(ModItems.PLATE_DIE.get()).getMaxStackSize() == 1, "Dies stack");

        // No die: pipes put nothing in. A plate die: steel goes in, but a die never does.
        var top = press.getItemHandler(Direction.UP);
        helper.assertTrue(FiberGameTests.insert(top, ModItems.STEEL_INGOT.get(), 8) == 0, "Automation fed a press with no die");
        items.setStack(MetalPressBlockEntity.SLOT_DIE, new ItemStack(ModItems.PLATE_DIE.get()));
        helper.assertTrue(FiberGameTests.insert(top, ModItems.STEEL_INGOT.get(), 8) == 8, "Automation refused steel for a plate die");
        helper.assertTrue(FiberGameTests.insert(press.getItemHandler(null), ModItems.GEAR_DIE.get(), 1) == 0, "Automation put a die in");
        helper.assertTrue(extract(press.getItemHandler(null), ModItems.PLATE_DIE.get(), 1) == 0, "Automation took the die out");
        helper.assertTrue(extract(press.getItemHandler(Direction.DOWN), ModItems.PLATE_DIE.get(), 1) == 0, "Output face gave the die");
        helper.succeed();
    }

    // No die: "No die". With a plate die it presses (lit), 2,000 FE and 100 ticks a plate; pulling the die
    // out mid-operation starts it over.
    static void pressPresses(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.METAL_PRESS.get());
        MetalPressBlockEntity press = helper.getBlockEntity(pos, MetalPressBlockEntity.class);
        var items = press.getItems();
        FiberGameTests.charge(press.getEnergy(), 20_000);
        items.setStack(MetalPressBlockEntity.SLOT_INPUT, steel(2));
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(press.getStatus() == MachineStatus.NO_DIE, "Press without a die is " + press.getStatus());
                    items.setStack(MetalPressBlockEntity.SLOT_DIE, new ItemStack(ModItems.PLATE_DIE.get()));
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(press.getStatus() == MachineStatus.PRESSING, "Press is " + press.getStatus());
                    helper.assertTrue(helper.getBlockState(pos).getValue(MachineBlock.LIT), "Press is not lit while pressing");
                    helper.assertTrue(press.getLane().getTotal() == 100, "An operation takes " + press.getLane().getTotal() + " ticks");
                    helper.assertTrue(press.getLane().getProgress() > 20, "Progress is " + press.getLane().getProgress());
                    items.setStack(MetalPressBlockEntity.SLOT_DIE, ItemStack.EMPTY);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(press.getLane().getProgress() == 0, "Progress kept without the die");
                    helper.assertTrue(press.getStatus() == MachineStatus.NO_DIE, "Press is " + press.getStatus());
                    items.setStack(MetalPressBlockEntity.SLOT_DIE, new ItemStack(ModItems.PLATE_DIE.get()));
                    FiberGameTests.charge(press.getEnergy(), 20_000);
                })
                .thenWaitUntil(() -> helper.assertTrue(items.getStack(MetalPressBlockEntity.SLOT_OUTPUT).getCount() == 2, "Not two plates yet"))
                .thenExecute(() -> {
                    helper.assertTrue(items.getStack(MetalPressBlockEntity.SLOT_OUTPUT).is(ModItems.STEEL_PLATE.get()), "Output is not steel plate");
                    helper.assertTrue(items.getStack(MetalPressBlockEntity.SLOT_INPUT).isEmpty(), "Ingots left over");
                    helper.assertTrue(items.getStack(MetalPressBlockEntity.SLOT_DIE).is(ModItems.PLATE_DIE.get()), "The die was used up");
                    int used = 20_000 - press.getEnergy().getAmountAsInt();
                    helper.assertTrue(used == 4_000, "Two plates used " + used + " FE");
                })
                .thenSucceed();
    }

    // Energy upgrades cut the FE per tick (so per operation); Speed upgrades shorten the operation and
    // draw FE as much faster.
    static void pressUpgrades(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.METAL_PRESS.get());
        MetalPressBlockEntity press = helper.getBlockEntity(pos, MetalPressBlockEntity.class);
        var items = press.getItems();
        int base = press.energyPerTick();
        items.setStack(items.getFirstUpgradeSlot(), new ItemStack(ModItems.ENERGY_UPGRADE.get(), 2));
        helper.assertTrue(press.upgrades(UpgradeType.ENERGY) == 2, "Energy upgrades not counted");
        int efficient = press.energyPerTick();
        helper.assertTrue(efficient < base, "Energy upgrades use " + efficient + " FE/t, base " + base);
        helper.assertTrue(!items.isValid(items.getFirstUpgradeSlot() + 1, ItemResource.of(ModItems.HEAT_UPGRADE.get())), "Press takes Heat upgrades");
        items.setStack(items.getFirstUpgradeSlot() + 1, new ItemStack(ModItems.SPEED_UPGRADE.get(), 2));
        helper.assertTrue(press.energyPerTick() > efficient, "Speed upgrades don't draw FE faster");

        FiberGameTests.charge(press.getEnergy(), 20_000);
        items.setStack(MetalPressBlockEntity.SLOT_DIE, new ItemStack(ModItems.PLATE_DIE.get()));
        items.setStack(MetalPressBlockEntity.SLOT_INPUT, steel(1));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(press.getLane().getTotal() > 0 && press.getLane().getTotal() < 100,
                        "Upgraded operation takes " + press.getLane().getTotal() + " ticks"))
                .thenSucceed();
    }

    private static void buildArray(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(2, 3, 2))) {
            helper.setBlock(pos.immutable(), ModBlocks.METAL_PRESSING_ARRAY_CASING.get());
        }
    }

    // 27 casings form it. Input faces refuse items no lane's die presses, feed only lanes whose die
    // presses the item (the one holding fewest first), and never reach a die slot.
    static void arrayRoutesInput(GameTestHelper helper) {
        buildArray(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    MetalPressingArrayBlockEntity array = helper.getBlockEntity(new BlockPos(1, 2, 1), MetalPressingArrayBlockEntity.class);
                    helper.assertTrue(array.isFormed(), "Array did not form");
                    var items = array.getItems();
                    var input = array.getItemHandler((SideMode) null);
                    helper.assertTrue(FiberGameTests.insert(input, ModItems.STEEL_INGOT.get(), 4) == 0, "Fed an array with no dies");
                    helper.assertTrue(array.getStatus() == MachineStatus.NO_DIE, "Array without dies is " + array.getStatus());

                    items.setStack(MetalPressingArrayBlockEntity.dieSlot(1), new ItemStack(ModItems.GEAR_DIE.get()));
                    items.setStack(MetalPressingArrayBlockEntity.dieSlot(2), new ItemStack(ModItems.ROD_DIE.get()));
                    items.setStack(MetalPressingArrayBlockEntity.inputSlot(2), steel(3));
                    helper.assertTrue(FiberGameTests.insert(input, ModItems.STEEL_INGOT.get(), 4) == 4, "Refused steel with steel dies in");
                    helper.assertTrue(items.getStack(MetalPressingArrayBlockEntity.inputSlot(0)).isEmpty(), "Fed the lane with no die");
                    helper.assertTrue(items.getStack(MetalPressingArrayBlockEntity.inputSlot(1)).getCount() == 4, "Did not fill the emptier lane first");
                    helper.assertTrue(items.getStack(MetalPressingArrayBlockEntity.inputSlot(2)).getCount() == 3, "Fed the fuller lane");
                    helper.assertTrue(FiberGameTests.insert(input, ModItems.PLATE_DIE.get(), 1) == 0, "Automation put a die in");
                    helper.assertTrue(extract(input, ModItems.ROD_DIE.get(), 1) == 0, "Automation took a die out");
                })
                .thenSucceed();
    }

    // Plate, gear and rod lanes run side by side, each in 50 ticks for 800 FE.
    static void arrayPressesLanes(GameTestHelper helper) {
        buildArray(helper);
        BlockPos center = new BlockPos(1, 2, 1);
        Item[] dies = { ModItems.PLATE_DIE.get(), ModItems.GEAR_DIE.get(), ModItems.ROD_DIE.get() };
        int[] ingots = { 1, 4, 1 };
        Item[] products = { ModItems.STEEL_PLATE.get(), ModItems.STEEL_GEAR.get(), ModItems.STEEL_ROD.get() };
        int[] counts = { 1, 1, 2 };
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    MetalPressingArrayBlockEntity array = helper.getBlockEntity(center, MetalPressingArrayBlockEntity.class);
                    FiberGameTests.charge(array.getEnergy(), 100_000);
                    for (int lane = 0; lane < 3; lane++) {
                        array.getItems().setStack(MetalPressingArrayBlockEntity.dieSlot(lane), new ItemStack(dies[lane]));
                        array.getItems().setStack(MetalPressingArrayBlockEntity.inputSlot(lane), steel(ingots[lane]));
                    }
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    MetalPressingArrayBlockEntity array = helper.getBlockEntity(center, MetalPressingArrayBlockEntity.class);
                    helper.assertTrue(array.getStatus() == MachineStatus.PRESSING, "Array is " + array.getStatus());
                    for (int lane = 0; lane < 3; lane++) {
                        helper.assertTrue(array.getLane(lane).getTotal() == 50, "Lane " + lane + " takes " + array.getLane(lane).getTotal() + " ticks");
                    }
                })
                .thenIdle(50)
                .thenExecute(() -> {
                    MetalPressingArrayBlockEntity array = helper.getBlockEntity(center, MetalPressingArrayBlockEntity.class);
                    for (int lane = 0; lane < 3; lane++) {
                        ItemStack out = array.getItems().getStack(MetalPressingArrayBlockEntity.outputSlot(lane));
                        helper.assertTrue(out.is(products[lane]) && out.getCount() == counts[lane], "Lane " + lane + " made " + out);
                        helper.assertTrue(array.getItems().getStack(MetalPressingArrayBlockEntity.dieSlot(lane)).is(dies[lane]), "Lane " + lane + " lost its die");
                    }
                    int used = 100_000 - array.getEnergy().getAmountAsInt();
                    helper.assertTrue(used == 3 * 800, "Three operations used " + used + " FE");
                })
                .thenSucceed();
    }
}
