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
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.blockentity.multiblock.VesselControllerBlockEntity;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.VesselStructure;
import net.zagdrath.arcforge.steam.ModelMesh;

// Draws a formed walled vessel (the Fission Reactor, the Steam Generator, the Heavy Water Tower) from its VesselMesh on
// the refinery tower's sheet; its walls' blocks draw nothing but their port plates. The model is built once, and again
// when its ports or what the subclass lays it out from change (checked every second). Its lists: solid, cutout, the
// glow (full-bright, always) and the running glow (full-bright, only while the controller is lit).
public abstract class VesselRenderer<B extends VesselControllerBlockEntity> implements StructureRenderer<B, VesselRenderer.State> {
    private static final int SCAN_INTERVAL = 20;
    private static final float[] EMPTY = new float[0];

    private final Map<B, Built> built = new WeakHashMap<>();

    private static final class Built {
        final VesselStructure.Box box;
        final ModelFrame frame;
        Object key;
        List<ModelMesh.Port> ports;
        float[][] parts = { EMPTY, EMPTY, EMPTY, EMPTY };
        long scanned;

        Built(VesselStructure.Box box, ModelFrame frame, Object key, List<ModelMesh.Port> ports) {
            this.box = box;
            this.frame = frame;
            this.key = key;
            this.ports = ports;
        }
    }

    public static class State extends BlockEntityRenderState {
        public boolean formed, running;
        public int offsetX, offsetY, offsetZ;
        public float[] solid = EMPTY, glass = EMPTY, glow = EMPTY, runningGlow = EMPTY;
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    // The model's frame: x across the front, u back from it, over the whole box.
    public static ModelFrame frame(VesselStructure.Box box) {
        Direction along = box.front().getOpposite();
        int width = box.size(along.getClockWise().getAxis()), length = box.size(along.getAxis());
        return ModelFrame.toward(along, width, box.size(Direction.Axis.Y), length);
    }

    // What the model is laid out from besides its box and ports (compared with equals to tell when to rebuild).
    protected abstract Object layout(Level level, B vessel, VesselStructure.Box box, ModelFrame frame);

    // The model's lists for this layout: {solid, cutout, glow} and, optionally, the glow drawn only while running.
    protected abstract float[][] build(ModelFrame frame, Object layout, List<ModelMesh.Port> ports);

    // The controller's block {x, y, u} in the frame.
    protected static int[] controllerBlock(VesselControllerBlockEntity vessel, VesselStructure.Box box, ModelFrame frame) {
        BlockPos min = box.min(), pos = vessel.getBlockPos();
        return frame.frameBlock(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
    }

    @Override
    public void extractRenderState(B vessel, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        StructureRenderer.super.extractRenderState(vessel, state, partialTicks, cameraPosition, breakProgress);
        VesselStructure.Box box = vessel.getBox();
        Level level = vessel.getLevel();
        state.formed = box != null && level != null && vessel.isFormed();
        if (box == null || level == null || !state.formed) {
            built.remove(vessel);
            return;
        }
        BlockPos min = box.min();
        state.offsetX = min.getX() - vessel.getBlockPos().getX();
        state.offsetY = min.getY() - vessel.getBlockPos().getY();
        state.offsetZ = min.getZ() - vessel.getBlockPos().getZ();
        long time = level.getGameTime();
        Built model = built.get(vessel);
        if (model == null || !model.box.equals(box)) {
            ModelFrame frame = frame(box);
            model = new Built(box, frame, layout(level, vessel, box, frame), ports(level, box, frame));
            model.parts = build(frame, model.key, model.ports);
            model.scanned = time;
            built.put(vessel, model);
        } else if (Math.abs(time - model.scanned) >= SCAN_INTERVAL) {
            model.scanned = time;
            Object key = layout(level, vessel, box, model.frame);
            List<ModelMesh.Port> ports = ports(level, box, model.frame);
            if (!Objects.equals(key, model.key) || !ports.equals(model.ports)) {
                model.key = key;
                model.ports = ports;
                model.parts = build(model.frame, key, ports);
            }
        }
        float[][] parts = model.parts;
        state.solid = parts[0];
        state.glass = parts[1];
        state.glow = parts[2];
        state.running = vessel.isLit();
        state.runningGlow = state.running && parts.length > 3 ? parts[3] : EMPTY;
        state.sheet = SteamBoilerArrayRenderer.sprite(DistillationArrayRenderer.SHEET);
        // The light just above the box's top middle.
        state.light = LightCoordsUtil.getLightCoords(level, box.centre().atY(box.max().getY() + 1));
    }

    // The ports on the walls' outer faces, in the model's frame.
    private static List<ModelMesh.Port> ports(Level level, VesselStructure.Box box, ModelFrame frame) {
        List<BlockPos> walls = new ArrayList<>();
        for (BlockPos pos : box.positions()) {
            if (box.isWall(pos)) {
                walls.add(pos.immutable());
            }
        }
        return MeshRendering.ports(level, walls, box::contains, box.min(), frame);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sheet = state.sheet;
        if (!state.formed || sheet == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
        float[] solid = state.solid, glass = state.glass, glow = state.glow, lit = state.runningGlow;
        int light = state.light;
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), (pose, buffer) -> MeshRendering.emit(pose, buffer, solid, sheet, light));
        if (glass.length > 0) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(sheet.atlasLocation()), (pose, buffer) -> MeshRendering.emit(pose, buffer, glass, sheet, light));
        }
        if (glow.length > 0 || lit.length > 0) {
            collector.submitCustomGeometry(poseStack, RenderTypes.eyes(sheet.atlasLocation()), (pose, buffer) -> {
                emitLit(pose, buffer, glow, sheet);
                emitLit(pose, buffer, lit, sheet);
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
    public AABB getRenderBoundingBox(B vessel) {
        return vessel.getRenderBox();
    }
}
