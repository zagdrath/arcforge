/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.blockentity.multiblock.SteamGeneratorBlockEntity;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.steam.SteamGeneratorMesh;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed Steam Generator (SteamGeneratorMesh): the vertical heat exchanger in its casing frame.
public class SteamGeneratorRenderer extends VesselRenderer<SteamGeneratorBlockEntity> {
    public SteamGeneratorRenderer(BlockEntityRendererProvider.Context context) {}

    // The controller's place on the front wall, which is all the model is laid out from besides its size and ports.
    private record Layout(int panelX, int panelY) {}

    @Override
    protected Object layout(Level level, SteamGeneratorBlockEntity vessel, VesselStructure.Box box, ModelFrame frame) {
        int[] controller = controllerBlock(vessel, box, frame);
        return new Layout(controller[0], controller[1]);
    }

    @Override
    protected float[][] build(ModelFrame frame, Object layout, List<ModelMesh.Port> ports) {
        Layout l = (Layout) layout;
        return new SteamGeneratorMesh(frame, l.panelX(), l.panelY()).buildStatic(ports);
    }
}
