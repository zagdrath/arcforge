/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.common;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.network.PortsPayload;

// Keeps a multiblock menu's Ports tab up to date: every half second, the server sends the player the
// structure's ports if they changed (see PortsPayload). Does nothing for single machines.
public final class PortSync {
    private static final int INTERVAL = 10;

    private final Player player;
    private final BlockPos pos;
    private @Nullable List<PortsPayload.Entry> sent;
    private int wait;

    public PortSync(Inventory inventory, BlockPos pos) {
        this.player = inventory.player;
        this.pos = pos;
    }

    // From the menu's broadcastChanges.
    public void update(int containerId) {
        if (!(player instanceof ServerPlayer serverPlayer) || --wait > 0) {
            return;
        }
        wait = INTERVAL;
        Level level = serverPlayer.level();
        if (!(level.getBlockEntity(pos) instanceof MultiblockController controller)) {
            return;
        }
        List<PortsPayload.Entry> ports = controller.isFormed()
                ? MultiblockPorts.list(level, controller).stream()
                        .map(port -> new PortsPayload.Entry(port.mode(), port.side(), port.pos().subtract(pos)))
                        .toList()
                : List.of();
        if (!ports.equals(sent)) {
            sent = ports;
            PacketDistributor.sendToPlayer(serverPlayer, new PortsPayload(containerId, ports));
        }
    }

    // Whether the menu at pos belongs to a multiblock (so its screen shows Ports rather than Sides).
    public static boolean isMultiblock(Inventory inventory, BlockPos pos) {
        return inventory.player.level().getBlockEntity(pos) instanceof MultiblockController;
    }
}
