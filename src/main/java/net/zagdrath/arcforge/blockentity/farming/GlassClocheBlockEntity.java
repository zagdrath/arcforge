/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Glass Cloche: the early, unpowered farm. A soil and a seed under glass on a treated-wood base, watered from its
// tank (pipes on any face but the bottom, or a held bucket), growing at glassCloche.speed. Its faces are fixed, with no
// Sides tab: seeds, soils, fertilizer and water go in anywhere but the bottom, and the harvest is pushed out of the
// bottom into the inventory under it (or taken from its output slots).
public class GlassClocheBlockEntity extends ClocheBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT);

    public GlassClocheBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.GLASS_CLOCHE.get(), pos, state, Kind.GLASS_CLOCHE, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT),
                SIDE_MODES, 0, 0, ArcforgeConfig.GLASS_CLOCHE_WATER_CAPACITY.getAsInt(), resource -> resource.getFluid() == Fluids.WATER);
    }

    @Override
    protected double speed() {
        return ArcforgeConfig.GLASS_CLOCHE_SPEED.getAsDouble();
    }

    @Override
    protected int fluidPerHarvest() {
        return ArcforgeConfig.GLASS_CLOCHE_WATER_PER_HARVEST.getAsInt();
    }

    // The harvest drops into whatever is below: a chest, a hopper, a conduit.
    @Override
    protected void moveOutputs(ServerLevel level) {
        outputs.pushItemsFrom(level, worldPosition, Direction.DOWN, outputHandler());
    }

    // Fixed faces: the bottom gives the harvest, the rest take everything in.
    @Override
    protected @Nullable SideMode modeFor(@Nullable Direction side) {
        return side == null ? null : side == Direction.DOWN ? SideMode.OUTPUT : SideMode.INPUT;
    }

    @Override
    public void setSideMode(RelativeSide side, SideMode mode) {}

    @Override
    public void clearSideModes() {}
}
