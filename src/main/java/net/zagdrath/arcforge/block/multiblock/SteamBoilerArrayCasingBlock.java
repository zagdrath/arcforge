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

// A casing of the Steam Boiler Array: a tower on a footprint 3 to 7 by 3 to 9 (either way round), 3 to 12 blocks
// tall, with Pressure Glass windows.
public class SteamBoilerArrayCasingBlock extends ShellCasingBlock {
    public static final int MIN_SIZE = 3, MAX_WIDTH = 7, MAX_DEPTH = 9, MAX_HEIGHT = 12;
    public static final ShellStructure STRUCTURE = new ShellStructure(block -> block instanceof SteamBoilerArrayCasingBlock,
            EnumSet.of(Direction.Axis.Y), (axis, x, y, z) -> fits(x, z, MAX_WIDTH, MAX_DEPTH) && y >= MIN_SIZE && y <= MAX_HEIGHT);

    // Whether a rectangle of sides a and b is at least MIN_SIZE each way, with its shorter side at most narrow and
    // its longer at most wide.
    public static boolean fits(int a, int b, int narrow, int wide) {
        return Math.min(a, b) >= MIN_SIZE && Math.min(a, b) <= narrow && Math.max(a, b) <= wide;
    }

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
            BlockPos low = shell.min();
            BlockPos top = shell.max();
            level.addParticle(ParticleTypes.CLOUD, low.getX() + random.nextDouble() * (top.getX() - low.getX() + 1), top.getY() + 1.05,
                    low.getZ() + random.nextDouble() * (top.getZ() - low.getZ() + 1), 0.0, 0.04, 0.0);
        }
    }
}
