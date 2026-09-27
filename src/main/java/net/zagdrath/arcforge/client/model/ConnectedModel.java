/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Quadrant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.CuboidFace;
import net.minecraft.client.resources.model.cuboid.FaceBakery;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.zagdrath.arcforge.block.multiblock.DistillationArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.ShellCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.TrayLevelCasingBlock;
import net.zagdrath.arcforge.multiblock.DistillationStructure;

// Connected textures for the steam arrays and Pressure Glass: a formed structure reads as one surface.
//
// Model JSON (loader "arcforge:connected"): "connect" is "structure" (a casing) or "glass", "textures"
// names base / beam / lip / window (and end_generator / end_bearing for the turbine), and "fallback" is a
// plain cube used wherever the model is baked on its own. Blockstates use it through the block state model
// type "arcforge:connected" ({"type": "arcforge:connected", "model": <that model>}), which is what sees
// the neighbouring blocks.
//
// Each visible face is built from pieces baked once: the seamless base tile; for casings, 8x8 window
// quadrants wherever glass touches that corner of the face (so every window reaches half a block into
// the casings around it), with a 1px lip where a window quadrant meets a plate quadrant; and a 3px beam
// along each side that is on the structure's outer edge. A formed block draws only its faces on the
// outside of the structure (not toward other parts, nor into the hollow core), so the whole thing is a
// thin skin the machine's renderer can be seen through. Loose casings use their plain model; a loose pane
// shows its base with a lip on all four sides.
//
// The Distillation Array's solid column uses two more modes (see ColumnBaked): "column_2x2" for its casings
// and controller (base / beam / roof_nw / roof_ne / roof_sw / roof_se, and front for the controller) and
// "tray_window" for its tray level casings (base / frame / top / bottom).
public final class ConnectedModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("arcforge", "connected");
    private static final String COLUMN = "column_2x2", TRAY = "tray_window";

    // Overlays sit just outside the face they decorate, so they never z-fight with it.
    private static final float WINDOW_OFFSET = 0.0F, LIP_OFFSET = 0.02F, BEAM_OFFSET = 0.04F;

    private ConnectedModel() {}

    // --- The model JSON ---

    public record JsonModel(String connect, Map<String, Identifier> textures, Identifier fallback) implements UnbakedModel {
        public boolean glass() {
            return connect.equals("glass");
        }

        public boolean column() {
            return connect.equals(COLUMN) || connect.equals(TRAY);
        }

        // Anything that bakes this as an ordinary model gets the plain fallback cube.
        @Override
        public Identifier parent() {
            return fallback;
        }
    }

    public static final class Loader implements UnbakedModelLoader<JsonModel> {
        public static final Loader INSTANCE = new Loader();

        @Override
        public JsonModel read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            String connect = GsonHelper.getAsString(json, "connect", "structure");
            Map<String, Identifier> textures = new HashMap<>();
            JsonObject textureJson = GsonHelper.getAsJsonObject(json, "textures");
            for (var entry : textureJson.entrySet()) {
                textures.put(entry.getKey(), Identifier.parse(entry.getValue().getAsString()));
            }
            String[] needed = switch (connect) {
                case "glass" -> new String[] { "base", "beam", "lip" };
                case COLUMN -> new String[] { "base", "beam", "roof_nw", "roof_ne", "roof_sw", "roof_se" };
                case TRAY -> new String[] { "base", "frame", "top", "bottom" };
                default -> new String[] { "base", "beam", "lip", "window" };
            };
            for (String required : needed) {
                if (!textures.containsKey(required)) {
                    throw new JsonParseException("Connected model is missing texture '" + required + "'");
                }
            }
            return new JsonModel(connect, textures, Identifier.parse(GsonHelper.getAsString(json, "fallback")));
        }
    }

    // --- The block state model ---

    public record BlockStateUnbaked(Identifier model) implements CustomUnbakedBlockStateModel {
        public static final MapCodec<BlockStateUnbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("model").forGetter(BlockStateUnbaked::model))
                .apply(instance, BlockStateUnbaked::new));

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            resolver.markDependency(model);
        }

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            ResolvedModel resolved = baker.getModel(model);
            if (!(resolved.wrapped() instanceof JsonModel unbaked)) {
                throw new IllegalStateException("Model " + model + " is not an arcforge:connected model");
            }
            return unbaked.column() ? new ColumnBaked(baker, unbaked, model) : new Baked(baker, unbaked, model);
        }
    }

    // Quadrants of a face, in texture orientation.
    private enum Quad {
        TOP_LEFT(true, false), TOP_RIGHT(true, true), BOTTOM_LEFT(false, false), BOTTOM_RIGHT(false, true);

        final boolean top;
        final boolean right;

        Quad(boolean top, boolean right) {
            this.top = top;
            this.right = right;
        }

        float u0() {
            return right ? 8 : 0;
        }

        float v0() {
            return top ? 0 : 8;
        }

        Quad horizontalNeighbour() {
            return values()[ordinal() ^ 1];
        }

        Quad verticalNeighbour() {
            return values()[ordinal() ^ 2];
        }
    }

    // Sides of a face, in texture orientation; the rotation turns a texture's top edge onto that side.
    private enum Side {
        TOP(Quadrant.R0), RIGHT(Quadrant.R90), BOTTOM(Quadrant.R180), LEFT(Quadrant.R270);

        final Quadrant rotation;

        Side(Quadrant rotation) {
            this.rotation = rotation;
        }
    }

    private static final class Baked implements DynamicBlockStateModel {
        private final boolean glass;
        private final Material.Baked particle;
        private final Map<Direction, List<BakedQuad>[]> base = new EnumMap<>(Direction.class);
        private final Map<Direction, List<BakedQuad>[]> window = new EnumMap<>(Direction.class);
        // Lips of a window quadrant toward its horizontal / vertical neighbour.
        private final Map<Direction, List<BakedQuad>[]> lipAcross = new EnumMap<>(Direction.class);
        private final Map<Direction, List<BakedQuad>[]> lipUpDown = new EnumMap<>(Direction.class);
        private final Map<Direction, List<BakedQuad>[]> beam = new EnumMap<>(Direction.class);
        private final Map<Direction, List<BakedQuad>[]> edgeLip = new EnumMap<>(Direction.class);
        private final Map<Direction, List<BakedQuad>> endGenerator = new EnumMap<>(Direction.class);
        private final Map<Direction, List<BakedQuad>> endBearing = new EnumMap<>(Direction.class);
        private final int materialFlags;

        @SuppressWarnings("unchecked")
        Baked(ModelBaker baker, JsonModel unbaked, Identifier name) {
            this.glass = unbaked.glass();
            Identifier particleId = unbaked.textures().getOrDefault("particle", unbaked.textures().get("base"));
            this.particle = material(baker, particleId, name);
            Material.Baked baseTexture = material(baker, unbaked.textures().get("base"), name);
            Material.Baked beamTexture = material(baker, unbaked.textures().get("beam"), name);
            Material.Baked lipTexture = material(baker, unbaked.textures().get("lip"), name);
            Material.Baked windowTexture = glass ? baseTexture : material(baker, unbaked.textures().get("window"), name);
            Identifier generatorId = unbaked.textures().get("end_generator");
            Identifier bearingId = unbaked.textures().get("end_bearing");
            int flags = 0;
            for (Direction face : Direction.values()) {
                List<BakedQuad>[] baseQuads = new List[4];
                List<BakedQuad>[] windowQuads = new List[4];
                List<BakedQuad>[] across = new List[4];
                List<BakedQuad>[] upDown = new List[4];
                for (Quad quad : Quad.values()) {
                    float u0 = quad.u0(), v0 = quad.v0();
                    baseQuads[quad.ordinal()] = List.of(bake(baker, face, u0, v0, u0 + 8, v0 + 8, 0, baseTexture, u0, v0, u0 + 8, v0 + 8, Quadrant.R0));
                    windowQuads[quad.ordinal()] = List.of(bake(baker, face, u0, v0, u0 + 8, v0 + 8, WINDOW_OFFSET, windowTexture, u0, v0, u0 + 8, v0 + 8, Quadrant.R0));
                    // The lip runs along the quadrant's inner edges (the face's centre lines): the first
                    // 8px of the lip texture, its top edge turned onto that side.
                    Side acrossSide = quad.right ? Side.LEFT : Side.RIGHT;
                    Side upDownSide = quad.top ? Side.BOTTOM : Side.TOP;
                    across[quad.ordinal()] = List.of(bake(baker, face, u0, v0, u0 + 8, v0 + 8, LIP_OFFSET, lipTexture, 0, 0, 8, 8, acrossSide.rotation));
                    upDown[quad.ordinal()] = List.of(bake(baker, face, u0, v0, u0 + 8, v0 + 8, LIP_OFFSET, lipTexture, 0, 0, 8, 8, upDownSide.rotation));
                }
                List<BakedQuad>[] beams = new List[4];
                List<BakedQuad>[] edges = new List[4];
                for (Side side : Side.values()) {
                    beams[side.ordinal()] = List.of(bake(baker, face, 0, 0, 16, 16, BEAM_OFFSET, beamTexture, 0, 0, 16, 16, side.rotation));
                    edges[side.ordinal()] = List.of(bake(baker, face, 0, 0, 16, 16, LIP_OFFSET, lipTexture, 0, 0, 16, 16, side.rotation));
                }
                base.put(face, baseQuads);
                window.put(face, windowQuads);
                lipAcross.put(face, across);
                lipUpDown.put(face, upDown);
                beam.put(face, beams);
                edgeLip.put(face, edges);
                if (generatorId != null) {
                    endGenerator.put(face, List.of(bake(baker, face, 0, 0, 16, 16, 0, material(baker, generatorId, name), 0, 0, 16, 16, Quadrant.R0)));
                }
                if (bearingId != null) {
                    endBearing.put(face, List.of(bake(baker, face, 0, 0, 16, 16, 0, material(baker, bearingId, name), 0, 0, 16, 16, Quadrant.R0)));
                }
                for (List<BakedQuad>[] set : List.of(baseQuads, windowQuads, across, upDown, beams, edges)) {
                    for (List<BakedQuad> quads : set) {
                        for (BakedQuad quad : quads) {
                            flags |= quad.materialInfo().flags();
                        }
                    }
                }
            }
            this.materialFlags = flags;
        }

        private static Material.Baked material(ModelBaker baker, Identifier id, Identifier name) {
            return baker.materials().get(new Material(id), () -> name.toString());
        }

        // A zero-thickness quad on one face: the face-local rectangle (u right, v down, in pixels) offset
        // outwards, showing the texture region (tu0, tv0)-(tu1, tv1) turned by rotation.
        private static BakedQuad bake(ModelBaker baker, Direction face, float u0, float v0, float u1, float v1, float offset,
                Material.Baked texture, float tu0, float tv0, float tu1, float tv1, Quadrant rotation) {
            Vector3f from = new Vector3f();
            Vector3f to = new Vector3f();
            // Inverse of the default face UVs, so the rectangle shows the matching part of the texture.
            switch (face) {
                case DOWN -> { from.set(u0, -offset, 16 - v1); to.set(u1, -offset, 16 - v0); }
                case UP -> { from.set(u0, 16 + offset, v0); to.set(u1, 16 + offset, v1); }
                case NORTH -> { from.set(16 - u1, 16 - v1, -offset); to.set(16 - u0, 16 - v0, -offset); }
                case SOUTH -> { from.set(u0, 16 - v1, 16 + offset); to.set(u1, 16 - v0, 16 + offset); }
                case WEST -> { from.set(-offset, 16 - v1, u0); to.set(-offset, 16 - v0, u1); }
                case EAST -> { from.set(16 + offset, 16 - v1, 16 - u1); to.set(16 + offset, 16 - v0, 16 - u0); }
            }
            CuboidFace cuboidFace = new CuboidFace(null, CuboidFace.NO_TINT, "", new CuboidFace.UVs(tu0, tv0, tu1, tv1), rotation);
            return FaceBakery.bakeQuad(baker, from, to, cuboidFace, texture, face, BlockModelRotation.IDENTITY, null, null, 0);
        }

        // --- Picking the pieces ---

        private static boolean isPart(BlockState state) {
            return ShellCasingBlock.isFormed(state) || PressureGlassBlock.isFormed(state);
        }

        // Air enclosed by the structure on two axes: the hollow core.
        private static boolean isCore(BlockAndTintGetter level, BlockPos pos) {
            if (!level.getBlockState(pos).isAir()) {
                return false;
            }
            int enclosed = 0;
            for (Direction.Axis axis : Direction.Axis.values()) {
                Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
                if (isPart(level.getBlockState(pos.relative(positive))) && isPart(level.getBlockState(pos.relative(positive.getOpposite())))) {
                    enclosed++;
                }
            }
            return enclosed >= 2;
        }

        // The face's texture-up and texture-right directions in the world.
        private static Direction up(Direction face) {
            return switch (face) {
                case UP -> Direction.NORTH;
                case DOWN -> Direction.SOUTH;
                default -> Direction.UP;
            };
        }

        private static Direction right(Direction face) {
            return switch (face) {
                case NORTH -> Direction.WEST;
                case SOUTH, UP, DOWN -> Direction.EAST;
                case WEST -> Direction.SOUTH;
                case EAST -> Direction.NORTH;
            };
        }

        private static Direction sideDirection(Direction face, Side side) {
            return switch (side) {
                case TOP -> up(face);
                case BOTTOM -> up(face).getOpposite();
                case RIGHT -> right(face);
                case LEFT -> right(face).getOpposite();
            };
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
            QuadCollection.Builder quads = new QuadCollection.Builder();
            boolean formed = isPart(state);
            for (Direction face : Direction.values()) {
                if (!formed) {
                    // A loose pane: its base and a lip all round, culled like glass.
                    for (List<BakedQuad> piece : base.get(face)) {
                        piece.forEach(quad -> quads.addCulledFace(face, quad));
                    }
                    for (List<BakedQuad> piece : edgeLip.get(face)) {
                        piece.forEach(quad -> quads.addCulledFace(face, quad));
                    }
                    continue;
                }
                BlockPos across = pos.relative(face);
                if (isPart(level.getBlockState(across)) || isCore(level, across)) {
                    continue;
                }
                List<BakedQuad> faceQuads = new ArrayList<>();
                pickFace(level, pos, state, face, faceQuads);
                faceQuads.forEach(quads::addUnculledFace);
            }
            parts.add(new SimpleModelWrapper(quads.build(), true, particle));
        }

        private void pickFace(BlockAndTintGetter level, BlockPos pos, BlockState state, Direction face, List<BakedQuad> out) {
            // The rotor's bearings: the end cap tile.
            if (state.hasProperty(SteamTurbineArrayCasingBlock.END)) {
                switch (state.getValue(SteamTurbineArrayCasingBlock.END)) {
                    case GENERATOR -> {
                        out.addAll(endGenerator.getOrDefault(face, List.of()));
                        return;
                    }
                    case BEARING -> {
                        out.addAll(endBearing.getOrDefault(face, List.of()));
                        return;
                    }
                    case NONE -> { }
                }
            }
            if (glass) {
                for (List<BakedQuad> piece : base.get(face)) {
                    out.addAll(piece);
                }
            } else {
                boolean[] windowed = new boolean[4];
                for (Quad quad : Quad.values()) {
                    Direction vertical = quad.top ? up(face) : up(face).getOpposite();
                    Direction horizontal = quad.right ? right(face) : right(face).getOpposite();
                    windowed[quad.ordinal()] = isGlass(level, pos.relative(vertical)) || isGlass(level, pos.relative(horizontal))
                            || isGlass(level, pos.relative(vertical).relative(horizontal));
                }
                for (Quad quad : Quad.values()) {
                    int i = quad.ordinal();
                    out.addAll(windowed[i] ? window.get(face)[i] : base.get(face)[i]);
                    if (windowed[i] && !windowed[quad.horizontalNeighbour().ordinal()]) {
                        out.addAll(lipAcross.get(face)[i]);
                    }
                    if (windowed[i] && !windowed[quad.verticalNeighbour().ordinal()]) {
                        out.addAll(lipUpDown.get(face)[i]);
                    }
                }
            }
            for (Side side : Side.values()) {
                if (!isPart(level.getBlockState(pos.relative(sideDirection(face, side))))) {
                    out.addAll(beam.get(face)[side.ordinal()]);
                }
            }
        }

        private static boolean isGlass(BlockAndTintGetter level, BlockPos pos) {
            return PressureGlassBlock.isFormed(level.getBlockState(pos));
        }

        @Override
        public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
            return null;
        }

        @Override
        public Material.Baked particleMaterial() {
            return particle;
        }

        @Override
        @BakedQuad.MaterialFlags
        public int materialFlags() {
            return materialFlags;
        }
    }

    // The Distillation Array's column: solid, so a formed block draws each face that isn't against
    // another block of the column (culled like any block). Casings show the plate, with a 2px beam along
    // every edge of the face whose neighbour in that direction isn't part of the column (so beams only run
    // up the four vertical edges and round the top and bottom); the top shows its quarter of the roof hatch,
    // and the controller its display on the side it faces. Tray level casings show their window, with a
    // frame wherever the neighbour on that side isn't a tray, so the windows of one layer merge.
    private static final class ColumnBaked implements DynamicBlockStateModel {
        private final boolean tray;
        private final Material.Baked particle;
        private final Map<Direction, List<BakedQuad>> base = new EnumMap<>(Direction.class);
        private final Map<Direction, List<BakedQuad>> front = new EnumMap<>(Direction.class);
        // Beams (casings) or the window frame (trays), by side of the face.
        private final Map<Direction, List<BakedQuad>[]> edges = new EnumMap<>(Direction.class);
        // The roof quarters, by [east][south].
        private final List<BakedQuad>[][] roof;
        private final int materialFlags;

        @SuppressWarnings("unchecked")
        ColumnBaked(ModelBaker baker, JsonModel unbaked, Identifier name) {
            this.tray = unbaked.connect().equals(TRAY);
            Map<String, Identifier> textures = unbaked.textures();
            this.particle = Baked.material(baker, textures.getOrDefault("particle", textures.get("base")), name);
            Material.Baked baseTexture = Baked.material(baker, textures.get("base"), name);
            Material.Baked edgeTexture = Baked.material(baker, textures.get(tray ? "frame" : "beam"), name);
            Material.Baked frontTexture = textures.containsKey("front") ? Baked.material(baker, textures.get("front"), name) : null;
            int flags = 0;
            for (Direction face : Direction.values()) {
                Material.Baked texture = baseTexture;
                if (tray && face.getAxis().isVertical()) {
                    texture = Baked.material(baker, textures.get(face == Direction.UP ? "top" : "bottom"), name);
                }
                base.put(face, List.of(Baked.bake(baker, face, 0, 0, 16, 16, 0, texture, 0, 0, 16, 16, Quadrant.R0)));
                if (frontTexture != null) {
                    front.put(face, List.of(Baked.bake(baker, face, 0, 0, 16, 16, 0, frontTexture, 0, 0, 16, 16, Quadrant.R0)));
                }
                List<BakedQuad>[] sides = new List[4];
                for (Side side : Side.values()) {
                    sides[side.ordinal()] = List.of(Baked.bake(baker, face, 0, 0, 16, 16, BEAM_OFFSET, edgeTexture, 0, 0, 16, 16, side.rotation));
                }
                edges.put(face, sides);
            }
            this.roof = new List[2][2];
            if (!tray) {
                String[][] names = { { "roof_nw", "roof_sw" }, { "roof_ne", "roof_se" } };
                for (int east = 0; east < 2; east++) {
                    for (int south = 0; south < 2; south++) {
                        roof[east][south] = List.of(Baked.bake(baker, Direction.UP, 0, 0, 16, 16, 0,
                                Baked.material(baker, textures.get(names[east][south]), name), 0, 0, 16, 16, Quadrant.R0));
                    }
                }
            }
            for (Map<Direction, List<BakedQuad>> set : List.of(base, front)) {
                for (List<BakedQuad> quads : set.values()) {
                    for (BakedQuad quad : quads) {
                        flags |= quad.materialInfo().flags();
                    }
                }
            }
            for (List<BakedQuad>[] sides : edges.values()) {
                for (List<BakedQuad> quads : sides) {
                    for (BakedQuad quad : quads) {
                        flags |= quad.materialInfo().flags();
                    }
                }
            }
            this.materialFlags = flags;
        }

        private static boolean isPart(BlockState state) {
            return DistillationStructure.isFormedPart(state);
        }

        private static boolean isTray(BlockState state) {
            return state.getBlock() instanceof TrayLevelCasingBlock && isPart(state);
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
            QuadCollection.Builder quads = new QuadCollection.Builder();
            boolean formed = isPart(state);
            Direction facing = state.hasProperty(DistillationArrayControllerBlock.FACING) ? state.getValue(DistillationArrayControllerBlock.FACING) : null;
            for (Direction face : Direction.values()) {
                if (formed && isPart(level.getBlockState(pos.relative(face)))) {
                    continue;
                }
                List<BakedQuad> faceQuads = new ArrayList<>();
                if (face == facing && front.containsKey(face)) {
                    faceQuads.addAll(front.get(face));
                } else if (face == Direction.UP && !tray && formed) {
                    int east = isPart(level.getBlockState(pos.west())) ? 1 : 0;
                    int south = isPart(level.getBlockState(pos.north())) ? 1 : 0;
                    faceQuads.addAll(roof[east][south]);
                } else {
                    faceQuads.addAll(base.get(face));
                }
                for (Side side : Side.values()) {
                    BlockState neighbour = level.getBlockState(pos.relative(Baked.sideDirection(face, side)));
                    if (formed && (tray ? !isTray(neighbour) : !isPart(neighbour))) {
                        faceQuads.addAll(edges.get(face)[side.ordinal()]);
                    }
                }
                faceQuads.forEach(quad -> quads.addCulledFace(face, quad));
            }
            parts.add(new SimpleModelWrapper(quads.build(), true, particle));
        }

        @Override
        public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
            return null;
        }

        @Override
        public Material.Baked particleMaterial() {
            return particle;
        }

        @Override
        @BakedQuad.MaterialFlags
        public int materialFlags() {
            return materialFlags;
        }
    }
}
