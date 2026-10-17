/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.machine.interaction.MachineInteractions;

// A nuclear tier block with a block entity of its own (an ECCS Accumulator or Pump, a Freeze Plug, a Drain Tank, a
// Salt Processing Unit...): it makes its block entity, ticks it on the server if it has a server tick (ServerTicking),
// fills or empties it from a held bucket if it takes one (FluidInteractable), and has the shape it's given.
public class NuclearEntityBlock extends Block implements EntityBlock {
    // A block entity that does something each server tick.
    public interface ServerTicking {
        void serverTick(ServerLevel level, BlockPos pos, BlockState state);
    }

    private final Supplier<? extends BlockEntityType<?>> type;
    private final VoxelShape shape;

    public NuclearEntityBlock(Supplier<? extends BlockEntityType<?>> type, VoxelShape shape, BlockBehaviour.Properties properties) {
        super(properties);
        this.type = type;
        this.shape = shape;
    }

    public NuclearEntityBlock(Supplier<? extends BlockEntityType<?>> type, BlockBehaviour.Properties properties) {
        this(type, Shapes.block(), properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return type.get().create(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel server) || type != this.type.get()) {
            return null;
        }
        return (inner, pos, blockState, blockEntity) -> {
            if (blockEntity instanceof ServerTicking ticking) {
                ticking.serverTick(server, pos, blockState);
            }
        };
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult fluid = MachineInteractions.useFluidContainer(level, pos, player, hand);
        return fluid != null ? fluid : super.useItemOn(stack, state, level, pos, player, hand, hit);
    }
}
