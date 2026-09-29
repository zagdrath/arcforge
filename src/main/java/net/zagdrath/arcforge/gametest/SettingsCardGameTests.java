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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcMelterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ElectrolyzerBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.item.tool.SettingsCardItem;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Settings Card: copying and pasting sides, redstone and auto-eject; refusing the wrong kind or size; ports
// landing where they belong on a turned copy of a structure; and conduit filter settings.
public final class SettingsCardGameTests {
    private SettingsCardGameTests() {}

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    // Arc Melter A's sides, redstone and auto-eject land on B; A's Speed Upgrade doesn't.
    static void copyPaste(GameTestHelper helper) {
        BlockPos aPos = new BlockPos(1, 1, 1);
        BlockPos bPos = new BlockPos(3, 1, 1);
        helper.setBlock(aPos, ModBlocks.ARC_MELTER.get());
        helper.setBlock(bPos, ModBlocks.ARC_MELTER.get());
        ArcMelterBlockEntity a = helper.getBlockEntity(aPos, ArcMelterBlockEntity.class);
        ArcMelterBlockEntity b = helper.getBlockEntity(bPos, ArcMelterBlockEntity.class);
        a.clearSideModes();
        a.setSideMode(RelativeSide.TOP, SideMode.INPUT);
        a.setSideMode(RelativeSide.BACK, SideMode.ENERGY);
        a.setRedstoneMode(RedstoneMode.LOW);
        a.setAutoEject(true);
        a.getItems().setStack(a.getItems().getFirstUpgradeSlot(), new ItemStack(ModItems.SPEED_UPGRADE.get()));

        ServerPlayer player = player(helper);
        ItemStack card = new ItemStack(ModItems.SETTINGS_CARD.get());
        helper.assertTrue(SettingsCardItem.copy(helper.getLevel(), player, card, a), "Copying failed");
        int skipped = SettingsCardItem.paste(helper.getLevel(), player, card, b, helper.absolutePos(bPos));
        helper.assertTrue(skipped == 0, "Pasting skipped " + skipped);
        for (RelativeSide side : RelativeSide.values()) {
            helper.assertTrue(b.getSideMode(side) == a.getSideMode(side), side + " is " + b.getSideMode(side) + ", not " + a.getSideMode(side));
        }
        helper.assertTrue(b.getRedstoneMode() == RedstoneMode.LOW, "Redstone is " + b.getRedstoneMode());
        helper.assertTrue(b.isAutoEject(), "Auto-eject didn't paste");
        helper.assertTrue(b.upgrades(UpgradeType.SPEED) == 0, "An upgrade was pasted");
        helper.succeed();
    }

