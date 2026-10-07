/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A casing of the Steam Turbine Array: a tube 3 to 15 blocks long along either horizontal axis, its cross-section
// 3 to 7 wide and 3 to 9 tall, with Pressure Glass windows. The casings in the middle of the two end faces are the
// rotor's bearings: END marks the generator end (positive along the axis, with the FE port) and the plain bearing
// end (a cross-section with an even side has no middle block, so its ends stay plain).
public class SteamTurbineArrayCasingBlock extends ShellCasingBlock {
    private static final MapCodec<SteamTurbineArrayCasingBlock> CODEC = simpleCodec(SteamTurbineArrayCasingBlock::new);

    @Override
    protected MapCodec<SteamTurbineArrayCasingBlock> codec() {
        return CODEC;
    }

    public enum End implements StringRepresentable {
        NONE("none"), GENERATOR("generator"), BEARING("bearing");

        private final String name;

        End(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<End> END = EnumProperty.create("end", End.class);
    public static final int MAX_WIDTH = 7, MAX_HEIGHT = 9, MAX_LENGTH = 15;
    public static final ShellStructure STRUCTURE = new ShellStructure(block -> block instanceof SteamTurbineArrayCasingBlock,
            EnumSet.of(Direction.Axis.X, Direction.Axis.Z), (axis, x, y, z) -> {
                int width = axis == Direction.Axis.X ? z : x, length = axis == Direction.Axis.X ? x : z;
                return width >= 3 && width <= MAX_WIDTH && y >= 3 && y <= MAX_HEIGHT && length >= 3 && length <= MAX_LENGTH;
            });

    public SteamTurbineArrayCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(END, End.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(END);
    }

    @Override
    public BlockState formedState(BlockState state, ShellStructure.Shell shell, BlockPos pos) {
        // A cross-section with an even side has no middle block for the generator and bearing.
        End end = !shell.hasEndCenters() ? End.NONE : pos.equals(shell.endCenter(Direction.AxisDirection.POSITIVE)) ? End.GENERATOR
                : pos.equals(shell.endCenter(Direction.AxisDirection.NEGATIVE)) ? End.BEARING
                : End.NONE;
        return super.formedState(state, shell, pos).setValue(END, end);
    }

    @Override
    public BlockState looseState(BlockState state) {
        return super.looseState(state).setValue(END, End.NONE);
    }

    @Override
    public ShellStructure structure() {
        return STRUCTURE;
    }

    @Override
    protected BlockEntityType<? extends ShellMultiblockBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.STEAM_TURBINE_ARRAY.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamTurbineArrayBlockEntity(pos, state);
    }

    // Clients tick the formed master too, to keep its running sound going (see SteamTurbineArrayBlockEntity.clientTick).
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            if (!state.getValue(FORMED) || type != blockEntityType()) {
                return null;
            }
            BlockEntityTicker<SteamTurbineArrayBlockEntity> ticker = (innerLevel, pos, blockState, turbine) -> turbine.clientTick();
            return (BlockEntityTicker<T>) (BlockEntityTicker<?>) ticker;
        }
        return super.getTicker(level, state, type);
    }
}
