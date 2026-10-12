/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.logistics;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.arcforge.block.logistics.HeatPipeBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModCapabilities;

// One block of a Heat Pipe (see HeatPipeBlock). The block at a pipe's negative end runs it: each tick it looks at the heat
// handlers beyond its two ends (heat ports and heat faces), and if one is hotter it draws up to heatPipe.ratePerTick HU
// from it and delivers what survives the trip to the colder one: heatPipe.lossPercentPerBlock percent of it is lost per
// block of length. It draws only what the colder end can take after the loss, and never moves heat from colder to
// hotter. The pipe holds no heat of its own and exposes nothing on its sides.
//
// Every block keeps the temperature the pipe carries (in heat-tint steps), sent to clients so the core's glow follows it
// like a Thermodynamic Conduit's (HeatPipeTint), and LIT while it carries heat.
public class HeatPipeBlockEntity extends BlockEntity {
    // Glow steps, in °C, like the conduits'.
    private static final int GLOW_STEP = 50;
    // How fast the shown rate follows the actual one (a share per tick).
    private static final double RATE_SMOOTHING = 0.1;

    // Controller only (not saved): the pipe's length, found again when the structure changes.
    private int length = -1;
    private double rate;
    private int carrying = HeatBuffer.AMBIENT_CELSIUS;
    private int syncedGlow = Integer.MIN_VALUE;
    // Every block: the temperature its glow shows (0 when not carrying).
    private int glowCelsius;

