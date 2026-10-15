/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.blockentity.multiblock.SteamCrackerBlockEntity;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.steam.ModelMesh;
import net.zagdrath.arcforge.steam.SteamCrackerMesh;

// Draws a formed Steam Cracker from SteamCrackerLayout, built by SteamCrackerMesh on the refinery tower's sheet, the
// fire in its peep holes as bright as the furnace is hot (see SteamCrackerBlockEntity.getGlow).
public class SteamCrackerRenderer extends PlantRenderer<SteamCrackerBlockEntity, SteamCrackerMesh> {
    public SteamCrackerRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected Identifier sheet() {
        return DistillationArrayRenderer.SHEET;
    }

    @Override
    protected SteamCrackerMesh newMesh(ModelFrame frame) {
        return new SteamCrackerMesh(frame);
    }

    @Override
    protected float[][] buildStatic(SteamCrackerMesh mesh, List<ModelMesh.Port> ports) {
        return mesh.buildStatic(ports);
    }

    @Override
    protected float brightness(SteamCrackerBlockEntity unit) {
        return unit.getGlow();
    }
}
