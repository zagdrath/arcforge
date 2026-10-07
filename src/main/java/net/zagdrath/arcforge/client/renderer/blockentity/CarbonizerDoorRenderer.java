/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.CarbonizerBlock;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;

// The steel door on each Carbonizer slice front, one leaf the full height of the slice (24px for a
// 2-tall slice, 40px for a 3-tall one), drawn a block-sized piece at a time. It stands 1px proud of the
// front face and swings 90 degrees outwards about its hinge (the viewer's left) while the slice works.
// The block models leave the door out. Leaf texture u = 0 is the hinge edge.
// Coordinates are model pixels for a north-facing block, rotated to the block's facing.
public class CarbonizerDoorRenderer implements BlockEntityRenderer<CarbonizerBlockEntity, CarbonizerDoorRenderer.State> {

    // The closed leaf, and the hinge edge it swings about.
    private static final float X0 = 4, X1 = 12, Z0 = -1, Z1 = 0;
    private static final float HINGE_X = 12, HINGE_Z = 0;
    // Each block's piece of the leaf: y 4..16 of the bottom block, all of a middle block, y 0..12 of the top.
    private enum Piece {
        BOTTOM("bottom", 4, 16),
        MIDDLE("middle", 0, 16),
        TOP("top", 0, 12);

        final String name;
        final float y0, y1;

        Piece(String name, float y0, float y1) {
            this.name = name;
            this.y0 = y0;
            this.y1 = y1;
        }

        Identifier texture(boolean tall) {
            return Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/carbonizer/" + (tall ? "door3_" : "door_") + name);
        }
    }

    public static class State extends BlockEntityRenderState {
        public @Nullable TextureAtlasSprite sprite;
        public Direction facing = Direction.NORTH;
        public Piece piece = Piece.BOTTOM;
        public float open;
        public int doorLight;
    }

    public CarbonizerDoorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CarbonizerBlockEntity carbonizer, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(carbonizer, state, partialTicks, cameraPosition, breakProgress);
        state.sprite = null;
        BlockState blockState = carbonizer.getBlockState();
        if (!CarbonizerBlockEntity.hasDoor(blockState)) {
            return;
        }
        state.facing = blockState.getValue(CarbonizerBlock.FACING);
        state.piece = switch (blockState.getValue(CarbonizerBlock.HALF)) {
            case TOP -> Piece.TOP;
            case MIDDLE -> Piece.MIDDLE;
            case BOTTOM -> Piece.BOTTOM;
        };
        state.open = carbonizer.getDoorOpen(partialTicks);
        state.sprite = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)
                .getSprite(state.piece.texture(blockState.getValue(CarbonizerBlock.TALL)));
        // The door hangs in front of the (opaque) block, so it takes the light of the space it sits in.
        BlockPos front = carbonizer.getBlockPos().relative(state.facing);
        state.doorLight = carbonizer.getLevel() != null ? LevelRenderer.getLightCoords(carbonizer.getLevel(), front) : state.lightCoords;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null) {
            return;
        }
        poseStack.pushPose();
        // Turn the north-facing model to the block's facing (as the blockstate's y rotation does).
        poseStack.rotateAround(Axis.YP.rotationDegrees(-(state.facing.toYRot() + 180.0F)), 0.5F, 0.0F, 0.5F);
        // Swing outwards about the hinge.
        poseStack.rotateAround(Axis.YP.rotationDegrees(-90.0F * state.open), HINGE_X / 16.0F, 0.0F, HINGE_Z / 16.0F);

        Piece piece = state.piece;
        int light = state.doorLight;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(sprite.atlasLocation()),
                (pose, buffer) -> leaf(pose, buffer, sprite, light, piece));
        poseStack.popPose();
    }

    // A piece of the leaf: face and back 8 wide from the door texture, edges from its 1px strip at u 8..9.
    private static void leaf(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int light, Piece piece) {
        float y0 = piece.y0, y1 = piece.y1, v = y1 - y0;
        // Front (north) and back (south, mirrored).
        quad(pose, buffer, sprite, light, 0, 0, -1, X1, y1, Z0, X1, y0, Z0, X0, y0, Z0, X0, y1, Z0, 0, 0, 8, v);
        quad(pose, buffer, sprite, light, 0, 0, 1, X0, y1, Z1, X0, y0, Z1, X1, y0, Z1, X1, y1, Z1, 8, 0, 0, v);
        // Hinge side (east) and free side (west).
        quad(pose, buffer, sprite, light, 1, 0, 0, X1, y1, Z1, X1, y0, Z1, X1, y0, Z0, X1, y1, Z0, 8, 0, 9, v);
        quad(pose, buffer, sprite, light, -1, 0, 0, X0, y1, Z0, X0, y0, Z0, X0, y0, Z1, X0, y1, Z1, 8, 0, 9, v);
        // The exposed ends: the top of the top piece, the bottom of the bottom one. Middle pieces have none.
        if (piece == Piece.TOP) {
            quad(pose, buffer, sprite, light, 0, 1, 0, X0, y1, Z0, X0, y1, Z1, X1, y1, Z1, X1, y1, Z0, 0, 0, 8, 1);
        } else if (piece == Piece.BOTTOM) {
            quad(pose, buffer, sprite, light, 0, -1, 0, X0, y0, Z1, X0, y0, Z0, X1, y0, Z0, X1, y0, Z1, 0, 1, 8, 0);
        }
    }

    // Four corners in model pixels, wound outwards; the texture region runs u0..u1 across and v0..v1 down (pixels).
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int light,
            float nx, float ny, float nz,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1) {
        float minU = sprite.getU(u0 / 16.0F), maxU = sprite.getU(u1 / 16.0F);
        float minV = sprite.getV(v0 / 16.0F), maxV = sprite.getV(v1 / 16.0F);
        vertex(pose, buffer, ax, ay, az, minU, minV, light, nx, ny, nz);
        vertex(pose, buffer, bx, by, bz, minU, maxV, light, nx, ny, nz);
        vertex(pose, buffer, cx, cy, cz, maxU, maxV, light, nx, ny, nz);
        vertex(pose, buffer, dx, dy, dz, maxU, minV, light, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v,
            int light, float nx, float ny, float nz) {
        buffer.addVertex(pose, x / 16.0F, y / 16.0F, z / 16.0F)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    // The open leaf reaches half a block in front of the carbonizer.
    @Override
    public AABB getRenderBoundingBox(CarbonizerBlockEntity carbonizer) {
        return new AABB(carbonizer.getBlockPos()).inflate(1.0, 0.0, 1.0);
    }
}
