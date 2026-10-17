/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.nuclear.EquipmentHatchBlockEntity;

// A containment's Equipment Hatch: a 3x3 opening in the wall with a round, bolted steel door, big enough to bring
// machines in. Placed as one item (its middle where it's put, facing the placer), it fills a 3x3 square of the wall;
// breaking any part breaks it all (the middle drops it). Using it opens or shuts the whole door (it swings out on its
// hinge); shut, it seals the containment (see Containment), open, it's walked through and the containment isn't sealed.
// Its parts' block models are empty: the middle's block entity draws the hatch (a large model; see EquipmentHatchRenderer).
public class EquipmentHatchBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    // The part: (column + 1) + (row + 1) * 3, columns along the facing's clockwise side, rows up; 4 is the middle.
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 8);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final int MIDDLE = 4;

    public EquipmentHatchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, MIDDLE).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, OPEN);
    }

    public static int column(int part) {
        return part % 3 - 1;
    }

    public static int row(int part) {
        return part / 3 - 1;
    }

    // Where a part is, from the middle.
    public static BlockPos partPos(BlockPos middle, Direction facing, int part) {
        return middle.relative(facing.getClockWise(), column(part)).above(row(part));
    }

    public static BlockPos middleOf(BlockPos pos, BlockState state) {
        int part = state.getValue(PART);
        return pos.relative(state.getValue(FACING).getClockWise(), -column(part)).below(row(part));
    }

    // Needs the whole 3x3 free.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos middle = context.getClickedPos();
        Level level = context.getLevel();
        for (int part = 0; part < 9; part++) {
            BlockPos pos = partPos(middle, facing, part);
            if (pos.getY() > level.getMaxY() || pos.getY() < level.getMinY() || !level.getBlockState(pos).canBeReplaced(context)) {
                return null;
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (int part = 0; part < 9; part++) {
            if (part != MIDDLE) {
                level.setBlock(partPos(pos, facing, part), state.setValue(PART, part), Block.UPDATE_ALL);
            }
        }
    }

    // A part whose neighbour in the hatch has gone goes too.
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        Direction facing = state.getValue(FACING);
        if (direction.getAxis() != facing.getAxis()) {
            int part = state.getValue(PART);
            int column = column(part), row = row(part);
            Direction right = facing.getClockWise();
            int dc = direction == right ? 1 : direction == right.getOpposite() ? -1 : 0;
            int dr = direction == Direction.UP ? 1 : direction == Direction.DOWN ? -1 : 0;
            int nc = column + dc, nr = row + dr;
            if (Math.abs(nc) <= 1 && Math.abs(nr) <= 1) {
                int expected = (nc + 1) + (nr + 1) * 3;
                if (!(neighborState.is(this) && neighborState.getValue(PART) == expected && neighborState.getValue(FACING) == facing)) {
                    return Blocks.AIR.defaultBlockState();
                }
                if (neighborState.getValue(OPEN) != state.getValue(OPEN)) {
                    return state.setValue(OPEN, neighborState.getValue(OPEN));
                }
            }
        }
        return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    // A creative player breaking an outer part mustn't leave the middle's drop.
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && player.preventsBlockDrops() && state.getValue(PART) != MIDDLE) {
            BlockPos middle = middleOf(pos, state);
            if (level.getBlockState(middle).is(this)) {
                level.setBlock(middle, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        boolean open = !state.getValue(OPEN);
        BlockPos middle = middleOf(pos, state);
        Direction facing = state.getValue(FACING);
        for (int part = 0; part < 9; part++) {
            BlockPos at = partPos(middle, facing, part);
            BlockState there = level.getBlockState(at);
            if (there.is(this)) {
                level.setBlock(at, there.setValue(OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
        }
        level.playSound(null, middle, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.6F);
        player.sendOverlayMessage(Component.translatable(open ? "message.arcforge.equipment_hatch.opened" : "message.arcforge.equipment_hatch.closed"));
        return InteractionResult.SUCCESS;
    }

    // Shut, a solid wall; open, the frame only round the edge parts' outer sides (nothing to walk into).
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? frame(state) : Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    private static VoxelShape frame(BlockState state) {
        int part = state.getValue(PART);
        if (part == MIDDLE) {
            return Block.box(0, 0, 0, 16, 1, 16);
        }
        return Block.box(2, 2, 2, 14, 14, 14);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == MIDDLE ? new EquipmentHatchBlockEntity(pos, state) : null;
    }
}
