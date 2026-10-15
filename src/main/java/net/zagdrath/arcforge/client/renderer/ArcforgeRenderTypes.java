/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer;

import java.util.function.Function;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.zagdrath.arcforge.Arcforge;

// Arcforge's own render types.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class ArcforgeRenderTypes {
    // Vanilla's entity_translucent without the depth write, for the fluids, gases and flames block entities draw behind
    // glass (tanks, Reservoirs, conduits, the arrays' windows). Block entities draw before the translucent terrain, so
    // contents that wrote depth would hide every translucent block behind them (glass conduits, glass, water), while
    // still showing what other block entities draw.
    private static final RenderPipeline VOLUME_TRANSLUCENT = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Arcforge.MODID, "pipeline/volume_translucent"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
            .build();

    private static final Function<Identifier, RenderType> VOLUME = Util.memoize(texture -> RenderType.create("arcforge_volume_translucent",
            RenderSetup.builder(VOLUME_TRANSLUCENT)
                    .setOitPipelines(RenderPipelines.OIT_ENTITY)
                    .withTexture("Sampler0", texture)
                    .useLightmap()
                    .useOverlay()
                    .sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.NONE)
                    .createRenderSetup()));

    private ArcforgeRenderTypes() {}

    public static RenderType volumeTranslucent(Identifier texture) {
        return VOLUME.apply(texture);
    }

    @SubscribeEvent
    static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(VOLUME_TRANSLUCENT);
    }
}