    // A card from an Arc Melter does nothing to an Electrolyzer. A 5-long turbine's card is refused by a 7-long one,
    // and its ports land in the matching places on a 5-long one built along the other axis.
    static void refusesMismatch(GameTestHelper helper) {
        BlockPos melterPos = new BlockPos(1, 1, 8);
        BlockPos electrolyzerPos = new BlockPos(3, 1, 8);
        helper.setBlock(melterPos, ModBlocks.ARC_MELTER.get());
        helper.setBlock(electrolyzerPos, ModBlocks.ELECTROLYZER.get());
        ArcMelterBlockEntity melter = helper.getBlockEntity(melterPos, ArcMelterBlockEntity.class);
        melter.setRedstoneMode(RedstoneMode.HIGH);
        ServerPlayer player = player(helper);
        ItemStack melterCard = new ItemStack(ModItems.SETTINGS_CARD.get());
        SettingsCardItem.copy(helper.getLevel(), player, melterCard, melter);
        ElectrolyzerBlockEntity electrolyzer = helper.getBlockEntity(electrolyzerPos, ElectrolyzerBlockEntity.class);
        helper.assertTrue(SettingsCardItem.paste(helper.getLevel(), player, melterCard, electrolyzer, helper.absolutePos(electrolyzerPos)) == -1,
                "A melter card pasted onto an electrolyzer");
        helper.assertTrue(electrolyzer.getRedstoneMode() == RedstoneMode.IGNORE, "The electrolyzer's redstone changed");

        BlockPos xFive = new BlockPos(0, 1, 0);
        BlockPos xSeven = new BlockPos(0, 5, 0);
        BlockPos zFive = new BlockPos(8, 1, 0);
        SteamGameTests.buildShell(helper, xFive, Direction.Axis.X, 5, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        SteamGameTests.buildShell(helper, xSeven, Direction.Axis.X, 7, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        SteamGameTests.buildShell(helper, zFive, Direction.Axis.Z, 5, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    var level = helper.getLevel();
                    SteamTurbineArrayBlockEntity source = helper.getBlockEntity(xFive, SteamTurbineArrayBlockEntity.class);
                    SteamTurbineArrayBlockEntity longer = helper.getBlockEntity(xSeven, SteamTurbineArrayBlockEntity.class);
                    SteamTurbineArrayBlockEntity turned = helper.getBlockEntity(zFive, SteamTurbineArrayBlockEntity.class);
                    helper.assertTrue(source.isFormed() && longer.isFormed() && turned.isFormed(), "A turbine didn't form");
                    // One port only: Input on the bottom of the second block along, on the structure's north edge.
                    for (MultiblockPorts.Port port : MultiblockPorts.list(level, source)) {
                        MultiblockPorts.set(level, source, port.pos(), SideMode.NONE, port.face());
                    }
                    MultiblockPorts.set(level, source, helper.absolutePos(xFive.offset(1, 0, 0)), SideMode.INPUT, Direction.DOWN);
                    ItemStack card = new ItemStack(ModItems.SETTINGS_CARD.get());
                    SettingsCardItem.copy(level, player, card, source);
                    helper.assertTrue(SettingsCardItem.paste(level, player, card, longer, longer.getBlockPos()) == -1, "A 5-long card pasted onto a 7-long turbine");
                    helper.assertTrue(SettingsCardItem.paste(level, player, card, turned, turned.getBlockPos()) == 0, "The turned turbine skipped ports");
                    List<MultiblockPorts.Port> ports = MultiblockPorts.list(level, turned);
                    // Along +Z the frame's right is west, so the source's north edge (right = 0) is the turned turbine's
                    // east edge; the second block along is z + 1.
                    BlockPos expected = helper.absolutePos(zFive.offset(2, 0, 1));
                    helper.assertTrue(ports.size() == 1 && ports.getFirst().pos().equals(expected) && ports.getFirst().face() == Direction.DOWN
                            && ports.getFirst().mode() == SideMode.INPUT, "Turned ports: " + ports + ", expected Input on the bottom of " + expected);
                })
                .thenSucceed();
    }

    // Filter settings paste onto sides that have a filter; a side without one is skipped, and none is made.
    static void conduitFilter(GameTestHelper helper) {
        BlockPos aPos = new BlockPos(1, 1, 1);
        BlockPos bPos = new BlockPos(1, 1, 4);
        helper.setBlock(aPos, ModBlocks.conduit(ConduitType.ITEM, ConduitTier.WROUGHT).get());
        helper.setBlock(bPos, ModBlocks.conduit(ConduitType.ITEM, ConduitTier.WROUGHT).get());
        ConduitBlockEntity a = helper.getBlockEntity(aPos, ConduitBlockEntity.class);
        ConduitBlockEntity b = helper.getBlockEntity(bPos, ConduitBlockEntity.class);
        FilterSettings deny = FilterSettings.DEFAULT.withDeny(true);
        ItemStack denyFilter = new ItemStack(ModItems.CONDUIT_FILTER.get());
        denyFilter.set(ModDataComponents.CONDUIT_FILTER.get(), deny);
        a.setFilter(Direction.NORTH, denyFilter);
        a.setFilter(Direction.SOUTH, new ItemStack(ModItems.CONDUIT_FILTER.get()));
        b.setFilter(Direction.NORTH, new ItemStack(ModItems.CONDUIT_FILTER.get()));

        ServerPlayer player = player(helper);
        ItemStack card = new ItemStack(ModItems.SETTINGS_CARD.get());
        SettingsCardItem.copy(helper.getLevel(), player, card, a);
        int skipped = SettingsCardItem.paste(helper.getLevel(), player, card, b, helper.absolutePos(bPos));
        helper.assertTrue(skipped == 1, "Skipped " + skipped + " sides, not 1");
        helper.assertTrue(FilterSettings.of(b.getFilter(Direction.NORTH)).equals(deny), "North's filter settings didn't paste");
        helper.assertFalse(b.hasFilter(Direction.SOUTH), "A filter was made on the south side");
        helper.succeed();
    }
}
