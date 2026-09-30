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
import net.zagdrath.arcforge.blockentity.multiblock.BiogasDigesterBlockEntity;
import net.zagdrath.arcforge.multiblock.BiogasDigesterStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.PortHolder;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Biogas Digester's controller, in the middle of one of the tank's sides: its sight glass and gauge face out of the
// tank (FACING), and its block entity runs the digester and holds its contents. LIT while it digests.
public class BiogasDigesterControllerBlock extends BaseEntityBlock implements DigesterPart, PortHolder {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public BiogasDigesterControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMED, false).setValue(LIT, false));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BiogasDigesterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel
                ? createTickerHelper(type, ModBlockEntityTypes.BIOGAS_DIGESTER.get(),
                        (innerLevel, pos, blockState, digester) -> digester.serverTick(serverLevel))
                : null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            BiogasDigesterStructure.notifyChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BiogasDigesterStructure.notifyChanged(level, pos);
    }

    // The controller always opens its GUI, even while the tank is incomplete, so its contents stay reachable.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof BiogasDigesterBlockEntity digester) {
            player.openMenu(digester, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BiogasDigesterBlockEntity digester && digester.isFormed() ? digester : null;
    }

    // Wrench (Configure mode): recheck, and say whether the tank is formed.
    @Override
    public InteractionResult useWrench(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockEntity(pos) instanceof BiogasDigesterBlockEntity digester)) {
            return InteractionResult.PASS;
        }
        digester.checkNow();
        Player player = context.getPlayer();
        if (player != null) {
            player.sendOverlayMessage(Component.translatable(digester.isFormed()
                    ? "message.arcforge.biogas_digester.formed" : "message.arcforge.biogas_digester.incomplete"));
        }
        return InteractionResult.SUCCESS;
    }

    // Gas bubbling up behind the sight glass while it digests.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || random.nextInt(5) != 0) {
            return;
        }
        Direction facing = state.getValue(FACING);
        double x = pos.getX() + 0.5 + facing.getStepX() * 0.51 + (random.nextDouble() - 0.5) * 0.3;
        double z = pos.getZ() + 0.5 + facing.getStepZ() * 0.51 + (random.nextDouble() - 0.5) * 0.3;
        level.addParticle(ParticleTypes.BUBBLE_POP, x, pos.getY() + 0.4 + random.nextDouble() * 0.3, z, 0.0, 0.01, 0.0);
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
        builder.add(FACING, FORMED, LIT);
    }
}
