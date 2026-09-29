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
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.multiblock.ArcCrushingArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.machine.ArcCrusherBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// Upgrades, the Arc Crusher and the Arc Crushing Array.
public final class CrushingGameTests {
    private CrushingGameTests() {}

    static void charge(EnergyHandler energy, int amount) {
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

    static int insert(ResourceHandler<ItemResource> handler, Item item, int count) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = handler.insert(ItemResource.of(item), count, tx);
            tx.commit();
            return inserted;
        }
    }

    static void install(MachineItemHandler items, Item upgrade, int count) {
        for (int slot = items.getFirstUpgradeSlot(); slot < items.size(); slot++) {
            if (items.getStack(slot).isEmpty()) {
                items.setStack(slot, new ItemStack(upgrade, count));
                return;
            }
        }
    }

    // Upgrade slots hold up to 8 of one accepted type each, and a type can't be split across slots.
    static void upgradeSlots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.ARC_CRUSHER.get());
        MachineItemHandler items = helper.getBlockEntity(pos, ArcCrusherBlockEntity.class).getItems();
        int first = items.getFirstUpgradeSlot();
        helper.assertTrue(items.size() - first == 4, "Machine has " + (items.size() - first) + " upgrade slots, expected 4");
        helper.assertTrue(!items.isValid(first, ItemResource.of(ModItems.HEAT_UPGRADE.get())), "Arc Crusher accepted a Heat upgrade");
        try (Transaction tx = Transaction.openRoot()) {
            int speed = items.insert(first, ItemResource.of(ModItems.SPEED_UPGRADE.get()), 16, tx);
            helper.assertTrue(speed == 8, "Upgrade slot took " + speed + " Speed upgrades, expected 8");
            int second = items.insert(first + 1, ItemResource.of(ModItems.SPEED_UPGRADE.get()), 1, tx);
            helper.assertTrue(second == 0, "A second slot took Speed upgrades");
            int energy = items.insert(first + 1, ItemResource.of(ModItems.ENERGY_UPGRADE.get()), 8, tx);
            helper.assertTrue(energy == 8, "Energy upgrades refused");
            tx.commit();
        }
        helper.succeed();
    }

    // 8 Speed upgrades make a 200-tick recipe take 13 ticks; 8 Energy upgrades cut the FE per operation to about a fifth.
    static void crusherUpgrades(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.ARC_CRUSHER.get());
        ArcCrusherBlockEntity crusher = helper.getBlockEntity(pos, ArcCrusherBlockEntity.class);
        int base = crusher.energyPerTick();
        install(crusher.getItems(), ModItems.ENERGY_UPGRADE.get(), 8);
        int efficient = crusher.energyPerTick();
        helper.assertTrue(base == 20 && efficient <= 4, "FE/t is " + base + " base, " + efficient + " with 8 Energy upgrades");
        install(crusher.getItems(), ModItems.SPEED_UPGRADE.get(), 8);
        charge(crusher.getEnergy(), 20_000);
        insert(crusher.getItemHandler(null), Items.RAW_IRON, 1);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(crusher.getLane().getTotal() == 13, "Operation takes " + crusher.getLane().getTotal() + " ticks, expected 13"))
                .thenIdle(15)
                .thenExecute(() -> helper.assertTrue(crusher.getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT).is(ModItems.IRON_DUST.get()),
                        "No iron dust after an upgraded operation"))
                .thenSucceed();
    }

    // Heat upgrades raise a Firebox's heat per tick up to 2x.
    static void fireboxHeatUpgrades(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 0);
        helper.setBlock(pos, ModBlocks.FIREBOX.get());
        FireboxBlockEntity firebox = helper.getBlockEntity(pos, FireboxBlockEntity.class);
        install(firebox.getItems(), ModItems.HEAT_UPGRADE.get(), 8);
        insert(firebox.getItemHandler(null), Items.COAL, 1);
        int[] before = new int[1];
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> before[0] = firebox.getHeat().getStored())
                .thenIdle(10)
                .thenExecute(() -> {
                    int made = firebox.getHeat().getStored() - before[0];
                    // The configured HU/t, doubled, for 10 ticks.
                    int expected = net.zagdrath.arcforge.config.ArcforgeConfig.FIREBOX_HEAT_PER_TICK.getAsInt() * 2 * 10;
                    helper.assertTrue(made == expected, "Firebox with 8 Heat upgrades made " + made + " HU in 10 ticks, expected " + expected);
                })
                .thenSucceed();
    }

    // Raw iron gives iron dust, iron ore two, coal carbon dust.
    static void crusherRecipes(GameTestHelper helper) {
        BlockPos[] pos = { new BlockPos(0, 1, 0), new BlockPos(2, 1, 0), new BlockPos(4, 1, 0) };
        Item[] inputs = { Items.RAW_IRON, Items.IRON_ORE, Items.COAL };
        Item[] outputs = { ModItems.IRON_DUST.get(), ModItems.IRON_DUST.get(), ModItems.CARBON_DUST.get() };
        int[] counts = { 1, 2, 1 };
        for (int i = 0; i < 3; i++) {
            helper.setBlock(pos[i], ModBlocks.ARC_CRUSHER.get());
            ArcCrusherBlockEntity crusher = helper.getBlockEntity(pos[i], ArcCrusherBlockEntity.class);
            charge(crusher.getEnergy(), 20_000);
            helper.assertTrue(insert(crusher.getItemHandler(Direction.UP), inputs[i], 1) == 1, "Top face refused " + inputs[i]);
        }
        helper.startSequence()
                .thenIdle(205)
                .thenExecute(() -> {
                    for (int i = 0; i < 3; i++) {
                        ItemStack out = helper.getBlockEntity(pos[i], ArcCrusherBlockEntity.class).getItems().getStack(ArcCrusherBlockEntity.SLOT_OUTPUT);
                        helper.assertTrue(out.is(outputs[i]) && out.getCount() == counts[i], inputs[i] + " gave " + out);
                    }
                })
                .thenSucceed();
    }

    private static void buildArray(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(2, 3, 2))) {
            helper.setBlock(pos.immutable(), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        }
    }

    private static ArcCrushingArrayCasingBlock.Part part(GameTestHelper helper, int x, int y, int z) {
        return helper.getBlockState(new BlockPos(x, y, z)).getValue(ArcCrushingArrayCasingBlock.PART);
    }

    // 27 casings form the Array (centre drawn, the rest hidden) and let light through; losing one unforms it.
    static void arrayForms(GameTestHelper helper) {
        buildArray(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(part(helper, 1, 2, 1) == ArcCrushingArrayCasingBlock.Part.CENTER, "Centre is " + part(helper, 1, 2, 1));
                    helper.assertTrue(part(helper, 0, 1, 0) == ArcCrushingArrayCasingBlock.Part.OTHER, "Corner is " + part(helper, 0, 1, 0));
                    helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 1)).getLightDampening() == 0, "Formed casings block light");
                    // Only an input port in the middle of the top takes items, not the rest of the top.
                    BlockPos top = helper.absolutePos(new BlockPos(1, 3, 1));
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class);
                    MultiblockPorts.set(helper.getLevel(), array, top, SideMode.INPUT, Direction.UP);
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, top, Direction.UP) != null, "Top port does not accept items");
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(new BlockPos(0, 3, 2)), Direction.UP) == null,
                            "A top casing that isn't a port accepts items");
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, top, Direction.DOWN) == null, "Inner face exposes items");
                })
                .thenExecute(() -> helper.setBlock(new BlockPos(2, 3, 2), Blocks.AIR))
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(part(helper, 1, 2, 1) == ArcCrushingArrayCasingBlock.Part.NONE, "Still formed after a casing was broken");
                    helper.assertTrue(helper.getBlockState(new BlockPos(1, 2, 1)).getLightDampening() == 15, "Loose casing lets light through");
                })
                .thenSucceed();
    }

    // Ore recipes give double; ingots don't.
    static void arrayDoublesOres(GameTestHelper helper) {
        buildArray(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class);
                    charge(array.getEnergy(), 100_000);
                    array.getItems().setStack(ArcCrushingArrayBlockEntity.inputSlot(0), new ItemStack(Items.RAW_IRON));
                    array.getItems().setStack(ArcCrushingArrayBlockEntity.inputSlot(1), new ItemStack(Items.IRON_INGOT));
                    array.getItems().setStack(ArcCrushingArrayBlockEntity.inputSlot(2), new ItemStack(Items.IRON_ORE));
                })
                .thenIdle(105)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class);
                    int[] expected = { 2, 1, 4 };
                    for (int lane = 0; lane < 3; lane++) {
                        ItemStack out = array.getItems().getStack(ArcCrushingArrayBlockEntity.outputSlot(lane));
                        helper.assertTrue(out.is(ModItems.IRON_DUST.get()) && out.getCount() == expected[lane],
                                "Lane " + lane + " made " + out + ", expected " + expected[lane] + " iron dust");
                    }
                })
                .thenSucceed();
    }

    // Nether gold ore uses its own recipe (one dust, doubled), not the gold ore one; gravel gives one sand, not doubled.
    static void arrayVanillaRecipes(GameTestHelper helper) {
        buildArray(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class);
                    charge(array.getEnergy(), 100_000);
                    array.getItems().setStack(ArcCrushingArrayBlockEntity.inputSlot(0), new ItemStack(Items.NETHER_GOLD_ORE));
                    array.getItems().setStack(ArcCrushingArrayBlockEntity.inputSlot(1), new ItemStack(Items.GRAVEL));
                })
                .thenIdle(105)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class);
                    ItemStack gold = array.getItems().getStack(ArcCrushingArrayBlockEntity.outputSlot(0));
                    helper.assertTrue(gold.is(ModItems.GOLD_DUST.get()) && gold.getCount() == 2, "Nether gold ore made " + gold + ", expected 2 gold dust");
                    ItemStack sand = array.getItems().getStack(ArcCrushingArrayBlockEntity.outputSlot(1));
                    helper.assertTrue(sand.is(Items.SAND) && sand.getCount() == 1, "Gravel made " + sand + ", expected 1 sand");
                    ItemStack flint = array.getItems().getStack(ArcCrushingArrayBlockEntity.bonusSlot(1));
                    helper.assertTrue(flint.isEmpty() || flint.is(Items.FLINT), "Gravel's bonus was " + flint + ", expected flint or nothing");
                })
                .thenSucceed();
    }
}
