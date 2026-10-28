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

/** Open calf harnesses, side dampers and stepped heel springs, on a native 64x32 unwrap. */
public class LongFallBootsLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private final ModelPart standard = model(false);
    private final ModelPart armored = model(true);

    public LongFallBootsLayer(RenderLayerParent<S, M> parent) { super(parent); }

    private static ModelPart model(boolean armor) {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("sole", CubeListBuilder.create().texOffs(0, 0).addBox(-3, 11, -4, 6, 2, 8), PartPose.ZERO);
        root.addOrReplaceChild("toe", CubeListBuilder.create().texOffs(0, 10).addBox(-3, 8, -4, 6, 3, 3), PartPose.ZERO);
        root.addOrReplaceChild("ankle", CubeListBuilder.create().texOffs(0, 16).addBox(-3, 6, -3, 6, 2, 6), PartPose.ZERO);
        root.addOrReplaceChild("rails", CubeListBuilder.create().texOffs(28, 0)
                .addBox(-3, 1, 1, 1, 5, 2).addBox(2, 1, 1, 1, 5, 2), PartPose.ZERO);
        root.addOrReplaceChild("dampers", CubeListBuilder.create().texOffs(34, 0)
                .addBox(-4, 4, 1, 1, 6, 1).addBox(3, 4, 1, 1, 6, 1), PartPose.ZERO);
        root.addOrReplaceChild("spring_upper", CubeListBuilder.create().texOffs(40, 0).addBox(-1, 1, 3, 2, 3, 1), PartPose.ZERO);
        root.addOrReplaceChild("spring_mid", CubeListBuilder.create().texOffs(46, 0).addBox(-1, 4, 4, 2, 4, 1), PartPose.ZERO);
        root.addOrReplaceChild("spring_lower", CubeListBuilder.create().texOffs(40, 0).addBox(-1, 8, 5, 2, 3, 1), PartPose.ZERO);
        root.addOrReplaceChild("spring_heel", CubeListBuilder.create().texOffs(52, 0).addBox(-1, 11, 4, 2, 2, 2), PartPose.ZERO);
        root.addOrReplaceChild("spring_foot", CubeListBuilder.create().texOffs(40, 8).addBox(-1, 12, 1, 2, 1, 4), PartPose.ZERO);
        if (armor) root.addOrReplaceChild("shin_guard", CubeListBuilder.create().texOffs(0, 24).addBox(-3, 2, -3, 6, 4, 1), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32).bakeRoot();
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
