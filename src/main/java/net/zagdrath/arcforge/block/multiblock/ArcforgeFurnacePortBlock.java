/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.multiblock.ArcforgeFurnaceStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Arcforge Furnace's front-bottom block, with a small window onto the fire. Its facing is the
// furnace's front, and its block entity runs the furnace and holds the inventory.
public class ArcforgeFurnacePortBlock extends BaseEntityBlock implements MultiblockPart {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ArcforgeFurnacePortBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcforgeFurnaceBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.ARCFORGE_FURNACE.get(),
                        (innerLevel, pos, blockState, furnace) -> ArcforgeFurnaceBlockEntity.serverTick(serverLevel, pos, blockState, furnace))
                : null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            ArcforgeFurnaceStructure.notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ArcforgeFurnaceStructure.notifyChanged(level, pos);
    }

    // The port always opens its GUI, even while the furnace is incomplete, so its contents stay reachable.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof ArcforgeFurnaceBlockEntity furnace) {
            player.openMenu(furnace, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ArcforgeFurnaceBlockEntity furnace && furnace.isFormed() ? furnace : null;
    }

    // Wrench (Configure mode): rotate the port while the furnace is incomplete, then recheck.
    @Override
    public InteractionResult useWrench(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!(level.getBlockEntity(pos) instanceof ArcforgeFurnaceBlockEntity furnace)) {
            return InteractionResult.PASS;
        }
        furnace.checkNow();
        if (!furnace.isFormed()) {
            BlockState state = level.getBlockState(pos);
            level.setBlock(pos, state.setValue(FACING, state.getValue(FACING).getClockWise()), Block.UPDATE_ALL);
            furnace.checkNow();
        }
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(furnace.isFormed()
                    ? "message.arcforge.arcforge_furnace.formed" : "message.arcforge.arcforge_furnace.incomplete"));
        }
        return InteractionResult.SUCCESS;
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
        builder.add(FACING, LIT);
    }

    // Flames flicker in the window, and smoke rises out of the stack.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.52;
        double y = pos.getY() + 0.3 + random.nextDouble() * 0.3;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.52;
        double spread = (random.nextDouble() - 0.5) * 0.3;
        level.addParticle(ParticleTypes.FLAME, x + facing.getStepZ() * spread, y, z + facing.getStepX() * spread, 0.0, 0.0, 0.0);
        if (random.nextDouble() < 0.1) {
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 0.8F, false);
        }
        if (level.getBlockEntity(pos) instanceof ArcforgeFurnaceBlockEntity furnace && furnace.isFormed()) {
            BlockPos top = ArcforgeFurnaceStructure.center(pos, facing).above(ArcforgeFurnaceStructure.HEIGHT);
            level.addParticle(ParticleTypes.LARGE_SMOKE, top.getX() + 0.5, top.getY() + 0.1, top.getZ() + 0.5, 0.0, 0.07, 0.0);
        }
    }
}
