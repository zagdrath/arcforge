/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;

// The Arc Quarry's moving parts, drawn from its main block (the static machine is the block model): the arc emitter
// on top of the head, spinning while it works, and an arc beam from the emitter's tip to the block it last mined,
// scrolling along its length and full-bright.
public class ArcQuarryRenderer implements BlockEntityRenderer<ArcQuarryBlockEntity, ArcQuarryRenderer.State> {
    public static final StandaloneModelKey<QuadCollection> EMITTER = new StandaloneModelKey<>(() -> Arcforge.MODID + ":arc_quarry_emitter");
    public static final Identifier EMITTER_MODEL = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/arc_quarry/emitter");
    private static final Identifier EMITTER_SPRITE = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/arc_quarry/emitter");
    private static final Identifier BEAM = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/block/arc_quarry/beam.png");
    // The emitter's tip, from the main block's corner.
    private static final Vec3 TIP = new Vec3(0.5, 1.95, 0.5);
    private static final float BEAM_HALF_WIDTH = 0.125F;
    // The beam texture covers this many blocks of length, and scrolls this fast.
    private static final float BEAM_TEXTURE_LENGTH = 4.0F;
    private static final float BEAM_SCROLL = 0.1F;
    private static final int FULL_BRIGHT = LightCoordsUtil.FULL_BRIGHT;

    public static class State extends BlockEntityRenderState {
        public float spin;
        public boolean lit;
        public @Nullable Vec3 beamEnd;
        public float scroll;
        public int light;
    }

    public ArcQuarryRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ArcQuarryBlockEntity quarry, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(quarry, state, partialTicks, cameraPosition, breakProgress);
        state.lit = quarry.getBlockState().getValue(MachineBlock.LIT);
        double time = quarry.getLevel() != null ? quarry.getLevel().getGameTime() + partialTicks : 0.0;
        state.spin = quarry.advanceSpin(time, state.lit);
        state.scroll = (float) (time * BEAM_SCROLL);
        BlockPos target = quarry.getLastTarget();
        state.beamEnd = state.lit && target != null ? Vec3.atCenterOf(target).subtract(Vec3.atLowerCornerOf(quarry.getBlockPos())) : null;
        state.light = state.lightCoords;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        QuadCollection emitter = Minecraft.getInstance().getModelManager().getStandaloneModel(EMITTER);
        if (emitter != null) {
            TextureAtlasSprite sprite = SteamBoilerArrayRenderer.sprite(EMITTER_SPRITE);
            List<BakedQuad> quads = emitter.getAll();
            poseStack.pushPose();
            poseStack.translate(0.5, 0.0, 0.5);
            poseStack.rotate(Axis.YP.rotationDegrees(state.spin));
            poseStack.translate(-0.5, 0.0, -0.5);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(sprite.atlasLocation()),
                    (pose, buffer) -> TiledBoxes.quads(pose, buffer, quads, -1, state.lit ? FULL_BRIGHT : state.light));
            poseStack.popPose();
        }
        Vec3 end = state.beamEnd;
        if (end != null) {
            collector.submitCustomGeometry(poseStack, RenderTypes.beaconBeam(BEAM, true), (pose, buffer) -> beam(pose, buffer, TIP, end, state.scroll));
        }
    }

    // Two crossed quads from start to end, each drawn from both sides.
    private static void beam(PoseStack.Pose pose, VertexConsumer buffer, Vec3 start, Vec3 end, float scroll) {
        Vec3 along = end.subtract(start);
        double length = along.length();
        if (length < 1.0E-3) {
            return;
        }
        Vec3 dir = along.scale(1.0 / length);
        Vec3 side = Math.abs(dir.y) > 0.99 ? new Vec3(1.0, 0.0, 0.0) : dir.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
        Vec3 other = dir.cross(side).normalize();
        float v0 = -scroll;
        float v1 = v0 + (float) (length / BEAM_TEXTURE_LENGTH);
        for (Vec3 offset : new Vec3[] { side.scale(BEAM_HALF_WIDTH), other.scale(BEAM_HALF_WIDTH) }) {
            Vec3 a = start.subtract(offset), b = start.add(offset), c = end.add(offset), d = end.subtract(offset);
            quad(pose, buffer, a, b, c, d, v0, v1);
            quad(pose, buffer, d, c, b, a, v1, v0);
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float vStart, float vEnd) {
        vertex(pose, buffer, a, 0.0F, vStart);
        vertex(pose, buffer, b, 1.0F, vStart);
        vertex(pose, buffer, c, 1.0F, vEnd);
        vertex(pose, buffer, d, 0.0F, vEnd);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, float u, float v) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    // The beam leaves the block, so don't cull it with the block.
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    // The machine, and the block the beam reaches.
    @Override
    public AABB getRenderBoundingBox(ArcQuarryBlockEntity quarry) {
        AABB box = new AABB(quarry.getBlockPos()).inflate(1.0);
        BlockPos target = quarry.getLastTarget();
        return target != null ? box.minmax(new AABB(target)) : box;
    }
}
