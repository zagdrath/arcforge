/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A casing of the Steam Turbine Array: a 3x3 tube 3 to 9 blocks long along either horizontal axis, with
// Pressure Glass windows. The casings in the middle of the two end faces are the rotor's bearings: END
// marks the generator end (positive along the axis, with the FE port) and the plain bearing end.
public class SteamTurbineArrayCasingBlock extends ShellCasingBlock {
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
    public static final ShellStructure STRUCTURE = new ShellStructure(block -> block instanceof SteamTurbineArrayCasingBlock,
            EnumSet.of(Direction.Axis.X, Direction.Axis.Z), 3, 9);

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
        End end = pos.equals(shell.endCenter(Direction.AxisDirection.POSITIVE)) ? End.GENERATOR
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
}
