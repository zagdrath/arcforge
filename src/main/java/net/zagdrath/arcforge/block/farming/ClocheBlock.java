/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.farming;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.blockentity.farming.ClocheBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.GlassClocheBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.GrowChamberBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.HydroponicCellBlockEntity;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// The Glass Cloche (a glass bell on a treated-wood planter), the Grow Chamber and the Hydroponic Cell (steel frames
// glazed with Pressure Glass). The plant growing inside is drawn by ClocheRenderer.
public class ClocheBlock extends MachineBlock {
    // 26.1 requires a block codec. Nothing decodes this block type, and its constructor arguments aren't
    // data, so the codec stands for this instance.
    @Override
    protected MapCodec<ClocheBlock> codec() {
        return MapCodec.unit(this);
    }

    private static final Map<Direction, VoxelShape> CLOCHE_SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.box(1, 0, 1, 15, 4, 15),
            Block.box(2.25, 4, 2.25, 13.75, 14.5, 13.75),
            Block.box(7, 14.5, 7, 9, 16, 9)));

    private final ClocheBlockEntity.Kind kind;

    public ClocheBlock(ClocheBlockEntity.Kind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public ClocheBlockEntity.Kind kind() {
        return kind;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind == ClocheBlockEntity.Kind.GLASS_CLOCHE ? CLOCHE_SHAPES.get(state.getValue(FACING)) : Shapes.block();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (kind) {
            case GLASS_CLOCHE -> new GlassClocheBlockEntity(pos, state);
            case GROW_CHAMBER -> new GrowChamberBlockEntity(pos, state);
            case HYDROPONIC_CELL -> new HydroponicCellBlockEntity(pos, state);
        };
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return switch (kind) {
            case GLASS_CLOCHE -> createTickerHelper(type, ModBlockEntityTypes.GLASS_CLOCHE.get(),
                    (innerLevel, pos, blockState, farm) -> farm.serverTick(serverLevel, pos, blockState));
            case GROW_CHAMBER -> createTickerHelper(type, ModBlockEntityTypes.GROW_CHAMBER.get(),
                    (innerLevel, pos, blockState, farm) -> farm.serverTick(serverLevel, pos, blockState));
            case HYDROPONIC_CELL -> createTickerHelper(type, ModBlockEntityTypes.HYDROPONIC_CELL.get(),
                    (innerLevel, pos, blockState, farm) -> farm.serverTick(serverLevel, pos, blockState));
        };
    }

    // Now and then while it grows, a drop of condensation on the glass (the powered farms' lamps light the block too).
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || random.nextInt(8) != 0) {
            return;
        }
        Direction side = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        double inset = kind == ClocheBlockEntity.Kind.GLASS_CLOCHE ? 0.37 : 0.44;
        double along = (random.nextDouble() - 0.5) * 0.6;
        double x = pos.getX() + 0.5 + side.getStepX() * inset + side.getClockWise().getStepX() * along;
        double y = pos.getY() + 0.35 + random.nextDouble() * 0.45;
        double z = pos.getZ() + 0.5 + side.getStepZ() * inset + side.getClockWise().getStepZ() * along;
        level.addParticle(ParticleTypes.DRIPPING_WATER, x, y, z, 0.0, 0.0, 0.0);
    }
}
