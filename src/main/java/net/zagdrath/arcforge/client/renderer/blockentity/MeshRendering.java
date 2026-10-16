/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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

    // A still list (built once, not each frame) drawn as emit draws it, from its copy on the GPU (StaticMeshes).
    public static void submitStill(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType, float[] data, TextureAtlasSprite sheet,
            int light) {
        StaticMeshes.submit(collector, poseStack, renderType, data, sheet, light, (pose, buffer) -> emit(pose, buffer, data, sheet, light));
    }

    // Each face shaded as a block face turned its way is, its normal straight up.
    public static void emit(PoseStack.Pose pose, VertexConsumer buffer, float[] data, TextureAtlasSprite sheet, int light) {
        for (int i = 0; i + 7 < data.length; i += 8) {
            float nx = data[i + 5], ny = data[i + 6], nz = data[i + 7];
            int shade = Math.round(255.0F * (nx * nx * 0.6F + ny * ny * (ny > 0.0F ? 1.0F : 0.5F) + nz * nz * 0.8F));
            buffer.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(0xFF000000 | shade << 16 | shade << 8 | shade)
                    .setUv(sheet.getU(data[i + 3]), sheet.getV(data[i + 4]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0.0F, 1.0F, 0.0F);
        }
    }
}
