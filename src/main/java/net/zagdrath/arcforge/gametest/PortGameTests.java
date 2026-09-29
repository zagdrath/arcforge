/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
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
import net.zagdrath.arcforge.multiblock.PortFaces;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModItems;

// Multiblock ports: a new structure starts with none, only ports do IO, and the Wrench sets them.
public final class PortGameTests {
    private PortGameTests() {}

    // The (absolute) position of the structure's first port in this mode, or null.
    static @Nullable BlockPos port(GameTestHelper helper, MultiblockController controller, SideMode mode) {
        return MultiblockPorts.list(helper.getLevel(), controller).stream()
                .filter(port -> port.mode() == mode).map(MultiblockPorts.Port::pos).findFirst().orElse(null);
    }

    // The port on this face of the block at pos (relative).
    static SideMode portAt(GameTestHelper helper, BlockPos relative, Direction face) {
        return MultiblockPorts.get(helper.getLevel(), helper.absolutePos(relative), face);
    }

    // The port on any face of the block at pos (relative), or NONE.
    static SideMode portAt(GameTestHelper helper, BlockPos relative) {
        PortFaces faces = MultiblockPorts.faces(helper.getLevel(), helper.absolutePos(relative));
        for (Direction face : Direction.values()) {
            if (faces.get(face) != SideMode.NONE) {
                return faces.get(face);
            }
        }
        return SideMode.NONE;
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

    // A new array starts with no ports, so no block does IO.
    static void portsStartEmpty(GameTestHelper helper) {
        buildArray(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class);
                    helper.assertTrue(array.isFormed(), "Array did not form");
                    helper.assertTrue(MultiblockPorts.list(helper.getLevel(), array).isEmpty(),
                            "New array has ports: " + MultiblockPorts.list(helper.getLevel(), array));
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(new BlockPos(1, 3, 1)), Direction.UP) == null,
                            "Top middle accepts items without a port");
                })
                .thenSucceed();
    }

    // An array saved and loaded before it first forms is still new: finishing it after the reload gives
    // it no ports (only saves from before ports get ports from their side configuration).
    static void portsStartEmptyAfterReload(GameTestHelper helper) {
        buildArray(helper);
        BlockPos corner = new BlockPos(0, 1, 0);
        helper.setBlock(corner, net.minecraft.world.level.block.Blocks.AIR);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class);
                    helper.assertTrue(!array.isFormed(), "Array formed without a corner");
                    var registries = helper.getLevel().registryAccess();
                    array.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, array.saveCustomOnly(registries)));
                    helper.setBlock(corner, ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class);
                    helper.assertTrue(array.isFormed(), "Array did not form");
                    helper.assertTrue(MultiblockPorts.list(helper.getLevel(), array).isEmpty(),
                            "Array reloaded before forming got ports: " + MultiblockPorts.list(helper.getLevel(), array));
                })
                .thenSucceed();
    }

    // In Port mode, Shift+Right-click on an outer face cycles that face's port through the modes the
    // structure allows, and each face of a block is its own port; an inner face isn't a port position; a
    // plain click only reports.
    static void wrenchSetsPort(GameTestHelper helper) {
        buildArray(helper);
        BlockPos corner = new BlockPos(0, 3, 2);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                    wrench(helper, player, corner, Direction.UP, WrenchMode.PORT);
                    helper.assertTrue(portAt(helper, corner, Direction.UP) == SideMode.NONE, "A plain click changed the port");
                    player.setShiftKeyDown(true);
                    wrench(helper, player, corner, Direction.UP, WrenchMode.PORT);
                    helper.assertTrue(portAt(helper, corner, Direction.UP) == SideMode.INPUT, "First port mode is " + portAt(helper, corner, Direction.UP));
                    wrench(helper, player, corner, Direction.UP, WrenchMode.PORT);
                    helper.assertTrue(portAt(helper, corner, Direction.UP) == SideMode.OUTPUT, "Second port mode is " + portAt(helper, corner, Direction.UP));
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(corner), Direction.UP) != null,
                            "New port does not give items");
                    // The port is on the clicked face only: the corner's other outer faces do nothing yet.
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(corner), Direction.SOUTH) == null,
                            "The port also works on another face of its block");
                    // Another face of the same block is its own port: the top keeps its output.
                    wrench(helper, player, corner, Direction.SOUTH, WrenchMode.PORT);
                    helper.assertTrue(portAt(helper, corner, Direction.SOUTH) == SideMode.INPUT, "South face is " + portAt(helper, corner, Direction.SOUTH));
                    helper.assertTrue(portAt(helper, corner, Direction.UP) == SideMode.OUTPUT, "Setting the south face changed the top to " + portAt(helper, corner, Direction.UP));
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(corner), Direction.SOUTH) != null,
                            "The south port does not take items");
                    // Clicking the side of the centre casing that faces into the array: not a port position.
                    wrench(helper, player, new BlockPos(1, 2, 1), Direction.UP, WrenchMode.PORT);
                    helper.assertTrue(portAt(helper, new BlockPos(1, 2, 1)) == SideMode.NONE, "The inside became a port");
                    // Round through energy back to none.
                    wrench(helper, player, corner, Direction.UP, WrenchMode.PORT);
                    wrench(helper, player, corner, Direction.UP, WrenchMode.PORT);
                    helper.assertTrue(portAt(helper, corner, Direction.UP) == SideMode.NONE, "Did not wrap back to none: " + portAt(helper, corner, Direction.UP));
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

    // Ports stay where they were set: breaking a port's block un-forms the array, and putting it back brings
    // the port back when the array forms again.
    static void portsSurviveRebuild(GameTestHelper helper) {
        buildArray(helper);
        BlockPos corner = new BlockPos(0, 3, 2);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ArcCrushingArrayBlockEntity array = helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class);
                    MultiblockPorts.set(helper.getLevel(), array, helper.absolutePos(corner), SideMode.OUTPUT, Direction.SOUTH);
                    MultiblockPorts.set(helper.getLevel(), array, helper.absolutePos(corner), SideMode.ENERGY, Direction.WEST);
                    helper.setBlock(corner, net.minecraft.world.level.block.Blocks.AIR);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(!helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class).isFormed(), "Array still formed");
                    helper.setBlock(corner, ModBlocks.ARC_CRUSHING_ARRAY_CASING.get());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(new BlockPos(1, 2, 1), ArcCrushingArrayBlockEntity.class).isFormed(), "Array did not re-form");
                    helper.assertTrue(portAt(helper, corner, Direction.SOUTH) == SideMode.OUTPUT && portAt(helper, corner, Direction.WEST) == SideMode.ENERGY,
                            "Ports after rebuilding: south " + portAt(helper, corner, Direction.SOUTH) + ", west " + portAt(helper, corner, Direction.WEST));
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(corner), Direction.SOUTH) != null,
                            "The rebuilt output port gives no items");
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

    // A new turbine starts with no ports, even on its end caps; an energy port set on the generator end
    // cap gives energy.
    static void turbinePorts(GameTestHelper helper) {
        BlockPos min = new BlockPos(0, 1, 0);
        SteamGameTests.buildShell(helper, min, Direction.Axis.X, 4, ModBlocks.STEAM_TURBINE_ARRAY_CASING.get(), new BlockPos(1, 2, 0));
        BlockPos generator = new BlockPos(3, 2, 1);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    SteamTurbineArrayBlockEntity turbine = helper.getBlockEntity(min, SteamTurbineArrayBlockEntity.class);
                    helper.assertTrue(turbine.isFormed(), "Turbine did not form");
                    helper.assertTrue(helper.getBlockState(generator).getValue(SteamTurbineArrayCasingBlock.END) == SteamTurbineArrayCasingBlock.End.GENERATOR,
                            "Generator cap is not at +X");
                    helper.assertTrue(MultiblockPorts.list(helper.getLevel(), turbine).isEmpty(), "New turbine has ports");
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(generator), Direction.EAST) == null,
                            "Generator end gives energy without a port");
                    MultiblockPorts.set(helper.getLevel(), turbine, helper.absolutePos(generator), SideMode.ENERGY, Direction.EAST);
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(generator), Direction.EAST) != null,
                            "Generator end port gives no energy");
                })
                .thenSucceed();
    }
}
