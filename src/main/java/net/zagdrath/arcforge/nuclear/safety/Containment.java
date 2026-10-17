/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear.safety;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.arcforge.block.nuclear.EquipmentHatchBlock;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.tag.ModBlockTags;

// A Fission Reactor's containment building: the room round its vessel, found by a flood fill out from the vessel (see
// scan) every few seconds by the reactor, which owns it and keeps what's in its air between scans.
//  - The fill goes through everything (air, machines, the Steam Generators, water) except the containment's own
//    blocks: a Steel Liner backed by Containment Concrete (or the lined Containment Dome), shut Airlock doors (an
//    airlock's doors are interlocked, so with a pair one is always shut), a shut Equipment Hatch and Penetrations (which
//    pipes, conduits and cables connect through). It's sealed if that bounds it within nuclear.failures.containmentMaxVolume blocks.
//  - Bare Containment Concrete reached from inside, or a Steel Liner with nothing solid behind it, isn't lined: the
//    fill goes on through it, and the room is open (unless something else bounds it again, which it won't for long).
//  - What's inside: the air (blocks without a full collision shape), the Suppression Pool (water source blocks), the
//    Hydrogen Recombiners and anything that can light Hydrogen (fire, lava, a lit furnace, Corium; #arcforge:
//    hydrogen_igniters or any lit block).
//  - Its air keeps Hydrogen (mB; see hydrogenPercent) and radioactivity (activity, a dose rate in mSv/h everywhere in
//    it), and its pool's temperature.
public final class Containment {
    public enum Status {
        SEALED("sealed"), OPEN("open"), UNLINED("unlined"), HATCH_OPEN("hatch_open"), UNLOADED("unloaded");

        private final String key;

