/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.blockentity.multiblock.HeavyWaterTowerBlockEntity;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.steam.HeavyWaterTowerMesh;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed Heavy Water Tower (HeavyWaterTowerMesh): the squat exchange tower of trays on its plinth.
public class HeavyWaterTowerRenderer extends VesselRenderer<HeavyWaterTowerBlockEntity> {
    public HeavyWaterTowerRenderer(BlockEntityRendererProvider.Context context) {}

    // The controller's place on the front wall, which is all the model is laid out from besides its size and ports.
    private record Layout(int panelX, int panelY) {}

    @Override
    protected Object layout(Level level, HeavyWaterTowerBlockEntity vessel, VesselStructure.Box box, ModelFrame frame) {
        int[] controller = controllerBlock(vessel, box, frame);
        return new Layout(controller[0], controller[1]);
    }

    @Override
    protected float[][] build(ModelFrame frame, Object layout, List<ModelMesh.Port> ports) {
        Layout l = (Layout) layout;
        return new HeavyWaterTowerMesh(frame, l.panelX(), l.panelY()).buildStatic(ports);
    }
}
