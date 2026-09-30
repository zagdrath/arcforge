/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Grow Chamber: steel and Pressure Glass around a soil, a seed and a grow lamp. Grows at growChamber.speed with
// FE and water, and takes fertilizer. Speed upgrades make it faster (drawing FE just as much faster), Energy upgrades
// cut the FE per harvest. Its faces are configured like any machine's; with auto-eject on, the harvest leaves through
// its output faces.
public class GrowChamberBlockEntity extends ClocheBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    public GrowChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.GROW_CHAMBER.get(), pos, state, Kind.GROW_CHAMBER, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES,
                ArcforgeConfig.GROW_CHAMBER_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.GROW_CHAMBER_MAX_INPUT.getAsInt(),
                ArcforgeConfig.GROW_CHAMBER_WATER_CAPACITY.getAsInt(), resource -> resource.getFluid() == Fluids.WATER);
    }

    @Override
    protected double speed() {
        return ArcforgeConfig.GROW_CHAMBER_SPEED.getAsDouble() * speedMultiplier();
    }

    @Override
    protected int fluidPerHarvest() {
        return ArcforgeConfig.GROW_CHAMBER_WATER_PER_HARVEST.getAsInt();
    }

    // FE/t while growing: Speed draws it faster, Energy cuts it.
    @Override
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.GROW_CHAMBER_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    @Override
    protected void moveOutputs(ServerLevel level) {
        autoEject(level, outputHandler());
    }
}
