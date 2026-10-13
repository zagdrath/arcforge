/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.blockentity.multiblock.DieselGeneratorBlockEntity;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.steam.DieselGeneratorMesh;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed Diesel Generator from DieselGeneratorLayout, built by DieselGeneratorMesh on the Pumpjack's sheet, its
// fan and coupling turning as fast as the engine runs (see DieselGeneratorBlockEntity.advance).
public class DieselGeneratorRenderer extends PlantRenderer<DieselGeneratorBlockEntity, DieselGeneratorMesh> {
    public DieselGeneratorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected Identifier sheet() {
        return PumpjackRenderer.SHEET;
    }

    @Override
    protected DieselGeneratorMesh newMesh(ModelFrame frame) {
        return new DieselGeneratorMesh(frame);
    }

    @Override
    protected float[][] buildStatic(DieselGeneratorMesh mesh, List<ModelMesh.Port> ports) {
        return mesh.buildStatic(ports);
    }

    @Override
    protected float[] buildMoving(DieselGeneratorMesh mesh, DieselGeneratorBlockEntity generator, float now) {
        return mesh.buildMoving(generator.advance(now));
    }
}
