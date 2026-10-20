/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.machine.FlareStackBlock;
import net.zagdrath.arcforge.blockentity.machine.FlareStackBlockEntity;
import net.zagdrath.arcforge.client.renderer.ChunkMeshes;
import net.zagdrath.arcforge.steam.FlareStackMesh;

// Draws the Flare Stack's stack over its base block (FlareStackMesh, built once, on the refinery tower's sheet; baked
// into the chunk mesh, see ChunkMeshes) and its flame at the tip while it burns: two crossed sheets of the animated
// block/flare_stack/flame texture (16x32 frames on a clear background), full-bright, from a block tall at the least burn
// to three and a half at full burn.
public class FlareStackRenderer implements BlockEntityRenderer<FlareStackBlockEntity, FlareStackRenderer.State> {
    public static final Identifier FLAME = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/flare_stack/flame");
    private static float @Nullable [] mesh;

    public static class State extends BlockEntityRenderState {
        public float[] stack = new float[0];
        public float flame;
        public @Nullable TextureAtlasSprite sheet, flameSprite;
        public int light;
    }

    public FlareStackRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    private static float[] mesh() {
        if (mesh == null) {
            mesh = new FlareStackMesh().build();
        }
        return mesh;
    }

    @Override
    public void extractRenderState(FlareStackBlockEntity flare, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(flare, state, partialTicks, cameraPosition, breakProgress);
        state.stack = mesh();
        state.flame = flare.getFlame();
        state.sheet = SteamBoilerArrayRenderer.sprite(DistillationArrayRenderer.SHEET);
        state.flameSprite = SteamBoilerArrayRenderer.sprite(FLAME);
        Level level = flare.getLevel();
        state.light = level != null ? LightCoordsUtil.getLightCoords(level, flare.getBlockPos().above(4)) : LightCoordsUtil.FULL_BRIGHT;
        // The stack into the chunk mesh, lit as the entity shader lit it (it used its real normals).
        float[] stack = state.stack;
        TextureAtlasSprite sheet = state.sheet;
        int light = state.light;
        ChunkMeshes.bakeEntityLit(flare, MeshRendering.SOLID, flare.getBlockPos(), ChunkSectionLayer.SOLID, new MeshRendering.Baked(stack, sheet), light,
                () -> flare.getBlockState().getBlock() instanceof FlareStackBlock, (pose, buffer) -> {
                    for (int i = 0; i + 7 < stack.length; i += 8) {
                        buffer.addVertex(pose, stack[i], stack[i + 1], stack[i + 2])
                                .setColor(-1)
                                .setUv(sheet.getU(stack[i + 3]), sheet.getV(stack[i + 4]))
                                .setOverlay(OverlayTexture.NO_OVERLAY)
                                .setLight(light)
                                .setNormal(pose, stack[i + 5], stack[i + 6], stack[i + 7]);
                    }
                });
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sheet = state.sheet, flame = state.flameSprite;
        if (sheet == null || flame == null) {
            return;
        }
        // The stack is in the chunk mesh. The flame, only while it burns: 16 px tall at the least, up to 56 px at full burn, a quarter as wide each side
        // (the texture's 1:2).
        float size = state.flame;
        if (size <= 0.0F) {
            return;
        }
        float height = (16.0F + 40.0F * size) / 16.0F, half = height / 4.0F;
        float base = (FlareStackMesh.TIP_TOP - 2.0F) / 16.0F, cx = FlareStackMesh.CX / 16.0F, cu = FlareStackMesh.CU / 16.0F;
        collector.submitCustomGeometry(poseStack, RenderTypes.eyes(flame.atlasLocation()), (pose, buffer) -> {
            sheet(pose, buffer, flame, cx - half, cu, cx + half, cu, base, height);
            sheet(pose, buffer, flame, cx, cu - half, cx, cu + half, base, height);
        });
    }

    // One flame sheet from (x0, z0) to (x1, z1), both sides.
    private static void sheet(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, float x0, float z0, float x1, float z1, float y,
            float height) {
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        float[][] corners = { { x0, y, z0, u0, v1 }, { x1, y, z1, u1, v1 }, { x1, y + height, z1, u1, v0 }, { x0, y + height, z0, u0, v0 } };
        for (int side = 0; side < 2; side++) {
            for (int k = 0; k < 4; k++) {
                float[] c = corners[side == 0 ? k : 3 - k];
                buffer.addVertex(pose, c[0], c[1], c[2])
                        .setColor(-1)
                        .setUv(c[3], c[4])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightCoordsUtil.FULL_BRIGHT)
                        .setNormal(pose, 0, 1, 0);
            }
        }
    }

    @Override
    public AABB getRenderBoundingBox(FlareStackBlockEntity flare) {
        return flare.getRenderBox();
    }
}
