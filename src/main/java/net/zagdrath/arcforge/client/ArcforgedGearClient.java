/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.renderer.ArcforgedGlowLayer;
import net.zagdrath.arcforge.item.tool.ArcforgedGear;
import net.zagdrath.arcforge.registry.ModItems;

// Arcforged tools and armour on the client: the charge bar over the durability bar in every slot (ChargeBar), and the
// glowing seams of charged worn armour (ArcforgedGlowLayer) on everything that wears vanilla's armour layer. The
// inlay on the item sprites lights up through the item models (items/arcforged_*.json: the arcforge:energy component).
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class ArcforgedGearClient {
    private ArcforgedGearClient() {}

    // The charge: a 1 px bar in the Arcforged cyan, one row above where the durability bar goes, the empty part black
    // (as the durability bar draws its own).
    public static final class ChargeBar implements IItemDecorator {
        @Override
        public boolean render(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
            if (!ArcforgedGear.enabled()) {
                return false;
            }
            int width = ArcforgedGear.barWidth(stack);
            int row = y + 12;
            graphics.fill(x + 2 + width, row, x + 15, row + 1, 0xFF000000);
            graphics.fill(x + 2, row, x + 2 + width, row + 1, 0xFF000000 | ArcforgedGear.BAR_COLOR);
            return false;
        }
    }

    @SubscribeEvent
    static void registerDecorators(RegisterItemDecorationsEvent event) {
        ChargeBar bar = new ChargeBar();
        for (var item : ModItems.arcforgedGear()) {
            event.register(item.get(), bar);
        }
    }

    // The glow layer goes beside vanilla's armour layer: on players (both skin models) and every entity whose renderer
    // has exactly vanilla's HumanoidArmorLayer (armor stands, zombies, skeletons, piglins and modded humanoids).
    @SubscribeEvent
    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType skin : event.getSkins()) {
            AvatarRenderer<?> renderer = event.getPlayerRenderer(skin);
            if (renderer != null) {
                addGlow(renderer);
            }
        }
        for (EntityType<?> type : event.getEntityTypes()) {
            if (event.getRenderer(type) instanceof LivingEntityRenderer<?, ?, ?> renderer) {
                addGlow(renderer);
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static void addGlow(LivingEntityRenderer<?, ?, ?> renderer) {
        for (RenderLayer<?, ?> layer : renderer.layers) {
            if (layer != null && layer.getClass() == HumanoidArmorLayer.class) {
                ((LivingEntityRenderer) renderer).addLayer(new ArcforgedGlowLayer((LivingEntityRenderer) renderer, (HumanoidArmorLayer) layer));
                return;
            }
        }
    }
}
