/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.AreaToolItem;

// The Steel Hammer and Excavator's 3x3. When a player breaks a block with one (not sneaking), each other
// block in AreaToolItem.targets goes through the player's own destroyBlock, so every one fires its own break
// event (claims and protection mods can stop it), drops with the tool's enchantments and costs 1 durability.
@EventBusSubscriber(modid = Arcforge.MODID)
public final class AreaToolEvents {
    // Set while the extra blocks break, so their own break events don't spread further.
    private static final ThreadLocal<Boolean> BREAKING = ThreadLocal.withInitial(() -> false);

    private AreaToolEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onBreak(BreakBlockEvent event) {
        if (event.isCanceled() || BREAKING.get() || !(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level) || player.isShiftKeyDown()) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof AreaToolItem)) {
            return;
        }
        BlockPos centre = event.getPos();
        var targets = AreaToolItem.targets(level, centre, event.getState(), AreaToolItem.miningAxis(player, centre), tool);
        BREAKING.set(true);
        try {
            for (BlockPos pos : targets) {
                // The centre breaks after these (this runs before it), so keep its 1 durability back.
                if (tool.isEmpty() || tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1) {
                    break;
                }
                player.gameMode.destroyBlock(pos);
            }
        } finally {
            BREAKING.set(false);
        }
    }
}
