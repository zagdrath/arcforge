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
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.fusion.RemoteHandlingArmBlockEntity;

// Draws the Remote Handling Arm's telescoping arm (the standalone models block/fusion/remote_handling_arm_stage_1,
// _stage_2 and _gripper; tools/RemoteHandlingArmModelGen.java): at rest it lies in its cradle inside the block; while it
// works its two stages slide out north (the way it faces, its block model's north) through the mount's opening into the
// Vessel Port and the vessel behind it, the gripper on the end pitching down a little as it reaches. Turned like the
// mount's blockstate variant.
public class RemoteHandlingArmRenderer implements BlockEntityRenderer<RemoteHandlingArmBlockEntity, RemoteHandlingArmRenderer.State> {
    public static final StandaloneModelKey<QuadCollection> STAGE_1 = new StandaloneModelKey<>(() -> Arcforge.MODID + ":remote_handling_arm_stage_1");
    public static final StandaloneModelKey<QuadCollection> STAGE_2 = new StandaloneModelKey<>(() -> Arcforge.MODID + ":remote_handling_arm_stage_2");
    public static final StandaloneModelKey<QuadCollection> GRIPPER = new StandaloneModelKey<>(() -> Arcforge.MODID + ":remote_handling_arm_gripper");
    public static final Identifier STAGE_1_MODEL = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fusion/remote_handling_arm_stage_1");
    public static final Identifier STAGE_2_MODEL = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fusion/remote_handling_arm_stage_2");
    public static final Identifier GRIPPER_MODEL = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fusion/remote_handling_arm_gripper");
    // How far (px) each stage slides out at full reach (stage 2 and the gripper ride on stage 1), and how far the
    // gripper pitches down about its wrist (at its back, the arm's axis).
    private static final float STAGE_1_TRAVEL = 14.0F, STAGE_2_TRAVEL = 22.0F, WRIST_PITCH = 20.0F;
    private static final float AXIS = 8.0F / 16.0F, WRIST_Z = 5.0F / 16.0F;

    public static class State extends BlockEntityRenderState {
        public float reach;
        public int yRot;
    }

    public RemoteHandlingArmRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RemoteHandlingArmBlockEntity arm, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(arm, state, partialTicks, cameraPosition, breakProgress);
        float t = arm.reachBefore + (arm.reach - arm.reachBefore) * partialTicks;
        // Eased, so the stages start and stop gently.
        state.reach = t * t * (3.0F - 2.0F * t);
        state.yRot = yRot(arm.getBlockState().getValue(MachineBlock.FACING));
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        var models = Minecraft.getInstance().getModelManager();
        float reach = state.reach;
        poseStack.pushPose();
        // The mount's variant rotation (about the block centre, as BlockModelRotation does).
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.rotate(Axis.YP.rotationDegrees(-state.yRot));
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        draw(collector, poseStack, models.getStandaloneModel(STAGE_1), 0.0F, -reach * STAGE_1_TRAVEL / 16.0F, state.lightCoords);
        float tip = -reach * STAGE_2_TRAVEL / 16.0F;
        draw(collector, poseStack, models.getStandaloneModel(STAGE_2), 0.0F, tip, state.lightCoords);
        draw(collector, poseStack, models.getStandaloneModel(GRIPPER), reach * WRIST_PITCH, tip, state.lightCoords);
        poseStack.popPose();
    }

    // A part slid z blocks along the arm, pitched down by pitch degrees about the wrist.
    private static void draw(SubmitNodeCollector collector, PoseStack poseStack, @Nullable QuadCollection model, float pitch, float z, int light) {
        if (model == null) {
            return;
        }
        List<BakedQuad> quads = model.getAll();
        if (quads.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0F, 0.0F, z);
        if (pitch != 0.0F) {
            // Nose down: the front (north, -z) end drops.
            poseStack.translate(AXIS, AXIS, WRIST_Z);
            poseStack.rotate(Axis.XP.rotationDegrees(-pitch));
            poseStack.translate(-AXIS, -AXIS, -WRIST_Z);
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(quads.getFirst().materialInfo().sprite().atlasLocation()),
                (pose, buffer) -> TiledBoxes.quads(pose, buffer, quads, -1, light));
        poseStack.popPose();
    }

    // Must match assets/arcforge/blockstates/remote_handling_arm.json.
    static int yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    // The arm reaches up to two blocks out of its own, into the port and the vessel.
    @Override
    public AABB getRenderBoundingBox(RemoteHandlingArmBlockEntity arm) {
        return new AABB(arm.getBlockPos()).inflate(2.0);
    }
}
