/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.zagdrath.arcforge.client.renderer.ChunkMeshes;
import net.zagdrath.arcforge.client.renderer.StaticMeshes;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.PortFaces;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.steam.ModelMesh;

// Drawing a ModelMesh model's float data, and finding the ports on a structure's outer faces for its nozzles: what
// every large-model renderer needs, kept here so a renderer needn't lean on another machine's (the same as
// PlantRenderer's, which the oil plants use).
public final class MeshRendering {
    private MeshRendering() {}

    // The ports on a structure's outer faces (its parts, inside telling which blocks are its own), in the model's frame
    // from min.
    public static List<ModelMesh.Port> ports(Level level, Iterable<BlockPos> parts, Predicate<BlockPos> inside, BlockPos min, ModelFrame frame) {
        List<ModelMesh.Port> ports = new ArrayList<>();
        for (BlockPos pos : parts) {
            PortFaces faces = MultiblockPorts.faces(level, pos);
            if (faces.isEmpty()) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PortHolder holder) || !holder.holdsPorts(state)) {
                continue;
            }
            int[] block = frame.frameBlock(pos.getX() - min.getX(), pos.getY() - min.getY(), pos.getZ() - min.getZ());
            for (Direction face : Direction.values()) {
                if (faces.get(face) == SideMode.NONE || inside.test(pos.relative(face))) {
                    continue;
                }
                int[] n = frame.frameNormal(face.getStepX(), face.getStepY(), face.getStepZ());
                ports.add(new ModelMesh.Port(block[2], block[1], block[0], n[0], n[1], n[2]));
            }
        }
        return ports;
    }

    // The slots a model's still lists are baked in (see bake): its solid parts and its glass.
    public static final int SOLID = 0, GLASS = 1;

    // A still list (built once, not each frame) drawn as emit draws it, from its copy on the GPU (StaticMeshes).
    public static void submitStill(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType, float[] data, TextureAtlasSprite sheet,
            int light) {
        StaticMeshes.submit(collector, poseStack, renderType, data, sheet, light, (pose, buffer) -> emit(pose, buffer, data, sheet, light));
    }

    // A still list into the chunk mesh at origin (see ChunkMeshes), drawn as emit draws it; baked again only when the list,
    // its sheet or its light changes. valid: whether the bake still stands (the structure is still formed as it was).
    public static void bake(BlockEntity owner, int slot, BlockPos origin, ChunkSectionLayer layer, float[] data, TextureAtlasSprite sheet, int light,
            BooleanSupplier valid) {
        ChunkMeshes.bake(owner, slot, origin, layer, new Baked(data, sheet), light, valid, (pose, buffer) -> emit(pose, buffer, data, sheet, light));
    }

    // What a bake was made from: the list, by identity, on its sheet.
    record Baked(float[] data, TextureAtlasSprite sheet) {}

    // A moving part (data, built once at rest) drawn turned by transform (blocks, in the pose's frame), from its copy on
    // the GPU: each frame only the transform changes. It looks as the part built turned and drawn by emit would.
    public static void submitMoving(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType, float[] data, TextureAtlasSprite sheet,
            int light, Matrix4f transform) {
        StaticMeshes.submitMoving(collector, poseStack, renderType, data, sheet, light, transform, (pose, buffer) -> emitRest(pose, buffer, data, sheet, light),
                (pose, buffer) -> emitTurned(pose, buffer, data, sheet, light, transform));
    }

    // A part turned about an upright axis (a door, an agitator), shaded by its faces as they stand at rest, as it was
    // when the pose turned it: from its GPU copy, each frame only the transform changes.
    public static void submitTurnedAboutY(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType, float[] data, TextureAtlasSprite sheet,
            int light, Matrix4f transform) {
        StaticMeshes.submitMoving(collector, poseStack, renderType, data, sheet, light, transform, (pose, buffer) -> emit(pose, buffer, data, sheet, light),
                (pose, buffer) -> {
                    Vector3f p = new Vector3f();
                    for (int i = 0; i + 7 < data.length; i += 8) {
                        transform.transformPosition(data[i], data[i + 1], data[i + 2], p);
                        int shade = shade(data[i + 5], data[i + 6], data[i + 7]);
                        buffer.addVertex(pose, p.x, p.y, p.z)
                                .setColor(0xFF000000 | shade << 16 | shade << 8 | shade)
                                .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                                .setOverlay(OverlayTexture.NO_OVERLAY)
                                .setLight(light)
                                .setNormal(pose, 0.0F, 1.0F, 0.0F);
                    }
                });
    }

    // The turn of a rotor built at rest about its axis (ModelMesh.rotorAxis: a point, then the signed axis) by degrees.
    public static Matrix4f turn(float[] axis, float degrees) {
        return new Matrix4f().translate(axis[0], axis[1], axis[2])
                .rotate((float) Math.toRadians(degrees), axis[3], axis[4], axis[5])
                .translate(-axis[0], -axis[1], -axis[2]);
    }

    // A box round the lists (blocks from origin), in the world: what a renderer that draws them needs in view.
    public static AABB bounds(BlockPos origin, float[]... lists) {
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        for (float[] data : lists) {
            for (int i = 0; i + 7 < data.length; i += 8) {
                x0 = Math.min(x0, data[i]);
                x1 = Math.max(x1, data[i]);
                y0 = Math.min(y0, data[i + 1]);
                y1 = Math.max(y1, data[i + 1]);
                z0 = Math.min(z0, data[i + 2]);
                z1 = Math.max(z1, data[i + 2]);
            }
        }
        if (x0 > x1) {
            return new AABB(origin);
        }
        return new AABB(origin.getX() + x0, origin.getY() + y0, origin.getZ() + z0, origin.getX() + x1, origin.getY() + y1, origin.getZ() + z1);
    }

    // Each face shaded as a block face turned its way is, its normal straight up.
    public static void emit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet, int light) {
        for (int i = 0; i + 7 < data.length; i += 8) {
            float nx = data[i + 5], ny = data[i + 6], nz = data[i + 7];
            int shade = shade(nx, ny, nz);
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(0xFF000000 | shade << 16 | shade << 8 | shade)
                    .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0.0F, 1.0F, 0.0F);
        }
    }

    // A block face's shade (0-255) for a face turned along the normal.
    private static int shade(float nx, float ny, float nz) {
        return Math.round(255.0F * (nx * nx * 0.6F + ny * ny * (ny > 0.0F ? 1.0F : 0.5F) + nz * nz * 0.8F));
    }

    // A moving part at rest for its GPU copy: white, its real normals (core/moving_mesh shades it as it turns).
    private static void emitRest(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet, int light) {
        for (int i = 0; i + 7 < data.length; i += 8) {
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(0xFFFFFFFF)
                    .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
        }
    }

    // A moving part turned on the CPU and drawn as emit draws a still one, for when it can't be drawn from the GPU.
    private static void emitTurned(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet, int light, Matrix4f transform) {
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (int i = 0; i + 7 < data.length; i += 8) {
            transform.transformPosition(data[i], data[i + 1], data[i + 2], p);
            transform.transformDirection(data[i + 5], data[i + 6], data[i + 7], n);
            int shade = shade(n.x, n.y, n.z);
            buffer.addVertex(pose, p.x, p.y, p.z)
                    .setColor(0xFF000000 | shade << 16 | shade << 8 | shade)
                    .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0.0F, 1.0F, 0.0F);
        }
    }
}
