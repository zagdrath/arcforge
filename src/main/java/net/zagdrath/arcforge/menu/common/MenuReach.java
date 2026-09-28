/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.common;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.zagdrath.arcforge.multiblock.MultiblockController;

// Whether a machine's menu stays open. As vanilla's check (the block is still there, and the player is within
// reach of it plus 4 blocks), except that for a formed multiblock the reach is to the nearest block of the
// whole structure rather than to its controller: every block opens the controller's menu, and on a big
// structure (a 9-long turbine) the controller can be out of reach of the block that was clicked.
public final class MenuReach {
    private static final double SLACK = 4.0;

    private MenuReach() {}

    public static boolean stillValid(ContainerLevelAccess access, Player player, Block block) {
        return access.evaluate((level, pos) -> {
            if (!level.getBlockState(pos).is(block)) {
                return false;
            }
            AABB box = new AABB(pos);
            if (level.getBlockEntity(pos) instanceof MultiblockController controller && controller.isFormed()) {
                box = AABB.encapsulatingFullBlocks(controller.getMinCorner(), controller.getMaxCorner());
            }
            double reach = player.blockInteractionRange() + SLACK;
            return box.distanceToSqr(player.getEyePosition()) <= reach * reach;
        }, true);
    }
}