    public HeatPipeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.HEAT_PIPE.get(), pos, state);
    }

    // The line was worked out again: the length is found again on the next tick.
    public void structureChanged() {
        length = -1;
        syncedGlow = Integer.MIN_VALUE;
    }

    // --- Running the pipe (negative end only) ---

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getValue(HeatPipeBlock.NEG_END)) {
            return;
        }
        Direction.Axis axis = state.getValue(HeatPipeBlock.AXIS);
        Direction back = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        Direction forward = back.getOpposite();
        if (length < 0) {
            length = findLength(level, pos, axis);
        }
        int moved = 0;
        int celsius = HeatBuffer.AMBIENT_CELSIUS;
        if (length > 0) {
            BlockPos far = pos.relative(forward, length - 1);
            // An end in an unloaded chunk joins nothing (and isn't loaded for it).
            HeatHandler a = handlerAt(level, pos.relative(back), forward);
            HeatHandler b = handlerAt(level, far.relative(forward), back);
            if (a != null && b != null) {
                int ta = a.getTemperature(), tb = b.getTemperature();
                if (ta != tb) {
                    HeatHandler hot = ta > tb ? a : b;
                    HeatHandler cold = ta > tb ? b : a;
                    moved = transfer(hot, cold, efficiency(length));
                    if (moved > 0) {
                        celsius = Math.max(ta, tb);
                    }
                }
            }
        } else {
            // Part of it isn't there yet (still being worked out): try again next tick.
            length = -1;
        }
        rate += (moved - rate) * RATE_SMOOTHING;
        if (moved == 0 && rate < 0.5) {
            rate = 0;
        }
        carrying = celsius;
        updateGlow(level, pos, forward, moved > 0 ? celsius : 0);
    }

    private static @Nullable HeatHandler handlerAt(ServerLevel level, BlockPos pos, Direction side) {
        return level.isLoaded(pos) ? level.getCapability(ModCapabilities.HEAT, pos, side) : null;
    }

    // Draws heat from hot and gives cold what's left after the loss; returns the HU delivered.
    static int transfer(HeatHandler hot, HeatHandler cold, double efficiency) {
        if (efficiency <= 0) {
            return 0;
        }
        int offered = hot.extractHeat(ArcforgeConfig.HEAT_PIPE_RATE.getAsInt(), true);
        if (offered <= 0) {
            return 0;
        }
        int room = cold.receiveHeat((int) Math.floor(offered * efficiency), true);
        if (room <= 0) {
            return 0;
        }
        int draw = (int) Math.min(offered, Math.ceil(room / efficiency));
        int drawn = hot.extractHeat(draw, false);
        return cold.receiveHeat(Math.min(room, (int) Math.floor(drawn * efficiency)), false);
    }

    // Share of the heat that arrives over a pipe this long.
    public static double efficiency(int length) {
        return Math.max(0.0, 1.0 - ArcforgeConfig.HEAT_PIPE_LOSS_PER_BLOCK.getAsDouble() / 100.0 * length);
    }

    // The pipe's length from its negative end at pos: up to and including the block marked POS_END; 0 if the line is
    // broken before one (it's being worked out again).
    public static int findLength(Level level, BlockPos start, Direction.Axis axis) {
        Direction forward = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        int max = Math.max(1, ArcforgeConfig.HEAT_PIPE_MAX_LENGTH.getAsInt());
        for (int i = 0; i < max; i++) {
            BlockPos at = start.relative(forward, i);
            if (!level.isLoaded(at)) {
                return 0;
            }
            BlockState state = level.getBlockState(at);
            if (!(state.getBlock() instanceof HeatPipeBlock) || state.getValue(HeatPipeBlock.AXIS) != axis) {
                return 0;
            }
            if (state.getValue(HeatPipeBlock.POS_END)) {
                return i + 1;
            }
        }
        return 0;
    }

    // Sets every block's glow (and LIT) when the carried temperature crosses a step.
    private void updateGlow(ServerLevel level, BlockPos pos, Direction forward, int celsius) {
        int key = celsius / GLOW_STEP;
        if (key == syncedGlow) {
            return;
        }
        syncedGlow = key;
        for (int i = 0; i < Math.max(1, length); i++) {
            BlockPos at = pos.relative(forward, i);
            if (level.getBlockEntity(at) instanceof HeatPipeBlockEntity pipe) {
                pipe.setGlow(level, at, celsius);
            }
        }
    }

    private void setGlow(Level level, BlockPos at, int celsius) {
        glowCelsius = celsius;
        setChanged();
        BlockState state = getBlockState();
        boolean lit = celsius > HeatBuffer.AMBIENT_CELSIUS;
        if (state.getValue(HeatPipeBlock.LIT) != lit) {
            level.setBlock(at, state.setValue(HeatPipeBlock.LIT, lit), Block.UPDATE_CLIENTS);
        } else {
            level.sendBlockUpdated(at, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // --- What Jade shows (from the negative end) ---

    public int getLength() {
        return Math.max(0, length);
    }

    public int getRate() {
        return (int) Math.round(rate);
    }

    public int getCarrying() {
        return carrying;
    }

    // The block running the pipe this block is part of: walks back to the NEG_END block (at most maxLength blocks).
    public static @Nullable HeatPipeBlockEntity controllerOf(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof HeatPipeBlock)) {
            return null;
        }
        Direction back = Direction.fromAxisAndDirection(state.getValue(HeatPipeBlock.AXIS), Direction.AxisDirection.NEGATIVE);
        BlockPos at = pos;
        for (int i = 0; i < ArcforgeConfig.HEAT_PIPE_MAX_LENGTH.getAsInt() && level.isLoaded(at); i++) {
            BlockState here = level.getBlockState(at);
            if (!(here.getBlock() instanceof HeatPipeBlock)) {
                return null;
            }
            if (here.getValue(HeatPipeBlock.NEG_END)) {
                return level.getBlockEntity(at) instanceof HeatPipeBlockEntity pipe ? pipe : null;
            }
            at = at.relative(back);
        }
        return null;
    }

    // --- The glow (every block) ---

    public int getGlowCelsius() {
        return glowCelsius;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int before = glowCelsius;
        glowCelsius = input.getIntOr("glow", 0);
        // A client redraws the pipe when its glow changes.
        if (level != null && level.isClientSide() && before / GLOW_STEP != glowCelsius / GLOW_STEP) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("glow", glowCelsius);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
