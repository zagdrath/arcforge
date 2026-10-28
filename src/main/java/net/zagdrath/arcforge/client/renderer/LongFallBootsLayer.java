/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */
package net.zagdrath.arcforge.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.item.tool.LongFallBootsItem;

/** Solid boot uppers, reinforced toes, side dampers and stepped heel springs on a 64x64 unwrap. */
public class LongFallBootsLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private final ModelPart standard = model(false);
    private final ModelPart armored = model(true);

    public LongFallBootsLayer(RenderLayerParent<S, M> parent) { super(parent); }

    private static ModelPart model(boolean armor) {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("sole", CubeListBuilder.create().texOffs(0, 0).addBox(-3, 10, -5, 6, 2, 8), PartPose.ZERO);
        root.addOrReplaceChild("toe", CubeListBuilder.create().texOffs(0, 12).addBox(-3, 8, -5, 6, 2, 4), PartPose.ZERO);
        root.addOrReplaceChild("upper", CubeListBuilder.create().texOffs(0, 20).addBox(-2.5F, 4, -2.5F, 5, 6, 5), PartPose.ZERO);
        root.addOrReplaceChild("cuff", CubeListBuilder.create().texOffs(0, 32).addBox(-3, 2, -3, 6, 2, 6), PartPose.ZERO);
        root.addOrReplaceChild("dampers", CubeListBuilder.create().texOffs(30, 0)
                .addBox(-4, 4, 1, 1, 6, 1).addBox(3, 4, 1, 1, 6, 1), PartPose.ZERO);
        root.addOrReplaceChild("spring_upper", CubeListBuilder.create().texOffs(36, 0).addBox(-1, 2, 3, 2, 3, 1), PartPose.ZERO);
        root.addOrReplaceChild("spring_mid", CubeListBuilder.create().texOffs(42, 0).addBox(-1, 5, 4, 2, 3, 1), PartPose.ZERO);
        root.addOrReplaceChild("spring_lower", CubeListBuilder.create().texOffs(36, 0).addBox(-1, 8, 5, 2, 3, 1), PartPose.ZERO);
        root.addOrReplaceChild("spring_heel", CubeListBuilder.create().texOffs(48, 0).addBox(-1, 10, 3, 2, 2, 2), PartPose.ZERO);
        root.addOrReplaceChild("spring_foot", CubeListBuilder.create().texOffs(36, 8).addBox(-1, 11, 1, 2, 1, 4), PartPose.ZERO);
        if (armor) root.addOrReplaceChild("shin_guard", CubeListBuilder.create().texOffs(0, 42).addBox(-3, 4, -3.5F, 6, 4, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64).bakeRoot();
    }

    @Override
    public void submit(PoseStack poses, SubmitNodeCollector collector, int light, S state, float yaw, float pitch) {
        if (state.isInvisible || !(state.feetEquipment.getItem() instanceof LongFallBootsItem boots)) return;
        ModelPart root = boots.armored() ? armored : standard;
        var texture = Identifier.fromNamespaceAndPath("arcforge", "textures/entity/equipment/long_fall/" + (boots.armored() ? "armored" : "standard") + ".png");
        for (ModelPart leg : new ModelPart[] {getParentModel().rightLeg, getParentModel().leftLeg}) {
            poses.pushPose();
            leg.translateAndRotate(poses);
            collector.submitModelPart(root, poses, RenderTypes.entityCutout(texture), light, OverlayTexture.NO_OVERLAY, null);
            poses.popPose();
        }
    }
}
