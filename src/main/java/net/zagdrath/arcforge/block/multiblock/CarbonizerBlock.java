/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.MachineInteractions;
import net.zagdrath.arcforge.multiblock.CarbonizerStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// One block of a Carbonizer. Slices (1 wide x 2 or 3 tall x 2 or 3 deep) placed side by side form one
// structure; ROW/HALF/DEPTH record where each block sits so the models can draw one continuous casing
// with a door per slice, and TALL picks the 3-tall front and door art. See CarbonizerStructure for formation and CarbonizerBlockEntity for processing.
public class CarbonizerBlock extends BaseEntityBlock implements MultiblockPart {
    // Position along the row of slices. NONE = not part of a formed structure.
    public enum Row implements StringRepresentable {
        NONE("none"), SINGLE("single"), LEFT("left"), MIDDLE("middle"), RIGHT("right");

        private final String name;

        Row(String name) {
            this.name = name;
        }

        public static Row of(int index, int count) {
            if (count == 1) return SINGLE;
            if (index == 0) return LEFT;
            return index == count - 1 ? RIGHT : MIDDLE;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public enum Half implements StringRepresentable {
        // MIDDLE only in 3-tall slices.
        TOP("top"), MIDDLE("middle"), BOTTOM("bottom");

        private final String name;

        Half(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    // FRONT = the row carrying the slice doors, on the facing side. MIDDLE only in 3-deep slices.
    public enum Depth implements StringRepresentable {
        FRONT("front"), MIDDLE("middle"), BACK("back");

        private final String name;

        Depth(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Row> ROW = EnumProperty.create("h", Row.class);
    public static final EnumProperty<Half> HALF = EnumProperty.create("v", Half.class);
    public static final EnumProperty<Depth> DEPTH = EnumProperty.create("d", Depth.class);
    // The slice is 3 tall (the front blocks show the tall door).
    public static final BooleanProperty TALL = BooleanProperty.create("tall");
    // The block's slice is processing: front blocks open their door and show the fire.
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public CarbonizerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ROW, Row.NONE)
                .setValue(HALF, Half.BOTTOM)
                .setValue(DEPTH, Depth.FRONT)
                .setValue(TALL, false)
                .setValue(LIT, false)
                .setValue(MultiblockPorts.PORT, SideMode.NONE));
    }

    public static boolean isFormed(BlockState state) {
        return state.getValue(ROW) != Row.NONE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CarbonizerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // The server runs the master; clients animate the slice doors.
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.CARBONIZER.get(),
                        (innerLevel, pos, blockState, carbonizer) -> CarbonizerBlockEntity.serverTick(serverLevel, carbonizer))
                : createTickerHelper(type, ModBlockEntityTypes.CARBONIZER.get(),
                        (innerLevel, pos, blockState, carbonizer) -> CarbonizerBlockEntity.clientTick(carbonizer, blockState));
    }

    // --- Formation: placing, breaking or wrenching any block re-evaluates its structure ---

    // The block entity doesn't exist yet during onPlace, so formation runs a tick later.
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        CarbonizerStructure.rebuild(level, pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this)) {
            CarbonizerStructure.rebuildAround(level, pos);
        }
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CarbonizerBlockEntity carbonizer ? carbonizer.getFormedMaster() : null;
    }

    // Wrench (Configure mode): rotate a loose block, then recheck the structure.
    @Override
    public InteractionResult useWrench(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (!isFormed(state)) {
            level.setBlock(pos, state.setValue(FACING, state.getValue(FACING).getClockWise()), Block.UPDATE_ALL);
        }
        if (level instanceof ServerLevel serverLevel) {
            CarbonizerStructure.rebuild(serverLevel, pos);
        }
        return InteractionResult.SUCCESS;
    }

    // --- Interaction: every block opens the master's GUI ---

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CarbonizerBlockEntity carbonizer) {
            CarbonizerBlockEntity target = carbonizer.getFormedMaster() != null ? carbonizer.getFormedMaster() : carbonizer;
            player.openMenu(target, target.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    // Empty buckets (or other containers) clicked on any block take creosote from the buffer tank.
    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        InteractionResult result = MachineInteractions.useFluidContainer(level, pos, player, hand);
        return result != null ? result : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ROW, HALF, DEPTH, TALL, LIT, MultiblockPorts.PORT, MultiblockPorts.PORT_FACE);
    }

    // Smoke curls out of the open doors of working slices.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || state.getValue(DEPTH) != Depth.FRONT || random.nextInt(3) != 0) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.55 + (random.nextDouble() - 0.5) * 0.4;
        double y = pos.getY() + 0.3 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.55 + (random.nextDouble() - 0.5) * 0.4;
        level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.03, 0.0);
        if (state.getValue(HALF) == Half.BOTTOM && random.nextInt(8) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.5F, 0.9F, false);
        }
    }
}
