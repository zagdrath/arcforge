/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;

// Looking at any block of a formed Steam Turbine Array outlines the whole array as one box, in the vanilla outline's
// colour and width, instead of the one block: its blocks are picked as full blocks, while they collide as the model
// (see TurbineHitbox), so the array is one target.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class TurbineOutline {
    private TurbineOutline() {}

    @SubscribeEvent
    static void onExtractOutline(ExtractBlockOutlineRenderStateEvent event) {
        ShellStructure.Shell shell = shellAt(event.getLevel(), event.getBlockPos());
        if (shell == null) {
            return;
        }
        BlockPos min = shell.min(), max = shell.max();
        VoxelShape box = Shapes.box(0.0, 0.0, 0.0, max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
        boolean highContrast = event.isHighContrast();
        event.addCustomRenderer((state, collector, poseStack, levelState) -> {
            var gameRenderer = Minecraft.getInstance().gameRenderer;
            RenderType type = highContrast ? RenderTypes.linesDepthBias()
                    : gameRenderer.useImprovedTransparency() ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent();
            int color = highContrast ? -11010079 : ARGB.black(102);
            float width = gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
            Vec3 camera = levelState.cameraRenderState.pos;
            poseStack.pushPose();
            poseStack.translate(min.getX() - camera.x, min.getY() - camera.y, min.getZ() - camera.z);
            collector.submitShapeOutline(poseStack, box, type, color, width, state.isTranslucent());
            poseStack.popPose();
            // In place of the block's own outline.
            return true;
        });
    }

    // The shell of the formed Steam Turbine Array the block at pos is a casing or window of.
    private static ShellStructure.@Nullable Shell shellAt(BlockGetter level, BlockPos pos) {
        ShellMultiblockBlockEntity master = level.getBlockEntity(pos) instanceof ShellMultiblockBlockEntity part
                ? part.getMaster()
                : PressureGlassBlock.findMaster(level, pos);
        return master instanceof SteamTurbineArrayBlockEntity turbine ? turbine.getShell() : null;
    }
}
