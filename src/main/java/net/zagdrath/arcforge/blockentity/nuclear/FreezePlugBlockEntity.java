/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.zagdrath.arcforge.block.nuclear.FreezePlugBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;

// A molten salt reactor's Freeze Plug: a plug of salt frozen in the drain line under the vessel, kept frozen by a
// cooling fan that takes ENERGY FE a tick. It's passive safety: it melts (MELTED) and opens the drain if the core
// overheats (its salt above nuclear.reprocessing.freezePlugMelt °C, as the reactor tells it) or if its power fails for
// THAW_TICKS; the reactor then drains its salt into the Drain Tank under it. Once powered again and with the salt cool
// (or gone) for REFREEZE_TICKS, it freezes shut.
public class FreezePlugBlockEntity extends NuclearBlockEntity implements net.zagdrath.arcforge.block.nuclear.NuclearEntityBlock.ServerTicking {
    public static final int ENERGY = 20;
    public static final int THAW_TICKS = 200, REFREEZE_TICKS = 200;

    private final ConsumerEnergyHandler energy;
    private int unpowered;
    private int cooled;
    // The game tick the reactor last watched it on; with none watching (the reactor stopped or gone) it ticks itself.
    private long watched = Long.MIN_VALUE;

    public FreezePlugBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FREEZE_PLUG.get(), pos, state);
        this.energy = new ConsumerEnergyHandler(ENERGY * 2_000, ENERGY * 10, this::setChanged);
    }

    public EnergyHandler getEnergyHandler() {
        return energy;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public boolean isMelted() {
        return getBlockState().getValue(FreezePlugBlock.MELTED);
    }

    // The reactor above, each tick: its salt's temperature, or a negative value with none in it.
    public void watch(ServerLevel level, double saltCelsius) {
        watched = level.getGameTime();
        boolean powered = energy.consume(ENERGY);
        unpowered = powered ? 0 : unpowered + 1;
        boolean melted = isMelted();
        if (!melted && (saltCelsius > ArcforgeConfig.FREEZE_PLUG_MELT.getAsDouble() || unpowered >= THAW_TICKS)) {
            set(level, true);
            level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.6F);
        } else if (melted) {
            cooled = powered && saltCelsius < ArcforgeConfig.FREEZE_PLUG_MELT.getAsDouble() - 150.0 ? cooled + 1 : 0;
            if (cooled >= REFREEZE_TICKS) {
                set(level, false);
            }
        }
    }

    // Melted at once (a salt core's power spiking).
    public void melt(ServerLevel level) {
        if (!isMelted()) {
            set(level, true);
            level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
    }

    // Without a running reactor watching it, it still draws its power, and thaws if that fails (no salt to read).
    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.getGameTime() - watched > 1L) {
            watch(level, -1.0);
        }
    }

    private void set(ServerLevel level, boolean melted) {
        cooled = 0;
        level.setBlock(worldPosition, getBlockState().setValue(FreezePlugBlock.MELTED, melted), Block.UPDATE_ALL);
        setChanged();
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        energy.deserialize(in.childOrEmpty("energy"));
        unpowered = in.getIntOr("unpowered", 0);
        cooled = in.getIntOr("cooled", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        energy.serialize(out.child("energy"));
        out.putInt("unpowered", unpowered);
        out.putInt("cooled", cooled);
    }
}
