/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.steam.SteamGrade;

// Colours the glowing bands of lit Pressurized and Thermodynamic Conduits (tint index 0 of their models):
// by the gas inside, or by how hot the heat inside is, from a dull red through orange and yellow to white.
// Unlit conduits keep their own colours.
public final class ConduitTints implements BlockTintSource {
    public static final ConduitTints INSTANCE = new ConduitTints();

    private static final int WHITE = 0xFFFFFFFF;
    // Tint index 0 on conduits without a lit state (item and fluid). They have nothing to glow, and no ACTIVE
    // property: the model loader reads every relevant property of a tint source, so they can't use INSTANCE.
    public static final BlockTintSource UNLIT = state -> WHITE;
    // Heat colours and the temperatures (°C) they are reached at; in between they blend.
    private static final int[] HEAT_CELSIUS = { 100, 500, 900, 1_200, 1_500 };
    private static final int[] HEAT_COLORS = { 0xFF9A2A18, 0xFFE0461A, 0xFFFF8A2A, 0xFFFFD35A, 0xFFFFF4D6 };

    private ConduitTints() {}

    @Override
    public int color(BlockState state) {
        return WHITE;
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (!state.hasProperty(ActiveConduitBlock.ACTIVE) || !state.getValue(ActiveConduitBlock.ACTIVE)
                || !(level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit)) {
            return WHITE;
        }
        return conduit.getConduitType() == ConduitType.THERMAL ? heatColor(conduit.getHeatTemperature()) : gasColor(conduit.getFluid());
    }

    @Override
    public Set<Property<?>> relevantProperties() {
        return Set.of(ActiveConduitBlock.ACTIVE);
    }

    public static int gasColor(FluidStack stack) {
        if (stack.isEmpty()) {
            return WHITE;
        }
        SteamGrade grade = SteamGrade.of(stack.getFluid());
        if (grade != null) {
            return grade.guiColor();
        }
        Fluid fluid = stack.getFluid();
        var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
        return model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(stack) | 0xFF000000 : WHITE;
    }

    static int heatColor(int celsius) {
        if (celsius <= HEAT_CELSIUS[0]) {
            return HEAT_COLORS[0];
        }
        for (int i = 1; i < HEAT_CELSIUS.length; i++) {
            if (celsius <= HEAT_CELSIUS[i]) {
                float t = (celsius - HEAT_CELSIUS[i - 1]) / (float) (HEAT_CELSIUS[i] - HEAT_CELSIUS[i - 1]);
                return blend(HEAT_COLORS[i - 1], HEAT_COLORS[i], t);
            }
        }
        return HEAT_COLORS[HEAT_COLORS.length - 1];
    }

    private static int blend(int from, int to, float t) {
        return 0xFF000000
                | (int) Mth.lerp(t, from >> 16 & 0xFF, to >> 16 & 0xFF) << 16
                | (int) Mth.lerp(t, from >> 8 & 0xFF, to >> 8 & 0xFF) << 8
                | (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
    }
}
