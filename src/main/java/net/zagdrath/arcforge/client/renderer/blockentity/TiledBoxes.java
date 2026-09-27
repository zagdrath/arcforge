/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;

// Geometry for the steam arrays' renderers: boxes several blocks big (coordinates in block units), their
// faces split into one-block tiles so the sprite repeats instead of stretching; drawn facing out, or
// facing in (for a lining seen through a window). Also draws baked model quads.
public final class TiledBoxes {
    private TiledBoxes() {}

    public static void box(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, int light,
            float x0, float y0, float z0, float x1, float y1, float z1, boolean inward) {
        box(pose, buffer, sprite, color, light, x0, y0, z0, x1, y1, z1, inward, null);
    }

    // As above, leaving out one face (skip), e.g. where the box sits on another and the two faces
    // would share a plane and flicker.
    public static void box(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, int light,
            float x0, float y0, float z0, float x1, float y1, float z1, boolean inward, @Nullable Direction skip) {
        if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
            return;
        }
        float[] min = { x0, y0, z0 };
        float[] max = { x1, y1, z1 };
        for (Direction face : Direction.values()) {
            if (face != skip) {
                face(pose, buffer, sprite, color, light, min, max, face, inward);
            }
        }
    }

    // One face of the box, tiled. For a face along axis a, the in-plane axes u and v are chosen so that
    // u x v points along +a; corners (u0,v0) (u1,v0) (u1,v1) (u0,v1) then wind counter-clockwise seen
    // from +a, and are reversed for faces that should point the other way.
    private static void face(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, int light,
            float[] min, float[] max, Direction face, boolean inward) {
        int a = face.getAxis().ordinal();
        int u = (a + 1) % 3;
        int v = (a + 2) % 3;
        boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        float plane = positive ? max[a] : min[a];
        // Which way the quad faces: out of the box, or into it.
        boolean facesPositive = positive != inward;
        float normal = facesPositive ? 1.0F : -1.0F;
        for (float tu = (float) Math.floor(min[u]); tu < max[u]; tu++) {
            for (float tv = (float) Math.floor(min[v]); tv < max[v]; tv++) {
                float u0 = Math.max(min[u], tu), u1 = Math.min(max[u], tu + 1);
                float v0 = Math.max(min[v], tv), v1 = Math.min(max[v], tv + 1);
                if (u1 <= u0 || v1 <= v0) {
                    continue;
                }
                float[][] corners = { { u0, v0 }, { u1, v0 }, { u1, v1 }, { u0, v1 } };
                for (int i = 0; i < 4; i++) {
                    float[] corner = corners[facesPositive ? i : 3 - i];
                    float[] p = new float[3];
                    p[a] = plane;
                    p[u] = corner[0];
                    p[v] = corner[1];
                    float[] n = new float[3];
                    n[a] = normal;
                    vertex(pose, buffer, p[0], p[1], p[2], sprite.getU(corner[0] - tu), sprite.getV(corner[1] - tv), color, light, n[0], n[1], n[2]);
                }
            }
        }
    }

    // Baked model quads (positions in block units), tinted by color.
    public static void quads(PoseStack.Pose pose, VertexConsumer buffer, List<BakedQuad> quads, int color, int light) {
        for (BakedQuad quad : quads) {
            var normal = quad.direction().getUnitVec3i();
            for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                Vector3fc position = quad.position(i);
                long uv = quad.packedUV(i);
                vertex(pose, buffer, position.x(), position.y(), position.z(), UVPair.unpackU(uv), UVPair.unpackV(uv), color, light,
                        normal.getX(), normal.getY(), normal.getZ());
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v,
            int color, int light, float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
