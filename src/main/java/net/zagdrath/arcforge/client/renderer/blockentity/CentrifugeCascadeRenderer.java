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
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
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
import net.zagdrath.arcforge.client.renderer.ChunkMeshes;
import net.zagdrath.arcforge.client.renderer.LargeModels;
import net.zagdrath.arcforge.multiblock.CentrifugeCascadeLayout;
import net.zagdrath.arcforge.multiblock.CentrifugeCascadeStructure;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.steam.CentrifugeCascadeMesh;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed Gas Centrifuge Cascade from CentrifugeCascadeLayout, built by CentrifugeCascadeMesh on the refinery
// tower's sheet (its blocks draw nothing but the floor's port plates). The hall is laid out from the centrifuges the
// client sees in it; it's built once, and again when its centrifuges or ports change (checked every second), its still
// parts baked into the chunk mesh (ChunkMeshes). While it runs the bands' marks spin (see
// CentrifugeCascadeBlockEntity.advance), full-bright, and the panel's screen is lit: drawn here out to
// stillPartsOnlyFrom, the marks standing still past simpleMovingPartsFrom (see LargeModels).
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
        float[] solid = EMPTY, glass = EMPTY, glow = EMPTY, marksAtRest = EMPTY;
        // The box round what this renderer draws: the screen and the marks.
        @Nullable AABB drawn;
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
            marksAtRest = mesh.buildMarks(0.0F);
            drawn = glow.length + marksAtRest.length == 0 ? null : MeshRendering.bounds(box.min(), glow, marksAtRest).inflate(0.5);
        }
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed, running;
        public int offsetX, offsetY, offsetZ;
        public float[] glow = EMPTY, marks = EMPTY;
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    public CentrifugeCascadeRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean bakes() {
        return true;
    }

    @Override
    public boolean bakesStill(CentrifugeCascadeBlockEntity cascade) {
        return ChunkMeshes.isBaked(cascade, MeshRendering.SOLID, MeshRendering.GLASS);
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
            ChunkMeshes.remove(cascade);
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
        TextureAtlasSprite sheet = SteamBoilerArrayRenderer.sprite(DistillationArrayRenderer.SHEET);
        // The light just above the hall's front middle.
        int light = LightCoordsUtil.getLightCoords(level, cascade.getBlockPos().above(CentrifugeCascadeStructure.HEIGHT));
        Built bakedModel = model;
        java.util.function.BooleanSupplier stands = () -> bakedModel.box.equals(cascade.getBox()) && built.get(cascade) == bakedModel;
        MeshRendering.bake(cascade, MeshRendering.SOLID, min, ChunkSectionLayer.SOLID, model.solid, sheet, light, stands);
        MeshRendering.bake(cascade, MeshRendering.GLASS, min, ChunkSectionLayer.CUTOUT, model.glass, sheet, light, stands);
        state.sheet = sheet;
        state.light = light;
        state.running = cascade.isRunning();
        LargeModels.Detail detail = LargeModels.detail(getRenderBoundingBox(cascade), cameraPosition);
        state.glow = detail != LargeModels.Detail.STILL ? model.glow : EMPTY;
        float turned = state.running ? cascade.advance(time + partialTicks) : 0.0F;
        state.marks = !state.running || detail == LargeModels.Detail.STILL ? EMPTY
                : detail == LargeModels.Detail.FULL ? model.mesh.buildMarks(turned) : model.marksAtRest;
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
        float[] glow = state.glow, marks = state.marks;
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

    // Only the screen and the marks are drawn here; the rest is in the chunk mesh.
    @Override
    public AABB getRenderBoundingBox(CentrifugeCascadeBlockEntity cascade) {
        Built model = built.get(cascade);
        if (model != null && model.box.equals(cascade.getBox())) {
            AABB drawn = model.drawn;
            return drawn != null ? drawn : new AABB(model.box.min()).deflate(0.5);
        }
        return cascade.getRenderBox();
    }
}
