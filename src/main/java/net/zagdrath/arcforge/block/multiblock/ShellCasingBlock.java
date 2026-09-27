/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.multiblock.ShellStructure;

// A casing of a steam array (see ShellStructure). Loose casings are ordinary solid blocks; formed ones
// (FORMED) draw only the outside of the structure as one connected surface and let light through, so the
// machine's insides (drawn by its renderer) show through the windows. They keep full collision, so the
// machine is still solid.
public abstract class ShellCasingBlock extends BaseEntityBlock implements MultiblockPart {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    protected ShellCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    public abstract ShellStructure structure();

    protected abstract BlockEntityType<? extends ShellMultiblockBlockEntity> blockEntityType();

    public static boolean isFormed(BlockState state) {
        return state.getBlock() instanceof ShellCasingBlock && state.getValue(FORMED);
    }

    // The state this casing takes when the structure forms / breaks.
    public BlockState formedState(BlockState state, ShellStructure.Shell shell, BlockPos pos) {
        return state.setValue(FORMED, true);
    }

    public BlockState looseState(BlockState state) {
        return state.setValue(FORMED, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    // Only the master of a formed structure runs.
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel serverLevel) || !state.getValue(FORMED) || type != blockEntityType()) {
            return null;
        }
        BlockEntityTicker<ShellMultiblockBlockEntity> ticker = (innerLevel, pos, blockState, part) ->
                ShellMultiblockBlockEntity.serverTick(serverLevel, pos, blockState, part);
        return (BlockEntityTicker<T>) (BlockEntityTicker<?>) ticker;
    }

    // --- Light: formed casings are see-through for light ---

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return state.getValue(FORMED) ? Shapes.empty() : super.getOcclusionShape(state);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return state.getValue(FORMED);
    }

    @Override
    protected int getLightDampening(BlockState state) {
        return state.getValue(FORMED) ? 0 : super.getLightDampening(state);
    }

    // --- Formation: placing or breaking a casing, or filling the core, re-evaluates the structure ---

    // Completed by a player: faces them (and a turbine cube runs the way they face).
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            rebuild(serverLevel, pos, placer);
        }
    }

    static void rebuild(ServerLevel level, BlockPos pos, @Nullable LivingEntity placer, ShellStructure structure) {
        Direction.Axis axis = placer != null ? placer.getDirection().getAxis() : null;
        Direction facing = placer != null ? placer.getDirection().getOpposite() : null;
        structure.rebuild(level, pos, axis, facing);
    }

    private void rebuild(ServerLevel level, BlockPos pos, @Nullable LivingEntity placer) {
        rebuild(level, pos, placer, structure());
    }

    // Placed any other way (e.g. by commands), formation runs a tick later.
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    // Something appeared beside a formed casing: if it is in the hollow core, the structure breaks.
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(FORMED) && !neighbourState.isAir()) {
            ticks.scheduleTick(pos, this, 1);
        }
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(FORMED)) {
            checkCore(level, pos, structure());
        } else {
            rebuild(level, pos, null);
        }
    }

    // Breaks the formed structure at pos if its core isn't empty any more.
    static void checkCore(ServerLevel level, BlockPos pos, ShellStructure structure) {
        ShellStructure.Shell shell = structure.findFormed(level, pos);
        if (shell == null) {
            return;
        }
        for (BlockPos inside : shell.positions()) {
            if (shell.isCore(inside) && !level.getBlockState(inside).isAir()) {
                structure.onRemoved(level, pos);
                return;
            }
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (!level.getBlockState(pos).is(this) && state.getValue(FORMED)) {
            structure().onRemoved(level, pos);
        }
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return structure().findMaster(level, pos);
    }

    // Sneak: pick the casing up. Otherwise recheck the structure.
    @Override
    public InteractionResult useWrench(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (player != null && player.isSecondaryUseActive()) {
            level.destroyBlock(pos, true, player);
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel serverLevel && !level.getBlockState(pos).getValue(FORMED)) {
            rebuild(serverLevel, pos, player);
        }
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.4F, 1.6F);
        return InteractionResult.SUCCESS;
    }

    // Any casing of a formed structure opens its GUI.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return openMenu(level, pos, player, structure());
    }

    static InteractionResult openMenu(Level level, BlockPos pos, Player player, ShellStructure structure) {
        ShellMultiblockBlockEntity master = structure.findMaster(level, pos);
        if (master == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.openMenu(master, master.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }
}
