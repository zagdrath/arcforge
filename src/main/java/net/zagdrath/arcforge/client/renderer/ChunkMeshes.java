/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.renderer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.AddSectionGeometryEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.zagdrath.arcforge.Arcforge;

// The large models' still parts in the world's own mesh. A formed model's casings, decks, pipes and frames don't move,
// so instead of being drawn by its block entity renderer every frame (even from the GPU copy StaticMeshes keeps, a draw
// per list per model per frame, and only while the renderer is asked), they're written into the chunk sections they lie
// in, once, when the structure forms: from then on the world's own section draws carry them at no cost of their own,
// culled and faded with the terrain, at any distance the terrain is drawn. They're written again only when the
// structure changes (its renderer hands over a new list, or its light changes) or a section it's in is rebuilt anyway.
//
// A renderer bakes a list (bake) as it would have emitted it: its emitter is run once into a capture with an identity
// pose, and the quads are shared out among the sections they're in, offset to each section's corner (a quad crossing
// into another section by more than CROSS_LIMIT is cut along the section planes, so a culled section never takes part
// of a big face with it). Section drawing has no back-face-free pass, so a cutout list is written both ways round. The
// sections meshes vanilla rebuilds look the bakes up here (AddSectionGeometryEvent, on the main thread) and write their
// share on the meshing thread from arrays nothing changes. A bake whose owner is gone, unloaded, replaced or no longer
// formed (its valid check) is dropped the next time one of its sections is meshed, and its sections are rebuilt.
//
// The terrain shader lights a vertex only by its colour and light, as MeshRendering.emit's still parts already are (face
// shading in the colour, the entity shader's level light being 1 for an upward normal), so a baked model looks as it did.
// A list the entity shader lit from its real normals (bakeEntityLit) gets that level light worked out here and put in
// its colour instead.
//
// Sodium (and its forks) mesh the sections themselves and never ask for AddSectionGeometryEvent, so with one of them
// loaded nothing is baked: each list is held here instead (as the bake would have been, made and replaced the same way)
// and its renderer draws it every frame from the GPU copy StaticMeshes keeps (submitHeld), out to any distance the
// world is drawn, as before the chunk mesh took them.
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public final class ChunkMeshes {
    // Floats a stored vertex: x, y, z (from the section's corner), u, v (in the atlas), colour and light (int bits); a
    // captured one has its normal too, until it's lit (see light).
    private static final int STRIDE = 7, CAPTURED = 10, QUAD = 4 * CAPTURED;
    // Vanilla's two level lights for entities (Lighting.DIFFUSE_LIGHT_0 and 1), for lists the entity shader lit from their
    // normals.
    private static final float[] LIGHT0 = normalized(0.2F, 1.0F, -0.7F), LIGHT1 = normalized(-0.2F, 1.0F, 0.7F);
    // How far (blocks) a quad may reach into the next section before it's cut.
    private static final float CROSS_LIMIT = 0.5F;
    private static final int SWEEP_TICKS = 100;
    private static final PoseStack.Pose IDENTITY = new PoseStack().last();

    // One baked list: whose it is, what it was made from (so the same list isn't baked twice), and its share of each
    // section, by section key.
    private static final class Bake {
        final WeakReference<BlockEntity> owner;
        final BlockPos pos;
        final BooleanSupplier valid;
        final Object source;
        final int light;
        final ChunkSectionLayer layer;
        final Map<Long, float[]> sections;

        Bake(BlockEntity owner, BooleanSupplier valid, Object source, int light, ChunkSectionLayer layer, Map<Long, float[]> sections) {
            this.owner = new WeakReference<>(owner);
            this.pos = owner.getBlockPos().immutable();
            this.valid = valid;
            this.source = source;
            this.light = light;
            this.layer = layer;
            this.sections = sections;
        }

        boolean isLive(Level level) {
            BlockEntity be = owner.get();
            return be != null && !be.isRemoved() && be.getLevel() == level && level.getBlockEntity(pos) == be && valid.getAsBoolean();
        }
    }

    // Per level: each owner's bakes by slot, and the bakes in each section.
    private static final class Baked {
        final Map<Long, Map<Integer, Bake>> byOwner = new HashMap<>();
        final Map<Long, Set<Bake>> bySection = new HashMap<>();
    }

    private static final Map<Level, Baked> LEVELS = new WeakHashMap<>();

    // Whether the sections are meshed by vanilla (so the bakes reach them), or by Sodium or a fork of it.
    private static final boolean IN_CHUNKS = !(ModList.get().isLoaded("sodium") || ModList.get().isLoaded("embeddium")
            || ModList.get().isLoaded("rubidium"));

    // A list held for its renderer to draw (when the sections can't take it): drawn as emitter writes it at origin, from
    // the GPU copy kept for key (an array of its size, made with it, so a new list is a new copy).
    private record Held(WeakReference<BlockEntity> owner, BlockPos origin, ChunkSectionLayer layer, Object source, int light, BooleanSupplier valid,
            SubmitNodeCollector.CustomGeometryRenderer emitter, float[] key) {
        boolean isLive(Level level, BlockPos pos) {
            BlockEntity be = owner.get();
            return be != null && !be.isRemoved() && be.getLevel() == level && level.getBlockEntity(pos) == be && valid.getAsBoolean();
        }
    }

    private static final Map<Level, Map<Long, Map<Integer, Held>>> HELD = new WeakHashMap<>();
    private static int sinceSweep;

    private ChunkMeshes() {}

    // Puts a still list into the chunk mesh for owner (one list per slot): what emitter writes with an identity pose, at
    // origin, in layer. source identifies the list (a renderer's built array, or a record of what it's built from):
    // baking an equal source at the same light again does nothing, and anything else replaces the slot's bake. valid says
    // whether it still stands (say, the structure is still formed with the shape it was built for); it's asked whenever
    // one of its sections is meshed. The emitter shades its faces in their colour, as MeshRendering.emit does.
    public static void bake(BlockEntity owner, int slot, BlockPos origin, ChunkSectionLayer layer, Object source, int light, BooleanSupplier valid,
            SubmitNodeCollector.CustomGeometryRenderer emitter) {
        bake(owner, slot, origin, layer, source, light, valid, false, emitter);
    }

    // The same for a list the entity shader lit from its normals (TiledBoxes' tiles, say): its level light is worked out
    // here from each face's normal and put in its colour, as the shader did in the overworld (a cutout list's back faces,
    // which it lit from behind, too).
    public static void bakeEntityLit(BlockEntity owner, int slot, BlockPos origin, ChunkSectionLayer layer, Object source, int light, BooleanSupplier valid,
            SubmitNodeCollector.CustomGeometryRenderer emitter) {
        bake(owner, slot, origin, layer, source, light, valid, true, emitter);
    }

    private static void bake(BlockEntity owner, int slot, BlockPos origin, ChunkSectionLayer layer, Object source, int light, BooleanSupplier valid,
            boolean entityLit, SubmitNodeCollector.CustomGeometryRenderer emitter) {
        Level level = owner.getLevel();
        if (level == null || !level.isClientSide()) {
            return;
        }
        if (!IN_CHUNKS) {
            hold(owner, slot, origin, layer, source, light, valid, emitter);
            return;
        }
        Baked baked = LEVELS.computeIfAbsent(level, l -> new Baked());
        Map<Integer, Bake> slots = baked.byOwner.computeIfAbsent(owner.getBlockPos().asLong(), k -> new HashMap<>());
        Bake old = slots.get(slot);
        if (old != null && old.source.equals(source) && old.light == light && old.layer == layer && old.owner.get() == owner) {
            return;
        }
        Capture capture = new Capture();
        emitter.render(IDENTITY, capture);
        Bake bake = new Bake(owner, valid, source, light, layer, split(capture.vertices(), origin, layer == ChunkSectionLayer.CUTOUT, entityLit));
        Set<Long> dirty = new HashSet<>(bake.sections.keySet());
        if (old != null) {
            unlink(baked, old);
            dirty.addAll(old.sections.keySet());
        }
        slots.put(slot, bake);
        for (Long section : bake.sections.keySet()) {
            baked.bySection.computeIfAbsent(section, k -> new HashSet<>()).add(bake);
        }
        markDirty(level, dirty);
    }

    private static void hold(BlockEntity owner, int slot, BlockPos origin, ChunkSectionLayer layer, Object source, int light, BooleanSupplier valid,
            SubmitNodeCollector.CustomGeometryRenderer emitter) {
        Map<Integer, Held> slots = HELD.computeIfAbsent(owner.getLevel(), l -> new HashMap<>()).computeIfAbsent(owner.getBlockPos().asLong(), k -> new HashMap<>());
        Held old = slots.get(slot);
        if (old != null && old.source().equals(source) && old.light() == light && old.layer() == layer && old.owner().get() == owner
                && old.origin().equals(origin)) {
            return;
        }
        Capture capture = new Capture();
        emitter.render(IDENTITY, capture);
        float[] key = new float[capture.vertices().length / CAPTURED * 8];
        slots.put(slot, new Held(new WeakReference<>(owner), origin.immutable(), layer, source, light, valid, emitter, key));
    }

    // Draws what the block entity state is for holds (when the sections can't take its bakes; else nothing): called by
    // a renderer that bakes at the start of its submit, with the pose at the block entity.
    public static void submitHeld(BlockEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
        Level level = Minecraft.getInstance().level;
        Map<Long, Map<Integer, Held>> held = IN_CHUNKS || level == null ? null : HELD.get(level);
        Map<Integer, Held> slots = held != null ? held.get(state.blockPos.asLong()) : null;
        if (slots == null) {
            return;
        }
        for (Held list : slots.values()) {
            if (list.key().length == 0 || !list.isLive(level, state.blockPos)) {
                continue;
            }
            RenderType type = list.layer() == ChunkSectionLayer.CUTOUT ? RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS)
                    : RenderTypes.entitySolid(TextureAtlas.LOCATION_BLOCKS);
            poseStack.pushPose();
            poseStack.translate(list.origin().getX() - state.blockPos.getX(), list.origin().getY() - state.blockPos.getY(),
                    list.origin().getZ() - state.blockPos.getZ());
            StaticMeshes.submit(collector, poseStack, type, list.key(), null, list.light(), list.emitter());
            poseStack.popPose();
        }
    }

    // Whether owner has a live bake in every one of the slots given (its renderer needn't draw those lists itself).
    // Never while they're held instead: its renderer draws them, out to the world's render distance.
    public static boolean isBaked(BlockEntity owner, int... slots) {
        if (!IN_CHUNKS) {
            return false;
        }
        Level level = owner.getLevel();
        Baked baked = level != null ? LEVELS.get(level) : null;
        Map<Integer, Bake> mine = baked != null ? baked.byOwner.get(owner.getBlockPos().asLong()) : null;
        if (mine == null) {
            return false;
        }
        for (int slot : slots) {
            Bake bake = mine.get(slot);
            if (bake == null || bake.owner.get() != owner) {
                return false;
            }
        }
        return true;
    }

    // Takes everything owner baked back out (it unformed, say).
    public static void remove(BlockEntity owner) {
        Level level = owner.getLevel();
        Map<Long, Map<Integer, Held>> held = level != null ? HELD.get(level) : null;
        if (held != null) {
            held.remove(owner.getBlockPos().asLong());
        }
        Baked baked = level != null ? LEVELS.get(level) : null;
        if (baked == null) {
            return;
        }
        Map<Integer, Bake> slots = baked.byOwner.remove(owner.getBlockPos().asLong());
        if (slots == null) {
            return;
        }
        Set<Long> dirty = new HashSet<>();
        for (Bake bake : slots.values()) {
            unlink(baked, bake);
            dirty.addAll(bake.sections.keySet());
        }
        markDirty(level, dirty);
    }

    private static void unlink(Baked baked, Bake bake) {
        for (Long section : bake.sections.keySet()) {
            Set<Bake> in = baked.bySection.get(section);
            if (in != null) {
                in.remove(bake);
                if (in.isEmpty()) {
                    baked.bySection.remove(section);
                }
            }
        }
    }

    private static void drop(Level level, Baked baked, Bake bake) {
        Map<Integer, Bake> slots = baked.byOwner.get(bake.pos.asLong());
        if (slots != null) {
            slots.values().removeIf(b -> b == bake);
            if (slots.isEmpty()) {
                baked.byOwner.remove(bake.pos.asLong());
            }
        }
        unlink(baked, bake);
        markDirty(level, bake.sections.keySet());
    }

    private static void markDirty(Level level, Set<Long> sections) {
        if (level != Minecraft.getInstance().level) {
            return;
        }
        for (long section : sections) {
            int x = SectionPos.x(section), y = SectionPos.y(section), z = SectionPos.z(section);
            Minecraft.getInstance().level.setSectionRangeDirty(x, y, z, x, y, z);
        }
    }

    @SubscribeEvent
    static void onSectionGeometry(AddSectionGeometryEvent event) {
        Level level = event.getLevel();
        Baked baked = LEVELS.get(level);
        if (baked == null) {
            return;
        }
        BlockPos origin = event.getSectionOrigin();
        long key = SectionPos.asLong(SectionPos.blockToSectionCoord(origin.getX()), SectionPos.blockToSectionCoord(origin.getY()),
                SectionPos.blockToSectionCoord(origin.getZ()));
        Set<Bake> in = baked.bySection.get(key);
        if (in == null || in.isEmpty()) {
            return;
        }
        List<Bake> stale = new ArrayList<>();
        List<ChunkSectionLayer> layers = new ArrayList<>();
        List<float[]> lists = new ArrayList<>();
        for (Bake bake : in) {
            if (!bake.isLive(level)) {
                stale.add(bake);
                continue;
            }
            layers.add(bake.layer);
            lists.add(bake.sections.get(key));
        }
        stale.forEach(bake -> drop(level, baked, bake));
        if (lists.isEmpty()) {
            return;
        }
        event.addRenderer(context -> {
            for (int i = 0; i < lists.size(); i++) {
                emit(context.getOrCreateChunkBuffer(layers.get(i)), lists.get(i));
            }
        });
    }

    private static void emit(VertexConsumer buffer, float[] data) {
        for (int i = 0; i + STRIDE <= data.length; i += STRIDE) {
            buffer.addVertex(data[i], data[i + 1], data[i + 2])
                    .setColor(Float.floatToRawIntBits(data[i + 5]))
                    .setUv(data[i + 3], data[i + 4])
                    .setLight(Float.floatToRawIntBits(data[i + 6]));
        }
    }

    // Now and then, forgets the bakes of block entities long gone (a chunk left behind), whose sections aren't meshed.
    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (++sinceSweep < SWEEP_TICKS) {
            return;
        }
        sinceSweep = 0;
        for (Map<Long, Map<Integer, Held>> held : HELD.values()) {
            held.values().removeIf(slots -> {
                slots.values().removeIf(list -> list.owner().get() == null || list.owner().get().isRemoved());
                return slots.isEmpty();
            });
        }
        for (Baked baked : LEVELS.values()) {
            for (Iterator<Map<Integer, Bake>> it = baked.byOwner.values().iterator(); it.hasNext();) {
                Map<Integer, Bake> slots = it.next();
                slots.values().removeIf(bake -> {
                    BlockEntity owner = bake.owner.get();
                    if (owner == null || owner.isRemoved()) {
                        unlink(baked, bake);
                        return true;
                    }
                    return false;
                });
                if (slots.isEmpty()) {
                    it.remove();
                }
            }
        }
    }

    // --- Sharing the quads out among the sections ---

    // Captured quads (CAPTURED floats a vertex, from origin) into each section's list, from its corner, lit if asked.
    private static Map<Long, float[]> split(float[] quads, BlockPos origin, boolean bothWays, boolean entityLit) {
        Map<Long, Floats> out = new HashMap<>();
        float ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
        float[] world = new float[QUAD];
        for (int q = 0; q + QUAD <= quads.length; q += QUAD) {
            System.arraycopy(quads, q, world, 0, QUAD);
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int v = 0; v < 4; v++) {
                int i = v * CAPTURED;
                world[i] += ox;
                world[i + 1] += oy;
                world[i + 2] += oz;
                minX = Math.min(minX, world[i]);
                maxX = Math.max(maxX, world[i]);
                minY = Math.min(minY, world[i + 1]);
                maxY = Math.max(maxY, world[i + 1]);
                minZ = Math.min(minZ, world[i + 2]);
                maxZ = Math.max(maxZ, world[i + 2]);
            }
            float cx = (minX + maxX) / 2.0F, cy = (minY + maxY) / 2.0F, cz = (minZ + maxZ) / 2.0F;
            int sx = section(cx), sy = section(cy), sz = section(cz);
            boolean fits = minX >= sx * 16 - CROSS_LIMIT && maxX <= sx * 16 + 16 + CROSS_LIMIT
                    && minY >= sy * 16 - CROSS_LIMIT && maxY <= sy * 16 + 16 + CROSS_LIMIT
                    && minZ >= sz * 16 - CROSS_LIMIT && maxZ <= sz * 16 + 16 + CROSS_LIMIT;
            if (fits) {
                add(out, sx, sy, sz, world, bothWays, entityLit);
                continue;
            }
            // Cut each of its two triangles (the two the GPU draws it as) to every section its box touches.
            for (int x = section(minX); x <= section(maxX); x++) {
                for (int y = section(minY); y <= section(maxY); y++) {
                    for (int z = section(minZ); z <= section(maxZ); z++) {
                        for (int[] triangle : new int[][] { { 0, 1, 2 }, { 0, 2, 3 } }) {
                            List<float[]> polygon = clip(world, triangle, x * 16, y * 16, z * 16);
                            if (polygon.size() >= 3) {
                                addPolygon(out, x, y, z, polygon, bothWays, entityLit);
                            }
                        }
                    }
                }
            }
        }
        Map<Long, float[]> sections = new HashMap<>();
        out.forEach((key, floats) -> sections.put(key, floats.toArray()));
        return sections;
    }

    private static int section(float coordinate) {
        return (int) Math.floor(coordinate / 16.0F);
    }

    // A quad (world coordinates) into its section's list from the section's corner, and wound the other way too if asked
    // (lit from behind, if it's lit).
    private static void add(Map<Long, Floats> out, int sx, int sy, int sz, float[] quad, boolean bothWays, boolean entityLit) {
        Floats list = out.computeIfAbsent(SectionPos.asLong(sx, sy, sz), k -> new Floats());
        for (int v = 0; v < 4; v++) {
            list.vertex(quad, v * CAPTURED, sx * 16, sy * 16, sz * 16, entityLit ? 1.0F : 0.0F);
        }
        if (bothWays) {
            for (int v : new int[] { 0, 3, 2, 1 }) {
                list.vertex(quad, v * CAPTURED, sx * 16, sy * 16, sz * 16, entityLit ? -1.0F : 0.0F);
            }
        }
    }

    // A convex polygon as a fan of quads (the last one a triangle with its third corner twice).
    private static void addPolygon(Map<Long, Floats> out, int sx, int sy, int sz, List<float[]> polygon, boolean bothWays, boolean entityLit) {
        float[] quad = new float[QUAD];
        for (int i = 1; i + 1 < polygon.size(); i += 2) {
            float[] a = polygon.get(0), b = polygon.get(i), c = polygon.get(i + 1), d = polygon.get(Math.min(i + 2, polygon.size() - 1));
            System.arraycopy(a, 0, quad, 0, CAPTURED);
            System.arraycopy(b, 0, quad, CAPTURED, CAPTURED);
            System.arraycopy(c, 0, quad, 2 * CAPTURED, CAPTURED);
            System.arraycopy(d, 0, quad, 3 * CAPTURED, CAPTURED);
            add(out, sx, sy, sz, quad, bothWays, entityLit);
        }
    }

    // The entity shader's level light for a face turned along (nx, ny, nz): 0.6 of the two lights, 0.4 ambient, at most 1.
    private static float entityLight(float nx, float ny, float nz) {
        float lit = Math.max(0.0F, LIGHT0[0] * nx + LIGHT0[1] * ny + LIGHT0[2] * nz) + Math.max(0.0F, LIGHT1[0] * nx + LIGHT1[1] * ny + LIGHT1[2] * nz);
        return Math.min(1.0F, lit * 0.6F + 0.4F);
    }

    private static float[] normalized(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[] { x / length, y / length, z / length };
    }

    // A triangle of the quad cut to the section box from (x0, y0, z0), 16 each way (Sutherland-Hodgman, an axis plane at
    // a time); its UVs, colour and light carried along each cut edge, as the GPU interpolates them across the triangle.
    private static List<float[]> clip(float[] quad, int[] triangle, float x0, float y0, float z0) {
        List<float[]> polygon = new ArrayList<>(3);
        for (int v : triangle) {
            float[] vertex = new float[CAPTURED];
            System.arraycopy(quad, v * CAPTURED, vertex, 0, CAPTURED);
            polygon.add(vertex);
        }
        float[] low = { x0, y0, z0 };
        for (int axis = 0; axis < 3 && !polygon.isEmpty(); axis++) {
            polygon = clipPlane(polygon, axis, low[axis], true);
            polygon = clipPlane(polygon, axis, low[axis] + 16.0F, false);
        }
        return polygon;
    }

    private static List<float[]> clipPlane(List<float[]> polygon, int axis, float plane, boolean keepAbove) {
        List<float[]> out = new ArrayList<>(polygon.size() + 2);
        for (int i = 0; i < polygon.size(); i++) {
            float[] a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
            boolean aIn = keepAbove ? a[axis] >= plane : a[axis] <= plane;
            boolean bIn = keepAbove ? b[axis] >= plane : b[axis] <= plane;
            if (aIn) {
                out.add(a);
            }
            if (aIn != bIn) {
                float t = (plane - a[axis]) / (b[axis] - a[axis]);
                out.add(lerp(a, b, t));
            }
        }
        return out;
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        float[] v = new float[CAPTURED];
        for (int i = 0; i < 5; i++) {
            v[i] = a[i] + (b[i] - a[i]) * t;
        }
        v[5] = Float.intBitsToFloat(lerpColor(Float.floatToRawIntBits(a[5]), Float.floatToRawIntBits(b[5]), t));
        // Light is the model's own, the same at every vertex, and a face's normal is the same at its corners.
        v[6] = a[6];
        System.arraycopy(a, 7, v, 7, 3);
        return v;
    }

    private static int lerpColor(int a, int b, float t) {
        int color = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int ca = a >>> shift & 0xFF, cb = b >>> shift & 0xFF;
            color |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return color;
    }

    // --- The capture ---

    // What an emitter writes, kept as CAPTURED floats a vertex (overlay dropped: sections have none).
    private static final class Capture implements VertexConsumer {
        private final Floats data = new Floats();
        private int color = -1, light;
        private float u, v, nx, ny = 1.0F, nz;
        private float @Nullable [] pending;

        float[] vertices() {
            flush();
            return data.toArray();
        }

        private void flush() {
            if (pending != null) {
                pending[3] = u;
                pending[4] = v;
                pending[5] = Float.intBitsToFloat(color);
                pending[6] = Float.intBitsToFloat(light);
                pending[7] = nx;
                pending[8] = ny;
                pending[9] = nz;
                data.add(pending);
                pending = null;
            }
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            flush();
            pending = new float[CAPTURED];
            pending[0] = x;
            pending[1] = y;
            pending[2] = z;
            color = -1;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            color = (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | b & 0xFF;
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            color = argb;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            light = u & 0xFFFF | (v & 0xFFFF) << 16;
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            nx = x;
            ny = y;
            nz = z;
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    // A growable float array.
    private static final class Floats {
        private float[] data = new float[1024];
        private int size;

        void add(float[] values) {
            ensure(values.length);
            System.arraycopy(values, 0, data, size, values.length);
            size += values.length;
        }

        // A captured vertex from vertices at offset, moved to be from (x0, y0, z0); lit by the entity shader's level light
        // from its normal when facing is 1, from behind when it's -1, and not when it's 0.
        void vertex(float[] vertices, int offset, float x0, float y0, float z0, float facing) {
            ensure(STRIDE);
            data[size] = vertices[offset] - x0;
            data[size + 1] = vertices[offset + 1] - y0;
            data[size + 2] = vertices[offset + 2] - z0;
            data[size + 3] = vertices[offset + 3];
            data[size + 4] = vertices[offset + 4];
            int color = Float.floatToRawIntBits(vertices[offset + 5]);
            if (facing != 0.0F) {
                float lit = entityLight(vertices[offset + 7] * facing, vertices[offset + 8] * facing, vertices[offset + 9] * facing);
                color = scaled(color, lit);
            }
            data[size + 5] = Float.intBitsToFloat(color);
            data[size + 6] = vertices[offset + 6];
            size += STRIDE;
        }

        // A colour's red, green and blue scaled by share, its alpha kept.
        private static int scaled(int color, float share) {
            int r = Math.round((color >> 16 & 0xFF) * share), g = Math.round((color >> 8 & 0xFF) * share), b = Math.round((color & 0xFF) * share);
            return color & 0xFF000000 | r << 16 | g << 8 | b;
        }

        private void ensure(int more) {
            if (size + more > data.length) {
                data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, size + more));
            }
        }

        float[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }
}
