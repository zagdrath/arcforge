/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;

// A renderer that draws a whole shell structure from its master (drawn globally, see StructureRenderer); only the
// master draws.
public interface ShellMultiblockRenderer<T extends ShellMultiblockBlockEntity, S extends BlockEntityRenderState> extends StructureRenderer<T, S> {
    @Override
    default boolean shouldRender(T blockEntity, Vec3 cameraPosition) {
        return blockEntity.isMaster() && StructureRenderer.super.shouldRender(blockEntity, cameraPosition);
    }

    @Override
    default AABB getRenderBoundingBox(T blockEntity) {
        return blockEntity.getRenderBox();
    }
}
