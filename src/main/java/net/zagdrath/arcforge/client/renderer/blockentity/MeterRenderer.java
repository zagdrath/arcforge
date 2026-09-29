/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.blockentity;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.arcforge.block.logistics.MeterBlock;
import net.zagdrath.arcforge.blockentity.logistics.MeterBlockEntity;

// A Meter's front screen: the smoothed rate in two lines on the LCD (x3..12, y3..10 px of the front face), the value
// compact ("850", "12.5k", "1.2M") over its unit, at sign-text scale, in status cyan at full brightness.
public class MeterRenderer implements BlockEntityRenderer<MeterBlockEntity, MeterRenderer.State> {
    private static final int COLOR = 0xFF5FD4C4;
    // Sign text: 1/96 of a block per font pixel.
    private static final float SCALE = 1.0F / 96.0F;
    // The LCD's centre on the front face, in px from its top-left.
    private static final float LCD_X = 8.0F, LCD_Y = 7.0F;
    private static final int LINE_HEIGHT = 9;

    public static class State extends BlockEntityRenderState {
        public Direction facing = Direction.NORTH;
        public String value = "";
        public @Nullable Component unit;
    }

    private final Font font;

    public MeterRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.font();
    }

    // "850", "12.5k", "65.5k", "1.2M".
    public static String compact(int value) {
        if (value < 1_000) {
            return Integer.toString(value);
        }
        if (value < 1_000_000) {
            return trim(String.format(Locale.ROOT, "%.1f", value / 1_000.0)) + "k";
        }
        return trim(String.format(Locale.ROOT, "%.1f", value / 1_000_000.0)) + "M";
    }

    private static String trim(String number) {
        return number.endsWith(".0") ? number.substring(0, number.length() - 2) : number;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public int getViewDistance() {
        return 16;
    }

    @Override
    public void extractRenderState(MeterBlockEntity meter, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(meter, state, partialTicks, cameraPosition, breakProgress);
        state.facing = meter.getBlockState().hasProperty(MeterBlock.FACING) ? meter.getBlockState().getValue(MeterBlock.FACING) : Direction.NORTH;
        state.value = compact(meter.getRate());
        state.unit = meter.getKind().unit();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.unit == null) {
            return;
        }
        poseStack.pushPose();
        // As a wall sign: turned so local +Z points out of the front face, then just in front of it.
        poseStack.translate(0.5F, 0.5F, 0.5F);
        poseStack.rotate(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        poseStack.translate((LCD_X - 8.0F) / 16.0F, (8.0F - LCD_Y) / 16.0F, 0.5F + 0.002F);
        poseStack.scale(SCALE, -SCALE, SCALE);
        FormattedCharSequence value = Component.literal(state.value).getVisualOrderText();
        FormattedCharSequence unit = state.unit.getVisualOrderText();
        collector.submitText(poseStack, -font.width(value) / 2.0F, -LINE_HEIGHT, value, false, Font.DisplayMode.POLYGON_OFFSET,
                LightCoordsUtil.FULL_BRIGHT, COLOR, 0, 0);
        collector.submitText(poseStack, -font.width(unit) / 2.0F, 1, unit, false, Font.DisplayMode.POLYGON_OFFSET,
                LightCoordsUtil.FULL_BRIGHT, COLOR, 0, 0);
        poseStack.popPose();
    }
}
