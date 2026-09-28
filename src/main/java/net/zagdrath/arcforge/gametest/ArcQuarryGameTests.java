/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.zagdrath.arcforge.block.machine.ArcQuarryBlock;
import net.zagdrath.arcforge.block.machine.ArcQuarryBoundingBlock;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.quarry.BlockFilter;
import net.zagdrath.arcforge.machine.quarry.QuarrySettings;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;

// The Arc Quarry: placing it as a 3x3x3, its filter and scan, Replace and Silk Touch, what it skips, its pauses, the
// settings' clamps, and the checks it makes before each block.
public final class ArcQuarryGameTests {
    // The quarry stands on (1..3, 3..5, 1..3); it mines a radius-2 square two layers deep under it (y 1 and 2).
    private static final BlockPos BASE = new BlockPos(2, 3, 2);
    private static final BlockPos MAIN = BASE.above();
    private static final int RADIUS = 2;
    private static final int LOW = 1, HIGH = 2;
    // One break-event cancel for quarry_break_event_cancel, set while it runs.
    private static volatile @Nullable BlockPos cancelAt;
    private static boolean listening;

    private ArcQuarryGameTests() {}

    // Clears the quarry's space and its area to air, on a stone floor (the test area outside the structure isn't ours).
    private static void clear(GameTestHelper helper) {
        BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(4, 6, 4)).forEach(pos -> helper.setBlock(pos.immutable(), Blocks.AIR));
        BlockPos.betweenClosed(new BlockPos(0, 0, 0), new BlockPos(4, 0, 4)).forEach(pos -> helper.setBlock(pos.immutable(), Blocks.STONE));
    }

    // Places the quarry with its item, as a player would, clicking BASE (air, so replaceable, which places it right
    // there). Returns the result.
    private static InteractionResult place(GameTestHelper helper) {
        BlockPos against = helper.absolutePos(BASE);
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack stack = new ItemStack(ModItems.ARC_QUARRY.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPlaceContext context = new BlockPlaceContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack,
                new BlockHitResult(Vec3.atCenterOf(against).add(0, 0.5, 0), Direction.UP, against, false));
        return ModItems.ARC_QUARRY.get().place(context);
    }

    // A placed, charged quarry with 8 Speed upgrades (a block a tick), mining the test area.
    private static ArcQuarryBlockEntity quarry(GameTestHelper helper, List<FilterSettings.Entry> filter, boolean deny, boolean silk, boolean replace) {
        helper.assertTrue(place(helper).consumesAction(), "The quarry wasn't placed");
        ArcQuarryBlockEntity quarry = helper.getBlockEntity(MAIN, ArcQuarryBlockEntity.class);
        CrushingGameTests.install(quarry.getItems(), ModItems.SPEED_UPGRADE.get(), 8);
        CrushingGameTests.charge(quarry.getEnergy(), 500_000);
        int low = helper.absolutePos(new BlockPos(0, LOW, 0)).getY();
        int high = helper.absolutePos(new BlockPos(0, HIGH, 0)).getY();
        quarry.setSettings(new QuarrySettings(RADIUS, low, high, filter, deny, silk, replace, false));
        return quarry;
    }

    private static List<FilterSettings.Entry> filter(Object... entries) {
        List<FilterSettings.Entry> list = new ArrayList<>();
        for (Object entry : entries) {
            list.add(entry instanceof Item item ? BlockFilter.of(new ItemStack(item)) : BlockFilter.ofTag(Identifier.parse((String) entry)));
        }
        return list;
    }

    // Every area position (x, z 0..4, y 1..2), in a fixed order.
    private static List<BlockPos> area() {
        List<BlockPos> positions = new ArrayList<>();
        for (int y = LOW; y <= HIGH; y++) {
            for (int z = 0; z <= 4; z++) {
                for (int x = 0; x <= 4; x++) {
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        return positions;
    }

    private static int buffered(ArcQuarryBlockEntity quarry, Item item) {
        int count = 0;
        for (int slot = ArcQuarryBlockEntity.FIRST_BUFFER; slot < ArcQuarryBlockEntity.MACHINE_SLOTS; slot++) {
            ItemStack stack = quarry.getItems().getStack(slot);
            count += stack.is(item) ? stack.getCount() : 0;
        }
        return count;
    }

    private static int count(GameTestHelper helper, List<BlockPos> positions, Block block) {
        return (int) positions.stream().filter(pos -> helper.getBlockState(pos).is(block)).count();
    }

    // Placing needs all 27 spaces clear; placed, it's 1 main block and 26 parts; breaking a corner removes all of it
    // and drops one quarry.
    static void needsRoom(GameTestHelper helper) {
        clear(helper);
        helper.setBlock(BASE.offset(1, 1, 1), Blocks.STONE);
        helper.assertTrue(place(helper) == InteractionResult.FAIL, "It was placed with stone in the way");
        helper.assertBlockPresent(Blocks.AIR, MAIN);
        helper.assertBlockPresent(Blocks.AIR, BASE);
        helper.setBlock(BASE.offset(1, 1, 1), Blocks.AIR);
        helper.assertTrue(place(helper).consumesAction(), "It wasn't placed in a clear space");
        int main = 0, parts = 0;
        for (BlockPos pos : ArcQuarryBlock.positions(MAIN)) {
            Block block = helper.getBlockState(pos).getBlock();
            main += block instanceof ArcQuarryBlock ? 1 : 0;
            parts += block instanceof ArcQuarryBoundingBlock ? 1 : 0;
        }
        helper.assertTrue(main == 1 && parts == 26, main + " main blocks and " + parts + " parts");
        helper.getLevel().destroyBlock(helper.absolutePos(MAIN.offset(1, -1, 1)), true);
        for (BlockPos pos : ArcQuarryBlock.positions(MAIN)) {
            helper.assertBlockPresent(Blocks.AIR, pos);
        }
        // helper.getEntities only looks inside the (tiny) test structure; look around the quarry itself.
        net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(helper.absolutePos(MAIN)).inflate(3.0);
        int dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around).stream()
                .filter(item -> item.getItem().is(ModItems.ARC_QUARRY.get())).mapToInt(item -> item.getItem().getCount()).sum();
        helper.assertTrue(dropped == 1, dropped + " quarries dropped");
        helper.succeed();
    }

    // The machine's shape follows its model: a top corner only has the head's edge to bump into, the base is solid, and the
    // outline (and what can be hit) from any part is the whole machine, not one cube.
    static void shapeFollowsModel(GameTestHelper helper) {
        clear(helper);
        helper.assertTrue(place(helper).consumesAction(), "The quarry wasn't placed");
        var level = helper.getLevel();
        BlockPos corner = helper.absolutePos(MAIN.offset(1, 1, 1));
        BlockPos base = helper.absolutePos(MAIN.offset(0, -1, 0));
        // The head band reaches 7 px into the top corners; the rest of the corner is open.
        var cornerBox = level.getBlockState(corner).getCollisionShape(level, corner).bounds();
        helper.assertTrue(cornerBox.maxX <= 7.0 / 16.0 + 1.0E-6 && cornerBox.maxZ <= 7.0 / 16.0 + 1.0E-6,
                "The top corner's collision box is " + cornerBox);
        helper.assertFalse(level.getBlockState(base).getCollisionShape(level, base).isEmpty(), "The base has no collision box");
        var outline = level.getBlockState(corner).getShape(level, corner).bounds();
        helper.assertTrue(outline.getXsize() > 2.0 && outline.getYsize() > 2.0, "The corner's outline isn't the whole machine: " + outline);
        var main = level.getBlockState(helper.absolutePos(MAIN)).getShape(level, helper.absolutePos(MAIN)).bounds();
        helper.assertTrue(main.maxY <= 2.0 && main.minY >= -1.0 && main.getXsize() < 3.0, "The main shape is " + main);
        helper.succeed();
    }

    // #c:ores picks the iron ore out of the stone; after mining the buffer holds that much raw iron, the stone
    // remains, and (Replace off) the ore's spaces are air.
    static void tagAllowlist(GameTestHelper helper) {
        clear(helper);
        List<BlockPos> area = area();
        area.forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        List<BlockPos> ores = List.of(area.get(3), area.get(11), area.get(27), area.get(40));
        ores.forEach(pos -> helper.setBlock(pos, Blocks.IRON_ORE));
        ArcQuarryBlockEntity quarry = quarry(helper, filter("c:ores"), false, false, false);
        quarry.scanOnly();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.IDLE, "Still scanning"))
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getTargetCount() == ores.size(), "The scan found " + quarry.getTargetCount() + ", not " + ores.size());
                    quarry.toggleRunning();
                })
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.FINISHED, "Not finished: " + quarry.getStatus()))
                .thenExecute(() -> {
                    helper.assertTrue(buffered(quarry, Items.RAW_IRON) == ores.size(), buffered(quarry, Items.RAW_IRON) + " raw iron");
                    helper.assertTrue(count(helper, area, Blocks.STONE) == area.size() - ores.size(), "Stone was mined");
                    ores.forEach(pos -> helper.assertBlockPresent(Blocks.AIR, pos));
                })
                .thenSucceed();
    }

    // A denylist of stone mines everything else.
    static void denylist(GameTestHelper helper) {
        clear(helper);
        List<BlockPos> area = area();
        for (int i = 0; i < area.size(); i++) {
            helper.setBlock(area.get(i), i % 3 == 0 ? Blocks.DIRT : i % 3 == 1 ? Blocks.STONE : Blocks.COBBLESTONE);
        }
        int stone = count(helper, area, Blocks.STONE);
        ArcQuarryBlockEntity quarry = quarry(helper, filter(Items.STONE), true, false, false);
        quarry.toggleRunning();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.FINISHED, "Not finished: " + quarry.getStatus()))
                .thenExecute(() -> {
                    helper.assertTrue(count(helper, area, Blocks.STONE) == stone, "Stone was mined");
                    helper.assertTrue(count(helper, area, Blocks.AIR) == area.size() - stone, "Not everything else was mined");
                    helper.assertTrue(quarry.getMinedCount() == area.size() - stone, quarry.getMinedCount() + " mined");
                })
                .thenSucceed();
    }

    // Replace with 3 cobblestone and 5 targets: 3 are mined and filled, then it waits for more; 2 more finish it.
    static void replaceMode(GameTestHelper helper) {
        clear(helper);
        List<BlockPos> area = area();
        List<BlockPos> ores = List.of(area.get(0), area.get(6), area.get(12), area.get(30), area.get(44));
        ores.forEach(pos -> helper.setBlock(pos, Blocks.IRON_ORE));
        ArcQuarryBlockEntity quarry = quarry(helper, filter(Items.IRON_ORE), false, false, true);
        quarry.getItems().setStack(ArcQuarryBlockEntity.SLOT_REPLACE, new ItemStack(Items.COBBLESTONE, 3));
        quarry.toggleRunning();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(quarry.getStatus() == MachineStatus.OUT_OF_REPLACE, "Status is " + quarry.getStatus()))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getMinedCount() == 3, quarry.getMinedCount() + " mined with 3 cobblestone");
                    helper.assertTrue(count(helper, ores, Blocks.COBBLESTONE) == 3 && count(helper, ores, Blocks.IRON_ORE) == 2,
                            "Not 3 filled and 2 left");
                    quarry.getItems().setStack(ArcQuarryBlockEntity.SLOT_REPLACE, new ItemStack(Items.COBBLESTONE, 2));
                })
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.FINISHED, "Not finished: " + quarry.getStatus()))
                .thenExecute(() -> helper.assertTrue(count(helper, ores, Blocks.COBBLESTONE) == 5, "Not all 5 filled"))
                .thenSucceed();
    }

    // Silk Touch: diamond ore and stone come whole for 2 x 200 x 5 FE; without it, a diamond and cobblestone for 2 x 200.
    static void silkTouch(GameTestHelper helper) {
        clear(helper);
        BlockPos ore = new BlockPos(0, HIGH, 0);
        BlockPos stone = new BlockPos(4, LOW, 4);
        helper.setBlock(ore, Blocks.DIAMOND_ORE);
        helper.setBlock(stone, Blocks.STONE);
        ArcQuarryBlockEntity quarry = quarry(helper, filter(Items.DIAMOND_ORE, Items.STONE), false, true, false);
        int perBlock = ArcforgeConfig.QUARRY_ENERGY_PER_BLOCK.getAsInt();
        int silk = (int) Math.ceil(perBlock * ArcforgeConfig.QUARRY_SILK_MULTIPLIER.getAsDouble());
        int[] start = { quarry.getEnergy().getAmountAsInt() };
        quarry.toggleRunning();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.FINISHED, "Not finished: " + quarry.getStatus()))
                .thenExecute(() -> {
                    helper.assertTrue(buffered(quarry, Items.DIAMOND_ORE) == 1 && buffered(quarry, Items.STONE) == 1, "Silk Touch didn't keep them whole");
                    int used = start[0] - quarry.getEnergy().getAmountAsInt();
                    helper.assertTrue(used == 2 * silk, "Silk Touch used " + used + " FE, not " + 2 * silk);
                    helper.setBlock(ore, Blocks.DIAMOND_ORE);
                    helper.setBlock(stone, Blocks.STONE);
                    quarry.setSettings(quarry.getSettings().withSilkTouch(false));
                    quarry.reset();
                    start[0] = quarry.getEnergy().getAmountAsInt();
                    quarry.toggleRunning();
                })
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.FINISHED && quarry.getMinedCount() == 2,
                        "Not finished again: " + quarry.getStatus()))
                .thenExecute(() -> {
                    helper.assertTrue(buffered(quarry, Items.DIAMOND) == 1 && buffered(quarry, Items.COBBLESTONE) == 1, "No diamond and cobblestone");
                    int used = start[0] - quarry.getEnergy().getAmountAsInt();
                    helper.assertTrue(used == 2 * perBlock, "Mining used " + used + " FE, not " + 2 * perBlock);
                })
                .thenSucceed();
    }

    // A chest, a furnace, water, lava, a multiblock casing and bedrock are never targets, even for an empty denylist.
    static void skipsBlockEntitiesAndFluids(GameTestHelper helper) {
        clear(helper);
        List<BlockPos> area = area();
        helper.setBlock(area.get(0), Blocks.CHEST);
        helper.setBlock(area.get(2), Blocks.FURNACE);
        helper.setBlock(area.get(4), Blocks.WATER);
        helper.setBlock(area.get(20), Blocks.LAVA);
        helper.setBlock(area.get(22), ModBlocks.METAL_PRESSING_ARRAY_CASING.get());
        helper.setBlock(area.get(24), Blocks.BEDROCK);
        helper.setBlock(area.get(30), Blocks.STONE);
        ArcQuarryBlockEntity quarry = quarry(helper, Collections.nCopies(QuarrySettings.FILTER_SIZE, FilterSettings.Entry.EMPTY), true, false, false);
        quarry.toggleRunning();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.FINISHED, "Not finished: " + quarry.getStatus()))
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getTargetCount() == 1 && quarry.getMinedCount() == 1, quarry.getTargetCount() + " targets, "
                            + quarry.getMinedCount() + " mined; only the stone should be");
                    helper.assertBlockPresent(Blocks.CHEST, area.get(0));
                    helper.assertBlockPresent(Blocks.FURNACE, area.get(2));
                    helper.assertBlockPresent(Blocks.WATER, area.get(4));
                    helper.assertBlockPresent(Blocks.LAVA, area.get(20));
                    helper.assertBlockPresent(ModBlocks.METAL_PRESSING_ARRAY_CASING.get(), area.get(22));
                    helper.assertBlockPresent(Blocks.BEDROCK, area.get(24));
                })
                .thenSucceed();
    }

    // A full buffer waits (OUTPUT_FULL) at the same target; so does an empty buffer with no FE (NO_POWER); FE
    // back, it carries on from there.
    static void pausesFullAndUnpowered(GameTestHelper helper) {
        clear(helper);
        List<BlockPos> area = area();
        List<BlockPos> stones = List.of(area.get(5), area.get(15), area.get(35));
        stones.forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        ArcQuarryBlockEntity quarry = quarry(helper, filter(Items.STONE), false, false, false);
        for (int slot = ArcQuarryBlockEntity.FIRST_BUFFER; slot < ArcQuarryBlockEntity.MACHINE_SLOTS; slot++) {
            quarry.getItems().setStack(slot, new ItemStack(Items.DIRT, 64));
        }
        quarry.toggleRunning();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(quarry.getStatus() == MachineStatus.OUTPUT_FULL, "Status is " + quarry.getStatus()))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getTargetIndex() == 0 && quarry.getMinedCount() == 0, "It moved on while full");
                    for (int slot = ArcQuarryBlockEntity.FIRST_BUFFER; slot < ArcQuarryBlockEntity.MACHINE_SLOTS; slot++) {
                        quarry.getItems().setStack(slot, ItemStack.EMPTY);
                    }
                    quarry.getEnergy().consume(quarry.getEnergy().getAmountAsInt());
                })
                .thenWaitUntil(() -> helper.assertTrue(quarry.getStatus() == MachineStatus.NO_POWER, "Status is " + quarry.getStatus()))
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getTargetIndex() == 0 && quarry.getMinedCount() == 0, "It moved on without FE");
                    stones.forEach(pos -> helper.assertBlockPresent(Blocks.STONE, pos));
                    CrushingGameTests.charge(quarry.getEnergy(), 500_000);
                })
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.FINISHED, "Not finished: " + quarry.getStatus()))
                .thenExecute(() -> helper.assertTrue(quarry.getMinedCount() == stones.size(), quarry.getMinedCount() + " mined"))
                .thenSucceed();
    }

    // The area clamps against the config and the level, and each Y bound against the other.
    static void clamps(GameTestHelper helper) {
        var level = helper.getLevel();
        QuarrySettings settings = new QuarrySettings(10, 0, 60, List.of(), false, false, true, false);
        helper.assertTrue(settings.withRadius(99).radius() == ArcforgeConfig.QUARRY_MAX_RADIUS.getAsInt(), "Radius 99 wasn't clamped");
        helper.assertTrue(settings.withMinY(-100, level).minY() == level.getMinY(), "Min Y -100 is " + settings.withMinY(-100, level).minY());
        helper.assertTrue(settings.withMaxY(999, level).maxY() == level.getMaxY(), "Max Y 999 is " + settings.withMaxY(999, level).maxY());
        helper.assertTrue(level.getMinY() == -64 && level.getMaxY() == 319, "The test level runs " + level.getMinY() + " to " + level.getMaxY());
        helper.assertTrue(settings.withMinY(80, level).minY() == 60, "Min Y 80 under max 60 is " + settings.withMinY(80, level).minY());
        QuarrySettings high = new QuarrySettings(10, 20, 60, List.of(), false, false, true, false);
        helper.assertTrue(high.withMaxY(10, level).maxY() == 20, "Max Y 10 over min 20 is " + high.withMaxY(10, level).maxY());
        QuarrySettings area = settings.withArea(99, -100, 999, level);
        long side = 2L * area.radius() + 1;
        helper.assertTrue(area.areaVolume() == side * side * (area.maxY() - area.minY() + 1), "Area volume is " + area.areaVolume());
        helper.succeed();
    }

    // 17 silver ore and 5 deepslate silver ore among stone: #c:ores/silver scans 22, and mines 22.
    static void scanMatchesOre(GameTestHelper helper) {
        clear(helper);
        List<BlockPos> area = area();
        area.forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        for (int i = 0; i < 22; i++) {
            helper.setBlock(area.get(i * 2), i < 17 ? ModBlocks.SILVER_ORE.get() : ModBlocks.DEEPSLATE_SILVER_ORE.get());
        }
        ArcQuarryBlockEntity quarry = quarry(helper, filter("c:ores/silver"), false, false, false);
        quarry.scanOnly();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.IDLE, "Still scanning"))
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getTargetCount() == 22, "The scan found " + quarry.getTargetCount());
                    helper.assertTrue(quarry.getScanCounts().getOrDefault(ModBlocks.SILVER_ORE.get(), 0) == 17
                            && quarry.getScanCounts().getOrDefault(ModBlocks.DEEPSLATE_SILVER_ORE.get(), 0) == 5, "Counts are " + quarry.getScanCounts());
                    quarry.toggleRunning();
                })
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.FINISHED, "Not finished: " + quarry.getStatus()))
                .thenExecute(() -> helper.assertTrue(quarry.getMinedCount() == 22, quarry.getMinedCount() + " mined, not 22"))
                .thenSucceed();
    }

    // A target turned to air after the scan is skipped: no FE, not counted.
    static void rechecksBeforeMining(GameTestHelper helper) {
        clear(helper);
        List<BlockPos> area = area();
        List<BlockPos> stones = List.of(area.get(1), area.get(8), area.get(33));
        stones.forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        ArcQuarryBlockEntity quarry = quarry(helper, filter(Items.STONE), false, false, false);
        int[] start = new int[1];
        quarry.scanOnly();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.IDLE, "Still scanning"))
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getTargetCount() == 3, quarry.getTargetCount() + " targets");
                    helper.setBlock(stones.get(1), Blocks.AIR);
                    start[0] = quarry.getEnergy().getAmountAsInt();
                    quarry.toggleRunning();
                })
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.FINISHED, "Not finished: " + quarry.getStatus()))
                .thenExecute(() -> {
                    helper.assertTrue(quarry.getMinedCount() == 2, quarry.getMinedCount() + " mined, not 2");
                    int used = start[0] - quarry.getEnergy().getAmountAsInt();
                    helper.assertTrue(used == 2 * ArcforgeConfig.QUARRY_ENERGY_PER_BLOCK.getAsInt(), used + " FE for 2 blocks");
                })
                .thenSucceed();
    }

    // A protection mod (here a listener) refusing one block: it stays, and the quarry moves on.
    static void breakEventCancel(GameTestHelper helper) {
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, event -> {
                if (event.getPos().equals(cancelAt)) {
                    event.setCanceled(true);
                }
            });
        }
        clear(helper);
        List<BlockPos> area = area();
        List<BlockPos> stones = List.of(area.get(2), area.get(9), area.get(31));
        stones.forEach(pos -> helper.setBlock(pos, Blocks.STONE));
        cancelAt = helper.absolutePos(stones.get(1));
        ArcQuarryBlockEntity quarry = quarry(helper, filter(Items.STONE), false, false, false);
        quarry.toggleRunning();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(quarry.getQuarryState() == ArcQuarryBlockEntity.State.FINISHED, "Not finished: " + quarry.getStatus()))
                .thenExecute(() -> {
                    cancelAt = null;
                    helper.assertBlockPresent(Blocks.STONE, stones.get(1));
                    helper.assertBlockPresent(Blocks.AIR, stones.get(0));
                    helper.assertBlockPresent(Blocks.AIR, stones.get(2));
                    helper.assertTrue(quarry.getMinedCount() == 2, quarry.getMinedCount() + " mined");
                })
                .thenSucceed();
    }
}
