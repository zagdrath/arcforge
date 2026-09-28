/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.AreaToolItem;

// Outlines the other blocks a Steel Hammer or Excavator will take with the one you're looking at, in the vanilla
// outline's colour and width. Not while sneaking, when it only breaks the one.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class AreaToolHighlight {
    private record Target(BlockPos pos, VoxelShape shape) {}

    private AreaToolHighlight() {}

    @SubscribeEvent
    static void onExtractOutline(ExtractBlockOutlineRenderStateEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || player.isShiftKeyDown()) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof AreaToolItem)) {
            return;
        }
        var level = event.getLevel();
        List<Target> targets = AreaToolItem.targets(level, event.getBlockPos(), event.getBlockState(),
                        event.getHitResult().getDirection().getAxis(), tool).stream()
                .map(pos -> new Target(pos, level.getBlockState(pos).getShape(level, pos, event.getCollisionContext())))
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        boolean highContrast = event.isHighContrast();
        event.addCustomRenderer((state, collector, poseStack, levelState) -> {
            var gameRenderer = Minecraft.getInstance().gameRenderer;
            RenderType type = highContrast ? RenderTypes.linesDepthBias()
                    : gameRenderer.useImprovedTransparency() ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent();
            int color = highContrast ? -11010079 : ARGB.black(102);
            float width = gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
            Vec3 camera = levelState.cameraRenderState.pos;
            for (Target target : targets) {
                poseStack.pushPose();
                poseStack.translate(target.pos().getX() - camera.x, target.pos().getY() - camera.y, target.pos().getZ() - camera.z);
                collector.submitShapeOutline(poseStack, target.shape(), type, color, width, state.isTranslucent());
                poseStack.popPose();
            }
            // Vanilla still draws the centre's own outline.
            return false;
        });
    }
}
