/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.security.Owned;
import net.zagdrath.arcforge.security.SecuredMenu;
import net.zagdrath.arcforge.security.SecurityRules;
import net.zagdrath.arcforge.tag.ModBlockTags;

// Where machine security is enforced, all server side and in one place: a player placing an Owned block becomes its
// owner; using one (its GUI, buckets, the Wrench, the Settings Card), left-clicking or breaking one is refused
// unless SecurityRules allows it; and a multiblock part can't be added to a structure the player may not use. Menus
// learn their block's security when they open (for the Security tab), and a player opening a block with no owner (one
// placed before security) becomes its owner.
@EventBusSubscriber(modid = Arcforge.MODID)
public final class SecurityEvents {
    private SecurityEvents() {}

    // The placer owns what they place, and a structure it completes if that has no owner yet.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof Player player) || player instanceof FakePlayer
                || !(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        BlockPos pos = event.getPos();
        if (level.getBlockEntity(pos) instanceof Owned owned && owned.owner() == null) {
            owned.setOwner(player.getUUID(), player.getGameProfile().name());
        }
        SecurityRules.of(level, pos).filter(owned -> owned.owner() == null)
                .ifPresent(owned -> owned.setOwner(player.getUUID(), player.getGameProfile().name()));
    }

    // Using a block the player may not: refused, except placing an ordinary block against it. A multiblock part
    // may not go next to a structure the player may not use, so no one extends (and takes over) someone else's.
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }
        ItemStack held = event.getItemStack();
        boolean placing = held.getItem() instanceof BlockItem;
        boolean part = placing && ((BlockItem) held.getItem()).getBlock().defaultBlockState().is(ModBlockTags.MULTIBLOCK_PARTS);
        Optional<Owned> target = SecurityRules.of(level, event.getPos());
        if (target.isPresent() && !SecurityRules.canAccess(player, target.get())) {
            if (placing && !part) {
                event.setUseBlock(TriState.FALSE);
                return;
            }
            refuse(event, player, target.get());
            return;
        }
        if (part) {
            BlockPos placed = event.getPos().relative(event.getFace() != null ? event.getFace() : Direction.UP);
            for (Direction direction : Direction.values()) {
                Optional<Owned> neighbour = SecurityRules.of(level, placed.relative(direction));
                if (neighbour.isPresent() && !SecurityRules.canAccess(player, neighbour.get())) {
                    refuse(event, player, neighbour.get());
                    return;
                }
            }
        }
    }

    private static void refuse(PlayerInteractEvent.RightClickBlock event, Player player, Owned target) {
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        SecurityRules.deny(player, target, false);
    }

    // Left-clicking (a Vault's extract, or starting to mine) a block the player may not use.
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        SecurityRules.of(event.getLevel(), event.getPos()).filter(owned -> !SecurityRules.canAccess(event.getEntity(), owned)).ifPresent(owned -> {
            event.setCanceled(true);
            SecurityRules.deny(event.getEntity(), owned, true);
        });
    }

    // Breaking it, creative mode included; operators get through canAccess.
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        SecurityRules.of(level, event.getPos()).filter(owned -> !SecurityRules.canAccess(event.getPlayer(), owned)).ifPresent(owned -> {
            event.setCanceled(true);
            event.setNotifyClient(true);
            SecurityRules.deny(event.getPlayer(), owned, true);
        });
    }

    @SubscribeEvent
    static void onMenuOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getContainer() instanceof SecuredMenu menu) {
            if (!(player instanceof FakePlayer)) {
                menu.securityAccess().execute((level, pos) -> SecurityRules.of(level, pos).filter(owned -> owned.owner() == null)
                        .ifPresent(owned -> owned.setOwner(player.getUUID(), player.getGameProfile().name())));
            }
            SecuredMenu.sync(player, event.getContainer().containerId, menu.securityAccess());
        }
    }
}
