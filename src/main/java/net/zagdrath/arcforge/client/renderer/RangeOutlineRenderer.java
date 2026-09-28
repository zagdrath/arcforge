/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
import net.zagdrath.arcforge.menu.machine.VacuumCollectorMenu;
import net.zagdrath.arcforge.registry.ModItems;

// The Vacuum Collector's range as a cyan box in the world: for every loaded collector within 32 blocks while the
// player holds a Vacuum Collector, and for the one whose GUI is open while its show-range button is on. Drawn as
// per-tick gizmos, which the game renders until the next tick.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class RangeOutlineRenderer {
    private static final int COLOR = 0xCC5FD4C4;
    private static final double NEARBY = 32.0;
    // The GUI's show-range button; on by default, remembered for the session.
    private static boolean guiOutline = true;

    private RangeOutlineRenderer() {}

    public static boolean isGuiOutlineShown() {
        return guiOutline;
    }

    public static void toggleGuiOutline() {
        guiOutline = !guiOutline;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        Set<BlockPos> shown = new HashSet<>();
        if (player.getItemInHand(InteractionHand.MAIN_HAND).is(ModItems.VACUUM_COLLECTOR.get())
                || player.getItemInHand(InteractionHand.OFF_HAND).is(ModItems.VACUUM_COLLECTOR.get())) {
            for (BlockPos pos : VacuumCollectorBlockEntity.clientLoaded()) {
                if (pos.closerToCenterThan(player.position(), NEARBY)) {
                    shown.add(pos);
                }
            }
        }
        if (guiOutline && player.containerMenu instanceof VacuumCollectorMenu menu) {
            shown.add(menu.getPos());
        }
        for (BlockPos pos : shown) {
            if (minecraft.level.getBlockEntity(pos) instanceof VacuumCollectorBlockEntity collector) {
                Gizmos.cuboid(VacuumCollectorBlockEntity.area(pos, collector.getRange()), GizmoStyle.stroke(COLOR, 2.0F));
            }
        }
    }
}
