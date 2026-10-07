/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.logistics.ChunkLoaderBlock;
import net.zagdrath.arcforge.blockentity.logistics.ChunkLoaderBlockEntity;

// The Chunk Loader's globe: a voxel ball 6 px across (1 px cells, like the rest of the model) sitting in the copper
// cradle on top of the pedestal, its axis tilted 22.5°, turning slowly about that axis while the loader is active. Each outer cell face
// takes one texel of the globe map (block/chunk_loader/globe, 32x16 longitude by latitude: blue oceans, green and tan
// land, white poles in flat tones), looked up from the direction of the face's centre, so the map wraps the ball.
public class ChunkLoaderRenderer implements BlockEntityRenderer<ChunkLoaderBlockEntity, ChunkLoaderRenderer.State> {
    private static final Identifier GLOBE = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/chunk_loader/globe");
    // Centre of the globe in the north-facing model, and its radius (cells whose centre is this close are in).
    private static final float CENTRE_X = 8.0F / 16.0F, CENTRE_Y = 12.5F / 16.0F, CENTRE_Z = 8.0F / 16.0F;
    private static final double RADIUS = 3.05;
    private static final float TILT = 22.5F;
    private static final float DEGREES_PER_TICK = 1.5F;
    private static final int MAP_W = 32, MAP_H = 16, SHEET_W = 32, SHEET_H = 16;
    // Every face of the ball: corners (x, y, z) x 4 in px about the centre, normal, texel (u, v).
    private static final List<float[]> FACES = build();

    public static class State extends BlockEntityRenderState {
        public @Nullable TextureAtlasSprite sprite;
        public float spin;
        public float facing;
    }

    public ChunkLoaderRenderer(BlockEntityRendererProvider.Context context) {}

    private static boolean inside(int i, int j, int k) {
        double x = i + 0.5, y = j + 0.5, z = k + 0.5;
        return Math.sqrt(x * x + y * y + z * z) <= RADIUS;
    }

    private static List<float[]> build() {
        List<float[]> faces = new ArrayList<>();
        for (int i = -3; i < 3; i++) {
            for (int j = -3; j < 3; j++) {
                for (int k = -3; k < 3; k++) {
                    if (!inside(i, j, k)) {
                        continue;
                    }
                    for (Direction d : Direction.values()) {
                        if (inside(i + d.getStepX(), j + d.getStepY(), k + d.getStepZ())) {
                            continue;
                        }
                        float cx = i + 0.5F + d.getStepX() * 0.5F, cy = j + 0.5F + d.getStepY() * 0.5F, cz = k + 0.5F + d.getStepZ() * 0.5F;
                        double r = Math.sqrt(cx * cx + cy * cy + cz * cz);
                        double lon = Math.atan2(cz, cx), lat = Math.asin(cy / r);
                        int u = Math.floorMod((int) Math.floor((lon + Math.PI) / (2 * Math.PI) * MAP_W), MAP_W);
                        int v = Math.max(0, Math.min(MAP_H - 1, (int) Math.floor((Math.PI / 2 - lat) / Math.PI * MAP_H)));
                        faces.add(face(i, j, k, i + 1, j + 1, k + 1, d, u, v));
                    }
                }
            }
        }
        return faces;
    }

    // One face of the box (x0..x1, y0..y1, z0..z1) on side d, wound outwards as vanilla block faces are.
    private static float[] face(float x0, float y0, float z0, float x1, float y1, float z1, Direction d, int u, int v) {
        float[][] c = switch (d) {
            case DOWN -> new float[][] { { x0, y0, z1 }, { x0, y0, z0 }, { x1, y0, z0 }, { x1, y0, z1 } };
            case UP -> new float[][] { { x0, y1, z0 }, { x0, y1, z1 }, { x1, y1, z1 }, { x1, y1, z0 } };
            case NORTH -> new float[][] { { x1, y1, z0 }, { x1, y0, z0 }, { x0, y0, z0 }, { x0, y1, z0 } };
            case SOUTH -> new float[][] { { x0, y1, z1 }, { x0, y0, z1 }, { x1, y0, z1 }, { x1, y1, z1 } };
            case WEST -> new float[][] { { x0, y1, z0 }, { x0, y0, z0 }, { x0, y0, z1 }, { x0, y1, z1 } };
            case EAST -> new float[][] { { x1, y1, z1 }, { x1, y0, z1 }, { x1, y0, z0 }, { x1, y1, z0 } };
        };
        return new float[] { c[0][0], c[0][1], c[0][2], c[1][0], c[1][1], c[1][2], c[2][0], c[2][1], c[2][2], c[3][0], c[3][1], c[3][2],
                d.getStepX(), d.getStepY(), d.getStepZ(), u, v };
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ChunkLoaderBlockEntity loader, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(loader, state, partialTicks, cameraPosition, breakProgress);
        BlockState block = loader.getBlockState();
        boolean active = block.hasProperty(ChunkLoaderBlock.ACTIVE) && block.getValue(ChunkLoaderBlock.ACTIVE);
        float now = loader.getLevel() != null ? loader.getLevel().getGameTime() + partialTicks : 0.0F;
        state.spin = loader.advanceGlobe(now, active, DEGREES_PER_TICK);
        state.facing = block.hasProperty(ChunkLoaderBlock.FACING) ? block.getValue(ChunkLoaderBlock.FACING).toYRot() : 180.0F;
        state.sprite = SteamBoilerArrayRenderer.sprite(GLOBE);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (sprite == null) {
            return;
        }
        int light = state.lightCoords;
        poseStack.pushPose();
        // Turned with the block (the model faces north at FACING north), then to the globe's centre, tilted, and spun.
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.facing));
        poseStack.translate(CENTRE_X - 0.5F, CENTRE_Y, CENTRE_Z - 0.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-TILT));
        poseStack.mulPose(Axis.YP.rotationDegrees(state.spin));
        poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        collector.submitCustomGeometry(poseStack, RenderTypes.entitySolid(sprite.atlasLocation()), (pose, buffer) -> {
            for (float[] f : FACES) {
                emit(pose, buffer, sprite, f, light);
            }
        });
        poseStack.popPose();
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, float[] f, int light) {
        // The texel's middle, so the whole face is that one map colour.
        float u = sprite.getU((f[15] + 0.5F) / SHEET_W), v = sprite.getV((f[16] + 0.5F) / SHEET_H);
        for (int corner = 0; corner < 4; corner++) {
            buffer.addVertex(pose, f[corner * 3], f[corner * 3 + 1], f[corner * 3 + 2])
                    .setColor(-1)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, f[12], f[13], f[14]);
        }
    }
}
