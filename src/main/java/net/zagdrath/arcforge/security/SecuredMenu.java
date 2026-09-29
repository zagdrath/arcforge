/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.security;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.arcforge.network.SecuritySyncPayload;

// A menu for an Owned block: its Security tab, the security buttons (MachineMenuButtons 300-303) and the access
// recheck in stillValid all go through the block it opened on.
public interface SecuredMenu {
    ContainerLevelAccess securityAccess();

    // How often stillValid re-checks access, so a change of mode closes screens that were open.
    int RECHECK_TICKS = 20;

    // Whether the player may still use the block (checked once a second; true on the client).
    static boolean stillAllowed(Player player, Level level, BlockPos pos) {
        if (level.isClientSide() || player.tickCount % RECHECK_TICKS != 0) {
            return true;
        }
        return SecurityRules.of(level, pos).map(owned -> SecurityRules.canAccess(player, owned)).orElse(true);
    }

    // Tells the player's client who owns the block and how it's secured, for the Security tab.
    static void sync(ServerPlayer player, int containerId, ContainerLevelAccess access) {
        access.execute((level, pos) -> SecurityRules.of(level, pos).ifPresent(owned -> {
            SecuritySyncPayload payload = new SecuritySyncPayload(containerId, owned.owner() != null, owned.ownerName(),
                    SecurityRules.profileMode(owned, level.getServer()), owned.securityOverride(), SecurityRules.canEditSecurity(player, owned),
                    SecurityRules.enabled());
            if (player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }));
    }

    static Optional<BlockPos> pos(ContainerLevelAccess access) {
        return access.evaluate((level, pos) -> pos);
    }
}
