/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.EnumSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.multiblock.CarbonizerBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.item.tool.WrenchMode;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;

// Multiblock ports: a new structure's default ports, only ports doing IO, and setting them with the Wrench.
public final class PortGameTests {
    private PortGameTests() {}

    // The (absolute) position of the structure's first port in this mode, or null.
    static @Nullable BlockPos port(GameTestHelper helper, MultiblockController controller, SideMode mode) {
        return MultiblockPorts.list(helper.getLevel(), controller).stream()
                .filter(port -> port.mode() == mode).map(MultiblockPorts.Port::pos).findFirst().orElse(null);
    }

    static SideMode portAt(GameTestHelper helper, BlockPos relative) {
        return MultiblockPorts.get(helper.getBlockState(relative));
    }

    // Right-clicks face of the block at pos (relative) with a Wrench in this mode.
    static void wrench(GameTestHelper helper, Player player, BlockPos pos, Direction face, WrenchMode mode) {
        BlockPos absolute = helper.absolutePos(pos);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        wrench.set(ModDataComponents.WRENCH_MODE.get(), mode);
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        Vec3 hit = Vec3.atCenterOf(absolute).relative(face, 0.5);
        wrench.getItem().onItemUseFirst(wrench, new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, absolute, false)));
    }

    private static ArcCrushingArrayBlockEntity buildArray(GameTestHelper helper) {
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(2, 3, 2))) {
            helper.setBlock(pos.immutable(), ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
        }
        return helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class);
    }

    // A new array gets a port in the middle of each face its default configuration used (items in at the
    // top, out at the bottom, energy at the back); only those blocks do IO.
    static void defaultPorts(GameTestHelper helper) {
        buildArray(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class);
                    helper.assertTrue(array.isFormed(), "Array did not form");
                    Set<SideMode> modes = EnumSet.noneOf(SideMode.class);
                    MultiblockPorts.list(helper.getLevel(), array).forEach(port -> modes.add(port.mode()));
                    helper.assertTrue(modes.equals(EnumSet.of(SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY)), "Default ports are " + modes);
                    helper.assertTrue(portAt(helper, new BlockPos(1, 3, 1)) == SideMode.INPUT, "Top middle is " + portAt(helper, new BlockPos(1, 3, 1)));
                    helper.assertTrue(portAt(helper, new BlockPos(1, 1, 1)) == SideMode.OUTPUT, "Bottom middle is " + portAt(helper, new BlockPos(1, 1, 1)));
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(new BlockPos(1, 3, 1)), Direction.UP) != null,
                            "Top port does not accept items");
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(new BlockPos(0, 3, 2)), Direction.UP) == null,
                            "A top block that isn't a port accepts items");
                })
                .thenSucceed();
    }

    // In Port mode, Shift+Right-click on an outer face cycles the block's port through the modes the
    // structure allows; an inner face isn't a port position; a plain click only reports.
    static void wrenchSetsPort(GameTestHelper helper) {
        buildArray(helper);
        BlockPos corner = new BlockPos(0, 3, 2);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    wrench(helper, player, corner, Direction.UP, WrenchMode.PORT);
                    helper.assertTrue(portAt(helper, corner) == SideMode.NONE, "A plain click changed the port");
                    player.setShiftKeyDown(true);
                    wrench(helper, player, corner, Direction.UP, WrenchMode.PORT);
                    helper.assertTrue(portAt(helper, corner) == SideMode.INPUT, "First port mode is " + portAt(helper, corner));
                    wrench(helper, player, corner, Direction.UP, WrenchMode.PORT);
                    helper.assertTrue(portAt(helper, corner) == SideMode.OUTPUT, "Second port mode is " + portAt(helper, corner));
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(corner), Direction.UP) != null,
                            "New port does not give items");
                    // The port is on the clicked face only: the corner's other outer faces do nothing.
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(corner), Direction.SOUTH) == null,
                            "The port also works on another face of its block");
                    // Setting it on another outer face moves it there.
                    wrench(helper, player, corner, Direction.SOUTH, WrenchMode.PORT);
                    helper.assertTrue(MultiblockPorts.face(helper.getBlockState(corner)) == Direction.SOUTH, "The port did not move to the south face");
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(corner), Direction.UP) == null,
                            "The old face still works after the port moved");
                    wrench(helper, player, corner, Direction.UP, WrenchMode.PORT);
                    wrench(helper, player, corner, Direction.UP, WrenchMode.PORT);
                    // Clicking the side of the centre casing that faces into the array: not a port position.
                    wrench(helper, player, new BlockPos(1, 2, 1), Direction.UP, WrenchMode.PORT);
                    helper.assertTrue(portAt(helper, new BlockPos(1, 2, 1)) == SideMode.NONE, "The inside became a port");
                    // Round through energy back to none.
                    wrench(helper, player, corner, Direction.UP, WrenchMode.PORT);
                    wrench(helper, player, corner, Direction.UP, WrenchMode.PORT);
                    helper.assertTrue(portAt(helper, corner) == SideMode.NONE, "Did not wrap back to none: " + portAt(helper, corner));
                })
                .thenSucceed();
    }

    // An item conduit next to a Carbonizer port connects to it, whether the conduit or the port came first;
    // one next to another face of the port's block doesn't.
    static void conduitsConnectToPorts(GameTestHelper helper) {
        BlockState carbonizer = ModBlocks.CARBONIZER.get().defaultBlockState().setValue(CarbonizerBlock.FACING, Direction.NORTH);
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(1, 2, 1))) {
            helper.setBlock(pos.immutable(), carbonizer);
        }
        Block conduit = ModBlocks.conduit(ConduitType.ITEM, ConduitTier.WROUGHT).get();
        BlockPos corner = new BlockPos(1, 2, 1), before = new BlockPos(1, 3, 1), beside = new BlockPos(2, 2, 1);
        BlockPos later = new BlockPos(0, 2, 0), after = new BlockPos(0, 3, 0);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.setBlock(before, conduit);
                    helper.setBlock(beside, conduit);
                    CarbonizerBlockEntity master = helper.getBlockEntity(new BlockPos(0, 1, 0), CarbonizerBlockEntity.class);
                    helper.assertTrue(master.isFormed(), "Carbonizer did not form");
                    MultiblockPorts.set(helper.getLevel(), master, helper.absolutePos(corner), SideMode.INPUT, Direction.UP);
                    MultiblockPorts.set(helper.getLevel(), master, helper.absolutePos(later), SideMode.INPUT, Direction.UP);
                    // Connected as a player placement would.
                    helper.setBlock(after, conduit);
                    ConduitBlock.refreshConnections(helper.getLevel(), helper.absolutePos(after));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    ConnectionMode existing = ConduitBlock.mode(helper.getBlockState(before), Direction.DOWN);
                    helper.assertTrue(existing == ConnectionMode.INPUT, "A conduit placed before the port connects " + existing);
                    ConnectionMode placed = ConduitBlock.mode(helper.getBlockState(after), Direction.DOWN);
                    helper.assertTrue(placed == ConnectionMode.INPUT, "A conduit placed after the port connects " + placed);
                    ConnectionMode side = ConduitBlock.mode(helper.getBlockState(beside), Direction.WEST);
                    helper.assertTrue(side == ConnectionMode.NONE, "A conduit on another face of the port's block connects " + side);
                })
                .thenSucceed();
    }

    // Sneaking with the Wrench in Configure mode no longer breaks a multiblock; in Dismantle mode it does.
    static void wrenchModesOnMultiblock(GameTestHelper helper) {
        buildArray(helper);
        BlockPos corner = new BlockPos(0, 1, 0);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    player.setShiftKeyDown(true);
                    wrench(helper, player, corner, Direction.NORTH, WrenchMode.CONFIGURE);
                    helper.assertBlockPresent(ModBlocks.ARC_CRUSHING_ARRAY_CASING.get(), corner);
                    wrench(helper, player, corner, Direction.NORTH, WrenchMode.ROTATE);
                    helper.assertBlockPresent(ModBlocks.ARC_CRUSHING_ARRAY_CASING.get(), corner);
                    wrench(helper, player, corner, Direction.NORTH, WrenchMode.DISMANTLE);
                    helper.assertBlockNotPresent(ModBlocks.ARC_CRUSHING_ARRAY_CASING.get(), corner);
                })
                .thenSucceed();
    }

    // A new turbine gets an energy port on its generator end cap and a steam port on its bearing end cap.
    static void turbinePorts(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        SteamGameTests.buildShell(helper, min, Direction.Axis.X, 4, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get(), new BlockPos(1, 2, 0));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(min, SteamTurbineArrayBlockEntity.class);
                    helper.assertTrue(turbine.isFormed(), "Turbine did not form");
                    BlockPos generator = new BlockPos(3, 2, 1), bearing = new BlockPos(0, 2, 1);
                    helper.assertTrue(helper.getBlockState(generator).getValue(SteamTurbineArrayCasingBlock.END) == SteamTurbineArrayCasingBlock.End.GENERATOR,
                            "Generator cap is not at +X");
                    helper.assertTrue(portAt(helper, generator) == SideMode.ENERGY, "Generator end is " + portAt(helper, generator));
                    helper.assertTrue(portAt(helper, bearing) == SideMode.INPUT, "Bearing end is " + portAt(helper, bearing));
                    helper.assertTrue(MultiblockPorts.list(helper.getLevel(), turbine).size() == 2, "Turbine has other ports");
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(generator), Direction.EAST) != null,
                            "Generator end gives no energy");
                })
                .thenSucceed();
    }
}
