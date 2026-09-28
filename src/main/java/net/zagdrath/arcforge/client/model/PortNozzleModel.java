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
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.math.Quadrant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.SimpleModelWrapper;
import net.minecraft.client.resources.model.cuboid.CuboidModelElement;
import net.minecraft.client.resources.model.cuboid.UnbakedCuboidGeometry;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.CubeCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SolarThermalArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SolarThermalArrayControllerBlock;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.PortFaces;

// Ports on multiblocks drawn as one big model (the cube arrays): their other blocks are invisible and the
// model isn't a cube, so a port there is a nozzle: a 10x10 px steel collar from the model's surface out to
// the block's outer face, capped with the port plate. How deep the surface lies under each outer face is
// found from the model's elements: the first one covering the middle of that face, going in (the whole
// block's depth if none does). Blockstate: {"type": "arcforge:port_nozzle", "model": <the block's own,
// usually hidden, model>, "kind": "cube", "shapes": {"formed": <the formed model>}}; the Solar Thermal Array's
// kind is "solar", with the shapes "base", "upper_ns" and "upper_ew".
public final class PortNozzleModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("arcforge", "port_nozzle");
    // The collar's own steel, painted for its faces (1 px lit and dark edges along its length, a lip and seam at
    // the port end): collar_v for faces whose length runs down the texture, collar_u (the same, transposed) for
    // faces whose length runs across it, so the lit edge is always the top or left one.
    private static final Identifier COLLAR_V = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/port_nozzle/collar_v");
    private static final Identifier COLLAR_U = Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/port_nozzle/collar_u");
    // The collar's cross-section, in pixels of the block face.
    private static final float COLLAR_MIN = 3.0F, COLLAR_MAX = 13.0F;

    private PortNozzleModel() {}

    // A formed model's element boxes (in pixels from the block it's drawn on), placed in the world: drawn on
    // origin, turned quarterTurns clockwise (seen from above) about that block's centre like a blockstate's y.
    public record Placed(BlockPos origin, List<float[]> boxes, int quarterTurns) {}

    // How a kind of structure's port blocks find the formed models around them, and the structure's box.
    public interface Kind {
        // The models drawn round the port block at pos, by the shape names in the blockstate, or null
        // when it isn't in a formed structure.
        @Nullable List<Placed> find(BlockAndTintGetter level, BlockPos pos, BlockState state, Map<String, List<float[]>> shapes);

        // Whether pos is inside the structure's box (sides facing it aren't outer).
        boolean inside(BlockAndTintGetter level, BlockPos port, BlockPos pos);

        // Whether the port shows on this outer face (the port still works there either way).
        default boolean shows(BlockState state, Direction face) {
            return true;
        }
    }

    private static final Map<String, Kind> KINDS = new HashMap<>();

    static {
        KINDS.put("cube", new CubeKind());
        KINDS.put("solar", new SolarKind());
    }

    public static void registerKind(String name, Kind kind) {
        KINDS.put(name, kind);
    }

    public record Unbaked(Variant variant, String kind, Map<String, Identifier> shapes) implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Variant.MAP_CODEC.forGetter(Unbaked::variant),
                Codec.STRING.fieldOf("kind").forGetter(Unbaked::kind),
                Codec.unboundedMap(Codec.STRING, Identifier.CODEC).fieldOf("shapes").forGetter(Unbaked::shapes))
                .apply(instance, Unbaked::new));

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            variant.resolveDependencies(resolver);
            shapes.values().forEach(resolver::markDependency);
        }

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            Kind found = KINDS.get(kind);
            if (found == null) {
                throw new IllegalStateException("Unknown port nozzle kind " + kind);
            }
            Map<String, List<float[]>> boxes = new HashMap<>();
            shapes.forEach((name, model) -> boxes.put(name, boxes(baker, model)));
            return new Baked(variant.bake(baker), found, boxes, new PortOverlays(baker, variant.modelLocation()),
                    templates(baker, COLLAR_V, variant.modelLocation()), templates(baker, COLLAR_U, variant.modelLocation()));
        }
    }

    // A model's element boxes, as {x0, y0, z0, x1, y1, z1} in pixels (rotated elements by their unrotated box).
    private static List<float[]> boxes(ModelBaker baker, Identifier model) {
        List<float[]> boxes = new ArrayList<>();
        if (baker.getModel(model).getTopGeometry() instanceof UnbakedCuboidGeometry geometry) {
            for (CuboidModelElement element : geometry.elements()) {
                Vector3fc from = element.from();
                Vector3fc to = element.to();
                boxes.add(new float[] { from.x(), from.y(), from.z(), to.x(), to.y(), to.z() });
            }
        }
        return boxes;
    }

    // A collar texture on a whole face of each side, to cut the collar's faces from.
    private static Map<Direction, BakedQuad> templates(ModelBaker baker, Identifier collar, Identifier name) {
        Material.Baked texture = ConnectedModel.Baked.material(baker, collar, name);
        Map<Direction, BakedQuad> templates = new EnumMap<>(Direction.class);
        for (Direction face : Direction.values()) {
            templates.put(face, ConnectedModel.Baked.bake(baker, face, 0, 0, 16, 16, 0, texture, 0, 0, 16, 16, Quadrant.R0));
        }
        return templates;
    }

    private static final class Baked implements DynamicBlockStateModel {
        private final BlockStateModelPart base;
        private final Kind kind;
        private final Map<String, List<float[]>> shapes;
        private final PortOverlays ports;
        private final Map<Direction, BakedQuad> collar;
        private final Map<Direction, BakedQuad> collarAcross;

        Baked(BlockStateModelPart base, Kind kind, Map<String, List<float[]>> shapes, PortOverlays ports, Map<Direction, BakedQuad> collar,
                Map<Direction, BakedQuad> collarAcross) {
            this.base = base;
            this.kind = kind;
            this.shapes = shapes;
            this.ports = ports;
            this.collar = collar;
            this.collarAcross = collarAcross;
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
            parts.add(base);
            PortFaces faces = MultiblockPorts.faces(level, pos);
            if (faces.isEmpty()) {
                return;
            }
            List<Placed> placed = kind.find(level, pos, state, shapes);
            if (placed == null) {
                return;
            }
            QuadCollection.Builder quads = new QuadCollection.Builder();
            for (Direction face : Direction.values()) {
                SideMode mode = faces.get(face);
                if (mode == SideMode.NONE || kind.inside(level, pos, pos.relative(face)) || !kind.shows(state, face)) {
                    continue;
                }
                float depth = depth(pos, face, placed);
                if (depth > 0) {
                    addCollar(quads, face, depth);
                }
                BakedQuad port = ports.get(mode, face);
                if (port != null) {
                    quads.addCulledFace(face, port);
                }
            }
            // No ambient occlusion: the nozzle sits among the structure's blocks, which would shade it black.
            parts.add(new SimpleModelWrapper(quads.build(), false, base.particleMaterial()));
        }

        // How far in from this outer face (in pixels, up to the whole block) the models' surface lies at
        // the middle of the face.
        private static float depth(BlockPos pos, Direction face, List<Placed> placed) {
            int axis = face.getAxis().ordinal();
            boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            float plane = positive ? 16.0F : 0.0F;
            float depth = 16.0F;
            for (Placed model : placed) {
                float[] shift = {
                        (model.origin().getX() - pos.getX()) * 16.0F,
                        (model.origin().getY() - pos.getY()) * 16.0F,
                        (model.origin().getZ() - pos.getZ()) * 16.0F };
                for (float[] box : model.boxes()) {
                    float[] turned = turn(box, model.quarterTurns());
                    float[] min = { turned[0] + shift[0], turned[1] + shift[1], turned[2] + shift[2] };
                    float[] max = { turned[3] + shift[0], turned[4] + shift[1], turned[5] + shift[2] };
                    // The ray runs along axis through the middle of the face.
                    boolean covers = true;
                    for (int other = 0; other < 3; other++) {
                        if (other != axis && !(min[other] < 8.0F && max[other] > 8.0F)) {
                            covers = false;
                        }
                    }
                    if (!covers) {
                        continue;
                    }
                    // Distance in from the face to the box's near side (0 if the box reaches the face).
                    float distance = positive ? plane - max[axis] : min[axis] - plane;
                    float far = positive ? plane - min[axis] : max[axis] - plane;
                    if (far > 0.0F) {
                        depth = Math.min(depth, Math.max(0.0F, distance));
                    }
                }
            }
            return depth;
        }

        // A box turned quarter turns clockwise (seen from above) about the block's centre: (x, z) -> (16 - z, x).
        private static float[] turn(float[] box, int quarterTurns) {
            float x0 = box[0], z0 = box[2], x1 = box[3], z1 = box[5];
            for (int i = 0; i < (quarterTurns & 3); i++) {
                float nx0 = 16.0F - z1, nx1 = 16.0F - z0, nz0 = x0, nz1 = x1;
                x0 = nx0;
                x1 = nx1;
                z0 = nz0;
                z1 = nz1;
            }
            return new float[] { x0, box[1], z0, x1, box[4], z1 };
        }

        // The collar's four sides, from the outer face depth pixels in, and both its ends: at the face, and
        // at the back, which closes it where the models' surface under it isn't flat (the depth is only
        // measured at the middle, so an edge of the collar can end in the open).
        private void addCollar(QuadCollection.Builder quads, Direction face, float depth) {
            int axis = face.getAxis().ordinal();
            boolean positive = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            float outer = positive ? 16.0F : 0.0F;
            float inner = positive ? 16.0F - depth : depth;
            float[] min = { COLLAR_MIN, COLLAR_MIN, COLLAR_MIN };
            float[] max = { COLLAR_MAX, COLLAR_MAX, COLLAR_MAX };
            min[axis] = Math.min(outer, inner);
            max[axis] = Math.max(outer, inner);
            for (Direction side : Direction.values()) {
                if (side.getAxis() != face.getAxis()) {
                    quads.addUnculledFace(FaceRects.cut((lengthDown(face, side) ? collar : collarAcross).get(side), side, min, max));
                }
            }
            quads.addCulledFace(face, FaceRects.cut(collar.get(face), face, min, max));
            Direction back = face.getOpposite();
            quads.addUnculledFace(FaceRects.cut(collar.get(back), back, min, max));
        }

        // Whether a collar pointing out of face runs down side's texture (v) rather than across it (u). In face
        // coordinates (FaceRects.local) y is v on every side face and z is v on the top and bottom ones.
        private static boolean lengthDown(Direction face, Direction side) {
            return face.getAxis() == Direction.Axis.Y || (face.getAxis() == Direction.Axis.Z && side.getAxis() == Direction.Axis.Y);
        }

        @Override
        public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
            return null;
        }

        @Override
        public Material.Baked particleMaterial() {
            return base.particleMaterial();
        }

        @Override
        @BakedQuad.MaterialFlags
        public int materialFlags() {
            return base.materialFlags() | ports.materialFlags() | collar.get(Direction.UP).materialInfo().flags();
        }
    }

    // The cube arrays: a 3x3x3 whose centre block draws the formed model, turned to its FACING.
    private static final class CubeKind implements Kind {
        @Override
        public @Nullable List<Placed> find(BlockAndTintGetter level, BlockPos pos, BlockState state, Map<String, List<float[]>> shapes) {
            BlockPos centre = centre(level, pos, state);
            if (centre == null) {
                return null;
            }
            Direction facing = level.getBlockState(centre).getValue(CubeCasingBlock.FACING);
            return List.of(new Placed(centre, shapes.getOrDefault("formed", List.of()), facing.get2DDataValue() + 2 & 3));
        }

        @Override
        public boolean inside(BlockAndTintGetter level, BlockPos port, BlockPos pos) {
            BlockPos centre = centre(level, port, level.getBlockState(port));
            return centre != null && Math.abs(pos.getX() - centre.getX()) <= 1 && Math.abs(pos.getY() - centre.getY()) <= 1
                    && Math.abs(pos.getZ() - centre.getZ()) <= 1;
        }

        // The formed centre block of the same kind within one block of pos.
        private static @Nullable BlockPos centre(BlockAndTintGetter level, BlockPos pos, BlockState state) {
            for (BlockPos candidate : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
                BlockState other = level.getBlockState(candidate);
                if (other.is(state.getBlock()) && other.getValue(CubeCasingBlock.PART) == CubeCasingBlock.Part.CENTER) {
                    return candidate.immutable();
                }
            }
            return null;
        }
    }

    // The Solar Thermal Array: the base model is drawn on the bottom layer's minimum corner (a casing, or the
    // controller) and the mast on the third layer's; the controller's control panel covers its front, so no
    // nozzle goes there.
    private static final class SolarKind implements Kind {
        @Override
        public @Nullable List<Placed> find(BlockAndTintGetter level, BlockPos pos, BlockState state, Map<String, List<float[]>> shapes) {
            BlockPos min = min(level, pos);
            if (min == null) {
                return null;
            }
            BlockState upper = level.getBlockState(min.above(2));
            String mast = upper.hasProperty(SolarThermalArrayCasingBlock.PART)
                    && upper.getValue(SolarThermalArrayCasingBlock.PART) == SolarThermalArrayCasingBlock.Part.UPPER_EW ? "upper_ew" : "upper_ns";
            return List.of(new Placed(min, shapes.getOrDefault("base", List.of()), 0), new Placed(min.above(2), shapes.getOrDefault(mast, List.of()), 0));
        }

        @Override
        public boolean inside(BlockAndTintGetter level, BlockPos port, BlockPos pos) {
            BlockPos min = min(level, port);
            return min != null && pos.getX() >= min.getX() && pos.getX() <= min.getX() + 1 && pos.getZ() >= min.getZ() && pos.getZ() <= min.getZ() + 1
                    && pos.getY() >= min.getY() && pos.getY() <= min.getY() + 3;
        }

        @Override
        public boolean shows(BlockState state, Direction face) {
            return !(state.getBlock() instanceof SolarThermalArrayControllerBlock) || state.getValue(SolarThermalArrayControllerBlock.FACING) != face;
        }

        // The bottom layer's minimum corner of the tower pos is in (it's among the three layers under the collectors).
        private static @Nullable BlockPos min(BlockAndTintGetter level, BlockPos pos) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dz = 0; dz <= 1; dz++) {
                        BlockPos candidate = pos.offset(-dx, -dy, -dz);
                        BlockState state = level.getBlockState(candidate);
                        boolean base = state.hasProperty(SolarThermalArrayCasingBlock.PART)
                                ? state.getValue(SolarThermalArrayCasingBlock.PART) == SolarThermalArrayCasingBlock.Part.BASE
                                : state.getBlock() instanceof SolarThermalArrayControllerBlock && state.getValue(SolarThermalArrayControllerBlock.BASE);
                        if (base) {
                            return candidate;
                        }
                    }
                }
            }
            return null;
        }
    }

    // Cutting a face-aligned rectangle out of a whole-face quad (baked with its texture unrotated, 0-16 across
    // the face), keeping its texture where it lies on the face.
    static final class FaceRects {
        private FaceRects() {}

        // The rectangle of the box (pixels) on side, in its own plane (which may be inside the block).
        static BakedQuad cut(BakedQuad template, Direction side, float[] min, float[] max) {
            int axis = side.getAxis().ordinal();
            float plane = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max[axis] : min[axis];
            // The template's texture across the face: u and v at 0 and 16 in face coordinates.
            float[] u = new float[2], v = new float[2];
            for (int i = 0; i < 4; i++) {
                Vector3fc p = template.position(i);
                float[] local = local(side, p.x() * 16.0F, p.y() * 16.0F, p.z() * 16.0F);
                long uv = template.packedUV(i);
                u[local[0] < 8 ? 0 : 1] = UVPair.unpackU(uv);
                v[local[1] < 8 ? 0 : 1] = UVPair.unpackV(uv);
            }
            float[] a = local(side, min[0], min[1], min[2]);
            float[] b = local(side, max[0], max[1], max[2]);
            float u0 = Math.min(a[0], b[0]), u1 = Math.max(a[0], b[0]);
            float v0 = Math.min(a[1], b[1]), v1 = Math.max(a[1], b[1]);
            Vector3f[] positions = new Vector3f[4];
            long[] uvs = new long[4];
            for (int i = 0; i < 4; i++) {
                Vector3fc p = template.position(i);
                float[] local = local(side, p.x() * 16.0F, p.y() * 16.0F, p.z() * 16.0F);
                float lu = local[0] < 8 ? u0 : u1;
                float lv = local[1] < 8 ? v0 : v1;
                positions[i] = world(side, lu, lv, plane).div(16.0F);
                uvs[i] = UVPair.pack(u[0] + (u[1] - u[0]) * lu / 16.0F, v[0] + (v[1] - v[0]) * lv / 16.0F);
            }
            return new BakedQuad(positions[0], positions[1], positions[2], positions[3], uvs[0], uvs[1], uvs[2], uvs[3],
                    template.direction(), template.materialInfo());
        }

        // Face coordinates (u right, v down, pixels) of a point, as ConnectedModel's quads lay textures.
        private static float[] local(Direction side, float x, float y, float z) {
            return switch (side) {
                case DOWN -> new float[] { x, 16 - z };
                case UP -> new float[] { x, z };
                case NORTH -> new float[] { 16 - x, 16 - y };
                case SOUTH -> new float[] { x, 16 - y };
                case WEST -> new float[] { z, 16 - y };
                case EAST -> new float[] { 16 - z, 16 - y };
            };
        }

        private static Vector3f world(Direction side, float u, float v, float plane) {
            return switch (side) {
                case DOWN, UP -> new Vector3f(u, plane, side == Direction.UP ? v : 16 - v);
                case NORTH -> new Vector3f(16 - u, 16 - v, plane);
                case SOUTH -> new Vector3f(u, 16 - v, plane);
                case WEST -> new Vector3f(plane, 16 - v, u);
                case EAST -> new Vector3f(plane, 16 - v, 16 - u);
            };
        }
    }
}
