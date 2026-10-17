/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.nuclear;

import java.util.function.Supplier;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

// A molten salt reactor's Freeze Plug (see FreezePlugBlockEntity), set in the drain line under the vessel: MELTED
// once it has thawed and opened the drain.
public class FreezePlugBlock extends NuclearEntityBlock {
    public static final BooleanProperty MELTED = BooleanProperty.create("melted");

    public FreezePlugBlock(Supplier<? extends BlockEntityType<?>> type, BlockBehaviour.Properties properties) {
        super(type, properties);
        registerDefaultState(defaultBlockState().setValue(MELTED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MELTED);
    }
}
