/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.redstone.ThrottleLeverBlock;
import net.zagdrath.arcforge.blockentity.redstone.ThrottleLeverBlockEntity;

// Draws the Throttle Lever's arm: the standalone model
// block/throttle_lever/arm, pivoted about the quadrant axle at (8, 3, 8) px, then turned exactly like the housing's
// blockstate variant (x then y, as BlockModelRotation does).
public class ThrottleLeverRenderer implements BlockEntityRenderer<ThrottleLeverBlockEntity, ThrottleLeverRenderer.State> {
    public static final StandaloneModelKey<QuadCollection> ARM = new StandaloneModelKey<>(() -> Arcforge.MODID + ":throttle_lever_arm");
    public static final Identifier ARM_MODEL = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/throttle_lever/arm");
    private static final float PIVOT_X = 8 / 16.0F, PIVOT_Y = 3 / 16.0F, PIVOT_Z = 8 / 16.0F;

    public static class State extends BlockEntityRenderState {
        public float angle;
        public int xRot, yRot;
    }

    public ThrottleLeverRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ThrottleLeverBlockEntity lever, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(lever, state, partialTicks, cameraPosition, breakProgress);
        state.angle = lever.renderAngle(partialTicks);
        BlockState block = lever.getBlockState();
        state.xRot = xRot(block);
        state.yRot = yRot(block);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        QuadCollection arm = Minecraft.getInstance().getModelManager().getStandaloneModel(ARM);
        if (arm == null) {
            return;
        }
        List<BakedQuad> quads = arm.getAll();
        if (quads.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        // The housing's variant rotation (BlockModelRotation: rotateYXZ(-y, -x, 0) about the block centre).
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.rotate(Axis.YP.rotationDegrees(-state.yRot));
        poseStack.rotate(Axis.XP.rotationDegrees(-state.xRot));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        // The arm pivots on the axle; positive angles lean it toward +z (signal 15's end of the model).
        poseStack.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);
        poseStack.rotate(Axis.XP.rotationDegrees(state.angle));
        poseStack.translate(-PIVOT_X, -PIVOT_Y, -PIVOT_Z);
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(quads.getFirst().materialInfo().sprite().atlasLocation()),
                (pose, buffer) -> TiledBoxes.quads(pose, buffer, quads, -1, state.lightCoords));
        poseStack.popPose();
    }

    // Must match assets/arcforge/blockstates/throttle_lever.json.
    static int xRot(BlockState state) {
        return switch (state.getValue(ThrottleLeverBlock.FACE)) {
            case FLOOR -> 0;
            case WALL -> 90;
            case CEILING -> 180;
        };
    }

    static int yRot(BlockState state) {
        int base = switch (state.getValue(ThrottleLeverBlock.FACING)) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        return state.getValue(ThrottleLeverBlock.FACE) == net.minecraft.world.level.block.state.properties.AttachFace.FLOOR
                ? (base + 180) % 360 : base;
    }
}
