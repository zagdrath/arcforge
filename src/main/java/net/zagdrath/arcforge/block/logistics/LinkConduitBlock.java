/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.logistics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.logistics.WiredMachineLinkBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.link.MachineLinks;

// The Link Conduit: a thin cable (a steel jacket round an Iolite core) that links one machine or multiblock to one Wired
// Machine Link. It carries nothing itself. Each block joins at most two neighbours, so a run never branches: another Link
// Conduit (only where that one joins back), or at a free end a machine, a multiblock's block, or a Wired Machine Link
// with no other cable (PLUG: the end carries a purple Wired Machine Link port plate). It never joins other conduits. LIT
// while the run is a live link (set by the Wired Machine Link, which traces the run; see WiredMachineLinkBlockEntity).
public class LinkConduitBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final Map<Direction, EnumProperty<Joint>> JOINTS = new EnumMap<>(Direction.class);
    static {
        for (Direction direction : Direction.values()) {
            JOINTS.put(direction, EnumProperty.create(direction.getSerializedName(), Joint.class));
        }
    }

    // What a side of the cable joins.
    public enum Joint implements StringRepresentable {
        NONE("none"),
        // Another Link Conduit.
        CABLE("cable"),
        // The machine, multiblock or Wired Machine Link at an end of the run.
        PLUG("plug");

        private final String name;

        Joint(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    // A 4x4 cable through the middle; the plate at a plugged end.
    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> ARMS = Shapes.rotateAll(Block.box(6, 6, 0, 10, 10, 6));
    private static final Map<Direction, VoxelShape> PLATES = Shapes.rotateAll(Block.box(3, 3, 0, 13, 13, 1.5));
    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    public LinkConduitBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(LIT, false);
        for (EnumProperty<Joint> joint : JOINTS.values()) {
            state = state.setValue(joint, Joint.NONE);
        }
        registerDefaultState(state);
    }

    public static EnumProperty<Joint> joint(Direction side) {
        return JOINTS.get(side);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
        JOINTS.values().forEach(builder::add);
    }

    public static boolean isCable(BlockState state) {
        return state.getBlock() instanceof LinkConduitBlock;
    }

    // How many sides it joins (0 to 2).
    public static int joints(BlockState state) {
        int count = 0;
        for (Direction side : Direction.values()) {
            if (state.getValue(joint(side)) != Joint.NONE) {
                count++;
            }
        }
        return count;
    }

    // What the cable at pos could plug into across side (CABLE for another Link Conduit, which only counts where it joins
    // back): a Wired Machine Link with no other cable, or a block a cable can attach to (see MachineLinks.canAttach).
    private static Joint candidate(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos across = pos.relative(side);
        BlockState state = level.getBlockState(across);
        if (isCable(state)) {
            return Joint.CABLE;
        }
        if (state.getBlock() instanceof WiredMachineLinkBlock) {
            Direction cable = cableFace(level, across);
            return cable == null || cable == side.getOpposite() ? Joint.PLUG : Joint.NONE;
        }
        return MachineLinks.canAttach(level, across) ? Joint.PLUG : Joint.NONE;
    }

    // The face of the block at pos with a Link Conduit plugged into it, or null (a Wired Machine Link takes one cable).
    public static @Nullable Direction cableFace(BlockGetter level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockState state = level.getBlockState(pos.relative(side));
            if (isCable(state) && state.getValue(joint(side.getOpposite())) == Joint.PLUG) {
                return side;
            }
        }
        return null;
    }

    // Placed: it joins up to two neighbours, the block it was placed against first. A Link Conduit only counts if it has a
    // free side (it joins back on its next shape update).
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        List<Direction> order = new ArrayList<>();
        order.add(context.getClickedFace().getOpposite());
        for (Direction side : Direction.values()) {
            if (!order.contains(side)) {
                order.add(side);
            }
        }
        BlockState state = defaultBlockState();
        int count = 0;
        for (Direction side : order) {
            if (count == 2) {
                break;
            }
            Joint joint = candidate(level, pos, side);
            if (joint == Joint.CABLE && joints(level.getBlockState(pos.relative(side))) >= 2) {
                continue;
            }
            if (joint != Joint.NONE) {
                state = state.setValue(joint(side), joint);
                count++;
            }
        }
        return state;
    }

    // A neighbour changed: a Link Conduit joins where it joins back (and it has a free side); a plug stays while there's
    // still something to plug into; a free end plugs into a machine put next to it.
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        Joint current = state.getValue(joint(side));
        Joint next;
        if (isCable(neighborState)) {
            boolean joinsBack = neighborState.getValue(joint(side.getOpposite())) == Joint.CABLE;
            next = joinsBack && (current == Joint.CABLE || joints(state) < 2) ? Joint.CABLE : Joint.NONE;
        } else if (current == Joint.PLUG || current == Joint.NONE && joints(state) < 2) {
            next = candidate(level, pos, side) == Joint.PLUG ? Joint.PLUG : Joint.NONE;
        } else {
            next = Joint.NONE;
        }
        BlockState updated = state.setValue(joint(side), next);
        // A lone block left over from a run isn't lit.
        return joints(updated) == 0 ? updated.setValue(LIT, false) : updated;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.computeIfAbsent(state, LinkConduitBlock::makeShape);
    }

    private static VoxelShape makeShape(BlockState state) {
        VoxelShape shape = CORE;
        for (Direction side : Direction.values()) {
            Joint joint = state.getValue(joint(side));
            if (joint != Joint.NONE) {
                shape = Shapes.or(shape, ARMS.get(side));
            }
            if (joint == Joint.PLUG) {
                shape = Shapes.or(shape, PLATES.get(side));
            }
        }
        return shape;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            rotated = rotated.setValue(joint(rotation.rotate(side)), state.getValue(joint(side)));
        }
        return rotated;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            mirrored = mirrored.setValue(joint(mirror.mirror(side)), state.getValue(joint(side)));
        }
        return mirrored;
    }

    // --- Telling the Wired Machine Links at the ends ---

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && !oldState.is(this)) {
            notifyRun(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this)) {
            for (Direction side : Direction.values()) {
                if (state.getValue(joint(side)) != Joint.NONE) {
                    notifyRun(level, pos.relative(side));
                }
            }
        }
    }

    // Asks the Wired Machine Link at either end of the run through pos (if it's a Link Conduit or a Wired Machine Link) to
    // trace it again.
    public static void notifyRun(Level level, BlockPos start) {
        int limit = 2 * ArcforgeConfig.LINK_CONDUIT_MAX_LENGTH.getAsInt() + 4;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < limit) {
            BlockPos pos = queue.poll();
            if (!seen.add(pos) || !level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof WiredMachineLinkBlockEntity link) {
                link.requestTrace();
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!isCable(state)) {
                continue;
            }
            for (Direction side : Direction.values()) {
                if (state.getValue(joint(side)) != Joint.NONE) {
                    queue.add(pos.relative(side));
                }
            }
        }
    }
}
