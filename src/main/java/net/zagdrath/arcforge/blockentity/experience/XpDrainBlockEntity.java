/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.experience;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.experience.LiquidExperience;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// Every tick the drain takes the experience orbs resting on it (up to experience.drainOrbPointsPerTick points) into the
// container below as Liquid Experience, an orb at a time and only whole orbs; every drainLevelInterval ticks it drains a
// player sneaking on it by one level (or what's left of their current one), as much as the container takes. It holds
// nothing itself.
public class XpDrainBlockEntity extends BlockEntity {
    private @Nullable BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> below;
    private long drained;

    public XpDrainBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.XP_DRAIN.get(), pos, state);
    }

    // The space just above the grate, where orbs come to rest and players stand.
    public static AABB area(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 0.5, pos.getZ() + 1);
    }

    private @Nullable ResourceHandler<FluidResource> container(ServerLevel level, BlockPos pos) {
        if (below == null) {
            below = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, pos.below(), Direction.UP);
        }
        return below.getCapability();
    }

    public void serverTick(ServerLevel level, BlockPos pos) {
        ResourceHandler<FluidResource> container = container(level, pos);
        if (container == null) {
            return;
        }
        AABB area = area(pos);
        int budget = ArcforgeConfig.XP_DRAIN_ORB_POINTS_PER_TICK.getAsInt();
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area, Entity::isAlive)) {
            int points = LiquidExperience.points(level, orb);
            if (points > budget) {
                break;
            }
            if (fill(container, points) == points) {
                budget -= points;
                drained += points;
                orb.discard();
            }
        }
        if (level.getGameTime() % ArcforgeConfig.XP_DRAIN_LEVEL_INTERVAL.getAsInt() == 0) {
            for (Player player : level.getEntitiesOfClass(Player.class, area, p -> p.isShiftKeyDown() && !p.isSpectator())) {
                int points = LiquidExperience.pointsToDrop(player);
                if (points <= 0) {
                    continue;
                }
                int moved = fill(container, room(container, points));
                if (moved > 0) {
                    player.giveExperiencePoints(-moved);
                    drained += moved;
                    level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.25F,
                            0.6F + level.getRandom().nextFloat() * 0.3F);
                }
            }
        }
    }

    // How many of points the container has room for, as whole points.
    private static int room(ResourceHandler<FluidResource> container, int points) {
        try (Transaction transaction = Transaction.openRoot()) {
            return container.insert(LiquidExperience.resource(), points * LiquidExperience.MB_PER_POINT, transaction) / LiquidExperience.MB_PER_POINT;
        }
    }

    // Puts exactly points of Liquid Experience into the container, or nothing; returns the points moved.
    private static int fill(ResourceHandler<FluidResource> container, int points) {
        if (points <= 0) {
            return 0;
        }
        int amount = points * LiquidExperience.MB_PER_POINT;
        try (Transaction transaction = Transaction.openRoot()) {
            if (container.insert(LiquidExperience.resource(), amount, transaction) != amount) {
                return 0;
            }
            transaction.commit();
            return points;
        }
    }

    // Points drained since it was loaded, for tests.
    public long getDrained() {
        return drained;
    }
}
