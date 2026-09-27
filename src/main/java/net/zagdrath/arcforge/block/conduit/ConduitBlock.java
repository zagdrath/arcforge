/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.conduit;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitCapabilities;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.conduit.SideSetting;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A 6px pipe that auto-connects to conduits of the same type (any tier) and to machines. A side facing a
// machine follows the machine's side configuration ("auto"), unless the wrench forces it to input,
// output or disabled. The block state holds the resulting connection; the setting lives in the block
// entity. Whether a run is drawn as one straight tube is derived from the six sides (see straightAxis)
// rather than stored, which keeps the block state count down.
public class ConduitBlock extends BaseEntityBlock {
    public static final EnumProperty<ConnectionMode> NORTH = EnumProperty.create("north", ConnectionMode.class);
    public static final EnumProperty<ConnectionMode> SOUTH = EnumProperty.create("south", ConnectionMode.class);
    public static final EnumProperty<ConnectionMode> EAST = EnumProperty.create("east", ConnectionMode.class);
    public static final EnumProperty<ConnectionMode> WEST = EnumProperty.create("west", ConnectionMode.class);
    public static final EnumProperty<ConnectionMode> UP = EnumProperty.create("up", ConnectionMode.class);
    public static final EnumProperty<ConnectionMode> DOWN = EnumProperty.create("down", ConnectionMode.class);
    private static final Map<Direction, EnumProperty<ConnectionMode>> PROPERTIES = new EnumMap<>(Map.of(
            Direction.NORTH, NORTH, Direction.SOUTH, SOUTH, Direction.EAST, EAST,
            Direction.WEST, WEST, Direction.UP, UP, Direction.DOWN, DOWN));

    // Core half-width in blocks; hits closer to the centre than this count as "on the core".
    private static final double CORE_HALF_SIZE = 3.0 / 16.0 + 1.0E-4;

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> PIPE_ARMS = Shapes.rotateAll(Block.box(5, 5, 0, 11, 11, 5));
    private static final Map<Direction, VoxelShape> INPUT_ARMS = Shapes.rotateAll(Shapes.or(Block.box(5, 5, 2, 11, 11, 5), Block.box(6, 6, 0, 10, 10, 2)));
    private static final Map<Direction, VoxelShape> OUTPUT_ARMS = Shapes.rotateAll(Shapes.or(Block.box(5, 5, 2, 11, 11, 5), Block.box(4, 4, 0, 12, 12, 2)));
    private static final Map<Direction.Axis, VoxelShape> STRAIGHT = Shapes.rotateAllAxis(Block.box(5, 5, 0, 11, 11, 16));

    private final ConduitType conduitType;
    private final ConduitTier tier;
    private final Map<BlockState, VoxelShape> shapeCache = new ConcurrentHashMap<>();

    public ConduitBlock(BlockBehaviour.Properties properties, ConduitType conduitType, ConduitTier tier) {
        super(properties);
        this.conduitType = conduitType;
        this.tier = tier;
        BlockState state = stateDefinition.any();
        for (EnumProperty<ConnectionMode> property : PROPERTIES.values()) {
            state = state.setValue(property, ConnectionMode.NONE);
        }
        registerDefaultState(state);
    }

    public ConduitType getConduitType() {
        return conduitType;
    }

    public ConduitTier getTier() {
        return tier;
    }

    public static EnumProperty<ConnectionMode> property(Direction side) {
        return PROPERTIES.get(side);
    }

