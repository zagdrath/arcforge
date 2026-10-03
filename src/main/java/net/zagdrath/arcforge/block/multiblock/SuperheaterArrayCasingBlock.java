/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.multiblock.CubeMultiblockBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A casing of the Superheater Array; 27 in a 3x3x3 cube form the machine (see CubeCasingBlock), and so does any solid box
// up to 7x7x7, drawn as one connected skin (box=true). The cube's coils glow while it upgrades steam (the formed model's
// lit texture).
public class SuperheaterArrayCasingBlock extends CubeCasingBlock {
    // The largest box, each way.
    public static final int MAX_SIZE = 7;
    public static final CubeMultiblockStructure<SuperheaterArrayBlockEntity> STRUCTURE =
            new CubeMultiblockStructure<>(SuperheaterArrayCasingBlock.class, SuperheaterArrayBlockEntity.class, MAX_SIZE);

    public SuperheaterArrayCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(BOX, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BOX);
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return STRUCTURE;
    }

    @Override
    protected BlockEntityType<? extends CubeMultiblockBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.SUPERHEATER_ARRAY.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SuperheaterArrayBlockEntity(pos, state);
    }
}
