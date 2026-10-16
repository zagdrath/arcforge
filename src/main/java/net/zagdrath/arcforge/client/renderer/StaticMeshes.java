/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.FeatureRenderer;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterFeatureRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.submit.RenderPhaseKeys;
import net.zagdrath.arcforge.Arcforge;

// The large models' still parts kept on the GPU. A formed refinery tower is 5,000 to 13,000 quads; drawn through
// submitCustomGeometry, every vertex of every model went through a VertexConsumer (and was copied to the GPU again)
// every frame, which with a dozen models in view cost more than the rest of the frame. Here each still list is encoded
// once, the same way the renderer's own emit would have (its emitter, run with an identity pose), into a vertex buffer
// of its own, and each frame is one draw of it with the pose's offset in the shader (core/static_mesh: vanilla's entity
// shader with ModelOffset added), so the model looks exactly as before.
//
// A mesh is found by its data (the array, by identity), sprite, light and render type: a renderer that rebuilds its
// model hands over a new array and gets a new mesh, and a mesh nobody has drawn for EVICT_NANOS is freed. Anything this
// can't draw (a pose that turns or scales, a render type it has no pipeline for) goes through submitCustomGeometry as
// before.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class StaticMeshes {
    private static final long EVICT_NANOS = 5_000_000_000L, SWEEP_NANOS = 1_000_000_000L;
    private static final Vector4f WHITE = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final PoseStack.Pose IDENTITY_POSE = new PoseStack().last();

    // RenderPipelines.ENTITY_SOLID and ENTITY_CUTOUT with the static_mesh vertex shader.
    private static final RenderPipeline SOLID = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Arcforge.MODID, "pipeline/static_mesh_solid"))
            .withVertexShader(Identifier.fromNamespaceAndPath(Arcforge.MODID, "core/static_mesh"))
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();
    private static final RenderPipeline CUTOUT = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(Arcforge.MODID, "pipeline/static_mesh_cutout"))
            .withVertexShader(Identifier.fromNamespaceAndPath(Arcforge.MODID, "core/static_mesh"))
            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
            .withShaderDefine("PER_FACE_LIGHTING")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
            .withCull(false)
            .withColorTargetState(ColorTargetState.DEFAULT)
            .build();

    static final FeatureRendererType<Submit> TYPE = FeatureRendererType.create("arcforge:static_mesh");

    private record Key(float[] data, TextureAtlasSprite sprite, int light, RenderType renderType) {}

    private static final Map<Key, Mesh> MESHES = new HashMap<>();
    private static long lastSweep;

    private StaticMeshes() {}

    // Draws data (a renderer's still list, which emitter writes in sprite at light) under renderType at the pose's place:
    // from its GPU copy when it can, else through submitCustomGeometry. The emitter must write the same vertices each
    // time it's called for the same data, sprite and light.
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType, float[] data, TextureAtlasSprite sprite,
            int light, SubmitNodeCollector.CustomGeometryRenderer emitter) {
        if (data.length == 0) {
            return;
        }
        RenderPipeline pipeline = pipelineFor(renderType);
        Matrix4fc pose = poseStack.last().pose();
        if (pipeline == null || !translationOnly(pose)) {
            collector.submitCustomGeometry(poseStack, renderType, emitter);
            return;
        }
        Mesh mesh = MESHES.computeIfAbsent(new Key(data, sprite, light, renderType), key -> new Mesh(renderType, pipeline, emitter, data.length / 8));
        mesh.lastUsed = System.nanoTime();
        collector.submitSpecial(RenderPhaseKeys.SOLID, new Submit(mesh, new Vector3f(pose.m30(), pose.m31(), pose.m32())));
    }

    private static @Nullable RenderPipeline pipelineFor(RenderType renderType) {
        RenderPipeline pipeline = renderType.pipeline();
        return pipeline == RenderPipelines.ENTITY_SOLID ? SOLID : pipeline == RenderPipelines.ENTITY_CUTOUT ? CUTOUT : null;
    }

    // Whether the pose only moves (the large-model renderers only translate), so its normals are untouched and the
    // shader's offset is the whole of it.
    private static boolean translationOnly(Matrix4fc m) {
        return m.m00() == 1.0F && m.m11() == 1.0F && m.m22() == 1.0F && m.m33() == 1.0F
                && m.m01() == 0.0F && m.m02() == 0.0F && m.m03() == 0.0F
                && m.m10() == 0.0F && m.m12() == 0.0F && m.m13() == 0.0F
                && m.m20() == 0.0F && m.m21() == 0.0F && m.m23() == 0.0F;
    }

    // Frees the meshes nobody has drawn for a while (a model rebuilt, unloaded or out of view), checked once a second.
    private static void sweep() {
        long now = System.nanoTime();
        if (now - lastSweep < SWEEP_NANOS) {
            return;
        }
        lastSweep = now;
        for (Iterator<Mesh> it = MESHES.values().iterator(); it.hasNext();) {
            Mesh mesh = it.next();
            if (now - mesh.lastUsed > EVICT_NANOS) {
                mesh.close();
                it.remove();
            }
        }
    }

    private static void closeAll() {
        MESHES.values().forEach(Mesh::close);
        MESHES.clear();
    }

    // One still list's GPU copy, encoded and uploaded the first time it's drawn.
    private static final class Mesh {
        final RenderType renderType;
        final RenderPipeline pipeline;
        private SubmitNodeCollector.@Nullable CustomGeometryRenderer emitter;
        private final int expectedVertices;
        private @Nullable GpuBuffer buffer;
        private int vertexCount;
        long lastUsed;

        Mesh(RenderType renderType, RenderPipeline pipeline, SubmitNodeCollector.CustomGeometryRenderer emitter, int expectedVertices) {
            this.renderType = renderType;
            this.pipeline = pipeline;
            this.emitter = emitter;
            this.expectedVertices = expectedVertices;
        }

        // The vertex buffer, uploading it the first time (null when the list draws nothing).
        @Nullable GpuBuffer upload() {
            SubmitNodeCollector.CustomGeometryRenderer emitter = this.emitter;
            if (emitter == null) {
                return buffer;
            }
            this.emitter = null;
            int size = Math.max(256, expectedVertices * renderType.format().getVertexSize());
            try (ByteBufferBuilder bytes = new ByteBufferBuilder(size)) {
                BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, renderType.format());
                emitter.render(IDENTITY_POSE, builder);
                try (MeshData data = builder.build()) {
                    if (data != null) {
                        vertexCount = data.drawState().vertexCount();
                        buffer = RenderSystem.getDevice().createBuffer(() -> "Arcforge static mesh", GpuBuffer.USAGE_VERTEX, data.vertexBuffer());
                    }
                }
            }
            return buffer;
        }

        void close() {
            emitter = null;
            if (buffer != null) {
                buffer.close();
                buffer = null;
            }
        }
    }

    record Submit(Mesh mesh, Vector3f offset) implements SubmitNode {
        @Override
        public FeatureRendererType<Submit> featureType() {
            return TYPE;
        }
    }

    // Draws the submitted meshes in the solid phase, each with its offset in the dynamic transforms and the textures
    // (atlas, overlay, lightmap) its vanilla render type binds.
    private static final class Renderer implements FeatureRenderer<Submit> {
        private record Draw(PreparedRenderType type, GpuBuffer buffer, int indexCount) {}

        private final List<List<Draw>> groups = new ArrayList<>();
        private final Map<RenderType, PreparedRenderType> prepared = new IdentityHashMap<>();

        @Override
        public void beginPrepare(FeatureFrameContext context) {
            prepared.clear();
            sweep();
        }

        @Override
        public void prepareGroup(FeatureFrameContext context, List<Submit> submits, boolean strictlyOrdered) {
            List<Draw> draws = new ArrayList<>(submits.size());
            Matrix4f modelView = RenderSystem.getModelViewMatrixCopy();
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            for (Submit submit : submits) {
                Mesh mesh = submit.mesh();
                GpuBuffer buffer = mesh.upload();
                if (buffer == null || buffer.isClosed()) {
                    continue;
                }
                PreparedRenderType base = prepared.computeIfAbsent(mesh.renderType, RenderType::prepare);
                GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView, WHITE, submit.offset(), IDENTITY);
                int indexCount = PrimitiveTopology.QUADS.indexCount(mesh.vertexCount);
                indices.requestIndexCount(indexCount);
                draws.add(new Draw(new PreparedRenderType(base.name(), mesh.pipeline, null, transforms, base.scissorState(), base.textures()), buffer,
                        indexCount));
            }
            groups.add(draws);
        }

        @Override
        public void executeGroup(FeatureFrameContext context, @Nullable OitStage stage, RenderPass renderPass, int groupIndex, List<Submit> submits,
                boolean strictlyOrdered) {
            // Submitted to the solid phase only, which is never drawn in an OIT stage.
            if (stage != null || groupIndex >= groups.size()) {
                return;
            }
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            for (Draw draw : groups.get(groupIndex)) {
                if (draw.buffer().isClosed()) {
                    continue;
                }
                draw.type().drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(draw.buffer(), null, indices.type(), 0, 0, draw.indexCount(),
                        PrimitiveTopology.QUADS), renderPass);
            }
        }

        @Override
        public void finishExecute(FeatureFrameContext context) {
            groups.clear();
        }

        @Override
        public void close() {
            closeAll();
        }
    }

    @SubscribeEvent
    static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(SOLID);
        event.registerPipeline(CUTOUT);
    }

    // Once for each feature dispatcher (the level's, and the hand and screen's): each gets a renderer of its own.
    @SubscribeEvent
    static void registerFeatureRenderers(RegisterFeatureRenderersEvent event) {
        event.register(TYPE, new Renderer());
    }
}
