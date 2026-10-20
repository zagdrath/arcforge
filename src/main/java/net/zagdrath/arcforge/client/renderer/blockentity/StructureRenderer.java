/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.client.renderer.LargeModels;

// A renderer that draws a whole structure from one block entity (its controller or master). Vanilla only draws a block
// entity while the chunk section it sits in is visible, so a big structure vanished whenever that section was culled or
// hidden behind other blocks, though the rest of it was in view. These are drawn globally instead, culled by the
// structure's box (getRenderBoundingBox), judged by the nearest part of the structure rather than by its corner, and
// skipped while none of the sections that box is in is drawn this frame (out of view, or hidden behind the terrain).
//
// One whose still parts are baked into the chunk mesh (bakesStill; see ChunkMeshes) has only its moving, glowing and
// fluid parts left to draw, so it's drawn out to stillPartsOnlyFrom and not past it (see LargeModels). Until it has
// baked, it's asked at any distance the world is drawn, so it bakes and its still parts show from afar.
public interface StructureRenderer<T extends BlockEntity, S extends BlockEntityRenderState> extends BlockEntityRenderer<T, S> {
    @Override
    default boolean shouldRenderOffScreen() {
        return true;
    }

    // Whether this renderer bakes the structure's still parts into the chunk mesh, and whether it has now.
    default boolean bakes() {
        return false;
    }

    default boolean bakesStill(T blockEntity) {
        return false;
    }

    @Override
    default boolean shouldRender(T blockEntity, Vec3 cameraPosition) {
        AABB box = getRenderBoundingBox(blockEntity);
        double d2 = box.distanceToSqr(cameraPosition);
        if (bakesStill(blockEntity)) {
            int distance = LargeModels.stillOnlyDistance();
            return d2 < (double) distance * distance && LargeModels.anySectionVisible(box);
        }
        if (bakes()) {
            int distance = Math.max(getViewDistance(), Minecraft.getInstance().options.getEffectiveRenderDistance() * 16);
            return d2 < (double) distance * distance;
        }
        int distance = getViewDistance();
        return d2 < (double) distance * distance && LargeModels.anySectionVisible(box);
    }
}
