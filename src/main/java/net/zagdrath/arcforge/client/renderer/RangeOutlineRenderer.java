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
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.logistics.ChunkLoaderBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
import net.zagdrath.arcforge.menu.logistics.ChunkLoaderMenu;
import net.zagdrath.arcforge.menu.machine.ArcQuarryConfigMenu;
import net.zagdrath.arcforge.menu.machine.VacuumCollectorMenu;
import net.zagdrath.arcforge.registry.ModItems;

// The Vacuum Collector's range as a cyan box in the world: for every loaded collector within 32 blocks while the
// player holds a Vacuum Collector, for the one whose GUI is open, and for any set to always show it (its Show area
// button). The Arc Quarry's area likewise (see quarryAreas), and the chunks of a Chunk Loader whose screen is open or
// that is set to always show them (chunkLoaderArea).
// Drawn as per-tick gizmos, which the game renders until the next tick.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class RangeOutlineRenderer {
    private static final int COLOR = 0xCC5FD4C4;
    private static final double NEARBY = 32.0;
    private static final double QUARRY_NEARBY = 64.0;
    private static final int MAX_QUARRY_RADIUS = 64;
    private static final int LAYER_COLOR = 0xFFA8FFF4;
    private RangeOutlineRenderer() {}

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
        if (player.containerMenu instanceof VacuumCollectorMenu menu) {
            shown.add(menu.getPos());
        }
        for (BlockPos pos : VacuumCollectorBlockEntity.clientLoaded()) {
            if (minecraft.level.getBlockEntity(pos) instanceof VacuumCollectorBlockEntity collector && collector.showArea()) {
                shown.add(pos);
            }
        }
        for (BlockPos pos : shown) {
            if (minecraft.level.getBlockEntity(pos) instanceof VacuumCollectorBlockEntity collector) {
                Gizmos.cuboid(VacuumCollectorBlockEntity.area(pos, collector.getRange()), GizmoStyle.stroke(COLOR, 2.0F));
            }
        }
        quarryAreas(minecraft, player);
        chunkLoaderArea(minecraft, player);
    }

    // Chunk Loaders: the one whose screen is open (at the radius the screen shows) and any set to always show its area.
    private static void chunkLoaderArea(Minecraft minecraft, LocalPlayer player) {
        BlockPos open = null;
        if (player.containerMenu instanceof ChunkLoaderMenu menu && minecraft.level.getBlockEntity(menu.getPos()) instanceof ChunkLoaderBlockEntity loader) {
            open = menu.getPos();
            chunkLoaderArea(open, loader, menu.getRadius());
        }
        for (BlockPos pos : ChunkLoaderBlockEntity.clientLoaded()) {
            if (!pos.equals(open) && minecraft.level.getBlockEntity(pos) instanceof ChunkLoaderBlockEntity loader && loader.showArea()) {
                chunkLoaderArea(pos, loader, loader.getRadius());
            }
        }
    }

    // Each chunk a loader covers as a square at its height, and the whole area as a box reaching a little above and
    // below it.
    private static void chunkLoaderArea(BlockPos pos, ChunkLoaderBlockEntity loader, int radius) {
        int chunkX = pos.getX() >> 4, chunkZ = pos.getZ() >> 4;
        double y = pos.getY();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double x0 = (chunkX + dx) * 16.0, z0 = (chunkZ + dz) * 16.0;
                Gizmos.cuboid(new AABB(x0, y, z0, x0 + 16, y, z0 + 16), GizmoStyle.stroke(dx == 0 && dz == 0 ? LAYER_COLOR : COLOR, 2.0F));
            }
        }
        double minX = (chunkX - radius) * 16.0, minZ = (chunkZ - radius) * 16.0;
        double maxX = (chunkX + radius + 1) * 16.0, maxZ = (chunkZ + radius + 1) * 16.0;
        Gizmos.cuboid(new AABB(minX, y - 8, minZ, maxX, y + 24, maxZ), GizmoStyle.stroke(loader.getRadius() == radius ? COLOR : LAYER_COLOR, 1.5F));
    }

    // Arc Quarry areas: every quarry within 64 blocks while the player holds one, the one whose settings are open,
    // and any set to always show its area.
    private static void quarryAreas(Minecraft minecraft, LocalPlayer player) {
        Set<BlockPos> shown = new HashSet<>();
        boolean holding = player.getItemInHand(InteractionHand.MAIN_HAND).is(ModItems.ARC_QUARRY.get())
                || player.getItemInHand(InteractionHand.OFF_HAND).is(ModItems.ARC_QUARRY.get());
        for (BlockPos pos : ArcQuarryBlockEntity.clientLoaded()) {
            if (holding && pos.closerToCenterThan(player.position(), QUARRY_NEARBY)
                    || minecraft.level.getBlockEntity(pos) instanceof ArcQuarryBlockEntity quarry && quarry.getSettings().showArea()) {
                shown.add(pos);
            }
        }
        if (player.containerMenu instanceof ArcQuarryConfigMenu menu) {
            shown.add(menu.getPos());
        }
        for (BlockPos pos : shown) {
            if (minecraft.level.getBlockEntity(pos) instanceof ArcQuarryBlockEntity quarry && quarry.getSettings().radius() <= MAX_QUARRY_RADIUS) {
                AABB area = quarry.area();
                Gizmos.cuboid(area, GizmoStyle.stroke(COLOR, 2.0F));
                // A brighter square where it's easy to see: the layer it's mining, or else the ground the quarry stands
                // on (kept inside the area). A big area's own edges are far away and mostly underground or in the sky.
                BlockPos target = quarry.getLastTarget();
                boolean mining = target != null && quarry.getQuarryState() == ArcQuarryBlockEntity.State.MINING;
                double y = mining ? target.getY() : Math.max(area.minY, Math.min(area.maxY, pos.getY() - 1));
                Gizmos.cuboid(new AABB(area.minX, y, area.minZ, area.maxX, mining ? y + 1 : y, area.maxZ), GizmoStyle.stroke(LAYER_COLOR, 3.0F));
            }
        }
    }
}
