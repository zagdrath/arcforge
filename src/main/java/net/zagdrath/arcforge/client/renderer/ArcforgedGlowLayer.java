/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.NeoForgeRenderTypes;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.ArcforgedGear;
import net.zagdrath.arcforge.item.tool.ArcforgedItem;

// The glowing Arcite seams of worn Arcforged armour: over a charged helmet (its visor) and chestplate (the chest and
// shoulders), the seams drawn full-bright and translucent (NeoForge's unlit translucent entity type at full-bright light,
// so the texture's alpha blends the lit seam into the plate and its faint spill either side, the same in daylight and
// in the dark) on the same armour models the vanilla armour layer uses, so they sit exactly on the armour's own seam
// pixels (textures/entity/arcforged_glow.png, the humanoid layer's UV layout with only the seams in it). Uncharged, the armour's own dull cyan seams show and nothing is drawn here. The
// armour itself, its trims and its glint are the vanilla layer's (equipment/arcforged.json).
public class ArcforgedGlowLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>, A extends HumanoidModel<S>> extends RenderLayer<S, M> {
    public static final Identifier GLOW = Identifier.fromNamespaceAndPath(Arcforge.MODID, "textures/entity/arcforged_glow.png");

    private final ArmorModelSet<A> modelSet;
    private final ArmorModelSet<A> babyModelSet;

    public ArcforgedGlowLayer(RenderLayerParent<S, M> parent, HumanoidArmorLayer<S, M, A> armorLayer) {
        super(parent);
        this.modelSet = armorLayer.modelSet;
        this.babyModelSet = armorLayer.babyModelSet;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        glow(poseStack, collector, state, state.headEquipment, EquipmentSlot.HEAD);
        glow(poseStack, collector, state, state.chestEquipment, EquipmentSlot.CHEST);
    }

    private void glow(PoseStack poseStack, SubmitNodeCollector collector, S state, ItemStack stack, EquipmentSlot slot) {
        if (!(stack.getItem() instanceof ArcforgedItem) || !ArcforgedGear.charged(stack)) {
            return;
        }
        A model = (state.isBaby ? babyModelSet : modelSet).get(slot);
        collector.order(1).submitModel(model, state, poseStack, NeoForgeRenderTypes.getEntityUnlitTranslucent(GLOW), LightCoordsUtil.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, -1, null, state.outlineColor);
    }
}
