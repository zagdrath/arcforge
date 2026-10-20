/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import net.zagdrath.arcforge.blockentity.multiblock.PurexBlockEntity;
import net.zagdrath.arcforge.client.renderer.ChunkMeshes;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.steam.PurexMesh;

// Draws a formed PUREX Plant's hot cell (PurexMesh, on the refinery tower's sheet): its insides full-bright (the cell's
// own lighting, seen through its windows), its lamps in the glow list, and the manipulators' master arms outside the
// windows in the world's light. Rebuilt when its windows change (checked every second).
public class PurexRenderer implements StructureRenderer<PurexBlockEntity, PurexRenderer.State> {
    private static final float[] EMPTY = new float[0];
    private static final int SCAN_INTERVAL = 20;
    // The chunk mesh slots of its two still lists (see ChunkMeshes): outside in the world's light, inside lit by the lamps.
    private static final int OUTSIDE = 0, INSIDE = 1;

    private final Map<PurexBlockEntity, Built> built = new WeakHashMap<>();

    private static final class Built {
        final VesselStructure.Box box;
        List<PurexMesh.Window> windows;
        float[][] parts;
        long scanned;

        Built(VesselStructure.Box box, List<PurexMesh.Window> windows, float[][] parts) {
            this.box = box;
            this.windows = windows;
            this.parts = parts;
        }
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public int offsetX, offsetY, offsetZ;
        public float[] glow = EMPTY;
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    public PurexRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public boolean bakes() {
        return true;
    }

    @Override
    public boolean bakesStill(PurexBlockEntity plant) {
        return ChunkMeshes.isBaked(plant, OUTSIDE, INSIDE);
    }

    // The windows in the side walls, as frame blocks with their wall's outward normal.
    private static List<PurexMesh.Window> windows(Level level, VesselStructure.Box box, ModelFrame frame) {
        List<PurexMesh.Window> windows = new ArrayList<>();
        BlockPos min = box.min();
        int width = frame.width(), length = frame.length();
        for (BlockPos pos : box.positions()) {
            if (!box.isWall(pos) || box.boundaries(pos) != 1 || pos.getY() == min.getY() || pos.getY() == box.max().getY()) {
                continue;
            }
            if (!level.getBlockState(pos).is(ModBlocks.HOT_CELL_WINDOW.get())) {
                continue;
            }
            int[] b = frame.frameBlock(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
            int nx = b[0] == 0 ? -1 : b[0] == width - 1 ? 1 : 0;
            int nu = nx != 0 ? 0 : b[2] == 0 ? -1 : b[2] == length - 1 ? 1 : 0;
            if (nx != 0 || nu != 0) {
                windows.add(new PurexMesh.Window(b[0], b[1], b[2], nx, nu));
            }
        }
        return windows;
    }

    @Override
    public void extractRenderState(PurexBlockEntity plant, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        StructureRenderer.super.extractRenderState(plant, state, partialTicks, cameraPosition, breakProgress);
        VesselStructure.Box box = plant.getBox();
        Level level = plant.getLevel();
        state.formed = box != null && level != null && plant.isFormed();
        if (box == null || level == null || !state.formed) {
            built.remove(plant);
            ChunkMeshes.remove(plant);
            return;
        }
        BlockPos min = box.min();
        state.offsetX = min.getX() - plant.getBlockPos().getX();
        state.offsetY = min.getY() - plant.getBlockPos().getY();
        state.offsetZ = min.getZ() - plant.getBlockPos().getZ();
        long time = level.getGameTime();
        ModelFrame frame = VesselRenderer.frame(box);
        Built model = built.get(plant);
        if (model == null || !model.box.equals(box)) {
            List<PurexMesh.Window> windows = windows(level, box, frame);
            model = new Built(box, windows, new PurexMesh(frame, windows).build());
            model.scanned = time;
            built.put(plant, model);
        } else if (Math.abs(time - model.scanned) >= SCAN_INTERVAL) {
            model.scanned = time;
            List<PurexMesh.Window> windows = windows(level, box, frame);
            if (!Objects.equals(windows, model.windows)) {
                model.windows = windows;
                model.parts = new PurexMesh(frame, windows).build();
            }
        }
        TextureAtlasSprite sheet = SteamBoilerArrayRenderer.sprite(DistillationArrayRenderer.SHEET);
        int light = LightCoordsUtil.getLightCoords(level, plant.getBlockPos().relative(plant.getFacing()));
        // The still lists into the chunk mesh, each face shaded as a block face turned its way: the inside full-bright.
        VesselStructure.Box builtBox = model.box;
        java.util.function.BooleanSupplier stands = () -> plant.isFormed() && builtBox.equals(plant.getBox());
        MeshRendering.bake(plant, INSIDE, min, ChunkSectionLayer.SOLID, model.parts[0], sheet, LightCoordsUtil.FULL_BRIGHT, stands);
        MeshRendering.bake(plant, OUTSIDE, min, ChunkSectionLayer.SOLID, model.parts[1], sheet, light, stands);
        state.glow = model.parts[2];
        state.sheet = sheet;
        state.light = light;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sheet = state.sheet;
        if (!state.formed || sheet == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
        float[] glow = state.glow;
        if (glow.length > 0) {
            collector.submitCustomGeometry(poseStack, RenderTypes.eyes(sheet.atlasLocation()), (pose, buffer) -> emit(pose, buffer, glow, sheet,
                    LightCoordsUtil.FULL_BRIGHT));
        }
        poseStack.popPose();
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet, int light) {
        for (int i = 0; i + 7 < data.length; i += 8) {
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(0xFFFFFFFF)
                    .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
        }
    }

    // Only the lamps' glow is drawn here; the rest is in the chunk mesh.
    @Override
    public AABB getRenderBoundingBox(PurexBlockEntity plant) {
        Built model = built.get(plant);
        if (model != null && model.box.equals(plant.getBox())) {
            float[] glow = model.parts[2];
            return glow.length > 0 ? MeshRendering.bounds(model.box.min(), glow) : new AABB(model.box.min()).deflate(0.5);
        }
        return plant.getRenderBox();
    }
}
