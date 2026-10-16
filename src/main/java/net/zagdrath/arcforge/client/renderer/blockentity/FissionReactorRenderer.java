/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.zagdrath.arcforge.blockentity.multiblock.FissionReactorBlockEntity;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.steam.FissionReactorMesh;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed Fission Reactor (FissionReactorMesh): the pressure vessel in its casing frame, a viewport in the
// shell facing each piece of Reactor Glass in the side walls, glowing Cherenkov blue (full-bright) while the reactor is
// critical.
public class FissionReactorRenderer extends VesselRenderer<FissionReactorBlockEntity> {
    public FissionReactorRenderer(BlockEntityRendererProvider.Context context) {}

    private record Layout(List<FissionReactorMesh.Viewport> viewports, int panelX, int panelY) {}

    @Override
    protected Object layout(Level level, FissionReactorBlockEntity reactor, VesselStructure.Box box, ModelFrame frame) {
        BlockPos min = box.min();
        List<FissionReactorMesh.Viewport> viewports = new ArrayList<>();
        for (BlockPos pos : box.positions()) {
            // The side walls only: glass in the roof or floor looks onto the heads, which carry no viewport.
            if (!box.isWall(pos) || pos.getY() == min.getY() || pos.getY() == box.max().getY()) {
                continue;
            }
            if (level.getBlockState(pos).is(ModBlocks.REACTOR_GLASS.get())) {
                int[] block = frame.frameBlock(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
                viewports.add(new FissionReactorMesh.Viewport(block[0], block[1], block[2]));
            }
        }
        int[] controller = controllerBlock(reactor, box, frame);
        return new Layout(viewports, controller[0], controller[1]);
    }

    @Override
    protected float[][] build(ModelFrame frame, Object layout, List<ModelMesh.Port> ports) {
        Layout l = (Layout) layout;
        return new FissionReactorMesh(frame, l.viewports(), l.panelX(), l.panelY()).buildStatic(ports);
    }
}
