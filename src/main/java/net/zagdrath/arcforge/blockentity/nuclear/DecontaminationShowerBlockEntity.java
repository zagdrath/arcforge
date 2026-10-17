/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.nuclear.NuclearEntityBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.nuclear.safety.Contamination;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// A Decontamination Shower: a shower head on the ceiling. Each second it washes whoever stands in the two blocks
// under it (nuclear.safety.showerRate of the contamination on them and everything they carry; see Contamination),
// using nuclear.safety.showerWater mB of Water from its tank, through its HEPA Filter, which catches what's washed off.
// A filter lasts nuclear.safety.filterUses washes and is then a Used HEPA Filter (low-level waste); without a good
// filter it doesn't run. Water comes in through its faces (pipes) or from a bucket; a filter is put in by hand.
public class DecontaminationShowerBlockEntity extends NuclearBlockEntity implements FluidInteractable, NuclearEntityBlock.ServerTicking {
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> input;
    private ItemStack filter = ItemStack.EMPTY;
    private int uses;
    private boolean running;

    public DecontaminationShowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.DECONTAMINATION_SHOWER.get(), pos, state);
        this.tank = new FilteredFluidTank(8_000, resource -> resource.is(Fluids.WATER), this::setChanged);
        this.input = new AutomationResourceHandler<>(tank, index -> true, index -> false);
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    public ResourceHandler<FluidResource> getFluidHandler() {
        return input;
    }

    public ItemStack getFilter() {
        return filter;
    }

    public int getUses() {
        return uses;
    }

    public boolean isRunning() {
        return running;
    }

    public boolean hasGoodFilter() {
        return filter.is(ModItems.HEPA_FILTER.get());
    }

    // Puts a filter in, giving back what was there.
    public ItemStack swapFilter(ItemStack in) {
        ItemStack out = filter;
        filter = in.copyWithCount(1);
        uses = 0;
        sync();
        return out;
    }

    public ItemStack takeFilter() {
        ItemStack out = filter;
        filter = ItemStack.EMPTY;
        uses = 0;
        sync();
        return out;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        boolean was = running;
        running = false;
        AABB under = new AABB(pos.below(2)).expandTowards(0.0, 1.0, 0.0);
        int water = ArcforgeConfig.SHOWER_WATER.getAsInt();
        for (Player player : level.getEntitiesOfClass(Player.class, under)) {
            if (!hasGoodFilter() || tank.getAmount() < water) {
                break;
            }
            int left = tank.getAmount() - water;
            tank.set(0, left > 0 ? tank.getResource(0) : FluidResource.EMPTY, left);
            Contamination.wash(player, ArcforgeConfig.SHOWER_RATE.getAsDouble());
            running = true;
            if (++uses >= ArcforgeConfig.FILTER_USES.getAsInt()) {
                filter = new ItemStack(ModItems.USED_HEPA_FILTER.get());
                uses = 0;
            }
        }
        if (running) {
            level.sendParticles(ParticleTypes.FALLING_WATER, pos.getX() + 0.5, pos.getY() - 0.1, pos.getZ() + 0.5, 12, 0.25, 0.05, 0.25, 0.0);
            level.playSound(null, pos, SoundEvents.WEATHER_RAIN, SoundSource.BLOCKS, 0.3F, 1.4F);
        }
        if (running != was || running) {
            sync();
        }
    }

    // A held bucket of Water fills it.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return input;
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tank.deserialize(in.childOrEmpty("tank"));
        filter = in.read("filter", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        uses = in.getIntOr("uses", 0);
        running = in.getBooleanOr("running", false);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        tank.serialize(out.child("tank"));
        out.store("filter", ItemStack.OPTIONAL_CODEC, filter);
        out.putInt("uses", uses);
        out.putBoolean("running", running);
    }
}
