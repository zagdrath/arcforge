/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.phys.shapes.VoxelShape;

// The controller of a multiblock drawn by a renderer whose blocks take the shape of its model (see PlantHitbox): the
// Pumpjack, the Cracking Unit and the Diesel Generator.
public interface ShapedPlant extends OilfieldController {
    // The frame its model and shapes are built in (from its minimum corner), or null while it isn't formed.
    @Nullable ModelFrame plantFrame();

    // Its blocks' shapes in that frame (ModelFrame.shapes of the model's still parts), indexed by ModelFrame.index.
    VoxelShape[] plantShapes();
}
