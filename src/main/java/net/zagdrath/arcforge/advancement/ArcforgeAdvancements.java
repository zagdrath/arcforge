/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.advancement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.registry.ModTriggers;
import net.zagdrath.arcforge.security.Owned;
import net.zagdrath.arcforge.security.SecurityRules;

// Fires the Arcforge advancement triggers from machines, server side. A structure forming counts for the player who
// caused it, or else everyone near it. What a machine does counts for its owner if they're online (for a multiblock
// that's the player who completed it), or else everyone near it. Output that happens every tick is throttled.
public final class ArcforgeAdvancements {
    // How far from a machine players count as being there.
    private static final double RANGE = 16.0;
    // A machine's output of one thing counts at most once in this many ticks.
    private static final int PRODUCED_INTERVAL = 20;
    private static final Map<BlockEntity, Map<Object, Long>> LAST_PRODUCED = new WeakHashMap<>();

    private ArcforgeAdvancements() {}

    // Real players within RANGE of pos.
    public static List<ServerPlayer> nearby(ServerLevel level, BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(pos);
        return level.getPlayers(player -> !(player instanceof FakePlayer) && player.distanceToSqr(centre) <= RANGE * RANGE);
    }

    // Who the machine at pos counts for: its owner if they're online, else the players near it.
    public static List<ServerPlayer> recipients(ServerLevel level, BlockPos pos) {
        UUID owner = SecurityRules.of(level, pos).map(Owned::owner).orElse(null);
        if (owner != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
            if (player != null) {
                return List.of(player);
            }
        }
        return nearby(level, pos);
    }

    // A structure just formed (not re-formed after loading: callers only call this on a real change).
    public static void formed(ServerLevel level, MultiblockController controller, @Nullable ServerPlayer cause) {
        BlockPos pos = ((BlockEntity) controller).getBlockPos();
        List<ServerPlayer> players = cause != null ? List.of(cause) : nearby(level, pos);
        Identifier id = controller.multiblockId();
        int length = controller.length();
        for (ServerPlayer player : players) {
            ModTriggers.MULTIBLOCK_FORMED.get().trigger(player, id, length);
        }
    }

    // A machine put out an item, a fluid, or both (with the recipe's category, if it has one).
    public static void produced(BlockEntity machine, ItemStack item, @Nullable Fluid fluid, @Nullable String category) {
        if (!(machine.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Object product = fluid != null ? BuiltInRegistries.FLUID.getKey(fluid) : BuiltInRegistries.ITEM.getKey(item.getItem());
        // Throttled per product and category, so one output can count for several categories at once.
        Object what = category == null ? product : product + "#" + category;
        Map<Object, Long> last = LAST_PRODUCED.computeIfAbsent(machine, key -> new HashMap<>());
        long now = level.getGameTime();
        Long before = last.get(what);
        if (before != null && now - before < PRODUCED_INTERVAL) {
            return;
        }
        last.put(what, now);
        for (ServerPlayer player : recipients(level, machine.getBlockPos())) {
            ModTriggers.MACHINE_PRODUCED.get().trigger(player, item, fluid, category);
        }
    }

    // A turbine array is at full speed (callers check this about once a second).
    public static void turbineFullSpeed(BlockEntity turbine, Identifier id, int length, int signal) {
        if (turbine.getLevel() instanceof ServerLevel level) {
            for (ServerPlayer player : recipients(level, turbine.getBlockPos())) {
                ModTriggers.TURBINE_FULL_SPEED.get().trigger(player, id, length, signal);
            }
        }
    }

    // A burner is running on oxy-fuel at this temperature (callers check this about once a second).
    public static void oxyFuel(BlockEntity burner, int celsius) {
        if (burner.getLevel() instanceof ServerLevel level) {
            for (ServerPlayer player : recipients(level, burner.getBlockPos())) {
                ModTriggers.OXY_FUEL.get().trigger(player, celsius);
            }
        }
    }
}
