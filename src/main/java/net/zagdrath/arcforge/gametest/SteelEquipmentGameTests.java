/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;

// Steel tools and armour, and the Hammer and Excavator's 3x3. Every wall stands in the plane x = 2, across
// y 1..3 and z 0..2, centred on (2, 2, 1); the player mines it from two blocks west, facing it.
public final class SteelEquipmentGameTests {
    private static final BlockPos CENTRE = new BlockPos(2, 2, 1);

    // The absolute position a test's break-event listener cancels (see areaBreakEventCancel), if any.
    private static volatile @Nullable BlockPos cancelAt;
    private static boolean listening;

    private SteelEquipmentGameTests() {}

    // Fills the 3x3 wall with `block`.
    private static void wall(GameTestHelper helper, Block block) {
        BlockPos.betweenClosed(CENTRE.offset(0, -1, -1), CENTRE.offset(0, 1, 1)).forEach(pos -> helper.setBlock(pos.immutable(), block));
    }

    // A survival player two blocks west of the centre, facing it, holding `tool`; breaks the centre.
    @SuppressWarnings("removal")
    private static ServerPlayer mine(GameTestHelper helper, ItemStack tool, boolean sneaking) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(CENTRE.west(2).below());
        // Face the centre in world terms (in case the test structure is rotated). A player looks where their head
        // points, which snapTo leaves alone, so turn that too.
        BlockPos centre = helper.absolutePos(CENTRE);
        float yaw = net.minecraft.core.Direction.getApproximateNearest(centre.getX() - stand.getX(), 0, centre.getZ() - stand.getZ()).toYRot();
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, yaw, 0.0F);
        player.setYHeadRot(yaw);
        player.yHeadRotO = yaw;
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        player.setShiftKeyDown(sneaking);
        player.gameMode.destroyBlock(centre);
        return player;
    }

    private static int broken(GameTestHelper helper) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(CENTRE.offset(0, -1, -1), CENTRE.offset(0, 1, 1))) {
            count += helper.getBlockState(pos).isAir() ? 1 : 0;
        }
        return count;
    }

    private static int dropped(GameTestHelper helper, Item item) {
        AABB box = new AABB(helper.absolutePos(CENTRE)).inflate(3);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, box).stream()
                .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    // Iron-level mining, 7.0 speed, 500 uses.
    static void toolLevels(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(ModItems.STEEL_PICKAXE.get());
        for (Block block : new Block[] { Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE, Blocks.REDSTONE_ORE, ModBlocks.SILVER_ORE.get() }) {
            helper.assertTrue(pickaxe.isCorrectToolForDrops(block.defaultBlockState()), "A steel pickaxe can't mine " + block);
        }
        for (Block block : new Block[] { Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.ANCIENT_DEBRIS, ModBlocks.ARCITE_ORE.get(), ModBlocks.DEEPSLATE_ARCITE_ORE.get() }) {
            helper.assertFalse(pickaxe.isCorrectToolForDrops(block.defaultBlockState()), "A steel pickaxe mines " + block);
        }
        helper.assertTrue(pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState()) == 7.0F, "Speed on stone is " + pickaxe.getDestroySpeed(Blocks.STONE.defaultBlockState()));
        helper.assertTrue(pickaxe.getMaxDamage() == 500, "Durability is " + pickaxe.getMaxDamage());
        helper.succeed();
    }

    // 2 / 7 / 5 / 2 armour and 1.0 toughness per piece, repaired with steel ingots.
    static void armorValues(GameTestHelper helper) {
        Item[] pieces = { ModItems.STEEL_HELMET.get(), ModItems.STEEL_CHESTPLATE.get(), ModItems.STEEL_LEGGINGS.get(), ModItems.STEEL_BOOTS.get() };
        int[] armor = { 2, 7, 5, 2 };
        for (int i = 0; i < pieces.length; i++) {
            ItemStack stack = new ItemStack(pieces[i]);
            var modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers();
            double value = modifiers.stream().filter(entry -> entry.attribute().equals(Attributes.ARMOR)).mapToDouble(entry -> entry.modifier().amount()).sum();
            double toughness = modifiers.stream().filter(entry -> entry.attribute().equals(Attributes.ARMOR_TOUGHNESS)).mapToDouble(entry -> entry.modifier().amount()).sum();
            helper.assertTrue(value == armor[i], pieces[i] + " gives " + value + " armour, not " + armor[i]);
            helper.assertTrue(toughness == 1.0, pieces[i] + " gives " + toughness + " toughness");
            helper.assertTrue(stack.isValidRepairItem(new ItemStack(ModItems.STEEL_INGOT.get())), pieces[i] + " doesn't repair with steel");
        }
        helper.succeed();
    }

    // Around the centre stone: a chest (block entity), obsidian (harder) and dirt (not a pickaxe block) stay;
    // bedrock behind the centre is out of the plane. The centre and the 5 other stones break: 6 uses, 6 cobblestone.
    static void hammerValidOnly(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        helper.setBlock(CENTRE.offset(0, 1, -1), Blocks.CHEST);
        helper.setBlock(CENTRE.offset(0, 1, 0), Blocks.OBSIDIAN);
        helper.setBlock(CENTRE.offset(0, -1, 1), Blocks.DIRT);
        helper.setBlock(CENTRE.east(), Blocks.BEDROCK);
        ItemStack hammer = new ItemStack(ModItems.STEEL_HAMMER.get());
        mine(helper, hammer, false);
        helper.assertTrue(broken(helper) == 6, broken(helper) + " blocks broke, not 6");
        helper.assertBlockPresent(Blocks.CHEST, CENTRE.offset(0, 1, -1));
        helper.assertBlockPresent(Blocks.OBSIDIAN, CENTRE.offset(0, 1, 0));
        helper.assertBlockPresent(Blocks.DIRT, CENTRE.offset(0, -1, 1));
        helper.assertBlockPresent(Blocks.BEDROCK, CENTRE.east());
        helper.assertTrue(hammer.getDamageValue() == 6, "The hammer took " + hammer.getDamageValue() + " damage, not 6");
        helper.assertTrue(dropped(helper, Items.COBBLESTONE) == 6, dropped(helper, Items.COBBLESTONE) + " cobblestone dropped, not 6");
        helper.succeed();
    }

    // A furnace in the ring is left alone, contents and all.
    static void hammerSkipsBlockEntities(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        BlockPos furnacePos = CENTRE.offset(0, 0, 1);
        helper.setBlock(furnacePos, Blocks.FURNACE);
        helper.getBlockEntity(furnacePos, FurnaceBlockEntity.class).setItem(0, new ItemStack(Items.IRON_ORE, 5));
        mine(helper, new ItemStack(ModItems.STEEL_HAMMER.get()), false);
        helper.assertBlockPresent(Blocks.FURNACE, furnacePos);
        ItemStack inside = helper.getBlockEntity(furnacePos, FurnaceBlockEntity.class).getItem(0);
        helper.assertTrue(inside.is(Items.IRON_ORE) && inside.getCount() == 5, "The furnace holds " + inside);
        helper.assertTrue(broken(helper) == 8, broken(helper) + " blocks broke, not 8");
        helper.succeed();
    }

    // All 9 of a dirt and gravel wall go, for 9 uses, when the gravel (0.6 hardness) is hit: the dirt (0.5) is
    // no harder. A stone in the ring stays (a shovel isn't good on it).
    static void excavatorDirtAndGravel(GameTestHelper helper) {
        wall(helper, Blocks.DIRT);
        for (BlockPos pos : new BlockPos[] { CENTRE, CENTRE.offset(0, 1, -1), CENTRE.offset(0, 0, 1), CENTRE.offset(0, -1, 0) }) {
            helper.setBlock(pos, Blocks.GRAVEL);
        }
        ItemStack excavator = new ItemStack(ModItems.STEEL_EXCAVATOR.get());
        mine(helper, excavator, false);
        helper.assertTrue(broken(helper) == 9, broken(helper) + " blocks broke, not 9");
        helper.assertTrue(excavator.getDamageValue() == 9, "The excavator took " + excavator.getDamageValue() + " damage, not 9");
        int drops = dropped(helper, Items.DIRT) + dropped(helper, Items.GRAVEL) + dropped(helper, Items.FLINT);
        helper.assertTrue(drops == 9, drops + " dirt, gravel and flint dropped, not 9");

        helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CENTRE)).inflate(3)).forEach(ItemEntity::discard);
        wall(helper, Blocks.DIRT);
        helper.setBlock(CENTRE.offset(0, 1, 1), Blocks.STONE);
        mine(helper, new ItemStack(ModItems.STEEL_EXCAVATOR.get()), false);
        helper.assertBlockPresent(Blocks.STONE, CENTRE.offset(0, 1, 1));
        helper.assertTrue(broken(helper) == 8, broken(helper) + " blocks broke around the stone, not 8");
        helper.succeed();
    }

    // Sneaking breaks only the centre, for 1 use.
    static void sneakSingle(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        ItemStack hammer = new ItemStack(ModItems.STEEL_HAMMER.get());
        mine(helper, hammer, true);
        helper.assertTrue(broken(helper) == 1, broken(helper) + " blocks broke while sneaking");
        helper.assertTrue(helper.getBlockState(CENTRE).isAir(), "The centre didn't break");
        helper.assertTrue(hammer.getDamageValue() == 1, "The hammer took " + hammer.getDamageValue() + " damage, not 1");
        helper.succeed();
    }

    // With 3 uses left: the centre and 2 more break, then the hammer does, and nothing else goes.
    static void durabilityPerBlock(GameTestHelper helper) {
        wall(helper, Blocks.STONE);
        ItemStack hammer = new ItemStack(ModItems.STEEL_HAMMER.get());
        hammer.setDamageValue(hammer.getMaxDamage() - 3);
        ServerPlayer player = mine(helper, hammer, false);
        helper.assertTrue(broken(helper) == 3, broken(helper) + " blocks broke with 3 uses left");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "The hammer didn't break");
        helper.succeed();
    }

    // Another mod cancelling the break of one ring block keeps it, costs nothing, and the rest still go.
    static void breakEventCancel(GameTestHelper helper) {
        synchronized (SteelEquipmentGameTests.class) {
            if (!listening) {
                listening = true;
                NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BreakBlockEvent.class, event -> {
                    if (event.getPos().equals(cancelAt)) {
                        event.setCanceled(true);
                    }
                });
            }
        }
        wall(helper, Blocks.STONE);
        BlockPos protectedPos = CENTRE.offset(0, -1, -1);
        cancelAt = helper.absolutePos(protectedPos);
        ItemStack hammer = new ItemStack(ModItems.STEEL_HAMMER.get());
        try {
            mine(helper, hammer, false);
        } finally {
            cancelAt = null;
        }
        helper.assertBlockPresent(Blocks.STONE, protectedPos);
        helper.assertTrue(broken(helper) == 8, broken(helper) + " blocks broke, not 8");
        helper.assertTrue(hammer.getDamageValue() == 8, "The hammer took " + hammer.getDamageValue() + " damage, not 8");
        helper.succeed();
    }
}
