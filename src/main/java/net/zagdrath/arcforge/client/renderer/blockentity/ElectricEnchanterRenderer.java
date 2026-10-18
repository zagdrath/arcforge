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
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.machine.ElectricEnchanterBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.EnchanterBook;

// The Electric Enchanter's book, floating over the enchanting plate on its top (the casing itself is a block model).
// Built from boxes on block/electric_enchanter/book (32x32): two covers on a flat spine, a block of pages on each, and
// two loose pages that flip from one side to the other. Its halves swing open on the spine while the machine works
// (EnchanterBook, ticked on the client) and fold shut when it stops; it bobs over the plate and leans toward the front
// like a book on a lectern. Sizes are in pixels (1/16 block) about the spine.
public class ElectricEnchanterRenderer implements BlockEntityRenderer<ElectricEnchanterBlockEntity, ElectricEnchanterRenderer.State> {
    public static final Identifier BOOK = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/electric_enchanter/book");
    private static final float SHEET = 32.0F;
    // Regions of the book sheet: u0, v0, u1, v1 in its pixels.
    private static final float[] COVER_OUT = { 0, 0, 6, 10 };
    private static final float[] COVER_IN = { 6, 0, 12, 10 };
    private static final float[] COVER_EDGE = { 12, 0, 13, 10 };
    private static final float[] PAGES_FACE = { 13, 0, 18, 8 };
    private static final float[] PAGES_EDGE = { 18, 0, 19, 8 };
    private static final float[] PAGES_END = { 19, 0, 24, 1 };
    private static final float[] SPINE = { 0, 10, 3, 20 };
    private static final float[] LOOSE_PAGE = { 13, 8, 18, 16 };
    // How far each half opens from upright, at full open.
    private static final float OPEN_DEGREES = 62.0F;
    // How far the book leans toward the front.
    private static final float LEAN_DEGREES = 42.0F;

    public static class State extends BlockEntityRenderState {
        public float open;
        public float flip;
        public float time;
        public float yaw;
        // The light over the top, where the book is (the casing's own block is opaque).
        public int light;
        public @Nullable TextureAtlasSprite sheet;
    }

