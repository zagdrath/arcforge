/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.storage.CrateBlock;
import net.zagdrath.arcforge.block.storage.VaultBlock;
import net.zagdrath.arcforge.blockentity.storage.CrateBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.VaultBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.SideSetting;
import net.zagdrath.arcforge.item.tool.WrenchMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.storage.VaultContents;

// Crates and Vaults: tier upgrades in the world and by crafting, what they keep when broken or wrenched, the vault's
// lock, void and stackable-only rules, and moving far more than a stack in and out with conduits and hoppers.
final class CrateVaultGameTests {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private CrateVaultGameTests() {}

    // --- Helpers ---

    private static void useItem(GameTestHelper helper, Player player, ItemStack stack, BlockPos pos, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(pos);
        Vec3 hit = Vec3.atCenterOf(absolute).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        stack.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, absolute, false)));
    }

    private static ItemStack dismantle(GameTestHelper helper, BlockPos pos) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        ConduitGameTests.useWrench(helper, player, pos, new Vec3(0, 0.5, 0), WrenchMode.DISMANTLE);
        var drops = helper.getEntities(EntityTypes.ITEM, pos, 2.0);
        helper.assertTrue(drops.size() == 1, "Wrenching dropped " + drops.size() + " items, expected just the block");
        ItemStack dropped = drops.getFirst().getItem().copy();
        drops.getFirst().discard();
        return dropped;
    }

    private static int insert(VaultBlockEntity vault, Item item, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = vault.getStorage().insert(0, ItemResource.of(item), amount, tx);
            tx.commit();
            return inserted;
        }
    }

    private static int insert(VaultBlockEntity vault, ItemStack stack) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = vault.getStorage().insert(0, ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
            return inserted;
        }
    }

    private static int extract(VaultBlockEntity vault, Item item, int amount) {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = vault.getStorage().extract(0, ItemResource.of(item), amount, tx);
            tx.commit();
            return extracted;
        }
    }

    private static void fillCrate(CrateBlockEntity crate) {
        crate.getItems().setStack(0, new ItemStack(Items.IRON_INGOT, 64));
        crate.getItems().setStack(20, new ItemStack(Items.GOLD_INGOT, 17));
        crate.getItems().setStack(53, new ItemStack(Items.DIAMOND, 3));
    }

    private static void assertFilled(GameTestHelper helper, CrateBlockEntity crate, String what) {
        helper.assertTrue(ItemStack.matches(crate.getItems().getStack(0), new ItemStack(Items.IRON_INGOT, 64))
                && ItemStack.matches(crate.getItems().getStack(20), new ItemStack(Items.GOLD_INGOT, 17))
                && ItemStack.matches(crate.getItems().getStack(53), new ItemStack(Items.DIAMOND, 3)),
                what + ": slots 0/20/53 hold " + crate.getItems().getStack(0) + ", " + crate.getItems().getStack(20) + ", " + crate.getItems().getStack(53));
    }

    // --- Crates ---

    // A Tempered Storage Upgrade turns a Wrought Crate into a Tempered one where it stands: same facing, the same
    // stacks at the same slots, the new slots empty, and its name and face settings kept.
    static void crateUpgradeInWorldKeepsContents(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.crate(ConduitTier.WROUGHT).get().defaultBlockState().setValue(CrateBlock.FACING, Direction.EAST));
        CrateBlockEntity crate = helper.getBlockEntity(POS, CrateBlockEntity.class);
        fillCrate(crate);
        crate.setComponents(DataComponentMap.builder().set(DataComponents.CUSTOM_NAME, Component.literal("Loot")).build());
        crate.setSideMode(RelativeSide.TOP, SideMode.NONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack upgrade = new ItemStack(ModItems.storageUpgrades().getFirst().get());
        useItem(helper, player, upgrade, POS, Direction.UP);

        helper.assertTrue(helper.getBlockState(POS).is(ModBlocks.crate(ConduitTier.TEMPERED).get()), "Block is " + helper.getBlockState(POS));
        helper.assertTrue(helper.getBlockState(POS).getValue(CrateBlock.FACING) == Direction.EAST, "Facing lost");
        CrateBlockEntity upgraded = helper.getBlockEntity(POS, CrateBlockEntity.class);
        helper.assertTrue(upgraded.getItems().size() == 72, "Upgraded crate has " + upgraded.getItems().size() + " slots");
        assertFilled(helper, upgraded, "Upgraded crate");
        for (int slot = 54; slot < 72; slot++) {
            helper.assertTrue(upgraded.getItems().getStack(slot).isEmpty(), "New slot " + slot + " isn't empty");
        }
        helper.assertTrue(Component.literal("Loot").equals(upgraded.components().get(DataComponents.CUSTOM_NAME)), "Name lost");
        helper.assertTrue(upgraded.getSideMode(RelativeSide.TOP) == SideMode.NONE, "Face settings lost");
        helper.assertTrue(upgrade.isEmpty(), "Upgrade wasn't used up");
        helper.assertTrue(helper.getEntities(EntityTypes.ITEM, POS, 2.0).isEmpty(), "Items spilled during the upgrade");
        helper.succeed();
    }

    // A wrenched Wrought Crate crafted into a Tempered one with its alloy keeps the same contents.
    static void crateUpgradeCraftedKeepsContents(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.crate(ConduitTier.WROUGHT).get());
        fillCrate(helper.getBlockEntity(POS, CrateBlockEntity.class));
        ItemStack wrenched = dismantle(helper, POS);
        ItemStack crafted = AlloyGameTests.craft(helper, "crafting/tempered_crate", new String[] { "AAA", "KXK", "AAA" }, Map.of(
                'A', new ItemStack(ModItems.TEMPERED_ALLOY.get()), 'K', new ItemStack(Items.CHEST), 'X', wrenched));
        helper.assertTrue(crafted.is(ModBlocks.crate(ConduitTier.TEMPERED).get().asItem()), "Didn't make a Tempered Crate");
        helper.setBlock(POS, ModBlocks.crate(ConduitTier.TEMPERED).get());
        CrateBlockEntity placed = helper.getBlockEntity(POS, CrateBlockEntity.class);
        placed.applyComponentsFromItemStack(crafted);
        assertFilled(helper, placed, "Crafted crate");
        helper.succeed();
    }

    // Broken, a crate spills its items; wrenched, the dropped crate carries them.
    static void crateBreakSpillsAndWrenchKeeps(GameTestHelper helper) {
        BlockPos broken = new BlockPos(1, 1, 1);
        BlockPos wrenched = new BlockPos(5, 1, 1);
        helper.setBlock(broken, ModBlocks.crate(ConduitTier.WROUGHT).get());
        fillCrate(helper.getBlockEntity(broken, CrateBlockEntity.class));
        helper.destroyBlock(broken);
        var spilled = helper.getEntities(EntityTypes.ITEM, broken, 2.0).stream().map(entity -> entity.getItem()).toList();
        helper.assertTrue(spilled.stream().anyMatch(stack -> stack.is(Items.IRON_INGOT)) && spilled.stream().anyMatch(stack -> stack.is(Items.GOLD_INGOT))
                && spilled.stream().anyMatch(stack -> stack.is(Items.DIAMOND)), "Broken crate spilled " + spilled);

        helper.setBlock(wrenched, ModBlocks.crate(ConduitTier.WROUGHT).get());
        fillCrate(helper.getBlockEntity(wrenched, CrateBlockEntity.class));
        ItemStack dropped = dismantle(helper, wrenched);
        List<ItemStack> held = dropped.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList();
        helper.assertTrue(held.size() == 3, "Wrenched crate carries " + held);
        helper.assertFalse(dropped.canFitInsideContainerItems(), "A filled crate fits inside containers");
        helper.succeed();
    }

    // Shift-clicking into a crate fills slots out of view, and a filled crate or vault can't go in.
    static void crateNoNesting(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.crate(ConduitTier.WROUGHT).get());
        CrateBlockEntity crate = helper.getBlockEntity(POS, CrateBlockEntity.class);
        ItemStack filledCrate = new ItemStack(ModBlocks.crate(ConduitTier.WROUGHT).get());
        filledCrate.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIRT))));
        ItemStack filledVault = new ItemStack(ModBlocks.vault(ConduitTier.WROUGHT).get());
        filledVault.set(ModDataComponents.VAULT_CONTENTS.get(), VaultContents.of(new ItemStack(Items.DIRT), 10, false, false));
        var handler = crate.getItemHandler(null);
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(handler.insert(ItemResource.of(filledCrate), 1, tx) == 0, "A filled crate went into a crate");
            helper.assertTrue(handler.insert(ItemResource.of(filledVault), 1, tx) == 0, "A filled vault went into a crate");
            helper.assertTrue(handler.insert(ItemResource.of(new ItemStack(ModBlocks.crate(ConduitTier.WROUGHT).get())), 1, tx) == 1,
                    "An empty crate didn't go into a crate");
        }
        helper.succeed();
    }

    // --- Vaults ---

    // A Hardened Vault of 50,000 cobblestone, locked and voiding, upgraded in place keeps all of that and its facing.
    static void vaultUpgradeKeepsContents(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.vault(ConduitTier.HARDENED).get().defaultBlockState().setValue(VaultBlock.FACING, Direction.WEST));
        VaultBlockEntity vault = helper.getBlockEntity(POS, VaultBlockEntity.class);
        insert(vault, Items.COBBLESTONE, 50_000);
        vault.setLocked(true);
        vault.setVoidMode(true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack upgrade = new ItemStack(ModItems.storageUpgrades().get(2).get());
        // A Tempered upgrade doesn't fit a Hardened vault.
        useItem(helper, player, new ItemStack(ModItems.storageUpgrades().getFirst().get()), POS, Direction.UP);
        helper.assertTrue(helper.getBlockState(POS).is(ModBlocks.vault(ConduitTier.HARDENED).get()), "The wrong-tier upgrade changed the vault");
        useItem(helper, player, upgrade, POS, Direction.UP);

        helper.assertTrue(helper.getBlockState(POS).is(ModBlocks.vault(ConduitTier.ARCFORGED).get()), "Block is " + helper.getBlockState(POS));
        helper.assertTrue(helper.getBlockState(POS).getValue(VaultBlock.FACING) == Direction.WEST, "Facing lost");
        helper.assertTrue(helper.getBlockState(POS).getValue(VaultBlock.LOCKED) && helper.getBlockState(POS).getValue(VaultBlock.VOID), "Status lights lost");
        VaultBlockEntity upgraded = helper.getBlockEntity(POS, VaultBlockEntity.class);
        helper.assertTrue(upgraded.getAmount() == 50_000 && upgraded.getTemplate().is(Items.COBBLESTONE), "Holds " + upgraded.getAmount() + " " + upgraded.getTemplate());
        helper.assertTrue(upgraded.isLocked() && upgraded.isVoidMode(), "Lock or void lost");
        helper.assertTrue(upgraded.getCapacity() == 262_144, "Capacity is " + upgraded.getCapacity());
        helper.succeed();
    }

    // Locked, an emptied vault keeps its type: other items are refused and its own go back in. Unlocked at 0 it forgets it.
    static void vaultLockKeepsType(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.vault(ConduitTier.WROUGHT).get());
        VaultBlockEntity vault = helper.getBlockEntity(POS, VaultBlockEntity.class);
        insert(vault, Items.IRON_INGOT, 100);
        vault.setLocked(true);
        helper.assertTrue(extract(vault, Items.IRON_INGOT, 1_000) == 100, "Couldn't take the iron out");
        helper.assertTrue(vault.getTemplate().is(Items.IRON_INGOT), "Locked vault forgot its type");
        helper.assertTrue(insert(vault, Items.GOLD_INGOT, 10) == 0, "Locked vault took gold");
        helper.assertTrue(insert(vault, Items.IRON_INGOT, 10) == 10, "Locked vault refused its own item");
        extract(vault, Items.IRON_INGOT, 10);
        vault.setLocked(false);
        helper.assertTrue(vault.getTemplate().isEmpty(), "Unlocked empty vault kept its type");
        helper.assertTrue(insert(vault, Items.GOLD_INGOT, 10) == 10, "Unlocked vault refused gold");
        helper.succeed();
    }

    // Void mode takes everything and destroys what doesn't fit; without it, a full vault takes nothing.
    static void vaultVoidMode(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.vault(ConduitTier.WROUGHT).get());
        VaultBlockEntity vault = helper.getBlockEntity(POS, VaultBlockEntity.class);
        insert(vault, Items.STONE, 4_090);
        vault.setVoidMode(true);
        int taken = insert(vault, Items.STONE, 64);
        helper.assertTrue(taken == 64 && vault.getAmount() == 4_096, "Void took " + taken + ", vault holds " + vault.getAmount());
        vault.setVoidMode(false);
        helper.assertTrue(insert(vault, Items.STONE, 1) == 0, "Full vault took more without void");
        helper.succeed();
    }

    // Unstackable items and filled containers are refused.
    static void vaultRejectsUnstackable(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.vault(ConduitTier.WROUGHT).get());
        VaultBlockEntity vault = helper.getBlockEntity(POS, VaultBlockEntity.class);
        helper.assertTrue(insert(vault, new ItemStack(Items.IRON_SWORD)) == 0, "Vault took a sword");
        ItemStack filledCrate = new ItemStack(ModBlocks.crate(ConduitTier.WROUGHT).get());
        filledCrate.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIRT))));
        helper.assertTrue(insert(vault, filledCrate) == 0, "Vault took a filled crate");
        helper.assertTrue(insert(vault, Items.WATER_BUCKET, 1) == 0, "Vault took a water bucket");
        helper.succeed();
    }

    // The capability reports the real capacity and amounts past a stack.
    static void vaultCapabilityReportsCapacity(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.vault(ConduitTier.WROUGHT).get());
        var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(POS), Direction.UP);
        helper.assertTrue(handler != null, "No item capability on the top");
        helper.assertTrue(handler.getCapacityAsInt(0, ItemResource.of(Items.IRON_INGOT)) == 4_096, "Capacity is " + handler.getCapacityAsInt(0, ItemResource.of(Items.IRON_INGOT)));
        try (Transaction tx = Transaction.openRoot()) {
            handler.insert(ItemResource.of(Items.IRON_INGOT), 1_000, tx);
            tx.commit();
        }
        helper.assertTrue(handler.getAmountAsInt(0) == 1_000, "Amount reads " + handler.getAmountAsInt(0));
        helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(POS), Direction.NORTH) == null,
                "The front exposes items");
        helper.succeed();
    }

    // Comparator: 0 empty, 1 with one item, 15 full.
    static void vaultComparator(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.vault(ConduitTier.WROUGHT).get());
        VaultBlockEntity vault = helper.getBlockEntity(POS, VaultBlockEntity.class);
        helper.assertTrue(vault.getComparatorSignal() == 0, "Empty vault signals " + vault.getComparatorSignal());
        insert(vault, Items.STONE, 1);
        helper.assertTrue(vault.getComparatorSignal() == 1, "One item signals " + vault.getComparatorSignal());
        insert(vault, Items.STONE, 4_095);
        helper.assertTrue(vault.getComparatorSignal() == 15, "Full vault signals " + vault.getComparatorSignal());
        helper.succeed();
    }

    // Right-clicking the front puts in what's held; a second right-click soon after puts in every matching stack.
    static void vaultFrontRightClick(GameTestHelper helper) {
        helper.setBlock(POS, ModBlocks.vault(ConduitTier.WROUGHT).get());
        VaultBlockEntity vault = helper.getBlockEntity(POS, VaultBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
        player.getInventory().setItem(10, new ItemStack(Items.IRON_INGOT, 30));
        player.getInventory().setItem(11, new ItemStack(Items.GOLD_INGOT, 5));
        player.getInventory().setSelectedSlot(0);
        BlockPos absolute = helper.absolutePos(POS);
        BlockHitResult front = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0, -0.5), Direction.NORTH, absolute, false);
        var state = helper.getBlockState(POS);
        state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, front);
        helper.assertTrue(vault.getAmount() == 20, "One click put in " + vault.getAmount());
        state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, front);
        state.useWithoutItem(helper.getLevel(), player, front);
        helper.assertTrue(vault.getAmount() == 50, "Double click put in " + vault.getAmount() + " in total, expected 50");
        helper.assertTrue(player.getInventory().getItem(11).getCount() == 5, "Gold went into an iron vault");
        helper.succeed();
    }

    // A chest's 640 iron goes through an item conduit into a Wrought Vault: far past one stack.
    static void vaultConduitInsertPast64(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 0), Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(0, 1, 0), ChestBlockEntity.class);
        for (int slot = 0; slot < 10; slot++) {
            chest.setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
        }
        helper.setBlock(new BlockPos(4, 1, 0), ModBlocks.vault(ConduitTier.WROUGHT).get());
        ConduitGameTests.placeRun(helper, ConduitType.ITEM, ConduitTier.ARCFORGED, 3);
        ConduitGameTests.setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, SideSetting.OUTPUT);
        ConduitGameTests.setPort(helper, new BlockPos(3, 1, 0), Direction.EAST, SideSetting.INPUT);
        VaultBlockEntity vault = helper.getBlockEntity(new BlockPos(4, 1, 0), VaultBlockEntity.class);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vault.getAmount() == 640 && chest.isEmpty(), "Vault holds " + vault.getAmount()))
                .thenSucceed();
    }

    // A vault's 1,000 iron goes out of its output face through an item conduit into two chests.
    static void vaultConduitExtractPast64(GameTestHelper helper) {
        helper.setBlock(new BlockPos(0, 1, 0), ModBlocks.vault(ConduitTier.WROUGHT).get());
        VaultBlockEntity vault = helper.getBlockEntity(new BlockPos(0, 1, 0), VaultBlockEntity.class);
        vault.setSideMode(RelativeSide.fromDirection(vault.getFacing(), Direction.EAST), SideMode.OUTPUT);
        insert(vault, Items.IRON_INGOT, 1_000);
        helper.setBlock(new BlockPos(4, 1, 0), Blocks.CHEST);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.CHEST);
        ConduitGameTests.placeRun(helper, ConduitType.ITEM, ConduitTier.ARCFORGED, 3);
        ConduitGameTests.setPort(helper, new BlockPos(1, 1, 0), Direction.WEST, SideSetting.OUTPUT);
        ConduitGameTests.setPort(helper, new BlockPos(2, 1, 0), Direction.SOUTH, SideSetting.INPUT);
        ConduitGameTests.setPort(helper, new BlockPos(3, 1, 0), Direction.EAST, SideSetting.INPUT);
        ChestBlockEntity a = helper.getBlockEntity(new BlockPos(4, 1, 0), ChestBlockEntity.class);
        ChestBlockEntity b = helper.getBlockEntity(new BlockPos(2, 1, 1), ChestBlockEntity.class);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vault.getAmount() == 0 && count(a) + count(b) == 1_000,
                        "Vault holds " + vault.getAmount() + ", chests " + count(a) + " + " + count(b)))
                .thenSucceed();
    }

    private static int count(ChestBlockEntity chest) {
        int total = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            total += chest.getItem(slot).getCount();
        }
        return total;
    }

    // Four hoppers (top, both sides and back) feed a vault 192 iron between them.
    static void vaultHopperPast64(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.vault(ConduitTier.WROUGHT).get());
        VaultBlockEntity vault = helper.getBlockEntity(pos, VaultBlockEntity.class);
        // The vault faces north: its front takes nothing.
        Map<BlockPos, Direction> hoppers = Map.of(pos.above(), Direction.DOWN, pos.west(), Direction.EAST, pos.east(), Direction.WEST, pos.south(), Direction.NORTH);
        hoppers.forEach((hopperPos, facing) -> {
            helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, facing));
            helper.getBlockEntity(hopperPos, HopperBlockEntity.class).setItem(0, new ItemStack(Items.IRON_INGOT, 48));
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(vault.getAmount() == 192, "Vault holds " + vault.getAmount()))
                .thenSucceed();
    }
}
