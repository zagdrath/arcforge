/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

// Draws axis-aligned boxes of fluid texture inside a block (coordinates in block units, 0..1).
// Each face maps the sprite by position, so a box smaller than a block shows the matching part of it.
public final class FluidBoxes {
    private FluidBoxes() {}

    public static void box(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, int light,
            float x0, float y0, float z0, float x1, float y1, float z1) {
        box(pose, buffer, sprite, color, light, x0, y0, z0, x1, y1, z1, 0);
    }

    // The same, leaving out the faces whose bit is set in skip (1 << Direction.get3DDataValue(): down, up, north, south,
    // west, east), e.g. where the box meets more of the same fluid in the next block.
    public static void box(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, int light,
            float x0, float y0, float z0, float x1, float y1, float z1, int skip) {
        if (x1 <= x0 || y1 <= y0 || z1 <= z0) {
            return;
        }
        // Vertex order matches vanilla block faces so every face is wound outwards.
        if ((skip & 1) == 0) {
            quad(pose, buffer, sprite, color, light, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, z1, x1, z0);
        }
        if ((skip & 2) == 0) {
            quad(pose, buffer, sprite, color, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, z0, x1, z1);
        }
        if ((skip & 4) == 0) {
            quad(pose, buffer, sprite, color, light, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, x0, y0);
        }
        if ((skip & 8) == 0) {
            quad(pose, buffer, sprite, color, light, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, x1, y0);
        }
        if ((skip & 16) == 0) {
            quad(pose, buffer, sprite, color, light, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, z0, y1, z1, y0);
        }
        if ((skip & 32) == 0) {
            quad(pose, buffer, sprite, color, light, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, z1, y1, z0, y0);
        }
    }

    // Four corners plus the texture region (u from a to b, v from c to d, in block units mapped onto the sprite).
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int color, int light,
            float nx, float ny, float nz,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
            float u0, float v0, float u1, float v1) {
        float minU = sprite.getU(u0), maxU = sprite.getU(u1);
        float minV = sprite.getV(1 - v0), maxV = sprite.getV(1 - v1);
        vertex(pose, buffer, ax, ay, az, minU, minV, color, light, nx, ny, nz);
        vertex(pose, buffer, bx, by, bz, minU, maxV, color, light, nx, ny, nz);
        vertex(pose, buffer, cx, cy, cz, maxU, maxV, color, light, nx, ny, nz);
        vertex(pose, buffer, dx, dy, dz, maxU, minV, color, light, nx, ny, nz);
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
