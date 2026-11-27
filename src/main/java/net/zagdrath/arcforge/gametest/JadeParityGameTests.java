/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.WrenchMode;
import net.zagdrath.arcforge.multiblock.MultiblockBlueprints;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.Multiblocks;

// Jade shows the same for every block of a formed multiblock: each blueprint is built, and what Jade's server side sends
// for one block of each kind in it (casing, glass, port, cell...) is checked against what it sends for the controller.
// Before that, each controller gets a few items if it takes any, so item views are compared too (an empty machine shows
// none). Every difference is logged and fails the test, except the few lines that are about the block itself (ALLOWED).
// Blueprints with no multiblock part in them (the reactor's containment building) are skipped.
final class JadeParityGameTests {
    private static final BlockPos ORIGIN = new BlockPos(2, 1, 2);
    private static final int SETTLE_TICKS = 5;
    // "blueprint - block: what": differences that are meant (a part that shows something of its own on top of the
    // structure's): a planting bed its crop, a control rod channel its bank.
    private static final List<String> ALLOWED = List.of(
            "greenhouse - planting_bed: different arcforge:greenhouse",
            "fission_reactor - control_rod_channel: different arcforge:vessel");
    // Tried in turn in the controller's item handler; whatever it takes is enough.
    private static final List<net.minecraft.world.item.Item> STOCK = List.of(net.minecraft.world.item.Items.COAL,
            net.minecraft.world.item.Items.CHARCOAL, net.minecraft.world.item.Items.IRON_ORE, net.minecraft.world.item.Items.RAW_IRON,
            net.minecraft.world.item.Items.IRON_INGOT, net.minecraft.world.item.Items.SAND, net.minecraft.world.item.Items.COBBLESTONE,
            net.minecraft.world.item.Items.WHEAT_SEEDS, net.minecraft.world.item.Items.OAK_LOG);

    private JadeParityGameTests() {}

    static void multiblocks(GameTestHelper helper) {
        if (!ModList.get().isLoaded("jade")) {
            helper.succeed();
            return;
        }
        ServerPlayer player = TestFixtures.player(helper);
        Map<String, List<String>> report = new LinkedHashMap<>();
        GameTestSequence sequence = helper.startSequence();
        for (MultiblockBlueprints.Blueprint blueprint : MultiblockBlueprints.all()) {
            if (blueprint.placements().stream().noneMatch(p -> p.state().getBlock() instanceof net.zagdrath.arcforge.multiblock.MultiblockPart)) {
                continue;
            }
            sequence.thenExecute(() -> blueprint.placements().forEach(p -> helper.setBlock(ORIGIN.offset(p.pos()), p.state())))
                    .thenIdle(SETTLE_TICKS)
                    .thenExecute(() -> {
                        if (controller(helper, blueprint) == null) {
                            // Some structures form on a Wrench click.
                            PortGameTests.wrench(helper, player, ORIGIN.offset(blueprint.placements().getFirst().pos()), Direction.UP, WrenchMode.CONFIGURE);
                        }
                    })
                    .thenIdle(SETTLE_TICKS)
                    .thenExecute(() -> stock(helper, blueprint))
                    .thenIdle(1)
                    .thenExecute(() -> report.put(blueprint.id(), compare(helper, player, blueprint)))
                    .thenExecute(() -> blueprint.placements().reversed().forEach(p -> helper.setBlock(ORIGIN.offset(p.pos()), Blocks.AIR)))
                    .thenIdle(2);
        }
        sequence.thenExecute(() -> {
            List<String> problems = new ArrayList<>();
            report.forEach((id, lines) -> lines.forEach(line -> {
                Arcforge.LOGGER.info("[jade parity] {} - {}", id, line);
                if (!ALLOWED.contains(id + " - " + line)) {
                    problems.add(id + " - " + line);
                }
            }));
            helper.assertTrue(problems.isEmpty(), problems.size() + " Jade differences between controllers and their parts:\n" + String.join("\n", problems));
        }).thenSucceed();
    }

    private static MultiblockController controller(GameTestHelper helper, MultiblockBlueprints.Blueprint blueprint) {
        for (MultiblockBlueprints.Placement placement : blueprint.placements()) {
            MultiblockController controller = Multiblocks.controllerAt(helper.getLevel(), helper.absolutePos(ORIGIN.offset(placement.pos())));
            if (controller != null && controller.isFormed()) {
                return controller;
            }
        }
        return null;
    }

    // A few of the first item the controller's item handler takes, so it has something to show.
    private static void stock(GameTestHelper helper, MultiblockBlueprints.Blueprint blueprint) {
        MultiblockController controller = controller(helper, blueprint);
        var items = controller != null ? helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                controller.getBlockPos(), null) : null;
        if (items == null) {
            return;
        }
        for (net.minecraft.world.item.Item item : STOCK) {
            try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                if (net.neoforged.neoforge.transfer.ResourceHandlerUtil.insertStacking(items,
                        net.neoforged.neoforge.transfer.item.ItemResource.of(item), 4, tx) > 0) {
                    tx.commit();
                    return;
                }
            }
        }
    }

    // One block of each kind in the blueprint against the controller.
    private static List<String> compare(GameTestHelper helper, ServerPlayer player, MultiblockBlueprints.Blueprint blueprint) {
        MultiblockController controller = controller(helper, blueprint);
        if (controller == null) {
            return List.of("didn't form");
        }
        Map<Block, BlockPos> kinds = new LinkedHashMap<>();
        for (MultiblockBlueprints.Placement placement : blueprint.placements()) {
            BlockPos pos = helper.absolutePos(ORIGIN.offset(placement.pos()));
            Block block = helper.getLevel().getBlockState(pos).getBlock();
            // Only what belongs to the structure (Jade names it after the machine); not, say, a Pumpjack's Well Head.
            boolean part = block instanceof net.zagdrath.arcforge.multiblock.MultiblockPart
                    || block instanceof net.zagdrath.arcforge.block.multiblock.PressureGlassBlock
                    || block instanceof net.zagdrath.arcforge.block.nuclear.VesselInteriorBlock;
            if (!pos.equals(controller.getBlockPos()) && part) {
                kinds.putIfAbsent(block, pos);
            }
        }
        List<String> lines = new ArrayList<>();
        kinds.values().forEach(pos -> lines.addAll(net.zagdrath.arcforge.compat.jade.JadeParity.differences(helper.getLevel(), player,
                controller.getBlockPos(), pos)));
        return lines;
    }
}
