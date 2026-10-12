/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;

// A renderer that draws a whole shell structure from its master. Vanilla only draws a block entity while the chunk
// section it sits in is visible, so a big structure vanished whenever the master's section was culled or hidden behind
// other blocks, though the rest of it was in view. These are drawn globally instead, culled by the whole structure's
// box, and only the master draws, judged by the nearest part of the structure rather than by its corner.
public interface ShellMultiblockRenderer<T extends ShellMultiblockBlockEntity, S extends BlockEntityRenderState> extends BlockEntityRenderer<T, S> {
    @Override
    default boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    default boolean shouldRender(T blockEntity, Vec3 cameraPosition) {
        int distance = getViewDistance();
        return blockEntity.isMaster() && getRenderBoundingBox(blockEntity).distanceToSqr(cameraPosition) < (double) distance * distance;
    }

    @Override
    default AABB getRenderBoundingBox(T blockEntity) {
        return blockEntity.getRenderBox();
    }
}
