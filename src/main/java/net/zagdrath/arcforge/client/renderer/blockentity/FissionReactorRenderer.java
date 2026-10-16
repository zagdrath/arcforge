/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.multiblock.FissionReactorBlockEntity;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.steam.FissionReactorMesh;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed Fission Reactor (FissionReactorMesh): the pressure vessel in its casing frame, windows in the shell
// facing the Reactor Glass in the side walls (glass that touches makes one window), and through them the core as it's
// built inside, its fuel glowing Cherenkov blue (full-bright) while the reactor is critical.
public class FissionReactorRenderer extends VesselRenderer<FissionReactorBlockEntity> {
    public FissionReactorRenderer(BlockEntityRendererProvider.Context context) {}

    private record Layout(List<FissionReactorMesh.Viewport> viewports, FissionReactorMesh.Core core, int panelX, int panelY) {}

    @Override
    protected Object layout(Level level, FissionReactorBlockEntity reactor, VesselStructure.Box box, ModelFrame frame) {
        BlockPos min = box.min();
        List<FissionReactorMesh.Viewport> viewports = new ArrayList<>();
        int w = Math.max(0, frame.width() - 2), h = Math.max(0, frame.height() - 2), l = Math.max(0, frame.length() - 2);
        char[] cells = new char[w * h * l];
        Arrays.fill(cells, FissionReactorMesh.Core.COOLANT);
        for (BlockPos pos : box.positions()) {
            int[] block = frame.frameBlock(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
            BlockState state = level.getBlockState(pos);
            if (box.isWall(pos)) {
                // The side walls only: glass in the roof or floor looks onto the heads, which carry no window.
                if (pos.getY() != min.getY() && pos.getY() != box.max().getY() && state.is(ModBlocks.REACTOR_GLASS.get())) {
                    viewports.add(new FissionReactorMesh.Viewport(block[0], block[1], block[2]));
                }
                continue;
            }
            int x = block[0] - 1, y = block[1] - 1, u = block[2] - 1;
            if (x < 0 || y < 0 || u < 0 || x >= w || y >= h || u >= l) {
                continue;
            }
            cells[FissionReactorMesh.Core.index(w, l, x, y, u)] = state.is(ModBlocks.FUEL_CHANNEL.get()) ? FissionReactorMesh.Core.FUEL
                    : state.is(ModBlocks.CONTROL_ROD_CHANNEL.get()) ? FissionReactorMesh.Core.CONTROL
                    : state.is(ModBlocks.GRAPHITE_MODERATOR.get()) ? FissionReactorMesh.Core.GRAPHITE
                    : state.is(ModBlocks.STEEL_REFLECTOR.get()) ? FissionReactorMesh.Core.REFLECTOR
                    : FissionReactorMesh.Core.COOLANT;
        }
        int[] controller = controllerBlock(reactor, box, frame);
        return new Layout(viewports, new FissionReactorMesh.Core(w, h, l, new String(cells)), controller[0], controller[1]);
    }

    @Override
    protected float[][] build(ModelFrame frame, Object layout, List<ModelMesh.Port> ports) {
        Layout l = (Layout) layout;
        return new FissionReactorMesh(frame, l.viewports(), l.core(), l.panelX(), l.panelY()).buildStatic(ports);
    }
}
