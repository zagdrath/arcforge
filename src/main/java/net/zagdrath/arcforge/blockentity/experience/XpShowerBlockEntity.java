/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.experience;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.experience.LiquidExperience;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// The XP Shower's tank of Liquid Experience (logistics.experience.showerTank). Every tick it draws up to showerPullRate mB from the
// container above it; conduits and buckets fill it from any face. Every showerLevelInterval ticks, unless a redstone
// signal turns it off, it gives each player sneaking under it (up to showerReach blocks down) what takes them to their
// next level, or as much as the tank holds. Breaking it keeps what's in the tank on the item.
public class XpShowerBlockEntity extends BlockEntity implements FluidInteractable {
    private final FilteredFluidTank tank = new FilteredFluidTank(ArcforgeConfig.XP_SHOWER_TANK.getAsInt(), LiquidExperience::is, this::setChanged);
    private final ResourceHandler<FluidResource> inputView = new SidedResourceHandler<>(tank, true, false, Integer.MAX_VALUE);
    private @Nullable BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction> above;
    private long given;

    public XpShowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.XP_SHOWER.get(), pos, state);
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    // Where a player can stand to be showered: the column under it, showerReach blocks down.
    public static AABB area(BlockPos pos) {
        int reach = ArcforgeConfig.XP_SHOWER_REACH.getAsInt();
        return new AABB(pos.getX(), pos.getY() - reach, pos.getZ(), pos.getX() + 1, pos.getY(), pos.getZ() + 1);
    }

    public boolean isOff() {
        return level != null && level.hasNeighborSignal(worldPosition);
    }

    public void serverTick(ServerLevel level, BlockPos pos) {
        if (tank.getSpace() > 0) {
            if (above == null) {
                above = BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, pos.above(), Direction.DOWN);
            }
            ResourceHandler<FluidResource> source = above.getCapability();
            if (source != null) {
                ResourceHandlerUtil.move(source, tank, LiquidExperience::is, Math.min(tank.getSpace(), ArcforgeConfig.XP_SHOWER_PULL_RATE.getAsInt()), null);
            }
        }
        if (tank.getAmount() < LiquidExperience.MB_PER_POINT || level.getGameTime() % ArcforgeConfig.XP_SHOWER_LEVEL_INTERVAL.getAsInt() != 0
                || level.hasNeighborSignal(pos)) {
            return;
        }
        for (Player player : level.getEntitiesOfClass(Player.class, area(pos), p -> p.isShiftKeyDown() && !p.isSpectator() && p.isAlive())) {
            int points = Math.min(LiquidExperience.pointsToRise(player), tank.getAmount() / LiquidExperience.MB_PER_POINT);
            if (points <= 0) {
                break;
            }
            int amount = points * LiquidExperience.MB_PER_POINT;
            try (Transaction transaction = Transaction.openRoot()) {
                if (tank.extract(0, tank.getResource(0), amount, transaction) != amount) {
                    continue;
                }
                transaction.commit();
            }
            player.giveExperiencePoints(points);
            given += points;
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.3F,
                    0.8F + level.getRandom().nextFloat() * 0.4F);
            level.sendParticles(ParticleTypes.COMPOSTER, pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, 8, 0.2, 0.05, 0.2, 0.0);
        }
    }

    // Points given since it was loaded, for tests.
    public long getGiven() {
        return given;
    }

    // --- Capabilities: filled from any face ---

    public ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        return inputView;
    }

    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return tank;
    }

    // --- Item form ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        FluidStack stack = components.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        if (!stack.isEmpty() && LiquidExperience.is(FluidResource.of(stack))) {
            tank.set(0, FluidResource.of(stack), Math.min(stack.getAmount(), tank.getCapacity()));
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (tank.getAmount() > 0) {
            components.set(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.copyOf(tank.getResource(0).toStack(tank.getAmount())));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("fluid");
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("fluid"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("fluid"));
    }
}
