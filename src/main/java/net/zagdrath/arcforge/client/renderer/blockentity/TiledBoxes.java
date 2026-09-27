/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.List;
import java.util.Set;

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
// facing in; and the lining of a structure's walls, seen through its windows. Also draws baked model quads.
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

    // A flat rectangle facing one way (x0 == x1 for a face along X, and so on), tiled like a box's face.
    public static void plane(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, int light, Direction facing,
            float x0, float y0, float z0, float x1, float y1, float z1) {
        face(pose, buffer, sprite, color, light, new float[] { x0, y0, z0 }, new float[] { x1, y1, z1 }, facing, false);
    }

    // The inward faces of a box inset from a structure's walls (sizes in blocks), so the walls have an
    // inside: the walls' own blocks draw only their outer faces, and without this a ray in through a
    // window at a steep angle would leave by the rim. Inward faces are culled from outside, so they never
    // fight the blocks' own, and the inset keeps them clear of anything pressed against the structure
    // (the block under its floor, a conduit's end cap on a wall), whose faces would otherwise share the
    // lining's plane and flicker. The six faces meet at the inset corners.
    //
    // Each face is laid in half-block tiles (the sprite still repeats once per block). Where windows says
    // a tile is window (see windowKey), there is none, so the structure can be seen through from one
    // window to another, and each edge of that opening that meets lining (or the face's rim) gets a reveal
    // from the outer skin in to the lining, textured with jamb, so the opening has a thickness.
    public static void lining(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, TextureAtlasSprite jamb,
            int color, int light, float inset, float sizeX, float sizeY, float sizeZ, Set<Long> windows) {
        float[] size = { sizeX, sizeY, sizeZ };
        for (Direction face : Direction.values()) {
            int a = face.getAxis().ordinal();
            int u = (a + 1) % 3;
            int v = (a + 2) % 3;
            boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            float plane = positive ? size[a] - inset : inset;
            float outer = positive ? size[a] : 0.0F;
            int halvesU = Math.round(size[u] * 2), halvesV = Math.round(size[v] * 2);
            for (int hu = 0; hu < halvesU; hu++) {
                for (int hv = 0; hv < halvesV; hv++) {
                    float u0 = Math.max(inset, hu * 0.5F), u1 = Math.min(size[u] - inset, (hu + 1) * 0.5F);
                    float v0 = Math.max(inset, hv * 0.5F), v1 = Math.min(size[v] - inset, (hv + 1) * 0.5F);
                    if (!windows.contains(windowKey(face, hu, hv))) {
                        float[] min = new float[3], max = new float[3];
                        min[a] = max[a] = plane;
                        min[u] = u0;
                        max[u] = u1;
                        min[v] = v0;
                        max[v] = v1;
                        float blockU = (float) Math.floor(hu * 0.5F), blockV = (float) Math.floor(hv * 0.5F);
                        // Facing into the structure: toward -a on the positive wall, +a on the other.
                        rect(pose, buffer, sprite, color, light, a, !positive, min, max, p -> sprite.getU(p[u] - blockU), p -> sprite.getV(p[v] - blockV));
                        continue;
                    }
                    // The opening's reveals, each facing into it.
                    boolean[] open = {
                            hu > 0 && windows.contains(windowKey(face, hu - 1, hv)),
                            hu < halvesU - 1 && windows.contains(windowKey(face, hu + 1, hv)),
                            hv > 0 && windows.contains(windowKey(face, hu, hv - 1)),
                            hv < halvesV - 1 && windows.contains(windowKey(face, hu, hv + 1)) };
                    for (int edge = 0; edge < 4; edge++) {
                        if (open[edge]) {
                            continue;
                        }
                        int across = edge < 2 ? u : v;
                        int along = edge < 2 ? v : u;
                        boolean low = edge % 2 == 0;
                        float[] min = new float[3], max = new float[3];
                        min[across] = max[across] = low ? (across == u ? u0 : v0) : (across == u ? u1 : v1);
                        min[along] = along == u ? u0 : v0;
                        max[along] = along == u ? u1 : v1;
                        min[a] = Math.min(outer, plane);
                        max[a] = Math.max(outer, plane);
                        float blockAlong = (float) Math.floor(min[along]);
                        rect(pose, buffer, jamb, color, light, across, low, min, max,
                                p -> jamb.getU(Math.abs(p[a] - outer)), p -> jamb.getV(p[along] - blockAlong));
                    }
                }
            }
        }
    }

    // The key a lining tile goes by: the wall it's on (face: the side of the structure) and its position in
    // half blocks along that face's two in-plane axes (the axes after face's in X, Y, Z order).
    public static long windowKey(Direction face, int halfU, int halfV) {
        return (long) face.ordinal() << 40 | (long) halfU << 20 | halfV;
    }

    private interface TexCoord {
        float at(float[] point);
    }

    // A flat rectangle across axis k, from min to max (min[k] == max[k]), facing +k or -k. As for face(),
    // the in-plane axes follow k in X, Y, Z order so the corners wind counter-clockwise seen from +k.
    private static void rect(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, int light,
            int k, boolean facesPositive, float[] min, float[] max, TexCoord texU, TexCoord texV) {
        int k1 = (k + 1) % 3;
        int k2 = (k + 2) % 3;
        float[][] corners = { { min[k1], min[k2] }, { max[k1], min[k2] }, { max[k1], max[k2] }, { min[k1], max[k2] } };
        float[] n = new float[3];
        n[k] = facesPositive ? 1.0F : -1.0F;
        for (int i = 0; i < 4; i++) {
            float[] corner = corners[facesPositive ? i : 3 - i];
            float[] p = new float[3];
            p[k] = min[k];
            p[k1] = corner[0];
            p[k2] = corner[1];
            vertex(pose, buffer, p[0], p[1], p[2], texU.at(p), texV.at(p), color, light, n[0], n[1], n[2]);
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
