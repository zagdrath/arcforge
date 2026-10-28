/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */
package net.zagdrath.arcforge.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.registry.ModItems;

// Full-length protective sleeves and rubber gloves/boots. Sample the original suit's flat yellow and visor
// pixels, preserving its exact palette and UV atlas while covering the skin left visible by vanilla armor.
public class HazmatLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("arcforge", "textures/entity/equipment/humanoid/hazmat.png");
    private static final int[][] FACES = { { 0, 3, 2, 1 }, { 4, 5, 6, 7 }, { 0, 4, 7, 3 }, { 1, 2, 6, 5 }, { 3, 7, 6, 2 }, { 0, 1, 5, 4 } };
    private static final int[][] NORMALS = { { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 }, { 1, 0, 0 }, { 0, 1, 0 }, { 0, -1, 0 } };

    public HazmatLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poses, SubmitNodeCollector collector, int light, S state, float yaw, float pitch) {
        if (state.isInvisible) {
            return;
        }
        M model = getParentModel();
        if (state.chestEquipment.is(ModItems.HAZMAT_CHESTPLATE.get())) {
            limb(poses, collector, model.rightArm, light, -3, false);
            limb(poses, collector, model.leftArm, light, -1, false);
        }
        if (state.feetEquipment.is(ModItems.HAZMAT_BOOTS.get())) {
            limb(poses, collector, model.rightLeg, light, -2, true);
            limb(poses, collector, model.leftLeg, light, -2, true);
        }
    }

    private static void limb(PoseStack poses, SubmitNodeCollector collector, ModelPart part, int light, float x, boolean boot) {
        poses.pushPose();
        part.translateAndRotate(poses);
        poses.scale(1 / 16.0F, 1 / 16.0F, 1 / 16.0F);
        collector.submitCustomGeometry(poses, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> {
            if (!boot) {
                box(pose, buffer, x - 0.6F, -2.6F, -2.6F, x + 4.6F, 7.1F, 2.6F, false, light);
            }
            box(pose, buffer, x - 0.65F, boot ? 5.5F : 7.0F, -2.65F, x + 4.65F, boot ? 12.65F : 10.65F, 2.65F, true, light);
        });
        poses.popPose();
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, boolean rubber, int light) {
        float[][] vertices = { { x0, y0, z0 }, { x1, y0, z0 }, { x1, y1, z0 }, { x0, y1, z0 },
                { x0, y0, z1 }, { x1, y0, z1 }, { x1, y1, z1 }, { x0, y1, z1 } };
        float u = (rubber ? 9.5F : 11.5F) / 64.0F, v = (rubber ? 11.5F : 2.5F) / 32.0F;
        for (int face = 0; face < FACES.length; face++) {
            int[] normal = NORMALS[face];
            for (int vertex : FACES[face]) {
                float[] at = vertices[vertex];
                buffer.addVertex(pose, at[0], at[1], at[2]).setColor(rubber ? 0xFF777777 : 0xFFFFFFFF)
                        .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                        .setNormal(pose, normal[0], normal[1], normal[2]);
            }
        }
    }
}
