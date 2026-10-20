/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.multiblock.PumpjackLayout;
import net.zagdrath.arcforge.multiblock.RefineryLayout;
import net.zagdrath.arcforge.steam.DerrickMesh;
import net.zagdrath.arcforge.steam.DieselGeneratorMesh;
import net.zagdrath.arcforge.steam.PumpjackMesh;
import net.zagdrath.arcforge.steam.RefineryMesh;

// The large models' moving parts are built once at rest and drawn turned (see the renderers and StaticMeshes.submitMoving):
// for each, the parts at rest turned about the axes their meshes give, with what's still built each frame, must be the
// moving parts the mesh builds in place at that angle, every quad where it would be, facing the same way, with the same
// UVs, whichever way round the structure stands.
public final class MovingPartsGameTests {
    private MovingPartsGameTests() {}

    static void pumpjackParts(GameTestHelper helper) {
        for (Direction back : Direction.Plane.HORIZONTAL) {
            ModelFrame frame = ModelFrame.toward(back, OilfieldStructures.UNIT_WIDTH, OilfieldStructures.UNIT_HEIGHT, OilfieldStructures.UNIT_LENGTH);
            PumpjackMesh mesh = new PumpjackMesh(frame);
            float[] beam = mesh.buildBeam(), cranks = mesh.buildCranks();
            for (double theta : new double[] { 0.4, 1.9, 4.4 }) {
                List<float[]> parts = new ArrayList<>();
                parts.add(turned(beam, mesh.beamAxis(), (float) Math.toDegrees(PumpjackLayout.beamAngle(theta))));
                parts.add(turned(cranks, mesh.crankAxis(), (float) Math.toDegrees(theta)));
                parts.add(mesh.buildLinks(theta));
                String mismatch = compare(join(parts), mesh.buildMoving(theta));
                if (mismatch != null) {
                    helper.fail("Pumpjack facing " + back + " at " + theta + ": " + mismatch);
                    return;
                }
            }
        }
        helper.succeed();
    }

    static void derrickParts(GameTestHelper helper) {
        for (Direction along : Direction.Plane.HORIZONTAL) {
            ModelFrame frame = ModelFrame.toward(along, OilfieldStructures.RIG_WIDTH, OilfieldStructures.RIG_HEIGHT, OilfieldStructures.RIG_WIDTH);
            DerrickMesh mesh = new DerrickMesh(frame);
            float[] drum = mesh.buildDrum();
            for (float hoist : new float[] { 0.0F, 0.35F, 1.0F }) {
                float kelly = 123.0F, drumAngle = hoist * 900.0F;
                List<float[]> parts = new ArrayList<>();
                parts.add(turned(drum, mesh.drumAxis(), drumAngle));
                parts.add(mesh.buildLinks(hoist, kelly));
                String mismatch = compare(join(parts), mesh.buildMoving(hoist, kelly, drumAngle));
                if (mismatch != null) {
                    helper.fail("Derrick toward " + along + " at hoist " + hoist + ": " + mismatch);
                    return;
                }
            }
        }
        helper.succeed();
    }

    static void dieselParts(GameTestHelper helper) {
        for (int length : OilfieldStructures.GENERATOR_LENGTHS) {
            for (Direction along : Direction.Plane.HORIZONTAL) {
                ModelFrame frame = ModelFrame.toward(along, OilfieldStructures.GENERATOR_WIDTH, OilfieldStructures.GENERATOR_HEIGHT, length);
                DieselGeneratorMesh mesh = new DieselGeneratorMesh(frame);
                float[] fan = mesh.buildFan(), flap = mesh.buildFlap(), pin = mesh.buildFlapPin();
                for (float[] at : new float[][] { { 0.0F, 0.0F }, { 77.0F, 31.0F }, { 300.0F, 64.0F } }) {
                    List<float[]> parts = List.of(turned(fan, mesh.fanAxis(), at[0]), turned(flap, mesh.flapAxis(), at[1]), pin);
                    String mismatch = compare(join(parts), mesh.buildMoving(at[0], at[1]));
                    if (mismatch != null) {
                        helper.fail("Diesel Generator " + length + " long toward " + along + " at " + at[0] + "°, flap " + at[1] + "°: " + mismatch);
                        return;
                    }
                }
            }
        }
        helper.succeed();
    }

