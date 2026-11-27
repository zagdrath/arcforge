/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */
package net.zagdrath.arcforge.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.arcforge.item.tool.LongFallBootsItem;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

// Long Fall Boots in their Curios slot, drawn as worn ones are (LongFallBootsLayer), unless a pair is also on the feet.
public class CurioLongFallBootsRenderer implements ICurioRenderer {
    @Override
    public <S extends LivingEntityRenderState, M extends EntityModel<? super S>> void render(ItemStack stack,
            SlotContext slotContext, PoseStack poseStack, SubmitNodeCollector collector, int light, S state,
            RenderLayerParent<S, M> parent, EntityRendererProvider.Context context, float yaw, float pitch) {
        if (state.isInvisible || !(stack.getItem() instanceof LongFallBootsItem boots) || !(parent.getModel() instanceof HumanoidModel<?> model)
                || state instanceof HumanoidRenderState humanoid && humanoid.feetEquipment.getItem() instanceof LongFallBootsItem) {
            return;
        }
        LongFallBootsLayer.submitBoots(boots, model, poseStack, collector, light);
    }
}
