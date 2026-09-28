/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.multiblock.CubeMultiblockBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SuperheaterArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A casing of the Superheater Array; 27 in a 3x3x3 cube form the machine (see CubeCasingBlock). Its coils
// glow while it upgrades steam (the formed model's lit texture).
public class SuperheaterArrayCasingBlock extends CubeCasingBlock {
    public static final CubeMultiblockStructure<SuperheaterArrayBlockEntity> STRUCTURE =
            new CubeMultiblockStructure<>(SuperheaterArrayCasingBlock.class, SuperheaterArrayBlockEntity.class);

    public SuperheaterArrayCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
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
