/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.JetpackItem;

// The pack on a Jetpack wearer's back: a backplate, two tanks with the tier band and two nozzles, moving with the
// body. Drawn for players and armor stands; a plated pack sits a little further out to clear the armour.
public class JetpackLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath(Arcforge.MODID, "jetpack"), "main");

    private final ModelPart root;

    public JetpackLayer(RenderLayerParent<S, M> parent, EntityModelSet models) {
        super(parent);
        this.root = models.bakeLayer(LAYER);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("backplate", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, 1.0F, 2.0F, 8, 10, 1), PartPose.ZERO);
        root.addOrReplaceChild("tank_left", CubeListBuilder.create().texOffs(0, 11).addBox(-4.5F, 0.0F, 3.0F, 4, 10, 4), PartPose.ZERO);
        root.addOrReplaceChild("tank_right", CubeListBuilder.create().texOffs(16, 11).addBox(0.5F, 0.0F, 3.0F, 4, 10, 4), PartPose.ZERO);
        root.addOrReplaceChild("nozzle_left", CubeListBuilder.create().texOffs(18, 0).addBox(-3.5F, 10.0F, 4.0F, 2, 2, 2), PartPose.ZERO);
        root.addOrReplaceChild("nozzle_right", CubeListBuilder.create().texOffs(18, 4).addBox(1.5F, 10.0F, 4.0F, 2, 2, 2), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
        if (state.isInvisible || !(state.chestEquipment.getItem() instanceof JetpackItem jetpack)) {
            return;
        }
        ModelPart body = getParentModel().body;
        root.x = body.x;
        root.y = body.y;
        root.z = body.z + (JetpackItem.isPlated(state.chestEquipment) ? 0.5F : 0.0F);
        root.xRot = body.xRot;
        root.yRot = body.yRot;
        root.zRot = body.zRot;
        Identifier texture = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/entity/jetpack/" + jetpack.tier().getSerializedName() + ".png");
        collector.submitModelPart(root, poseStack, RenderTypes.entityCutout(texture), lightCoords, OverlayTexture.NO_OVERLAY, null);
    }
}