    public ElectricEnchanterRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ElectricEnchanterBlockEntity enchanter, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(enchanter, state, partialTicks, cameraPosition, breakProgress);
        EnchanterBook book = enchanter.book();
        state.open = book.open(partialTicks);
        state.flip = book.flip(partialTicks);
        state.time = book.time + partialTicks;
        Direction facing = enchanter.getBlockState().getValue(MachineBlock.FACING);
        state.yaw = 180.0F - facing.toYRot();
        state.sheet = SteamBoilerArrayRenderer.sprite(BOOK);
        state.light = enchanter.getLevel() != null ? LightCoordsUtil.getLightCoords(enchanter.getLevel(), enchanter.getBlockPos().above())
                : state.lightCoords;
    }

    // The book floats up to half a block over the top.
    @Override
    public AABB getRenderBoundingBox(ElectricEnchanterBlockEntity enchanter) {
        return new AABB(enchanter.getBlockPos()).expandTowards(0.0, 0.6, 0.0);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sheet = state.sheet;
        if (sheet == null) {
            return;
        }
        poseStack.pushPose();
        float bob = Mth.sin(state.time * 0.1F) * 0.02F;
        poseStack.translate(0.5F, 1.22F + bob, 0.5F);
        poseStack.rotate(Axis.YP.rotationDegrees(state.yaw + Mth.sin(state.time * 0.05F) * 4.0F));
        poseStack.rotate(Axis.XP.rotationDegrees(-LEAN_DEGREES));
        poseStack.scale(1 / 16.0F, 1 / 16.0F, 1 / 16.0F);
        int light = state.light;
        float open = Mth.clamp(state.open, 0.0F, 1.0F);
        float half = OPEN_DEGREES * open;
        float flip1 = Mth.clamp(Mth.frac(state.flip + 0.25F) * 1.6F - 0.3F, 0.0F, 1.0F);
        float flip2 = Mth.clamp(Mth.frac(state.flip + 0.75F) * 1.6F - 0.3F, 0.0F, 1.0F);
        RenderType type = RenderTypes.entityCutout(sheet.atlasLocation());
        // The spine, flat under the fold.
        collector.submitCustomGeometry(poseStack, type,
                (pose, buffer) -> box(pose, buffer, sheet, light, -1.3F, -0.3F, -5.0F, 1.3F, 0.0F, 5.0F, SPINE, SPINE, COVER_EDGE));
        submitTurned(poseStack, collector, type, 1.0F, -half, (pose, buffer) -> half(pose, buffer, sheet, light, 1.0F));
        submitTurned(poseStack, collector, type, -1.0F, half, (pose, buffer) -> half(pose, buffer, sheet, light, -1.0F));
        if (open > 0.05F) {
            submitTurned(poseStack, collector, type, 0.0F, Mth.lerp(flip1, half, -half), (pose, buffer) -> loosePage(pose, buffer, sheet, light));
            submitTurned(poseStack, collector, type, 0.0F, Mth.lerp(flip2, half, -half), (pose, buffer) -> loosePage(pose, buffer, sheet, light));
        }
        poseStack.popPose();
    }

    // A part hinged at x = hinge on the spine, turned by angle (degrees, about Z) from upright.
    private static void submitTurned(PoseStack poseStack, SubmitNodeCollector collector, RenderType type, float hinge, float angle,
            SubmitNodeCollector.CustomGeometryRenderer part) {
        poseStack.pushPose();
        poseStack.translate(hinge, 0.0F, 0.0F);
        poseStack.rotate(Axis.ZP.rotationDegrees(angle));
        collector.submitCustomGeometry(poseStack, type, part);
        poseStack.popPose();
    }

    // One half, in its hinge's frame (see submitTurned): the cover and its pages. Its outward axis points up when
    // upright; the pages face the middle (side: which side of the spine it's on).
    private static void half(PoseStack.Pose turned, VertexConsumer buffer, TextureAtlasSprite sheet, int light, float side) {
        // Upright, the cover is a plate from the hinge up, outside the pages (x away from the middle).
        float sign = Math.signum(side);
        float coverIn = 0.0F, coverOut = 0.3F * sign;
        box(turned, buffer, sheet, light, Math.min(coverIn, coverOut), 0.0F, -5.0F, Math.max(coverIn, coverOut), 6.0F, 5.0F,
                COVER_EDGE, COVER_EDGE, sign > 0 ? COVER_IN : COVER_OUT, sign > 0 ? COVER_OUT : COVER_IN);
        // The pages: 1 px thick toward the middle, 5 px tall, 8 long.
        float pagesIn = -1.0F * sign;
        box(turned, buffer, sheet, light, Math.min(0.0F, pagesIn), 0.3F, -4.0F, Math.max(0.0F, pagesIn), 5.3F, 4.0F,
                PAGES_EDGE, PAGES_END, PAGES_FACE, PAGES_FACE);
    }

    // A loose page, in its hinge's frame at the middle of the spine.
    private static void loosePage(PoseStack.Pose turned, VertexConsumer buffer, TextureAtlasSprite sheet, int light) {
        quad(turned, buffer, sheet, light, LOOSE_PAGE, new float[][] { { 0.0F, 5.3F, 4.0F }, { 0.0F, 5.3F, -4.0F }, { 0.0F, 0.3F, -4.0F }, { 0.0F, 0.3F, 4.0F } },
                1.0F, 0.0F, 0.0F);
        quad(turned, buffer, sheet, light, LOOSE_PAGE, new float[][] { { 0.0F, 5.3F, -4.0F }, { 0.0F, 5.3F, 4.0F }, { 0.0F, 0.3F, 4.0F }, { 0.0F, 0.3F, -4.0F } },
                -1.0F, 0.0F, 0.0F);
    }

    // A box: its top and bottom from ends, its x faces from xNeg/xPos (or sides for both when they're null), its z
    // faces from sides.
    private static void box(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sheet, int light, float x0, float y0, float z0,
            float x1, float y1, float z1, float[] top, float[] ends, float[] sides) {
        box(pose, buffer, sheet, light, x0, y0, z0, x1, y1, z1, sides, ends, top, top);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sheet, int light, float x0, float y0, float z0,
            float x1, float y1, float z1, float[] zFaces, float[] yFaces, float[] xNeg, float[] xPos) {
        // +x and -x: the broad faces (u along z, v down y).
        quad(pose, buffer, sheet, light, xPos, new float[][] { { x1, y1, z1 }, { x1, y1, z0 }, { x1, y0, z0 }, { x1, y0, z1 } }, 1, 0, 0);
        quad(pose, buffer, sheet, light, xNeg, new float[][] { { x0, y1, z0 }, { x0, y1, z1 }, { x0, y0, z1 }, { x0, y0, z0 } }, -1, 0, 0);
        // +y and -y.
        quad(pose, buffer, sheet, light, yFaces, new float[][] { { x0, y1, z0 }, { x1, y1, z0 }, { x1, y1, z1 }, { x0, y1, z1 } }, 0, 1, 0);
        quad(pose, buffer, sheet, light, yFaces, new float[][] { { x0, y0, z1 }, { x1, y0, z1 }, { x1, y0, z0 }, { x0, y0, z0 } }, 0, -1, 0);
        // +z and -z.
        quad(pose, buffer, sheet, light, zFaces, new float[][] { { x0, y1, z1 }, { x1, y1, z1 }, { x1, y0, z1 }, { x0, y0, z1 } }, 0, 0, 1);
        quad(pose, buffer, sheet, light, zFaces, new float[][] { { x1, y1, z0 }, { x0, y1, z0 }, { x0, y0, z0 }, { x1, y0, z0 } }, 0, 0, -1);
    }

    // Four corners: the region's top left, top right, bottom right and bottom left as seen from outside (emitted in
    // reverse, counter-clockwise).
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sheet, int light, float[] region, float[][] corners,
            float nx, float ny, float nz) {
        float u0 = region[0] / SHEET, v0 = region[1] / SHEET, u1 = region[2] / SHEET, v1 = region[3] / SHEET;
        float[][] uv = { { u0, v0 }, { u1, v0 }, { u1, v1 }, { u0, v1 } };
        for (int i = 3; i >= 0; i--) {
            float[] p = corners[i];
            buffer.addVertex(pose, p[0], p[1], p[2])
                    .setColor(-1)
                    .setUv(sheet.getU(uv[i][0]), sheet.getV(uv[i][1]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
    }
}