        Status(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    // Each Suppression Pool water block's heat capacity, HU per °C.
    public static final double POOL_HEAT_PER_BLOCK = 4_000.0;

    public final BlockPos owner;
    private LongOpenHashSet cells = new LongOpenHashSet();
    private int air;
    private int pool;
    private final List<BlockPos> recombiners = new ArrayList<>();
    private boolean igniter;
    private Status status = Status.OPEN;
    private @Nullable BlockPos breach;
    // What's in its air, kept by the reactor between scans (and saved with it).
    public double hydrogen;
    public double activity;
    public double poolTemp = 20.0;

    public Containment(BlockPos owner) {
        this.owner = owner.immutable();
    }

    public boolean sealed() {
        return status == Status.SEALED;
    }

    public Status status() {
        return status;
    }

    // Where the fill got out (or found the lining missing), for the GUI and Jade.
    public @Nullable BlockPos breach() {
        return breach;
    }

    public int airVolume() {
        return air;
    }

    public int volume() {
        return cells.size();
    }

    public int poolBlocks() {
        return pool;
    }

    public List<BlockPos> recombiners() {
        return recombiners;
    }

    public boolean hasIgniter() {
        return igniter;
    }

    public boolean contains(BlockPos pos) {
        return cells.contains(pos.asLong());
    }

    // Hydrogen as a percentage of the air (1,000 mB of gas fills a block).
    public double hydrogenPercent() {
        return air > 0 ? hydrogen / (air * 1_000.0) * 100.0 : 0.0;
    }

    // --- The scan ---

    // Fills out from the vessel (the box min..max, its walls included) through everything but the containment's own
    // blocks, up to maxVolume blocks, and takes what it found.
    public void scan(Level level, BlockPos min, BlockPos max, int maxVolume) {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongOpenHashSet found = new LongOpenHashSet();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> recombinersFound = new ArrayList<>();
        int airFound = 0, poolFound = 0;
        boolean igniterFound = false;
        Status result = Status.SEALED;
        BlockPos firstBreach = null;
        // The vessel itself is inside but isn't room: its outside faces are where the fill starts.
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            seen.add(pos.asLong());
        }
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            boolean edge = pos.getX() == min.getX() || pos.getX() == max.getX() || pos.getY() == min.getY() || pos.getY() == max.getY()
                    || pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
            if (edge) {
                queue.add(pos.immutable());
            }
        }
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        fill:
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (Direction direction : Direction.values()) {
                next.setWithOffset(at, direction);
                long key = next.asLong();
                if (seen.contains(key)) {
                    continue;
                }
                seen.add(key);
                if (!level.isLoaded(next)) {
                    result = Status.UNLOADED;
                    firstBreach = next.immutable();
                    break fill;
                }
                BlockState state = level.getBlockState(next);
                Seal seal = seal(level, next, state, direction);
                if (seal == Seal.SEALS) {
                    continue;
                }
                if (seal == Seal.UNLINED && result == Status.SEALED) {
                    result = Status.UNLINED;
                    firstBreach = next.immutable();
                } else if (seal == Seal.OPEN_HATCH && result == Status.SEALED) {
                    result = Status.HATCH_OPEN;
                    firstBreach = next.immutable();
                }
                found.add(key);
                if (found.size() > maxVolume) {
                    if (result == Status.SEALED) {
                        result = Status.OPEN;
                        firstBreach = next.immutable();
                    }
                    break fill;
                }
                FluidState fluid = state.getFluidState();
                if (fluid.is(Fluids.WATER) && fluid.isSource()) {
                    poolFound++;
                } else if (state.isAir() || state.getCollisionShape(level, next).isEmpty()) {
                    airFound++;
                }
                if (state.is(ModBlocks.HYDROGEN_RECOMBINER.get())) {
                    recombinersFound.add(next.immutable());
                }
                igniterFound |= ignites(state);
                queue.add(next.immutable());
            }
        }
        // A fill that ran out of room, or got outside, is no containment: forget its blocks (so nothing counts as in it).
        boolean ok = result == Status.SEALED;
        this.cells = ok ? found : new LongOpenHashSet();
        this.air = airFound;
        this.pool = poolFound;
        this.recombiners.clear();
        if (ok) {
            this.recombiners.addAll(recombinersFound);
        }
        this.igniter = ok && igniterFound;
        this.status = result;
        this.breach = firstBreach;
    }

    private enum Seal { SEALS, PASSES, UNLINED, OPEN_HATCH }

    // What the block at pos (reached going in direction from inside) does to the fill.
    private static Seal seal(Level level, BlockPos pos, BlockState state, Direction direction) {
        if (state.is(ModBlocks.STEEL_LINER.get())) {
            BlockState behind = level.getBlockState(pos.relative(direction));
            return behind.is(ModBlocks.CONTAINMENT_CONCRETE.get()) || behind.is(ModBlocks.CONTAINMENT_DOME.get()) || behind.is(ModBlocks.STEEL_LINER.get())
                    ? Seal.SEALS : Seal.UNLINED;
        }
        if (state.is(ModBlocks.CONTAINMENT_DOME.get()) || state.is(ModBlocks.PENETRATION.get())) {
            return Seal.SEALS;
        }
        // An Airlock seals while shut. Open, the fill goes through it: into the airlock's vestibule, stopped by its
        // other (interlocked, so shut) door, or outside through a lone one.
        if (state.is(ModBlocks.AIRLOCK.get())) {
            return state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN) ? Seal.PASSES : Seal.SEALS;
        }
        if (state.is(ModBlocks.EQUIPMENT_HATCH.get())) {
            return state.getValue(EquipmentHatchBlock.OPEN) ? Seal.OPEN_HATCH : Seal.SEALS;
        }
        if (state.is(ModBlocks.CONTAINMENT_CONCRETE.get())) {
            return Seal.UNLINED;
        }
        return Seal.PASSES;
    }

    // Open flame, lava, a red-hot block, Corium, or anything lit (a furnace at work, a lit campfire).
    public static boolean ignites(BlockState state) {
        if (state.is(ModBlockTags.HYDROGEN_IGNITERS) || state.is(ModBlocks.CORIUM.get())) {
            return true;
        }
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.LAVA)) {
            return true;
        }
        return state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT);
    }
}
