/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.machine.AssemblerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockBreakerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.BlockPlacerBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.menu.machine.AssemblerMenu;
import net.zagdrath.arcforge.menu.machine.VacuumCollectorMenu;
import net.zagdrath.arcforge.network.AssemblerPatternPayload;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;

// The Assembler, Block Breaker, Block Placer and Vacuum Collector.
public final class AutomationGameTests {
    private AutomationGameTests() {}

    // A mock player standing on the machine with its menu attached (the mock connection can't open screens).
    private static ServerPlayer playerAt(GameTestHelper helper, BlockPos pos) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos absolute = helper.absolutePos(pos);
        player.snapTo(absolute.getX() + 0.5, absolute.getY() + 1, absolute.getZ() + 0.5);
        return player;
    }

    // The test area outside the tiny structure isn't ours: clear a box (inclusive) to air, on a stone floor so
    // items dropped there stay put.
    private static void clear(GameTestHelper helper, BlockPos from, BlockPos to) {
        BlockPos.betweenClosed(from, to).forEach(pos -> helper.setBlock(pos.immutable(), Blocks.AIR));
        BlockPos.betweenClosed(from.atY(from.getY() - 1), to.atY(from.getY() - 1)).forEach(pos -> helper.setBlock(pos.immutable(), Blocks.STONE));
    }

    private static int count(List<ItemStack> stacks, Item item) {
        return stacks.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static List<ItemStack> slots(net.zagdrath.arcforge.transfer.item.MachineItemHandler items, int from, int to) {
        return java.util.stream.IntStream.range(from, to).mapToObj(items::getStack).toList();
    }

    // --- Assembler ---

    // The piston pattern, set through the menu's cell buttons with the item carried.
    static void assemblerCraftsPistons(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ASSEMBLER.get());
        AssemblerBlockEntity assembler = helper.getBlockEntity(pos, AssemblerBlockEntity.class);
        ServerPlayer player = playerAt(helper, pos);
        AssemblerMenu menu = (AssemblerMenu) assembler.createMenu(1, player.getInventory(), player);
        Item[] pattern = { Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.COBBLESTONE, Items.IRON_INGOT, Items.COBBLESTONE,
                Items.COBBLESTONE, Items.REDSTONE, Items.COBBLESTONE };
        for (int cell = 0; cell < pattern.length; cell++) {
            menu.setCarried(new ItemStack(pattern[cell], 5));
            menu.clickMenuButton(player, AssemblerMenu.BUTTON_SET_CELL + cell);
        }
        menu.setCarried(ItemStack.EMPTY);
        helper.assertTrue(assembler.getPattern().getItem(4).is(Items.IRON_INGOT) && assembler.getPattern().getItem(4).getCount() == 1,
                "The centre cell holds " + assembler.getPattern().getItem(4));
        var top = assembler.getItemHandler(Direction.UP);
        helper.assertTrue(CrushingGameTests.insert(top, Items.DIRT, 1) == 0, "The buffer took dirt, which the pattern doesn't use");
        CrushingGameTests.insert(top, Items.OAK_PLANKS, 6);
        CrushingGameTests.insert(top, Items.COBBLESTONE, 8);
        CrushingGameTests.insert(top, Items.IRON_INGOT, 2);
        CrushingGameTests.insert(top, Items.REDSTONE, 2);
        CrushingGameTests.charge(assembler.getEnergy(), 20_000);
        int ticks = 2 * ArcforgeConfig.ASSEMBLER_CRAFT_TICKS.getAsInt() + 3;
        helper.startSequence()
                .thenIdle(ticks)
                .thenExecute(() -> {
                    ItemStack output = assembler.getItems().getStack(AssemblerBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(output.is(Items.PISTON) && output.getCount() == 2, "Output holds " + output);
                    helper.assertTrue(slots(assembler.getItems(), 0, AssemblerBlockEntity.BUFFER_SLOTS).stream().allMatch(ItemStack::isEmpty),
                            "The buffer isn't empty");
                    helper.assertTrue(assembler.getCrafts() == 2, assembler.getCrafts() + " crafts");
                })
                .thenSucceed();
    }

    private static final Item[] CAKE = { Items.MILK_BUCKET, Items.MILK_BUCKET, Items.MILK_BUCKET, Items.SUGAR, Items.EGG, Items.SUGAR,
            Items.WHEAT, Items.WHEAT, Items.WHEAT };

    private static AssemblerBlockEntity cakeAssembler(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ASSEMBLER.get());
        AssemblerBlockEntity assembler = helper.getBlockEntity(pos, AssemblerBlockEntity.class);
        for (int cell = 0; cell < CAKE.length; cell++) {
            assembler.setPatternCell(cell, new ItemStack(CAKE[cell]));
        }
        for (Item item : CAKE) {
            CrushingGameTests.insert(assembler.getItemHandler(null), item, 1);
        }
        CrushingGameTests.charge(assembler.getEnergy(), 20_000);
        return assembler;
    }

    // A cake gives back its three buckets. With both leftover slots full of something else, it doesn't craft and
    // uses nothing.
    static void assemblerReturnsBuckets(GameTestHelper helper) {
        AssemblerBlockEntity assembler = cakeAssembler(helper, new BlockPos(1, 1, 1));
        AssemblerBlockEntity blocked = cakeAssembler(helper, new BlockPos(4, 1, 1));
        blocked.getItems().setStack(AssemblerBlockEntity.FIRST_REMAINDER, new ItemStack(Items.DIRT, 64));
        blocked.getItems().setStack(AssemblerBlockEntity.FIRST_REMAINDER + 1, new ItemStack(Items.STONE, 64));
        helper.startSequence()
                .thenIdle(ArcforgeConfig.ASSEMBLER_CRAFT_TICKS.getAsInt() + 3)
                .thenExecute(() -> {
                    ItemStack output = assembler.getItems().getStack(AssemblerBlockEntity.SLOT_OUTPUT);
                    helper.assertTrue(output.is(Items.CAKE) && output.getCount() == 1, "Output holds " + output);
                    List<ItemStack> leftovers = slots(assembler.getItems(), AssemblerBlockEntity.FIRST_REMAINDER, AssemblerBlockEntity.FIRST_REMAINDER + 2);
                    helper.assertTrue(count(leftovers, Items.BUCKET) == 3, "Leftovers are " + leftovers);

                    helper.assertTrue(blocked.getStatus() == MachineStatus.OUTPUT_FULL, "Blocked assembler is " + blocked.getStatus());
                    helper.assertTrue(blocked.getItems().getStack(AssemblerBlockEntity.SLOT_OUTPUT).isEmpty(), "Blocked assembler made a cake");
                    List<ItemStack> buffer = slots(blocked.getItems(), 0, AssemblerBlockEntity.BUFFER_SLOTS);
                    helper.assertTrue(count(buffer, Items.MILK_BUCKET) == 3 && count(buffer, Items.WHEAT) == 3 && count(buffer, Items.SUGAR) == 2
                            && count(buffer, Items.EGG) == 1, "Blocked assembler used items: " + buffer);
                })
                .thenSucceed();
    }

    // JEI's payload, applied on the server, sets the pattern and the recipe resolves.
    static void assemblerJeiPayload(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.ASSEMBLER.get());
        AssemblerBlockEntity assembler = helper.getBlockEntity(pos, AssemblerBlockEntity.class);
        ServerPlayer player = playerAt(helper, pos);
        AssemblerMenu menu = (AssemblerMenu) assembler.createMenu(3, player.getInventory(), player);
        player.containerMenu = menu;
        List<ItemStack> cells = java.util.Arrays.stream(new Item[] { Items.OAK_PLANKS, Items.OAK_PLANKS, Items.OAK_PLANKS, Items.COBBLESTONE,
                Items.IRON_INGOT, Items.COBBLESTONE, Items.COBBLESTONE, Items.REDSTONE, Items.COBBLESTONE }).map(item -> new ItemStack(item, 3)).toList();
        helper.assertFalse(AssemblerPatternPayload.apply(player, new AssemblerPatternPayload(menu.containerId + 1, cells)),
                "A payload for another menu was applied");
        helper.assertTrue(AssemblerPatternPayload.apply(player, new AssemblerPatternPayload(menu.containerId, cells)), "The payload wasn't applied");
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(assembler.getPattern().getItem(0).getCount() == 1, "Pattern cells aren't single ghosts");
                    helper.assertTrue(assembler.getRecipe() != null, "The pattern didn't resolve a recipe");
                    helper.assertTrue(assembler.getPreview().getItem(0).is(Items.PISTON), "The pattern makes " + assembler.getPreview().getItem(0));
                })
                .thenSucceed();
    }

    // --- Block Breaker ---

    private static BlockBreakerBlockEntity breaker(GameTestHelper helper, BlockPos pos, int energy) {
        helper.setBlock(pos, ModBlocks.BLOCK_BREAKER.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        BlockBreakerBlockEntity breaker = helper.getBlockEntity(pos, BlockBreakerBlockEntity.class);
        CrushingGameTests.charge(breaker.getEnergy(), energy);
        return breaker;
    }

    // No tool: iron ore gives raw iron, stone cobblestone and obsidian obsidian (after 200 ticks).
    static void breakerDropsWithoutTool(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 2);
        BlockPos front = pos.north();
        BlockBreakerBlockEntity breaker = breaker(helper, pos, 20_000);
        helper.setBlock(front, Blocks.IRON_ORE);
        long[] start = new long[1];
        helper.startSequence()
                .thenExecute(() -> start[0] = helper.getTick())
                .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.AIR, front))
                .thenExecute(() -> {
                    long took = helper.getTick() - start[0];
                    helper.assertTrue(took >= 11 && took <= 14, "Iron ore took " + took + " ticks");
                    helper.assertTrue(count(slots(breaker.getItems(), 0, 9), Items.RAW_IRON) == 1, "No raw iron: " + slots(breaker.getItems(), 0, 9));
                    helper.setBlock(front, Blocks.STONE);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(slots(breaker.getItems(), 0, 9), Items.COBBLESTONE) == 1, "No cobblestone yet"))
                .thenExecute(() -> {
                    helper.setBlock(front, Blocks.OBSIDIAN);
                    start[0] = helper.getTick();
                })
                .thenWaitUntil(() -> helper.assertTrue(count(slots(breaker.getItems(), 0, 9), Items.OBSIDIAN) == 1, "No obsidian yet"))
                .thenExecute(() -> {
                    long took = helper.getTick() - start[0];
                    helper.assertTrue(took >= 199 && took <= 203, "Obsidian took " + took + " ticks");
                })
                .thenSucceed();
    }

    // One stone break costs 6 x 40 = 240 FE; with 30 FE it waits and the stone stays.
    static void breakerEnergy(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 2);
        BlockPos starved = new BlockPos(4, 1, 2);
        BlockBreakerBlockEntity breaker = breaker(helper, pos, 1_000);
        BlockBreakerBlockEntity weak = breaker(helper, starved, 30);
        helper.setBlock(pos.north(), Blocks.STONE);
        helper.setBlock(starved.north(), Blocks.STONE);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.AIR, pos.north()))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(breaker.getEnergy().getAmountAsInt() == 1_000 - 240, "Breaking stone used " + (1_000 - breaker.getEnergy().getAmountAsInt()) + " FE");
                    helper.assertTrue(weak.getStatus() == MachineStatus.NO_POWER, "Starved breaker is " + weak.getStatus());
                    helper.assertBlockPresent(Blocks.STONE, starved.north());
                })
                .thenSucceed();
    }

    // Bedrock and a multiblock casing are never broken.
    static void breakerRefusesMultiblockAndBedrock(GameTestHelper helper) {
        BlockPos first = new BlockPos(1, 1, 2);
        BlockPos second = new BlockPos(4, 1, 2);
        BlockBreakerBlockEntity bedrock = breaker(helper, first, 20_000);
        BlockBreakerBlockEntity casing = breaker(helper, second, 20_000);
        helper.setBlock(first.north(), Blocks.BEDROCK);
        helper.setBlock(second.north(), ModBlocks.METAL_PRESSING_ARRAY_CASING.get());
        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.BEDROCK, first.north());
                    helper.assertBlockPresent(ModBlocks.METAL_PRESSING_ARRAY_CASING.get(), second.north());
                    helper.assertTrue(bedrock.getStatus() == MachineStatus.CANNOT_BREAK, "Bedrock breaker is " + bedrock.getStatus());
                    helper.assertTrue(casing.getStatus() == MachineStatus.CANNOT_BREAK, "Casing breaker is " + casing.getStatus());
                })
                .thenSucceed();
    }

    // Pulse: exactly one break per rising edge of a redstone block beside it.
    static void breakerPulse(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 2);
        BlockPos front = pos.north();
        BlockPos power = pos.east();
        BlockBreakerBlockEntity breaker = breaker(helper, pos, 20_000);
        breaker.setRedstoneMode(RedstoneMode.PULSE);
        helper.setBlock(front, Blocks.COBBLESTONE);
        helper.startSequence()
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.COBBLESTONE, front);
                    helper.assertTrue(breaker.getStatus() == MachineStatus.WAITING_PULSE, "Breaker is " + breaker.getStatus());
                    helper.setBlock(power, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(breaker.getBreaks() == 1, "No break after the first pulse"))
                .thenExecute(() -> helper.setBlock(front, Blocks.COBBLESTONE))
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(breaker.getBreaks() == 1, "It broke again with the signal still on");
                    helper.setBlock(power, Blocks.AIR);
                })
                .thenIdle(5)
                .thenExecute(() -> helper.setBlock(power, Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> helper.assertTrue(breaker.getBreaks() == 2, "No break after the second pulse"))
                .thenExecute(() -> helper.setBlock(front, Blocks.COBBLESTONE))
                .thenIdle(30)
                .thenExecute(() -> helper.assertTrue(breaker.getBreaks() == 2, breaker.getBreaks() + " breaks for two pulses"))
                .thenSucceed();
    }

    // --- Block Placer ---

    private static BlockPlacerBlockEntity placer(GameTestHelper helper, BlockPos pos, int energy, ItemStack... contents) {
        helper.setBlock(pos, ModBlocks.BLOCK_PLACER.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        BlockPlacerBlockEntity placer = helper.getBlockEntity(pos, BlockPlacerBlockEntity.class);
        for (int slot = 0; slot < contents.length; slot++) {
            placer.getItems().setStack(slot, contents[slot]);
        }
        CrushingGameTests.charge(placer.getEnergy(), energy);
        return placer;
    }

    // It skips the stick and places cobblestone for 20 FE; blocked, it uses nothing; without FE it places nothing;
    // on Pulse it places once per edge.
    static void placerPlacesFromInventory(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 2);
        BlockPos dry = new BlockPos(4, 1, 2);
        BlockPos pulsed = new BlockPos(7, 1, 2);
        clear(helper, new BlockPos(0, 1, 0), new BlockPos(8, 2, 3));
        BlockPlacerBlockEntity placer = placer(helper, pos, 1_000, new ItemStack(Items.STICK), new ItemStack(Items.COBBLESTONE, 2));
        BlockPlacerBlockEntity empty = placer(helper, dry, 0, new ItemStack(Items.COBBLESTONE, 2));
        BlockPlacerBlockEntity pulse = placer(helper, pulsed, 1_000, new ItemStack(Items.COBBLESTONE, 4));
        pulse.setRedstoneMode(RedstoneMode.PULSE);
        helper.startSequence()
                .thenIdle(ArcforgeConfig.PLACER_INTERVAL.getAsInt() + 1)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.COBBLESTONE, pos.north());
                    helper.assertTrue(placer.getItems().getStack(0).is(Items.STICK), "The stick went");
                    helper.assertTrue(placer.getItems().getStack(1).getCount() == 1, "Cobblestone left: " + placer.getItems().getStack(1));
                    helper.assertTrue(placer.getEnergy().getAmountAsInt() == 980, "Placing used " + (1_000 - placer.getEnergy().getAmountAsInt()) + " FE");
                    helper.assertTrue(empty.getStatus() == MachineStatus.NO_POWER, "Unpowered placer is " + empty.getStatus());
                    helper.assertBlockPresent(Blocks.AIR, dry.north());
                    helper.assertBlockPresent(Blocks.AIR, pulsed.north());
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(placer.getStatus() == MachineStatus.FRONT_BLOCKED, "Blocked placer is " + placer.getStatus());
                    helper.assertTrue(placer.getItems().getStack(1).getCount() == 1 && placer.getEnergy().getAmountAsInt() == 980,
                            "A blocked placer used something");
                    helper.setBlock(pulsed.east(), Blocks.REDSTONE_BLOCK);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.COBBLESTONE, pulsed.north());
                    helper.setBlock(pulsed.north(), Blocks.AIR);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.AIR, pulsed.north());
                    helper.assertTrue(pulse.getPlacements() == 1, pulse.getPlacements() + " placements for one pulse");
                    helper.setBlock(pulsed.east(), Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> helper.setBlock(pulsed.east(), Blocks.REDSTONE_BLOCK))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.COBBLESTONE, pulsed.north());
                    helper.assertTrue(pulse.getPlacements() == 2, pulse.getPlacements() + " placements for two pulses");
                })
                .thenSucceed();
    }

    // --- Vacuum Collector ---

    private static VacuumCollectorBlockEntity collector(GameTestHelper helper, BlockPos pos, int range) {
        helper.setBlock(pos, ModBlocks.VACUUM_COLLECTOR.get());
        VacuumCollectorBlockEntity collector = helper.getBlockEntity(pos, VacuumCollectorBlockEntity.class);
        collector.setRange(range);
        CrushingGameTests.charge(collector.getEnergy(), 20_000);
        return collector;
    }

    // Test areas can land where entities don't tick, and an item that never ages is never old enough to collect.
    // Tick these ones every test tick (in a ticking chunk they just age twice as fast).
    private static void ageItems(GameTestHelper helper, net.minecraft.world.entity.item.ItemEntity... items) {
        helper.onEachTick(() -> {
            for (var item : items) {
                if (item.isAlive()) {
                    item.tick();
                }
            }
        });
    }

    private static int buffered(VacuumCollectorBlockEntity collector, Item item) {
        return count(slots(collector.getItems(), VacuumCollectorBlockEntity.FIRST_BUFFER, VacuumCollectorBlockEntity.MACHINE_SLOTS), item);
    }

    // Range 3 takes items 2 and 3 blocks away but not 5; the + button twice (range 5) takes that one too.
    static void vacuumRadius(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 1);
        clear(helper, new BlockPos(0, 1, 0), new BlockPos(7, 3, 3));
        VacuumCollectorBlockEntity collector = collector(helper, pos, 3);
        var near = helper.spawnItem(Items.IRON_INGOT, new Vec3(2.5, 1.0, 1.5));
        var edge = helper.spawnItem(Items.GOLD_INGOT, new Vec3(3.5, 1.0, 1.5));
        var far = helper.spawnItem(Items.DIAMOND, new Vec3(5.5, 1.0, 1.5));
        ageItems(helper, near, edge, far);
        int scan = ArcforgeConfig.VACUUM_SCAN_INTERVAL.getAsInt();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(buffered(collector, Items.IRON_INGOT) == 1 && !near.isAlive()
                        && buffered(collector, Items.GOLD_INGOT) == 1 && !edge.isAlive(), "The items 2 and 3 blocks away weren't taken"))
                // Another scan or two, to be sure the far one stays.
                .thenIdle(2 * scan + 1)
                .thenExecute(() -> {
                    helper.assertTrue(far.isAlive() && buffered(collector, Items.DIAMOND) == 0, "The item 5 blocks away was taken at range 3");
                    ServerPlayer player = playerAt(helper, pos);
                    VacuumCollectorMenu menu = (VacuumCollectorMenu) collector.createMenu(2, player.getInventory(), player);
                    menu.clickMenuButton(player, VacuumCollectorMenu.BUTTON_RANGE_PLUS);
                    menu.clickMenuButton(player, VacuumCollectorMenu.BUTTON_RANGE_PLUS);
                    helper.assertTrue(collector.getRange() == 5, "Range is " + collector.getRange());
                })
                .thenWaitUntil(() -> helper.assertTrue(buffered(collector, Items.DIAMOND) == 1 && !far.isAlive(), "The item 5 blocks away wasn't taken at range 5"))
                .thenSucceed();
    }

    // An allowlist filter of iron ingots takes only iron; an unset filter takes everything.
    static void vacuumFilter(GameTestHelper helper) {
        BlockPos filteredPos = new BlockPos(0, 1, 1);
        BlockPos openPos = new BlockPos(6, 1, 1);
        clear(helper, new BlockPos(0, 1, 0), new BlockPos(8, 3, 3));
        VacuumCollectorBlockEntity filtered = collector(helper, filteredPos, 1);
        VacuumCollectorBlockEntity open = collector(helper, openPos, 1);
        ItemStack filter = new ItemStack(ModItems.CONDUIT_FILTER.get());
        filter.set(ModDataComponents.CONDUIT_FILTER.get(), FilterSettings.DEFAULT.withEntry(0, FilterSettings.Entry.of(new ItemStack(Items.IRON_INGOT))));
        filtered.getItems().setStack(VacuumCollectorBlockEntity.SLOT_FILTER, filter);
        open.getItems().setStack(VacuumCollectorBlockEntity.SLOT_FILTER, new ItemStack(ModItems.CONDUIT_FILTER.get()));
        var iron = helper.spawnItem(Items.IRON_INGOT, new Vec3(1.5, 1.0, 1.5));
        var gold = helper.spawnItem(Items.GOLD_INGOT, new Vec3(0.5, 1.0, 2.5));
        var openIron = helper.spawnItem(Items.IRON_INGOT, new Vec3(7.5, 1.0, 1.5));
        var openGold = helper.spawnItem(Items.GOLD_INGOT, new Vec3(6.5, 1.0, 2.5));
        ageItems(helper, iron, gold, openIron, openGold);
        int scan = ArcforgeConfig.VACUUM_SCAN_INTERVAL.getAsInt();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(buffered(filtered, Items.IRON_INGOT) == 1 && buffered(open, Items.IRON_INGOT) == 1
                        && buffered(open, Items.GOLD_INGOT) == 1, "The filtered collector hasn't taken iron, or the unset one both"))
                .thenIdle(2 * scan + 1)
                .thenExecute(() -> helper.assertTrue(buffered(filtered, Items.GOLD_INGOT) == 0 && gold.isAlive(), "The filtered collector took gold"))
                .thenSucceed();
    }

    // --- Together ---

    // A Placer and a Breaker facing the same cell, the Breaker's top feeding the Placer's top through item
    // conduits: one cobblestone goes round and round, never more and never less.
    static void cobblestoneLoop(GameTestHelper helper) {
        BlockPos placerPos = new BlockPos(0, 1, 1);
        BlockPos cell = new BlockPos(1, 1, 1);
        BlockPos breakerPos = new BlockPos(2, 1, 1);
        clear(helper, new BlockPos(0, 1, 0), new BlockPos(3, 3, 2));
        helper.setBlock(placerPos, ModBlocks.BLOCK_PLACER.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(breakerPos, ModBlocks.BLOCK_BREAKER.get().defaultBlockState().setValue(BlockStateProperties.FACING, Direction.WEST));
        BlockPlacerBlockEntity placer = helper.getBlockEntity(placerPos, BlockPlacerBlockEntity.class);
        BlockBreakerBlockEntity breaker = helper.getBlockEntity(breakerPos, BlockBreakerBlockEntity.class);
        for (int x = 0; x <= 2; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), ModBlocks.conduit(ConduitType.ITEM, ConduitTier.ARCFORGED).get());
        }
        for (int x = 0; x <= 2; x++) {
            ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(new BlockPos(x, 2, 1)));
        }
        placer.getItems().setStack(0, new ItemStack(Items.COBBLESTONE));
        helper.onEachTick(() -> {
            CrushingGameTests.charge(placer.getEnergy(), 10_000);
            CrushingGameTests.charge(breaker.getEnergy(), 20_000);
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(breaker.getBreaks() >= 10, breaker.getBreaks() + " breaks so far"))
                .thenExecute(() -> breaker.setRedstoneMode(RedstoneMode.HIGH))
                .thenWaitUntil(() -> helper.assertBlockPresent(Blocks.COBBLESTONE, cell))
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertBlockPresent(Blocks.COBBLESTONE, cell);
                    int loose = count(slots(placer.getItems(), 0, 9), Items.COBBLESTONE) + count(slots(breaker.getItems(), 0, 9), Items.COBBLESTONE);
                    helper.assertTrue(loose == 0, loose + " more cobblestone in the machines: it was duplicated");
                    // helper.getEntities only looks inside the (tiny) test structure; look around the machines.
                    net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(helper.absolutePos(cell)).inflate(4.0);
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around).isEmpty(),
                            "Items were dropped");
                    helper.assertTrue(placer.getPlacements() >= 10, placer.getPlacements() + " placements");
                })
                .thenSucceed();
    }
}
