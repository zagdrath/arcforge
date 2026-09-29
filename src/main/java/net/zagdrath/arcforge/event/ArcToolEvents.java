/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.tool.ArcToolItem;
import net.zagdrath.arcforge.item.tool.AreaToolItem;
import net.zagdrath.arcforge.item.tool.ModuleType;
import net.zagdrath.arcforge.item.tool.ToolModules;
import net.zagdrath.arcforge.tag.ModBlockTags;

// Arc Drill and Arc Saw mining. Without the FE for a block they don't dig it at all (and the break is cancelled
// as a backup). The Speed module and the configured tier speed scale the dig speed. A break also takes, in this
// order of preference: the rest of the tree (Saw with Felling on), the rest of the ore vein (Drill with Vein
// on), or the area around it (Area on). Each extra block goes through the player's own destroyBlock, so it fires
// its own break event, drops with the tool's Silk Touch or Fortune, and costs its own FE.
@EventBusSubscriber(modid = Arcforge.MODID)
public final class ArcToolEvents {
    private static final ThreadLocal<Boolean> BREAKING = ThreadLocal.withInitial(() -> false);
    private static final Map<UUID, Long> LAST_NO_ENERGY = new HashMap<>();

    private ArcToolEvents() {}

    @SubscribeEvent
    static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof ArcToolItem item) || event.getPosition().isEmpty()) {
            return;
        }
        Level level = player.level();
        BlockPos pos = event.getPosition().get();
        if (!player.hasInfiniteMaterials() && ArcToolItem.energy(tool) < ArcToolItem.cost(tool, level, pos, event.getState())) {
            event.setNewSpeed(0.0F);
            if (level.isClientSide()) {
                long now = level.getGameTime();
                Long last = LAST_NO_ENERGY.get(player.getUUID());
                if (last == null || now - last >= 20 || now < last) {
                    LAST_NO_ENERGY.put(player.getUUID(), now);
                    player.sendOverlayMessage(Component.translatable("message.arcforge.arc_tool.no_energy"));
                }
            }
            return;
        }
        // Only where the tool's own rule applies (its mineable blocks): rescale to the configured speed.
        float speed = event.getNewSpeed();
        if (tool.getDestroySpeed(event.getState()) > 1.0F) {
            speed *= item.configuredSpeed() / item.baseSpeed();
        }
        if (ArcToolItem.modules(tool).isOn(ModuleType.SPEED)) {
            speed *= (float) ArcforgeConfig.SPEED_MODULE_MULTIPLIER.getAsDouble();
        }
        event.setNewSpeed(speed);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onBreak(BreakBlockEvent event) {
        if (event.isCanceled() || BREAKING.get() || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof ArcToolItem item)) {
            return;
        }
        BlockPos centre = event.getPos();
        BlockState state = event.getState();
        boolean free = player.hasInfiniteMaterials();
        int centreCost = ArcToolItem.cost(tool, level, centre, state);
        if (!free && ArcToolItem.energy(tool) < centreCost) {
            event.setCanceled(true);
            return;
        }
        ToolModules modules = ArcToolItem.modules(tool);
        List<BlockPos> extras;
        boolean felling = false;
        if (item.kind() == ArcToolItem.Kind.SAW && ArcToolItem.isFelling(tool) && !player.isShiftKeyDown() && state.is(ModBlockTags.FELLABLE)) {
            int limit = modules.isOn(ModuleType.VEIN) ? ArcforgeConfig.FELLING_VEIN_LIMIT.getAsInt() : ArcforgeConfig.FELLING_LIMIT.getAsInt();
            extras = connected(level, centre, limit, candidate -> candidate.is(ModBlockTags.FELLABLE), centre.getY() - 1);
            felling = true;
        } else if (item.kind() == ArcToolItem.Kind.DRILL && modules.isOn(ModuleType.VEIN) && state.is(ModBlockTags.VEIN_MINEABLE)) {
            Block block = state.getBlock();
            extras = connected(level, centre, ArcforgeConfig.VEIN_LIMIT.getAsInt(), candidate -> candidate.is(block), Integer.MIN_VALUE);
        } else if (modules.isOn(ModuleType.AREA)) {
            extras = AreaToolItem.targets(level, centre, state, AreaToolItem.miningAxis(player, centre), tool, item.areaRadius());
        } else {
            return;
        }
        BREAKING.set(true);
        if (felling) {
            ArcToolItem.COST_MULTIPLIER.set(ArcforgeConfig.FELLING_FE_MULTIPLIER.getAsDouble());
        }
        try {
            for (BlockPos pos : extras) {
                // Keep the centre's FE back: it breaks after these.
                if (!free && ArcToolItem.energy(tool) - centreCost < ArcToolItem.cost(tool, level, pos, level.getBlockState(pos))) {
                    break;
                }
                player.gameMode.destroyBlock(pos);
            }
        } finally {
            BREAKING.set(false);
            ArcToolItem.COST_MULTIPLIER.remove();
        }
    }

    // Blocks matching `matches` connected to `start` through any of the 26 neighbours, nearest first, at or above
    // `minY`: at most `limit` including the start (which isn't returned).
    public static List<BlockPos> connected(Level level, BlockPos start, int limit, Predicate<BlockState> matches, int minY) {
        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && found.size() + 1 < limit) {
            BlockPos current = queue.poll();
            for (BlockPos next : BlockPos.betweenClosed(current.offset(-1, -1, -1), current.offset(1, 1, 1))) {
                if (found.size() + 1 >= limit) {
                    break;
                }
                if (next.getY() < minY || seen.contains(next) || !matches.test(level.getBlockState(next))) {
                    continue;
                }
                BlockPos immutable = next.immutable();
                seen.add(immutable);
                found.add(immutable);
                queue.add(immutable);
            }
        }
        return found;
    }
}
