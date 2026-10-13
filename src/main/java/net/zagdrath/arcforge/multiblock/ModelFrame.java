/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.multiblock;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// The frame a formed multiblock's model is built in (as ModelMesh builds it): x across, y up, u along, in px from the
// structure's minimum corner, turned one of four ways. alongX: u runs along world X (x then toward world -Z); flip: the
// model's u = 0 is at the structure's positive end, x mirrored with it. width, height and length are in blocks.
//
// It also turns a model's solid parts (boxes in the frame) into each block's shape: the boxes clipped to that block, so
// a formed structure's outline follows its model (the Distillation Array's tower, the Pumpjack).
public record ModelFrame(int width, int height, int length, boolean alongX, boolean flip) {
    private static final float PX = 16.0F;

    // The frame whose u runs toward `along` (a horizontal direction) over a structure width x height x length blocks.
    public static ModelFrame toward(Direction along, int width, int height, int length) {
        return switch (along) {
            case NORTH -> new ModelFrame(width, height, length, false, true);
            case EAST -> new ModelFrame(width, height, length, true, false);
            case WEST -> new ModelFrame(width, height, length, true, true);
            default -> new ModelFrame(width, height, length, false, false);
        };
    }

    // The block (x, y, z offsets from the minimum corner) holding the frame point (x, y, u) px.
    public int[] worldBlock(float x, float y, float u) {
        float[] w = worldPoint(x, y, u);
        return new int[] { (int) Math.floor(w[0] / PX), (int) Math.floor(w[1] / PX), (int) Math.floor(w[2] / PX) };
    }

    // A frame point (px) in world px from the minimum corner.
    public float[] worldPoint(float x, float y, float u) {
        float fx = flip ? width * PX - x : x, fu = flip ? length * PX - u : u;
        return alongX ? new float[] { fu, y, width * PX - fx } : new float[] { fx, y, fu };
    }

    // The frame's block {x, y, u} holding the world block (dx, dy, dz) from the minimum corner.
    public int[] frameBlock(int dx, int dy, int dz) {
        float wx = (dx + 0.5F) * PX, wz = (dz + 0.5F) * PX;
        float fx = alongX ? width * PX - wz : wx, fu = alongX ? wx : wz;
        float x = flip ? width * PX - fx : fx, u = flip ? length * PX - fu : fu;
        return new int[] { (int) Math.floor(x / PX), dy, (int) Math.floor(u / PX) };
    }

    // A world direction's steps (sx, sy, sz) as {x, y, u} in the frame.
    public int[] frameNormal(int sx, int sy, int sz) {
        int nx = alongX ? -sz : sx, nu = alongX ? sx : sz;
        return flip ? new int[] { -nx, sy, -nu } : new int[] { nx, sy, nu };
    }

    // How many blocks the structure spans along world X and Z.
    public int sizeX() {
        return alongX ? length : width;
    }

    public int sizeZ() {
        return alongX ? width : length;
    }

    // The shape of each block of the structure from the model's solid boxes ({x0, y0, u0, x1, y1, u1} px in the frame):
    // indexed (dx * height + dy) * sizeZ + dz from the minimum corner; null where no part reaches.
    public VoxelShape[] shapes(List<float[]> boxes) {
        int sx = sizeX(), sz = sizeZ();
        List<List<float[]>> perBlock = new ArrayList<>();
        for (int i = 0; i < sx * height * sz; i++) {
            perBlock.add(new ArrayList<>());
        }
        for (float[] box : boxes) {
            float[] a = worldPoint(box[0], box[1], box[2]), b = worldPoint(box[3], box[4], box[5]);
            float x0 = Math.min(a[0], b[0]), x1 = Math.max(a[0], b[0]), y0 = Math.min(a[1], b[1]), y1 = Math.max(a[1], b[1]);
            float z0 = Math.min(a[2], b[2]), z1 = Math.max(a[2], b[2]);
            for (int dx = Math.max(0, (int) Math.floor(x0 / PX)); dx < Math.min(sx, (int) Math.ceil(x1 / PX)); dx++) {
                for (int dy = Math.max(0, (int) Math.floor(y0 / PX)); dy < Math.min(height, (int) Math.ceil(y1 / PX)); dy++) {
                    for (int dz = Math.max(0, (int) Math.floor(z0 / PX)); dz < Math.min(sz, (int) Math.ceil(z1 / PX)); dz++) {
                        float cx0 = Math.max(0, x0 - dx * PX), cx1 = Math.min(PX, x1 - dx * PX);
                        float cy0 = Math.max(0, y0 - dy * PX), cy1 = Math.min(PX, y1 - dy * PX);
                        float cz0 = Math.max(0, z0 - dz * PX), cz1 = Math.min(PX, z1 - dz * PX);
                        if (cx1 - cx0 > 0.01F && cy1 - cy0 > 0.01F && cz1 - cz0 > 0.01F) {
                            perBlock.get((dx * height + dy) * sz + dz).add(new float[] { cx0, cy0, cz0, cx1, cy1, cz1 });
                        }
                    }
                }
            }
        }
        VoxelShape[] shapes = new VoxelShape[perBlock.size()];
        for (int i = 0; i < shapes.length; i++) {
            List<float[]> pieces = perBlock.get(i);
            if (pieces.isEmpty()) {
                continue;
            }
            VoxelShape shape = Shapes.empty();
            for (float[] p : pieces) {
                shape = Shapes.or(shape, Block.box(snap(p[0]), snap(p[1]), snap(p[2]), snap(p[3]), snap(p[4]), snap(p[5])));
            }
            shapes[i] = shape.optimize();
        }
        return shapes;
    }

    // To the nearest half px: shapes made of many slightly different fractions merge badly.
    private static double snap(float px) {
        return Math.round(px * 2.0F) / 2.0;
    }

    public int index(int dx, int dy, int dz) {
        return (dx * height + dy) * sizeZ() + dz;
    }
}
