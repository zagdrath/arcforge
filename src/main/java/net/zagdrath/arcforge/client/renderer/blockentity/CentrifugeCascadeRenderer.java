/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.blockentity.multiblock.CentrifugeCascadeBlockEntity;
import net.zagdrath.arcforge.multiblock.CentrifugeCascadeLayout;
import net.zagdrath.arcforge.multiblock.CentrifugeCascadeStructure;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.steam.CentrifugeCascadeMesh;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed Gas Centrifuge Cascade from CentrifugeCascadeLayout, built by CentrifugeCascadeMesh on the refinery
// tower's sheet (its blocks draw nothing but the floor's port plates). The hall is laid out from the centrifuges the
// client sees in it; it's built once, and again when its centrifuges or ports change (checked every second). While it
// runs the bands' marks spin (see CentrifugeCascadeBlockEntity.advance), full-bright, and the panel's screen is lit.
public class CentrifugeCascadeRenderer implements StructureRenderer<CentrifugeCascadeBlockEntity, CentrifugeCascadeRenderer.State> {
    private static final int SCAN_INTERVAL = 20;
    private static final float[] EMPTY = new float[0];

    private final Map<CentrifugeCascadeBlockEntity, Built> built = new WeakHashMap<>();

    private static final class Built {
        final CentrifugeCascadeStructure.Box box;
        final ModelFrame frame;
        CentrifugeCascadeLayout layout;
        CentrifugeCascadeMesh mesh;
        List<ModelMesh.Port> ports;
        float[] solid = EMPTY, glass = EMPTY, glow = EMPTY;
        long scanned;

        Built(CentrifugeCascadeStructure.Box box, ModelFrame frame, CentrifugeCascadeLayout layout, List<ModelMesh.Port> ports) {
            this.box = box;
            this.frame = frame;
            this.layout = layout;
            this.mesh = new CentrifugeCascadeMesh(frame, layout);
            this.ports = ports;
        }

        void rebuild() {
            float[][] parts = mesh.buildStatic(ports);
            solid = parts[0];
            glass = parts[1];
            glow = parts[2];
        }
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed, running;
        public int offsetX, offsetY, offsetZ;
        public float[] solid = EMPTY, glass = EMPTY, glow = EMPTY, marks = EMPTY;
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    public CentrifugeCascadeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    // The model's frame: x across the front, u back from it, over the hall's three layers.
    public static ModelFrame frame(CentrifugeCascadeStructure.Box box) {
        return ModelFrame.toward(box.front().getOpposite(), box.width(), CentrifugeCascadeStructure.HEIGHT, box.depth());
    }

    // The layout of the hall as the level shows it: its centrifuges and the controller's place on the front edge.
    public static CentrifugeCascadeLayout layout(Level level, CentrifugeCascadeStructure.Box box, BlockPos controller, ModelFrame frame) {
        BlockPos min = box.min();
        List<CentrifugeCascadeLayout.Cell> cells = new ArrayList<>();
        for (BlockPos pos : CentrifugeCascadeStructure.centrifuges(level, box)) {
            int[] block = frame.frameBlock(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
            cells.add(new CentrifugeCascadeLayout.Cell(block[0], block[2]));
        }
        int[] panel = frame.frameBlock(controller.getX() - min.getX(), 0, controller.getZ() - min.getZ());
        return new CentrifugeCascadeLayout(box.width(), box.depth(), panel[0], cells);
    }

    @Override
    public void extractRenderState(CentrifugeCascadeBlockEntity cascade, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        StructureRenderer.super.extractRenderState(cascade, state, partialTicks, cameraPosition, breakProgress);
        CentrifugeCascadeStructure.Box box = cascade.getBox();
        Level level = cascade.getLevel();
        state.formed = box != null && level != null;
        if (box == null || level == null) {
            built.remove(cascade);
            return;
        }
        BlockPos min = box.min();
        state.offsetX = min.getX() - cascade.getBlockPos().getX();
        state.offsetY = min.getY() - cascade.getBlockPos().getY();
        state.offsetZ = min.getZ() - cascade.getBlockPos().getZ();
        long time = level.getGameTime();
        Built model = built.get(cascade);
        if (model == null || !model.box.equals(box)) {
            ModelFrame frame = frame(box);
            model = new Built(box, frame, layout(level, box, cascade.getBlockPos(), frame), ports(level, box, frame));
            model.rebuild();
            model.scanned = time;
            built.put(cascade, model);
        } else if (Math.abs(time - model.scanned) >= SCAN_INTERVAL) {
            model.scanned = time;
            CentrifugeCascadeLayout layout = layout(level, box, cascade.getBlockPos(), model.frame);
            List<ModelMesh.Port> ports = ports(level, box, model.frame);
            if (!layout.cells.equals(model.layout.cells) || !ports.equals(model.ports)) {
                model.layout = layout;
                model.mesh = new CentrifugeCascadeMesh(model.frame, layout);
                model.ports = ports;
                model.rebuild();
            }
        }
        state.solid = model.solid;
        state.glass = model.glass;
        state.glow = model.glow;
        state.running = cascade.isRunning();
        state.marks = state.running ? model.mesh.buildMarks(cascade.advance(time + partialTicks)) : EMPTY;
        state.sheet = SteamBoilerArrayRenderer.sprite(DistillationArrayRenderer.SHEET);
        // The light just above the hall's front middle.
        state.light = LightCoordsUtil.getLightCoords(level, cascade.getBlockPos().above(CentrifugeCascadeStructure.HEIGHT));
    }

    // The ports on the floor's outer faces, in the model's frame.
    private static List<ModelMesh.Port> ports(Level level, CentrifugeCascadeStructure.Box box, ModelFrame frame) {
        List<BlockPos> floor = new ArrayList<>();
        for (BlockPos pos : box.floor()) {
            floor.add(pos.immutable());
        }
        return MeshRendering.ports(level, floor, box::contains, box.min(), frame);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sheet = state.sheet;
        if (!state.formed || sheet == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
        float[] solid = state.solid, glass = state.glass, glow = state.glow, marks = state.marks;
        int light = state.light;
        MeshRendering.submitStill(collector, poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), solid, sheet, light);
        MeshRendering.submitStill(collector, poseStack, RenderTypes.entityCutout(sheet.atlasLocation()), glass, sheet, light);
        // The panel's screen always, the bands' marks while it runs: full-bright.
        if (glow.length > 0 || marks.length > 0) {
            collector.submitCustomGeometry(poseStack, RenderTypes.eyes(sheet.atlasLocation()), (pose, buffer) -> {
                emitLit(pose, buffer, glow, sheet);
                emitLit(pose, buffer, marks, sheet);
            });
        }
        poseStack.popPose();
    }

    private static void emitLit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet) {
        for (int i = 0; i + 7 < data.length; i += 8) {
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(0xFFFFFFFF)
                    .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightCoordsUtil.FULL_BRIGHT)
                    .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
        }
    }

    @Override
    public AABB getRenderBoundingBox(CentrifugeCascadeBlockEntity cascade) {
        return cascade.getRenderBox();
    }
}
