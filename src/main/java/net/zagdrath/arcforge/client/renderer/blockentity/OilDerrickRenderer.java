/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

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
import net.zagdrath.arcforge.block.multiblock.OilfieldControllerBlock;
import net.zagdrath.arcforge.blockentity.multiblock.OilDerrickBlockEntity;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.steam.DrillStringMesh;

// Draws a formed Oil Derrick's drill string over its bore (the rig's own blocks draw the floor and frame): while it
// drills, the kelly turns (18° a tick) and the travelling block runs down with it, a stroke every two seconds; idle, it
// hangs at the top of its run.
public class OilDerrickRenderer implements StructureRenderer<OilDerrickBlockEntity, OilDerrickRenderer.State> {
    private static final float SPIN = 18.0F, STROKE = 40.0F;
    private final DrillStringMesh mesh = new DrillStringMesh();

    public static class State extends BlockEntityRenderState {
        public boolean formed;
        public int offsetX, offsetY, offsetZ;
        public float[] quads = new float[0];
        public @Nullable TextureAtlasSprite sheet;
        public int light;
    }

    public OilDerrickRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(OilDerrickBlockEntity derrick, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        StructureRenderer.super.extractRenderState(derrick, state, partialTicks, cameraPosition, breakProgress);
        OilfieldStructures.Rig rig = derrick.getRig();
        Level level = derrick.getLevel();
        state.formed = rig != null && level != null;
        if (rig == null || level == null) {
            return;
        }
        BlockPos min = rig.min();
        state.offsetX = min.getX() - derrick.getBlockPos().getX();
        state.offsetY = min.getY() - derrick.getBlockPos().getY();
        state.offsetZ = min.getZ() - derrick.getBlockPos().getZ();
        boolean lit = derrick.getBlockState().getValue(OilfieldControllerBlock.LIT);
        float now = level.getGameTime() + partialTicks;
        state.quads = mesh.build(lit ? now % STROKE / STROKE : 0.0F, lit ? now * SPIN % 360.0F : 0.0F);
        state.sheet = SteamBoilerArrayRenderer.sprite(PumpjackRenderer.SHEET);
        state.light = LightCoordsUtil.getLightCoords(level, rig.bore().above());
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sheet = state.sheet;
        if (!state.formed || sheet == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(state.offsetX, state.offsetY, state.offsetZ);
        float[] data = state.quads;
        int light = state.light;
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(sheet.atlasLocation()), (pose, buffer) -> {
            for (int i = 0; i + 7 < data.length; i += 8) {
                buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                        .setColor(-1)
                        .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
            }
        });
        poseStack.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(OilDerrickBlockEntity derrick) {
        return derrick.getRenderBox();
    }
}