    static void distillationFans(GameTestHelper helper) {
        for (Direction along : Direction.Plane.HORIZONTAL) {
            for (int[] size : new int[][] { { 2, 6 }, { 3, 12 } }) {
                ModelFrame frame = ModelFrame.toward(along, size[0], size[1], size[0]);
                RefineryMesh mesh = new RefineryMesh(new RefineryLayout(size[0], size[1]), frame, 3);
                mesh.buildStatic(List.of());
                float[][] fans = mesh.buildFanParts(), axes = mesh.fanAxes();
                for (float angle : new float[] { 0.0F, 37.0F, 222.0F }) {
                    List<float[]> parts = new ArrayList<>();
                    for (int i = 0; i < fans.length; i++) {
                        parts.add(turned(fans[i], axes[i], angle));
                    }
                    String mismatch = compare(join(parts), mesh.buildFans(angle));
                    if (mismatch != null) {
                        helper.fail("Distillation Array " + size[0] + "x" + size[1] + " toward " + along + " at " + angle + "°: " + mismatch);
                        return;
                    }
                }
            }
        }
        helper.succeed();
    }

    // The part turned by degrees about axis (a point, then the direction), as the renderer turns it.
    static float[] turned(float[] rest, float[] axis, float degrees) {
        Matrix4f turn = new Matrix4f().translate(axis[0], axis[1], axis[2]).rotate((float) Math.toRadians(degrees), axis[3], axis[4], axis[5])
                .translate(-axis[0], -axis[1], -axis[2]);
        float[] out = rest.clone();
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (int i = 0; i + 7 < out.length; i += 8) {
            turn.transformPosition(rest[i], rest[i + 1], rest[i + 2], p);
            turn.transformDirection(rest[i + 5], rest[i + 6], rest[i + 7], n);
            out[i] = p.x;
            out[i + 1] = p.y;
            out[i + 2] = p.z;
            out[i + 5] = n.x;
            out[i + 6] = n.y;
            out[i + 7] = n.z;
        }
        return out;
    }

    static float[] join(List<float[]> parts) {
        int size = parts.stream().mapToInt(part -> part.length).sum();
        float[] out = new float[size];
        int at = 0;
        for (float[] part : parts) {
            System.arraycopy(part, 0, out, at, part.length);
            at += part.length;
        }
        return out;
    }

    // Within float noise (the turned normals and positions come out a few thousandths off the ones built in place).
    private static final float CLOSE = 0.005F;

    // Null when both lists hold the same quads (in any order, each value within CLOSE), else what differs.
    static String compare(float[] a, float[] b) {
        if (a.length != b.length) {
            return a.length / 32 + " quads against " + b.length / 32;
        }
        boolean[] matched = new boolean[b.length / 32];
        for (int q = 0; q + 31 < a.length; q += 32) {
            int found = -1;
            for (int r = 0; r < matched.length && found < 0; r++) {
                if (!matched[r] && close(a, q, b, r * 32)) {
                    found = r;
                }
            }
            if (found < 0) {
                return "quad " + quad(a, q) + " has no match";
            }
            matched[found] = true;
        }
        return null;
    }

    private static boolean close(float[] a, int q, float[] b, int r) {
        for (int i = 0; i < 32; i++) {
            if (Math.abs(a[q + i] - b[r + i]) > CLOSE) {
                return false;
            }
        }
        return true;
    }

    private static String quad(float[] data, int q) {
        StringBuilder s = new StringBuilder();
        for (int i = q; i < q + 32; i++) {
            s.append(String.format(Locale.ROOT, "%.3f,", data[i]));
        }
        return s.toString();
    }
}
