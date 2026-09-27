/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.SteamBoilerArrayBlockEntity;
import net.zagdrath.arcforge.multiblock.ShellStructure;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// A casing of the Steam Boiler Array: a 3x3 tower 3 to 7 blocks tall, with Pressure Glass windows.
public class SteamBoilerArrayCasingBlock extends ShellCasingBlock {
    public static final ShellStructure STRUCTURE = new ShellStructure(block -> block instanceof SteamBoilerArrayCasingBlock,
            EnumSet.of(Direction.Axis.Y), 3, 7);

    public SteamBoilerArrayCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public ShellStructure structure() {
        return STRUCTURE;
    }

    @Override
    protected BlockEntityType<? extends ShellMultiblockBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.STEAM_BOILER_ARRAY.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamBoilerArrayBlockEntity(pos, state);
    }

    // Steam wisps from the roof while boiling (the master is lit through its block entity's data).
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FORMED) || !(level.getBlockEntity(pos) instanceof SteamBoilerArrayBlockEntity boiler) || !boiler.isBoilingOnClient()) {
            return;
        }
        ShellStructure.Shell shell = boiler.getShell();
        if (shell != null && random.nextInt(2) == 0) {
            BlockPos top = shell.max();
            level.addParticle(ParticleTypes.CLOUD, top.getX() - 2 + random.nextDouble() * 3, top.getY() + 1.05,
                    top.getZ() - 2 + random.nextDouble() * 3, 0.0, 0.04, 0.0);
        }
    }
}
