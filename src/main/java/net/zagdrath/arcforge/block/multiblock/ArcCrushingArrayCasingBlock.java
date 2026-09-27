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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.multiblock.ArcCrushingArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.ArcCrushingArrayStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A casing of the Arc Crushing Array. 27 of them in a 3x3x3 cube form the machine: the centre block
// (part=center) draws the whole 48px machine model and runs it; the others (part=other) draw nothing.
// Formed casings let light through (the centre is enclosed, so its model would otherwise render
// black) but keep full collision and selection boxes, so the machine is still solid and clickable.
public class ArcCrushingArrayCasingBlock extends BaseEntityBlock implements MultiblockPart {
    public enum Part implements StringRepresentable {
        NONE("none"), CENTER("center"), OTHER("other");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ArcCrushingArrayCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.NONE).setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public static boolean isFormed(BlockState state) {
        return state.getValue(PART) != Part.NONE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, FACING, LIT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcCrushingArrayBlockEntity(pos, state);
    }

    // Only the centre of a formed Array runs.
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel && state.getValue(PART) == Part.CENTER
                ? createTickerHelper(type, ModBlockEntityTypes.ARC_CRUSHING_ARRAY.get(),
                        (innerLevel, pos, blockState, array) -> array.serverTick(serverLevel, pos, blockState))
                : null;
    }

    // --- Light: formed casings are see-through for light, so the machine model is lit normally ---

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return isFormed(state) ? Shapes.empty() : super.getOcclusionShape(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return isFormed(state);
    }

    @Override
    protected int getLightDampening(BlockState state) {
        return isFormed(state) ? 0 : super.getLightDampening(state);
    }

    // --- Formation: placing, breaking or wrenching a casing re-evaluates its structure ---

    // Completed by a player: the front faces them.
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            ArcCrushingArrayStructure.rebuild(serverLevel, pos, placer != null ? placer.position() : null);
        }
    }

    // Placed any other way (e.g. by commands), formation runs a tick later.
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        ArcCrushingArrayStructure.rebuild(level, pos, null);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this)) {
            ArcCrushingArrayStructure.rebuildAround(level, pos);
        }
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return ArcCrushingArrayStructure.findController(level, pos);
    }

    // Sneak: pick the casing up. On a formed Array, turn it clockwise; otherwise recheck the structure.
    @Override
    public InteractionResult useWrench(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player != null && player.isSecondaryUseActive()) {
            level.destroyBlock(pos, true, player);
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos center = ArcCrushingArrayStructure.findCenter(level, pos);
        if (center != null) {
            BlockState centerState = level.getBlockState(center);
            level.setBlock(center, centerState.setValue(FACING, centerState.getValue(FACING).getClockWise()), Block.UPDATE_ALL);
            if (level.getBlockEntity(center) instanceof ArcCrushingArrayBlockEntity array) {
                array.onStructureChanged();
            }
        } else {
            ArcCrushingArrayStructure.rebuild(serverLevel, pos, player != null ? player.position() : null);
        }
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.6F);
        return InteractionResult.SUCCESS;
    }

    // Any casing of a formed Array opens its GUI.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos center = ArcCrushingArrayStructure.findCenter(level, pos);
        if (center == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level.getBlockEntity(center) instanceof ArcCrushingArrayBlockEntity array) {
            player.openMenu(array, center);
        }
        return InteractionResult.SUCCESS;
    }

    // Arc sparks at the rollers while crushing.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) != Part.CENTER || !state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double along = (random.nextDouble() - 0.5) * 1.2;
        double x = pos.getX() + 0.5 + facing.getStepX() * 1.4 + facing.getClockWise().getStepX() * along;
        double y = pos.getY() + 0.2 + random.nextDouble() * 0.6;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 1.4 + facing.getClockWise().getStepZ() * along;
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.0, 0.0);
        }
        if (random.nextDouble() < 0.08) {
            level.playLocalSound(x, y, z, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.35F, 0.5F, false);
        }
    }
}
