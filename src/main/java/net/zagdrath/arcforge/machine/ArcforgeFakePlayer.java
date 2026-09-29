/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// The player the Block Breaker and Block Placer act as, so break and place events (and protection mods) see a
// player. One per level, shared by every machine; each action places it at the machine, facing the target.
public final class ArcforgeFakePlayer {
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("5f3a1c2e-8b7d-4e61-9a0f-2c4d6e8a0b1c"), "[Arcforge]");

    private ArcforgeFakePlayer() {}

    // Whether this is the fake player Arcforge's machines act through.
    public static boolean is(net.minecraft.world.entity.player.Player player) {
        return player instanceof FakePlayer && PROFILE.id().equals(player.getUUID());
    }

    public static FakePlayer get(ServerLevel level) {
        return FakePlayerFactory.get(level, PROFILE);
    }

    // The fake player at the machine's centre, looking at the face in front of it, holding `held`.
    public static FakePlayer at(ServerLevel level, BlockPos machine, Direction facing, ItemStack held) {
        FakePlayer player = get(level);
        player.snapTo(machine.getX() + 0.5, machine.getY() + 0.5 - player.getEyeHeight(), machine.getZ() + 0.5,
                facing.toYRot(), facing == Direction.UP ? -90.0F : facing == Direction.DOWN ? 90.0F : 0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }
}
