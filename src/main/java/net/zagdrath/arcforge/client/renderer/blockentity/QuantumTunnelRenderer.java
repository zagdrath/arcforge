/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.logistics.QuantumTunnelBlockEntity;
import net.zagdrath.arcforge.quantum.QuantumFaces;

// The Quantum Tunnel's moving and changing parts: the 4 px cyan core floating in the middle of the frame, turning slowly
// on two axes at full brightness (dimmer while the tunnel isn't on a frequency), and on each configured face the coloured
// ring laid over its blank port: blue for a face that only takes, orange for one that only gives, both for a face doing
// each (for different resources). Faces set to NONE for everything keep the plain steel collar of the model.
public class QuantumTunnelRenderer implements BlockEntityRenderer<QuantumTunnelBlockEntity, QuantumTunnelRenderer.State> {
    private static final Identifier CORE = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/quantum_tunnel/core");
    private static final Identifier RING_INPUT = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/quantum_tunnel/ring_input");
    private static final Identifier RING_OUTPUT = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/quantum_tunnel/ring_output");
    private static final Identifier RING_MIXED = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/quantum_tunnel/ring_mixed");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float CORE_HALF = 2.0F / 16.0F;
    // The port plate spans 4..12 on its face; the ring sits just outside it.
    private static final float RING_HALF = 4.0F / 16.0F, RING_OUT = 0.5F + 0.002F;

    public static class State extends BlockEntityRenderState {
        public float time;
        public boolean linked;
        public long faces;
        public @Nullable TextureAtlasSprite core, ringInput, ringOutput, ringMixed;
    }

    public QuantumTunnelRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(QuantumTunnelBlockEntity tunnel, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tunnel, state, partialTicks, cameraPosition, breakProgress);
        state.time = tunnel.getLevel() != null ? (tunnel.getLevel().getGameTime() % 72_000L) + partialTicks : 0.0F;
        state.linked = tunnel.isLinked();
        state.faces = tunnel.getFaces();
        state.core = SteamBoilerArrayRenderer.sprite(CORE);
        state.ringInput = SteamBoilerArrayRenderer.sprite(RING_INPUT);
        state.ringOutput = SteamBoilerArrayRenderer.sprite(RING_OUTPUT);
        state.ringMixed = SteamBoilerArrayRenderer.sprite(RING_MIXED);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite core = state.core;
        if (core == null) {
            return;
        }
        // The core: two slow turns, about the vertical and then a tilted axis.
        float time = state.time;
        int coreColor = state.linked ? -1 : 0xFF7A8C8C;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F + 0.02F * (float) Math.sin(time * 0.05F), 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 1.2F));
        poseStack.mulPose(Axis.XP.rotationDegrees(time * 0.7F));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(core.atlasLocation()),
                (pose, buffer) -> cube(pose, buffer, core, coreColor, FULL_BRIGHT, CORE_HALF));
        poseStack.popPose();

        // The rings.
        long faces = state.faces;
        if (faces == 0 || state.ringInput == null || state.ringOutput == null || state.ringMixed == null) {
            return;
        }
        int light = state.lightCoords;
        for (Direction face : Direction.values()) {
            boolean in = QuantumFaces.any(faces, face, QuantumFaces.INPUT);
            boolean out = QuantumFaces.any(faces, face, QuantumFaces.OUTPUT);
            if (!in && !out) {
                continue;
            }
            TextureAtlasSprite ring = in && out ? state.ringMixed : in ? state.ringInput : state.ringOutput;
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            poseStack.mulPose(face.getRotation());
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(ring.atlasLocation()),
                    (pose, buffer) -> ring(pose, buffer, ring, light));
            poseStack.popPose();
        }
    }

    // A cube of half-size h about the origin, every face showing the sprite's top-left 4x4.
    private static void cube(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, int light, float h) {
        float u0 = sprite.getU(0.0F), u1 = sprite.getU(4.0F / 16.0F), v0 = sprite.getV(0.0F), v1 = sprite.getV(4.0F / 16.0F);
        // down, up, north, south, west, east, wound outwards
        quad(pose, buffer, color, light, 0, -1, 0, -h, -h, h, -h, -h, -h, h, -h, -h, h, -h, h, u0, v0, u1, v1);
        quad(pose, buffer, color, light, 0, 1, 0, -h, h, -h, -h, h, h, h, h, h, h, h, -h, u0, v0, u1, v1);
        quad(pose, buffer, color, light, 0, 0, -1, h, h, -h, h, -h, -h, -h, -h, -h, -h, h, -h, u0, v0, u1, v1);
        quad(pose, buffer, color, light, 0, 0, 1, -h, h, h, -h, -h, h, h, -h, h, h, h, h, u0, v0, u1, v1);
        quad(pose, buffer, color, light, -1, 0, 0, -h, h, -h, -h, -h, -h, -h, -h, h, -h, h, h, u0, v0, u1, v1);
        quad(pose, buffer, color, light, 1, 0, 0, h, h, h, h, -h, h, h, -h, -h, h, h, -h, u0, v0, u1, v1);
    }

    // The ring on the up face (the pose turns it onto its face): the sprite's 4..12 over the plate's 4..12.
    private static void ring(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int light) {
        float u0 = sprite.getU(4.0F / 16.0F), u1 = sprite.getU(12.0F / 16.0F), v0 = sprite.getV(4.0F / 16.0F), v1 = sprite.getV(12.0F / 16.0F);
        float h = RING_HALF, y = RING_OUT;
        quad(pose, buffer, -1, light, 0, 1, 0, -h, y, -h, -h, y, h, h, y, h, h, y, -h, u0, v0, u1, v1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int color, int light, float nx, float ny, float nz,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1) {
        vertex(pose, buffer, ax, ay, az, u0, v0, color, light, nx, ny, nz);
        vertex(pose, buffer, bx, by, bz, u0, v1, color, light, nx, ny, nz);
        vertex(pose, buffer, cx, cy, cz, u1, v1, color, light, nx, ny, nz);
        vertex(pose, buffer, dx, dy, dz, u1, v0, color, light, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int color, int light,
            float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
