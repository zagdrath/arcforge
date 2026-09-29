/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

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
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A casing of the Gas Turbine Array: a 3x3 tube 5 to 9 blocks long along either horizontal axis, with
// Pressure Glass windows. The casings in the middle of the two end faces must be casings: END marks the
// intake (which needs air in front of it, and can't hold a port) and the exhaust. The master picks which is
// which when the structure forms (see GasTurbineArrayBlockEntity.onFormed).
public class GasTurbineArrayCasingBlock extends ShellCasingBlock {
    public enum End implements StringRepresentable {
        NONE("none"), INTAKE("intake"), EXHAUST("exhaust");

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
    public static final ShellStructure STRUCTURE = new ShellStructure(block -> block instanceof GasTurbineArrayCasingBlock,
            EnumSet.of(Direction.Axis.X, Direction.Axis.Z), 5, 9, true);

    public GasTurbineArrayCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(END, End.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(END);
    }

    @Override
    public BlockState looseState(BlockState state) {
        return super.looseState(state).setValue(END, End.NONE);
    }

    // The intake's middle stays open to the air, so it never holds a port.
    @Override
    public boolean holdsPorts(BlockState state) {
        return state.getValue(END) != End.INTAKE;
    }

    @Override
    public ShellStructure structure() {
        return STRUCTURE;
    }

    @Override
    protected BlockEntityType<? extends ShellMultiblockBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.GAS_TURBINE_ARRAY.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GasTurbineArrayBlockEntity(pos, state);
    }

    // Clients tick the formed master too, for its running sound and exhaust haze (see GasTurbineArrayBlockEntity.clientTick).
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            if (!state.getValue(FORMED) || type != blockEntityType()) {
                return null;
            }
            BlockEntityTicker<GasTurbineArrayBlockEntity> ticker = (innerLevel, pos, blockState, turbine) -> turbine.clientTick();
            return (BlockEntityTicker<T>) (BlockEntityTicker<?>) ticker;
        }
        return super.getTicker(level, state, type);
    }
}
