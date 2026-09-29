/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.menu.common.MachineMenuButtons;
import net.zagdrath.arcforge.menu.machine.SecurityTerminalMenu;
import net.zagdrath.arcforge.network.SecurityEditPayload;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.security.Owned;
import net.zagdrath.arcforge.security.SecurityMode;
import net.zagdrath.arcforge.security.SecurityProfiles;
import net.zagdrath.arcforge.security.SecurityRules;

// Machine security. Owners and profiles are set directly; each test resets the profiles it touched.
public final class SecurityGameTests {
    private SecurityGameTests() {}

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, BlockPos standRelative) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(standRelative);
        player.snapTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        return player;
    }

    // An Arc Melter at pos owned by `owner`.
    private static Owned machine(GameTestHelper helper, BlockPos pos, ServerPlayer owner) {
        helper.setBlock(pos, ModBlocks.ARC_MELTER.get());
        Owned owned = (Owned) helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.BlockEntity.class);
        owned.setOwner(owner.getUUID(), "owner");
        return owned;
    }

    // `player` right-clicks the block at pos from above with `held`.
    private static InteractionResult use(GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos absolute = helper.absolutePos(pos);
        return player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    private static void reset(GameTestHelper helper, ServerPlayer... players) {
        SecurityProfiles profiles = SecurityProfiles.get(helper.getLevel().getServer());
        for (ServerPlayer player : players) {
            profiles.setDefault(player.getUUID(), ArcforgeConfig.SECURITY_DEFAULT_MODE.get());
            profiles.profile(player.getUUID()).trusted().keySet().forEach(id -> profiles.untrust(player.getUUID(), id));
        }
    }

    // `player` opens the menu of the block at pos, as the server does (mock players can't be sent the screen).
    private static void open(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        var provider = (net.minecraft.world.MenuProvider) helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.BlockEntity.class);
        var menu = provider.createMenu(1, player.getInventory(), player);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.PlayerContainerEvent.Open(player, menu));
    }

    // A machine with no owner (placed before security) belongs to the first player to open it; opening it again
    // later, as someone else, doesn't change that.
    static void firstOpenClaims(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer first = player(helper, new BlockPos(0, 1, 0));
        ServerPlayer second = player(helper, new BlockPos(2, 1, 0));
        helper.setBlock(pos, ModBlocks.ARC_MELTER.get());
        Owned owned = (Owned) helper.getBlockEntity(pos, net.minecraft.world.level.block.entity.BlockEntity.class);
        helper.assertTrue(owned.owner() == null, "A machine set without a player has an owner");
        open(helper, first, pos);
        helper.assertTrue(first.getUUID().equals(owned.owner()), "Opening an unowned machine did not claim it: owner " + owned.owner());
        open(helper, second, pos);
        helper.assertTrue(first.getUUID().equals(owned.owner()), "Opening someone else's machine took it: owner " + owned.owner());
        helper.succeed();
    }

    // A Private machine turns another player away (using, wrenching, breaking) but not automation; an operator
    // still gets in.
    static void privateBlocksPlayersNotConduits(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer owner = player(helper, new BlockPos(0, 1, 0));
        ServerPlayer other = player(helper, new BlockPos(2, 1, 0));
        Owned machine = machine(helper, pos, owner);
        machine.setSecurityOverride(Optional.of(SecurityMode.PRIVATE));

        helper.assertTrue(use(helper, other, pos, ItemStack.EMPTY) == InteractionResult.FAIL, "An outsider opened a private machine");
        helper.assertTrue(other.containerMenu == other.inventoryMenu, "An outsider has a menu open");
        use(helper, other, pos, new ItemStack(ModItems.WRENCH.get()));
        helper.assertBlockPresent(ModBlocks.ARC_MELTER.get(), pos);
        helper.assertFalse(other.gameMode.destroyBlock(helper.absolutePos(pos)), "An outsider broke a private machine");
        helper.assertBlockPresent(ModBlocks.ARC_MELTER.get(), pos);

        // Automation isn't affected: its item capability still takes cobblestone.
        var items = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(items != null, "No item capability on the machine");
        try (Transaction tx = Transaction.openRoot()) {
            helper.assertTrue(items.insert(ItemResource.of(Items.COBBLESTONE), 1, tx) == 1, "A conduit couldn't insert into a private machine");
        }

        helper.assertTrue(SecurityRules.canAccess(owner, machine), "The owner was refused");
        var server = helper.getLevel().getServer();
        NameAndId op = new NameAndId(other.getGameProfile());
        // The GameTest server's default operator level is 0, so give this one the gamemaster level outright.
        server.getPlayerList().op(op, Optional.of(net.minecraft.server.permissions.LevelBasedPermissionSet.GAMEMASTER), Optional.empty());
        try {
            helper.assertTrue(SecurityRules.canAccess(other, machine), "An operator was refused");
        } finally {
            server.getPlayerList().deop(op);
        }
        helper.succeed();
    }

    // The owner's terminal profile applies to every machine at once: Public, then Private, then Trusted with the
    // other player trusted.
    static void terminalAppliesEverywhere(GameTestHelper helper) {
        ServerPlayer owner = player(helper, new BlockPos(0, 1, 0));
        ServerPlayer other = player(helper, new BlockPos(4, 1, 0));
        Owned[] machines = { machine(helper, new BlockPos(1, 1, 1), owner), machine(helper, new BlockPos(2, 1, 1), owner),
                machine(helper, new BlockPos(3, 1, 1), owner) };
        helper.setBlock(new BlockPos(1, 1, 3), ModBlocks.SECURITY_TERMINAL.get());
        SecurityTerminalMenu terminal = new SecurityTerminalMenu(0, owner.getInventory(), helper.absolutePos(new BlockPos(1, 1, 3)));
        try {
            SecurityEditPayload.apply(owner, terminal, SecurityEditPayload.setMode(SecurityMode.PUBLIC));
            for (Owned machine : machines) {
                helper.assertTrue(SecurityRules.canAccess(other, machine), "Public refused a player");
            }
            SecurityEditPayload.apply(owner, terminal, SecurityEditPayload.setMode(SecurityMode.PRIVATE));
            for (Owned machine : machines) {
                helper.assertFalse(SecurityRules.canAccess(other, machine), "Private let a player in");
            }
            SecurityProfiles.get(helper.getLevel().getServer()).trust(owner.getUUID(), other.getUUID(), "other");
            SecurityEditPayload.apply(owner, terminal, SecurityEditPayload.setMode(SecurityMode.TRUSTED));
            for (Owned machine : machines) {
                helper.assertTrue(SecurityRules.canAccess(other, machine), "Trusted refused a trusted player");
            }
            SecurityEditPayload.apply(owner, terminal, SecurityEditPayload.remove(other.getUUID()));
            helper.assertFalse(SecurityRules.canAccess(other, machines[0]), "Trusted let in a removed player");
        } finally {
            reset(helper, owner);
        }
        helper.succeed();
    }

    // A block's override beats the profile; the profile button clears it; only the owner may change it.
    static void overrideWins(GameTestHelper helper) {
        ServerPlayer owner = player(helper, new BlockPos(0, 1, 0));
        ServerPlayer other = player(helper, new BlockPos(4, 1, 0));
        SecurityProfiles profiles = SecurityProfiles.get(helper.getLevel().getServer());
        profiles.setDefault(owner.getUUID(), SecurityMode.PRIVATE);
        try {
            Owned first = machine(helper, new BlockPos(1, 1, 1), owner);
            BlockPos secondPos = new BlockPos(2, 1, 1);
            Owned second = machine(helper, secondPos, owner);
            second.setSecurityOverride(Optional.of(SecurityMode.PUBLIC));
            helper.assertFalse(SecurityRules.canAccess(other, first), "The profile's Private let a player in");
            helper.assertTrue(SecurityRules.canAccess(other, second), "A Public override refused a player");

            ContainerLevelAccess access = ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(secondPos));
            MachineMenuButtons.handle(access, owner, MachineMenuButtons.SECURITY_PROFILE);
            helper.assertTrue(second.securityOverride().isEmpty(), "The profile button didn't clear the override");
            helper.assertFalse(SecurityRules.canAccess(other, second), "Clearing the override didn't apply the profile");

            profiles.trust(owner.getUUID(), other.getUUID(), "other");
            MachineMenuButtons.handle(access, other, MachineMenuButtons.securityButtonId(Optional.of(SecurityMode.TRUSTED)));
            helper.assertTrue(second.securityOverride().isEmpty(), "A trusted player changed the security");
        } finally {
            reset(helper, owner);
        }
        helper.succeed();
    }

    // With security off, anyone may use a Private machine. (All in one tick, so other tests never see it off.)
    static void disabledConfig(GameTestHelper helper) {
        ServerPlayer owner = player(helper, new BlockPos(0, 1, 0));
        ServerPlayer other = player(helper, new BlockPos(2, 1, 0));
        Owned machine = machine(helper, new BlockPos(1, 1, 1), owner);
        machine.setSecurityOverride(Optional.of(SecurityMode.PRIVATE));
        boolean was = ArcforgeConfig.SECURITY_ENABLED.get();
        ArcforgeConfig.SECURITY_ENABLED.set(false);
        try {
            helper.assertTrue(SecurityRules.canAccess(other, machine), "Security off still refused a player");
        } finally {
            ArcforgeConfig.SECURITY_ENABLED.set(was);
        }
        helper.assertFalse(SecurityRules.canAccess(other, machine), "Security back on let a player in");
        helper.succeed();
    }

    // A placed block belongs to its placer.
    static void placerOwns(GameTestHelper helper) {
        ServerPlayer placer = player(helper, new BlockPos(0, 1, 0));
        BlockPos below = new BlockPos(1, 0, 1);
        helper.setBlock(below, Block.byItem(Items.STONE));
        helper.setBlock(below.above(), net.minecraft.world.level.block.Blocks.AIR);
        ItemStack melter = new ItemStack(ModItems.ARC_MELTER.get());
        use(helper, placer, below, melter);
        BlockPos placed = below.above();
        helper.assertBlockPresent(ModBlocks.ARC_MELTER.get(), placed);
        Owned owned = (Owned) helper.getBlockEntity(placed, net.minecraft.world.level.block.entity.BlockEntity.class);
        helper.assertTrue(placer.getUUID().equals(owned.owner()), "The placer doesn't own it: " + owned.owner());
        helper.succeed();
    }
}
