/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.blockentity.multiblock.CrackingUnitBlockEntity;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.steam.CrackingUnitMesh;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed Cracking Unit from CrackingUnitLayout, built by CrackingUnitMesh on the refinery tower's sheet, its
// regenerator's sight port glowing as bright as the unit is hot (see CrackingUnitBlockEntity.getGlow).
public class CrackingUnitRenderer extends PlantRenderer<CrackingUnitBlockEntity, CrackingUnitMesh> {
    public CrackingUnitRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected Identifier sheet() {
        return DistillationArrayRenderer.SHEET;
    }

    @Override
    protected CrackingUnitMesh newMesh(ModelFrame frame) {
        return new CrackingUnitMesh(frame);
    }

    @Override
    protected float[][] buildStatic(CrackingUnitMesh mesh, List<ModelMesh.Port> ports) {
        return mesh.buildStatic(ports);
    }

    @Override
    protected float brightness(CrackingUnitBlockEntity unit) {
        return unit.getGlow();
    }
}
