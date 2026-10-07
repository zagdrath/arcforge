/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.blockentity.farming.CopperSprinklerBlockEntity;
import net.zagdrath.arcforge.machine.interaction.MachineInteractions;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Copper Sprinkler (see CopperSprinklerBlockEntity): a copper riser with a spray head on top, sitting on the pipe or
// tank that feeds it. A water bucket fills it by hand. RUNNING while it has water, which shows the spray.
public class CopperSprinklerBlock extends BaseEntityBlock {
    private static final MapCodec<CopperSprinklerBlock> CODEC = simpleCodec(CopperSprinklerBlock::new);

    @Override
    protected MapCodec<CopperSprinklerBlock> codec() {
        return CODEC;
    }

    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    // The flanged foot, the riser and the spray head.
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(4, 0, 4, 12, 2, 12),
            Block.box(6, 2, 6, 10, 9, 10),
            Block.box(4, 9, 4, 12, 12, 12));

    public CopperSprinklerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = MachineInteractions.useFluidContainer(level, pos, player, hand);
        return result != null ? result : InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    // Water arcing out of the head in every direction, and drips.
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(RUNNING)) {
            return;
        }
        double x = pos.getX() + 0.5, y = pos.getY() + 0.8, z = pos.getZ() + 0.5;
        for (int i = 0; i < 4; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.15 + random.nextDouble() * 0.2;
            level.addParticle(ParticleTypes.SPLASH, x, y, z, Math.cos(angle) * speed, 0.25 + random.nextDouble() * 0.15, Math.sin(angle) * speed);
        }
        if (random.nextInt(3) == 0) {
            double angle = random.nextDouble() * Math.PI * 2;
            double reach = 1.0 + random.nextDouble() * 3.0;
            level.addParticle(ParticleTypes.FALLING_WATER, x + Math.cos(angle) * reach, y + 0.4, z + Math.sin(angle) * reach, 0, 0, 0);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopperSprinklerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level instanceof ServerLevel serverLevel ? createTickerHelper(type, ModBlockEntityTypes.COPPER_SPRINKLER.get(),
                (l, pos, s, sprinkler) -> sprinkler.serverTick(serverLevel, pos, s)) : null;
    }
}