    public static ConnectionMode mode(BlockState state, Direction side) {
        return state.getValue(PROPERTIES.get(side));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    // --- Shape ---

    // The axis of a straight run: both sides on one axis are pipes and the other four are empty.
    public static Direction.@Nullable Axis straightAxis(BlockState state) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            Direction positive = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
            if (mode(state, positive) != ConnectionMode.PIPE || mode(state, positive.getOpposite()) != ConnectionMode.PIPE) {
                continue;
            }
            boolean othersEmpty = true;
            for (Direction side : Direction.values()) {
                if (side.getAxis() != axis && mode(state, side) != ConnectionMode.NONE) {
                    othersEmpty = false;
                }
            }
            if (othersEmpty) {
                return axis;
            }
        }
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeCache.computeIfAbsent(state, ConduitBlock::buildShape);
    }

    private static VoxelShape buildShape(BlockState state) {
        Direction.Axis straight = straightAxis(state);
        if (straight != null) {
            return STRAIGHT.get(straight);
        }
        VoxelShape shape = CORE;
        for (Direction side : Direction.values()) {
            shape = switch (mode(state, side)) {
                case PIPE -> Shapes.or(shape, PIPE_ARMS.get(side));
                case INPUT -> Shapes.or(shape, INPUT_ARMS.get(side));
                case OUTPUT -> Shapes.or(shape, OUTPUT_ARMS.get(side));
                case NONE -> shape;
            };
        }
        return shape.optimize();
    }

    // --- Connections ---

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.values()) {
            state = state.setValue(property(side), computeSide(context.getLevel(), context.getClickedPos(), side, ConnectionMode.NONE));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        ConnectionMode current = mode(state, side);
        ConnectionMode updated = computeSide(level, pos, side, current);
        if (updated != current && level instanceof ServerLevel serverLevel) {
            ConduitNetworkManager.get(serverLevel).markDirty(pos);
        }
        return state.setValue(property(side), updated);
    }

    // Decides a side's connection from its wrench setting:
    //  - a same-type conduit connects unless either end is disabled
    //    (or, for fluid conduits, the two hold different fluids);
    //  - next to anything else, auto asks the block (see ConduitCapabilities.autoMode), a forced
    //    input/output applies while the block has the capability, and disabled never connects;
    //  - once the neighbour is gone (air) the setting goes back to auto.
    ConnectionMode computeSide(LevelReader level, BlockPos pos, Direction side, ConnectionMode current) {
        BlockPos neighbourPos = pos.relative(side);
        ConduitBlockEntity own = level.getBlockEntity(pos) instanceof ConduitBlockEntity be ? be : null;
        SideSetting setting = own != null ? own.getSetting(side) : SideSetting.AUTO;
        BlockState neighbourState = level.getBlockState(neighbourPos);

        if (neighbourState.getBlock() instanceof ConduitBlock other && other.conduitType == conduitType) {
            ConduitBlockEntity neighbour = level.getBlockEntity(neighbourPos) instanceof ConduitBlockEntity be ? be : null;
            boolean disabled = setting == SideSetting.DISABLED || (neighbour != null && neighbour.isSideDisabled(side.getOpposite()));
            if (disabled || hasConflictingFluids(own, neighbour)) {
                return ConnectionMode.NONE;
            }
            return ConnectionMode.PIPE;
        }

        // Capabilities can only be queried on a full level with the neighbour loaded; keep what we have.
        if (!(level instanceof Level fullLevel) || !fullLevel.isLoaded(neighbourPos)) {
            return current == ConnectionMode.PIPE ? ConnectionMode.NONE : current;
        }
        if (neighbourState.isAir()) {
            if (own != null) {
                own.setSetting(side, SideSetting.AUTO);
            }
            return ConnectionMode.NONE;
        }
        return switch (setting) {
            case AUTO -> ConduitCapabilities.autoMode(fullLevel, pos, side, conduitType);
            case INPUT -> ConduitCapabilities.canConnect(fullLevel, pos, side, conduitType) ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case OUTPUT -> ConduitCapabilities.canConnect(fullLevel, pos, side, conduitType) ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case DISABLED -> ConnectionMode.NONE;
        };
    }

    // A fluid network carries one fluid at a time, so conduits holding different fluids stay apart.
    private static boolean hasConflictingFluids(@Nullable ConduitBlockEntity a, @Nullable ConduitBlockEntity b) {
        if (a == null || b == null) {
            return false;
        }
        FluidStack fa = a.getFluid();
        FluidStack fb = b.getFluid();
        return !fa.isEmpty() && !fb.isEmpty() && !FluidStack.isSameFluidSameComponents(fa, fb);
    }

    // Re-evaluates the conduits touching a machine, e.g. after its side configuration or facing changed.
    public static void refreshAround(Level level, BlockPos machinePos) {
        if (level.isClientSide()) {
            return;
        }
        for (Direction side : Direction.values()) {
            refreshConnections(level, machinePos.relative(side));
        }
    }

    // Re-evaluates every side, e.g. after a fluid network empties and can now merge with its neighbours.
    public static void refreshConnections(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ConduitBlock conduit)) {
            return;
        }
        BlockState updated = state;
        for (Direction side : Direction.values()) {
            updated = updated.setValue(property(side), conduit.computeSide(level, pos, side, mode(state, side)));
        }
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
            if (level instanceof ServerLevel serverLevel) {
                ConduitNetworkManager.get(serverLevel).markDirty(pos);
            }
        }
    }

    // --- Wrench (server side; see WrenchItem) ---

    public InteractionResult useWrench(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockState state = level.getBlockState(pos);
        boolean sneaking = player != null && player.isSecondaryUseActive();

        Vec3 offset = context.getClickLocation().subtract(Vec3.atCenterOf(pos));
        boolean onCore = Math.abs(offset.x) <= CORE_HALF_SIZE && Math.abs(offset.y) <= CORE_HALF_SIZE && Math.abs(offset.z) <= CORE_HALF_SIZE;
        if (sneaking && onCore) {
            level.destroyBlock(pos, true, player);
            return InteractionResult.SUCCESS;
        }

        Direction side = Direction.getApproximateNearest(offset);
        BlockPos neighbourPos = pos.relative(side);
        Component result;

        ConduitBlockEntity own = level.getBlockEntity(pos) instanceof ConduitBlockEntity be ? be : null;
        BlockState neighbourState = level.getBlockState(neighbourPos);

        if (own == null) {
            result = Component.translatable("message.arcforge.conduit.nothing");
        } else if (neighbourState.getBlock() instanceof ConduitBlock other && other.conduitType == conduitType
                && level.getBlockEntity(neighbourPos) instanceof ConduitBlockEntity neighbour) {
            SideSetting setting = mode(state, side) == ConnectionMode.PIPE ? SideSetting.DISABLED : SideSetting.AUTO;
            own.setSetting(side, setting);
            neighbour.setSetting(side.getOpposite(), setting);
            refreshConnections(level, pos);
            refreshConnections(level, neighbourPos);
            boolean connected = mode(level.getBlockState(pos), side) == ConnectionMode.PIPE;
            result = Component.translatable(connected ? "message.arcforge.conduit.connected" : "message.arcforge.conduit.disconnected");
        } else if (!neighbourState.isAir()
                && (own.getSetting(side) != SideSetting.AUTO || ConduitCapabilities.canConnect(level, pos, side, conduitType))) {
            SideSetting next = own.getSetting(side).next(sneaking);
            own.setSetting(side, next);
            refreshConnections(level, pos);
            ConnectionMode effective = mode(level.getBlockState(pos), side);
            result = next == SideSetting.AUTO
                    ? Component.translatable("message.arcforge.conduit.auto", effective.getDisplayName())
                    : next.getDisplayName();
        } else {
            result = Component.translatable("message.arcforge.conduit.nothing");
        }

        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.8F);
        if (player != null) {
            player.sendOverlayMessage(Component.translatable("message.arcforge.conduit.side",
                    Component.translatable("conduit_side.arcforge." + side.getSerializedName()), result));
        }
        return InteractionResult.SUCCESS;
    }

    // --- Block entity ---

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConduitBlockEntity(pos, state);
    }

    // Clients move item packets between server updates; everything else is driven by the network manager.
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() && conduitType == ConduitType.ITEM
                ? createTickerHelper(type, ModBlockEntityTypes.TRANSPARENT_CONDUIT.get(), ConduitBlockEntity::clientTick)
                : null;
    }
}
