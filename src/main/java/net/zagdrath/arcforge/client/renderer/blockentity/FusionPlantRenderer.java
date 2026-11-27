/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.blockentity.fusion.FusionPlantBlockEntity;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.steam.CryogenicColumnMesh;
import net.zagdrath.arcforge.steam.CryogenicPlantMesh;
import net.zagdrath.arcforge.steam.ModelMesh;
import net.zagdrath.arcforge.steam.TritiumPlantMesh;

// Draws a formed Cryogenic Plant, Cryogenic Distillation Column or Tritium Plant (FusionPlantMesh's models: the cold box
// and its column, compressor and dewar; the insulated column with its reboiler and condenser; the glovebox line with its
// permeator, beds and isotope column). Their sight glasses and glovebox lamps light while the controller is lit, and the
// gloveboxes' glass is tinted.
public class FusionPlantRenderer extends VesselRenderer<FusionPlantBlockEntity> {
    public FusionPlantRenderer(BlockEntityRendererProvider.Context context) {}

    // What the model is laid out from besides its size and ports: which plant, and the controller's place on the front
    // wall.
    private record Layout(FusionPlantBlockEntity.Kind kind, int panelX, int panelY) {}

    @Override
    protected Object layout(Level level, FusionPlantBlockEntity plant, VesselStructure.Box box, ModelFrame frame) {
        int[] controller = controllerBlock(plant, box, frame);
        return new Layout(plant.getKind(), controller[0], controller[1]);
    }

    @Override
    protected float[][] build(ModelFrame frame, Object layout, List<ModelMesh.Port> ports) {
        Layout l = (Layout) layout;
        return switch (l.kind()) {
            case CRYOGENIC_PLANT -> new CryogenicPlantMesh(frame, l.panelX(), l.panelY()).buildStatic(ports);
            case CRYOGENIC_COLUMN -> new CryogenicColumnMesh(frame, l.panelX(), l.panelY()).buildStatic(ports);
            case TRITIUM_PLANT -> new TritiumPlantMesh(frame, l.panelX(), l.panelY()).buildStatic(ports);
        };
    }
}
