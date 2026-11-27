/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer.item;

import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.storage.ReservoirKind;
import net.zagdrath.arcforge.client.ConduitTints;
import net.zagdrath.arcforge.client.renderer.ArcforgeRenderTypes;
import net.zagdrath.arcforge.client.renderer.blockentity.FluidBoxes;
import net.zagdrath.arcforge.client.renderer.blockentity.ReservoirRenderer;
import net.zagdrath.arcforge.registry.ModDataComponents;

// Draws the share a Fluid or Gas Reservoir item carries inside its frame, matching a lone placed block (see
// ReservoirRenderer): a liquid to its level, a gas filling the block and as opaque as it is full.
// Used as part of a composite item model, with the Reservoir's own block model.
public class ReservoirContentsRenderer implements SpecialModelRenderer<FluidStack> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "reservoir_contents");

    private final ReservoirKind kind;

    public ReservoirContentsRenderer(ReservoirKind kind) {
        this.kind = kind;
    }

    @Override
    public @Nullable FluidStack extractArgument(ItemStack stack) {
        SimpleFluidContent content = stack.get(ModDataComponents.FLUID_CONTENTS.get());
        return content == null || content.isEmpty() ? null : content.copy();
    }

    @Override
    public void submit(@Nullable FluidStack fluid, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
            boolean hasFoil, int outlineColor) {
        int capacity = kind.capacity();
        if (fluid == null || fluid.isEmpty() || capacity <= 0) {
            return;
        }
        float share = Math.min(1.0F, fluid.getAmount() / (float) capacity);
        int light = LightCoordsUtil.lightCoordsWithEmission(lightCoords, fluid.getFluidType().getLightLevel(fluid));
        float min = ReservoirRenderer.INSET, max = 1.0F - ReservoirRenderer.INSET;
        TextureAtlasSprite sprite;
        int color;
        float top;
        if (kind == ReservoirKind.GAS) {
            sprite = ReservoirRenderer.gasSprite();
            color = ReservoirRenderer.gasColor(ConduitTints.gasColor(fluid), share);
            top = max;
        } else {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
            sprite = model.stillMaterial().sprite();
            color = (model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(fluid) : -1) | 0xFF000000;
            top = Math.max(ReservoirRenderer.surface(fluid.getAmount(), capacity), min + 1.0F / 64.0F);
        }
        collector.submitCustomGeometry(poseStack, ArcforgeRenderTypes.fluid(sprite, color),
                (pose, buffer) -> FluidBoxes.box(pose, buffer, sprite, color, light, min, min, min, max, top, max));
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0.0F, 0.0F, 0.0F));
        output.accept(new Vector3f(1.0F, 1.0F, 1.0F));
    }

    public record Unbaked(boolean gas) implements SpecialModelRenderer.Unbaked<FluidStack> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("gas", false).forGetter(Unbaked::gas)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<FluidStack> bake(SpecialModelRenderer.BakingContext context) {
            return new ReservoirContentsRenderer(gas ? ReservoirKind.GAS : ReservoirKind.FLUID);
        }
    }
}
