/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeClientConfig;

// How much of a large model its renderer draws, by how far it is (arcforge-client.toml, largeModels): the still parts are
// in the chunk mesh at any distance (ChunkMeshes); up close the moving parts turn (FULL); past simpleMovingPartsFrom
// they're plain solid shapes standing still (SIMPLE); past stillPartsOnlyFrom the renderer draws nothing (STILL). And
// whether any chunk section a model's box is in is being drawn this frame (in view and not hidden behind the terrain),
// so a model hidden from view isn't drawn either.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class LargeModels {
    public enum Detail { FULL, SIMPLE, STILL }

    // The sections drawn this frame, gathered the first time a renderer asks.
    private static final LongOpenHashSet VISIBLE = new LongOpenHashSet();
    private static long frame, gathered = -1;

    private LargeModels() {}

    public static int simpleDistance() {
        return ArcforgeClientConfig.LOD_DISTANCE.getAsInt();
    }

    public static int stillOnlyDistance() {
        return Math.max(simpleDistance(), ArcforgeClientConfig.STATIC_ONLY_DISTANCE.getAsInt());
    }

    // The detail a model in box is drawn at from camera.
    public static Detail detail(AABB box, Vec3 camera) {
        double d2 = box.distanceToSqr(camera);
        double simple = simpleDistance(), still = stillOnlyDistance();
        return d2 < simple * simple ? Detail.FULL : d2 < still * still ? Detail.SIMPLE : Detail.STILL;
    }

    // Whether any section box is in is drawn this frame. Until the frame's sections are known (or with none), yes.
    public static boolean anySectionVisible(AABB box) {
        if (gathered != frame) {
            gather();
        }
        if (VISIBLE.isEmpty()) {
            return true;
        }
        int x0 = SectionPos.blockToSectionCoord(box.minX), x1 = SectionPos.blockToSectionCoord(box.maxX);
        int y0 = SectionPos.blockToSectionCoord(box.minY), y1 = SectionPos.blockToSectionCoord(box.maxY);
        int z0 = SectionPos.blockToSectionCoord(box.minZ), z1 = SectionPos.blockToSectionCoord(box.maxZ);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (VISIBLE.contains(SectionPos.asLong(x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void gather() {
        gathered = frame;
        VISIBLE.clear();
        LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
        for (SectionRenderDispatcher.RenderSection section : renderer.visibleSections()) {
            VISIBLE.add(section.getSectionNode());
        }
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        frame++;
    }
}
