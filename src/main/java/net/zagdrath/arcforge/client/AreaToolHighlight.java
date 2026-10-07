/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.item.tool.ArcToolItem;
import net.zagdrath.arcforge.item.tool.AreaToolItem;
import net.zagdrath.arcforge.item.tool.ModuleType;
import net.zagdrath.arcforge.tag.ModBlockTags;

// Outlines the other blocks a Steel Hammer or Excavator (not while sneaking, when it only breaks the one), or an Arc
// tool with its Area module on, will take with the one you're looking at, in the vanilla outline's colour and width.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class AreaToolHighlight {
    private record Target(BlockPos pos, VoxelShape shape) {}

    private AreaToolHighlight() {}

    // Vein mining or felling takes over from Area on the blocks they apply to (see ArcToolEvents).
    private static boolean veinOrFelling(ItemStack tool, ArcToolItem item, BlockState state) {
        if (item.kind() == ArcToolItem.Kind.SAW) {
            return ArcToolItem.isFelling(tool) && state.is(ModBlockTags.FELLABLE);
        }
        return ArcToolItem.modules(tool).isOn(ModuleType.VEIN) && state.is(ModBlockTags.VEIN_MINEABLE);
    }

    @SubscribeEvent
    static void onExtractOutline(ExtractBlockOutlineRenderStateEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        int radius;
        if (tool.getItem() instanceof AreaToolItem && !player.isShiftKeyDown()) {
            radius = 1;
        } else if (tool.getItem() instanceof ArcToolItem arcTool && ArcToolItem.modules(tool).isOn(ModuleType.AREA) && !veinOrFelling(tool, arcTool, event.getBlockState())) {
            radius = arcTool.areaRadius();
        } else {
            return;
        }
        var level = event.getLevel();
        List<Target> targets = AreaToolItem.targets(level, event.getBlockPos(), event.getBlockState(),
                        event.getHitResult().getDirection().getAxis(), tool, radius).stream()
                .map(pos -> new Target(pos, level.getBlockState(pos).getShape(level, pos, event.getCollisionContext())))
                .toList();
        if (targets.isEmpty()) {
            return;
        }
        boolean highContrast = event.isHighContrast();
        // Called in the opaque and the translucent pass; draws, as vanilla's outline does (LevelRenderer.renderBlockOutline),
        // in the one the centre block's own outline uses.
        event.addCustomRenderer((state, buffers, poseStack, translucentPass, levelState) -> {
            if (state.isTranslucent() != translucentPass) {
                return false;
            }
            int color = highContrast ? -11010079 : ARGB.black(102);
            float width = Minecraft.getInstance().gameRenderer.getGameRenderState().windowRenderState.appropriateLineWidth;
            Vec3 camera = levelState.cameraRenderState.pos;
            for (Target target : targets) {
                double x = target.pos().getX() - camera.x, y = target.pos().getY() - camera.y, z = target.pos().getZ() - camera.z;
                if (highContrast) {
                    ShapeRenderer.renderShape(poseStack, buffers.getBuffer(RenderTypes.secondaryBlockOutline()), target.shape(), x, y, z,
                            -16777216, 7.0F);
                }
                ShapeRenderer.renderShape(poseStack, buffers.getBuffer(RenderTypes.lines()), target.shape(), x, y, z, color, width);
            }
            // Vanilla still draws the centre's own outline.
            return false;
        });
    }
}
