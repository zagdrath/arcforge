/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

// A renderer that draws a whole structure from one block entity (its controller or master). Vanilla only draws a block
// entity while the chunk section it sits in is visible, so a big structure vanished whenever that section was culled or
// hidden behind other blocks, though the rest of it was in view. These are drawn globally instead, culled by the
// structure's box (getRenderBoundingBox), judged by the nearest part of the structure rather than by its corner.
public interface StructureRenderer<T extends BlockEntity, S extends BlockEntityRenderState> extends BlockEntityRenderer<T, S> {
    @Override
    default boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    default boolean shouldRender(T blockEntity, Vec3 cameraPosition) {
        int distance = getViewDistance();
        return getRenderBoundingBox(blockEntity).distanceToSqr(cameraPosition) < (double) distance * distance;
    }
}
