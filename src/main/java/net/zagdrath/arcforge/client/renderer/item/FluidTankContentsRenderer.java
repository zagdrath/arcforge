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
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.renderer.blockentity.FluidBoxes;
import net.zagdrath.arcforge.client.renderer.blockentity.FluidTankRenderer;
import net.zagdrath.arcforge.registry.ModDataComponents;

// Draws the fluid a tank item carries inside its glass, matching the placed block (see FluidTankRenderer).
// Used as part of a composite item model, after the tank's own block model.
public class FluidTankContentsRenderer implements SpecialModelRenderer<FluidStack> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "fluid_tank_contents");

    private final int capacity;

    public FluidTankContentsRenderer(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public @Nullable FluidStack extractArgument(ItemStack stack) {
        SimpleFluidContent content = stack.get(ModDataComponents.FLUID_CONTENTS.get());
        return content == null || content.isEmpty() ? null : content.copy();
    }

    @Override
    public void submit(@Nullable FluidStack fluid, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
            boolean hasFoil, int outlineColor) {
        if (fluid == null || fluid.isEmpty() || capacity <= 0) {
            return;
        }
        var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
        TextureAtlasSprite sprite = model.stillMaterial().sprite();
        int color = (model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(fluid) : -1) | 0xFF000000;
        int light = LightCoordsUtil.lightCoordsWithEmission(lightCoords, fluid.getFluidType().getLightLevel(fluid));
        float[] box = FluidTankRenderer.fluidBox(Math.min(1.0F, fluid.getAmount() / (float) capacity), fluid.getFluidType().isLighterThanAir());
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(sprite.atlasLocation()),
                (pose, buffer) -> FluidBoxes.box(pose, buffer, sprite, color, light, box[0], box[1], box[2], box[3], box[4], box[5]));
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        float[] box = FluidTankRenderer.fluidBox(1.0F, false);
        output.accept(new Vector3f(box[0], box[1], box[2]));
        output.accept(new Vector3f(box[3], box[4], box[5]));
    }

    public record Unbaked(int capacity) implements SpecialModelRenderer.Unbaked<FluidStack> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Codec.INT.fieldOf("capacity").forGetter(Unbaked::capacity)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public SpecialModelRenderer<FluidStack> bake(SpecialModelRenderer.BakingContext context) {
            return new FluidTankContentsRenderer(capacity);
        }
    }
}
