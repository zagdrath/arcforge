/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.HashMap;
import java.util.Map;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.block.nuclear.EquipmentHatchBlock;
import net.zagdrath.arcforge.blockentity.nuclear.EquipmentHatchBlockEntity;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.steam.EquipmentHatchMesh;

// Draws an Equipment Hatch from its middle block (EquipmentHatchMesh, on the refinery tower's sheet; built once for each
// way it can face): its frame, and its door swung about the hinge, opening outward through 100° over SWING_TICKS as the
// hatch opens (and back as it shuts), in the light just outside it.
public class EquipmentHatchRenderer implements BlockEntityRenderer<EquipmentHatchBlockEntity, EquipmentHatchRenderer.State> {
    private static final float OPEN_DEGREES = 100.0F;
    private static final Map<Direction, float[][]> MESHES = new HashMap<>();

    public static class State extends BlockEntityRenderState {
        public float[] frame = new float[0], door = new float[0];
        public float offsetX, offsetY, offsetZ;
        public float hingeX, hingeZ;
        public float angle;
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    public EquipmentHatchRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    private static ModelFrame frame(Direction facing) {
        return ModelFrame.toward(facing.getOpposite(), 3, 3, 1);
    }

    private static float[][] mesh(Direction facing) {
        return MESHES.computeIfAbsent(facing, key -> {
            EquipmentHatchMesh mesh = new EquipmentHatchMesh(frame(key));
            return new float[][] { mesh.buildFrame(), mesh.buildDoor() };
        });
    }

    // The angle (radians, about +Y as JOML turns it) that swings the door from shut to open: its middle, from the hinge,
    // turns from pointing across the wall to pointing out of it.
    private static float openAngle(ModelFrame frame) {
        float[] o = frame.worldPoint(0.0F, 0.0F, 0.0F), x = frame.worldPoint(1.0F, 0.0F, 0.0F), u = frame.worldPoint(0.0F, 0.0F, 1.0F);
        double ax = -(x[0] - o[0]), az = -(x[2] - o[2]);
        double bx = -(u[0] - o[0]), bz = -(u[2] - o[2]);
        double from = Math.atan2(az, ax), to = Math.atan2(bz, bx);
        double turn = from - to;
        turn = Math.atan2(Math.sin(turn), Math.cos(turn));
        return (float) (turn * OPEN_DEGREES / 90.0);
    }

    @Override
    public void extractRenderState(EquipmentHatchBlockEntity hatch, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(hatch, state, partialTicks, cameraPosition, breakProgress);
        BlockState block = hatch.getBlockState();
        Level level = hatch.getLevel();
        if (!(block.getBlock() instanceof EquipmentHatchBlock) || level == null) {
            state.sheet = null;
            return;
        }
        Direction facing = block.getValue(EquipmentHatchBlock.FACING);
        ModelFrame frame = frame(facing);
        float[][] mesh = mesh(facing);
        state.frame = mesh[0];
        state.door = mesh[1];
        // The model's corner: the lowest of the hatch's nine blocks.
        BlockPos middle = hatch.getBlockPos();
        BlockPos a = EquipmentHatchBlock.partPos(middle, facing, 0), b = EquipmentHatchBlock.partPos(middle, facing, 8);
        state.offsetX = Math.min(a.getX(), b.getX()) - middle.getX();
        state.offsetY = Math.min(a.getY(), b.getY()) - middle.getY();
        state.offsetZ = Math.min(a.getZ(), b.getZ()) - middle.getZ();
        float[] hinge = frame.worldPoint(EquipmentHatchMesh.HINGE_X, 0.0F, EquipmentHatchMesh.HINGE_U);
        state.hingeX = hinge[0] / 16.0F;
        state.hingeZ = hinge[2] / 16.0F;
        // The swing, the client's own: toward open or shut at a steady pace.
        double now = level.getGameTime() + partialTicks;
        float target = block.getValue(EquipmentHatchBlock.OPEN) ? 1.0F : 0.0F;
        if (hatch.swing < 0.0F) {
            hatch.swing = target;
        } else {
            float step = (float) Math.max(0.0, now - hatch.swungAt) / EquipmentHatchBlockEntity.SWING_TICKS;
            hatch.swing = target > hatch.swing ? Math.min(target, hatch.swing + step) : Math.max(target, hatch.swing - step);
        }
        hatch.swungAt = now;
        float eased = hatch.swing * hatch.swing * (3.0F - 2.0F * hatch.swing);
        state.angle = openAngle(frame) * eased;
        state.sheet = SteamBoilerArrayRenderer.sprite(DistillationArrayRenderer.SHEET);
        state.light = LightCoordsUtil.getLightCoords(level, middle.relative(facing));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sheet = state.sheet;
        if (sheet == null) {
            return;
        }
        int light = state.light;
        float[] frame = state.frame, door = state.door;
        poseStack.pushPose();
        poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
        // The frame is still (kept on the GPU, MeshRendering.submitStill); the door turns, so it's emitted each frame.
        MeshRendering.submitStill(collector, poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), frame, sheet, light);
        poseStack.translate(state.hingeX, 0.0F, state.hingeZ);
        poseStack.rotate(new Quaternionf().rotateY(state.angle));
        poseStack.translate(-state.hingeX, 0.0F, -state.hingeZ);
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), (pose, buffer) -> MeshRendering.emit(pose, buffer, door, sheet, light));
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(EquipmentHatchBlockEntity hatch) {
        return new AABB(hatch.getBlockPos()).inflate(3.0);
    }
}
