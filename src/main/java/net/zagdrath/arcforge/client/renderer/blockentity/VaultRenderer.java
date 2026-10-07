/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.block.storage.VaultBlock;
import net.zagdrath.arcforge.blockentity.storage.VaultBlockEntity;
import net.zagdrath.arcforge.storage.StorageCounts;

// Draws a Vault's front display: the stored item, flat, in the dark window, and its count ("12.4k") under it, lit by
// the light in front of the vault (the vault itself is a solid block, so its own light would be dark). Face
// positions are in pixels from the front's top-left corner (see previews/vault_front_render_mock.png).
public class VaultRenderer implements BlockEntityRenderer<VaultBlockEntity, VaultRenderState> {
    private static final float PX = 1.0F / 16.0F;
    private static final float ITEM_X = 8.0F, ITEM_Y = 6.5F, ITEM_SIZE = 7.0F;
    private static final float COUNT_X = 8.0F, COUNT_Y = 11.5F;
    // A font pixel is 1/64 of a block (1/4 px); counts wider than this are shrunk to fit the window.
    private static final float FONT_SCALE = 1.0F / 64.0F, COUNT_MAX_WIDTH = 10.0F * PX;
    // Just in front of the face, so nothing z-fights with the window.
    private static final float FACE_OFFSET = 0.505F;
    private static final int COUNT_COLOR = 0xFFFFFFFF;
    // The ghost item of an empty, locked vault is drawn in near darkness.
    private static final int GHOST_LIGHT = LightCoordsUtil.pack(3, 3);

    private final ItemModelResolver itemModelResolver;
    private final Font font;

    public VaultRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
        this.font = context.font();
    }

    @Override
    public VaultRenderState createRenderState() {
        return new VaultRenderState();
    }

    // Past this the display is too small to read anyway.
    @Override
    public int getViewDistance() {
        return 24;
    }

    @Override
    public void extractRenderState(VaultBlockEntity vault, VaultRenderState state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(vault, state, partialTicks, cameraPosition, breakProgress);
        state.facing = vault.getBlockState().getValue(VaultBlock.FACING);
        state.frontLight = vault.getLevel() != null
                ? LevelRenderer.getLightCoords(vault.getLevel(), vault.getBlockPos().relative(state.facing))
                : LightCoordsUtil.FULL_BRIGHT;
        ItemStack template = vault.getTemplate();
        int amount = vault.getAmount();
        state.hasItem = !template.isEmpty() && (amount > 0 || vault.isLocked());
        state.ghost = amount == 0;
        if (state.hasItem) {
            itemModelResolver.updateForTopItem(state.item, template, ItemDisplayContext.GUI, vault.getLevel(), null, 0);
        } else {
            state.item.clear();
        }
        if (amount > 0) {
            state.count = Component.literal(StorageCounts.compact(amount)).getVisualOrderText();
            state.countWidth = font.width(state.count);
        } else {
            state.count = null;
        }
    }

    @Override
    public void submit(VaultRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.hasItem && state.count == null) {
            return;
        }
        poseStack.pushPose();
        // +Z out of the front, the face plane at z = 0 of this pose; x right and y up as seen from the front.
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        poseStack.translate(0.0F, 0.0F, FACE_OFFSET);

        if (state.hasItem) {
            poseStack.pushPose();
            poseStack.translate((ITEM_X - 8.0F) * PX, (8.0F - ITEM_Y) * PX, 0.0F);
            poseStack.scale(ITEM_SIZE * PX, ITEM_SIZE * PX, 0.01F);
            state.item.submit(poseStack, collector, state.ghost ? GHOST_LIGHT : state.frontLight, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }

        if (state.count != null) {
            float scale = FONT_SCALE;
            if (state.countWidth * scale > COUNT_MAX_WIDTH) {
                scale = COUNT_MAX_WIDTH / state.countWidth;
            }
            poseStack.pushPose();
            poseStack.translate((COUNT_X - 8.0F) * PX, (8.0F - COUNT_Y) * PX, 0.001F);
            // Font y runs down.
            poseStack.scale(scale, -scale, scale);
            collector.submitText(poseStack, -state.countWidth / 2.0F, -font.lineHeight / 2.0F + 1.0F, state.count, false,
                    Font.DisplayMode.POLYGON_OFFSET, state.frontLight, COUNT_COLOR, 0, 0);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
