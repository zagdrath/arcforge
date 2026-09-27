/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.network;

import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.steam.Gases;

// Pressurized conduits: a fluid network for gases only (one gas at a time), holding 2,000 mB per conduit
// and moving twice a fluid conduit's rate. The conduits glow while they hold or move gas, like energy conduits.
public class GasConduitNetwork extends FluidConduitNetwork {
    // Ticks without movement before the conduits go dark (prevents flicker).
    private static final int IDLE_TICKS = 10;

    private boolean active;
    private long lastMovedTick;

    public GasConduitNetwork(ServerLevel level, List<BlockPos> members, Set<BlockPos> memberSet, ConduitTier tier) {
        super(level, members, memberSet, tier, ConduitTier.GAS_CAPACITY_PER_CONDUIT, tier.gasPerTick(), Gases::isGas);
    }

    @Override
    protected void onCreated() {
        super.onCreated();
        // Conduits saved as lit stay lit until the network confirms it is idle; a merged network matches up.
        active = members.stream().anyMatch(pos -> {
            var state = level.getBlockState(pos);
            return state.hasProperty(ActiveConduitBlock.ACTIVE) && state.getValue(ActiveConduitBlock.ACTIVE);
        });
        lastMovedTick = level.getGameTime();
        for (BlockPos pos : members) {
            ActiveConduitBlock.setActive(level, pos, active);
        }
    }

    @Override
    protected void onMoved(int moved, long gameTime) {
        if (moved > 0 || getAmount() > 0) {
            lastMovedTick = gameTime;
            setActive(true);
        } else if (active && gameTime - lastMovedTick >= IDLE_TICKS) {
            setActive(false);
        }
    }

    private void setActive(boolean value) {
        if (active == value) {
            return;
        }
        active = value;
        for (BlockPos pos : members) {
            ActiveConduitBlock.setActive(level, pos, value);
        }
    }
}
