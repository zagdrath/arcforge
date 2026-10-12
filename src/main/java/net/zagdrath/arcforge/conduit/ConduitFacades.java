/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.registry.ModItems;

// The Conduit Facades on a conduit's sides: a 16x16x1 panel over a side, copying the look of its target block (no target
// is a blank facade). Facades only change how the conduit looks and what you can walk on: the conduit keeps connecting
// and working through them. Immutable; one per side, by Direction ordinal.
public final class ConduitFacades {
    public record Facade(@Nullable BlockState target) {
        public static final Facade BLANK = new Facade(null);

        // The facade as an item: blank, or carrying the block it copies (pick block).
        public ItemStack toItem() {
            return target == null ? new ItemStack(ModItems.CONDUIT_FACADE.get())
                    : net.zagdrath.arcforge.item.conduit.ConduitFacadeItem.of(ModItems.CONDUIT_FACADE.get(), target);
        }
    }

    // A facade as saved: its side (Direction ordinal) and its target, if dressed.
    private record Saved(byte side, Optional<BlockState> target) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BYTE.fieldOf("side").forGetter(Saved::side),
                BlockState.CODEC.optionalFieldOf("target").forGetter(Saved::target))
                .apply(i, Saved::new));
    }

    public static final ConduitFacades NONE = new ConduitFacades(new Facade[6]);

    public static final Codec<ConduitFacades> CODEC = Saved.CODEC.listOf().xmap(ConduitFacades::fromSaved, ConduitFacades::toSaved);

    private final @Nullable Facade[] sides;

    private ConduitFacades(@Nullable Facade[] sides) {
        this.sides = sides;
    }

    // The same facade on all six sides (a Conduit Cover from before facades).
    public static ConduitFacades all(Facade facade) {
        Facade[] sides = new Facade[6];
        Arrays.fill(sides, facade);
        return new ConduitFacades(sides);
    }

    private static ConduitFacades fromSaved(List<Saved> saved) {
        Facade[] sides = new Facade[6];
        for (Saved entry : saved) {
            if (entry.side() >= 0 && entry.side() < 6) {
                sides[entry.side()] = new Facade(entry.target().orElse(null));
            }
        }
        return new ConduitFacades(sides);
    }

    private List<Saved> toSaved() {
        List<Saved> saved = new ArrayList<>();
        for (Direction side : Direction.values()) {
            Facade facade = sides[side.ordinal()];
            if (facade != null) {
                saved.add(new Saved((byte) side.ordinal(), Optional.ofNullable(facade.target())));
            }
        }
        return saved;
    }

    public @Nullable Facade get(Direction side) {
        return sides[side.ordinal()];
    }

    public boolean has(Direction side) {
        return sides[side.ordinal()] != null;
    }

    // The block the facade on a side copies, or null (no facade, or a blank one).
    public @Nullable BlockState target(Direction side) {
        Facade facade = sides[side.ordinal()];
        return facade != null ? facade.target() : null;
    }

    public ConduitFacades with(Direction side, @Nullable Facade facade) {
        Facade[] copy = sides.clone();
        copy[side.ordinal()] = facade;
        return new ConduitFacades(copy);
    }

    public boolean isEmpty() {
        for (Facade facade : sides) {
            if (facade != null) {
                return false;
            }
        }
        return true;
    }

    // Facades on all six sides: the conduit is enclosed, a full block.
    public boolean enclosed() {
        for (Facade facade : sides) {
            if (facade == null) {
                return false;
            }
        }
        return true;
    }

    // Which sides have a facade, one bit per Direction ordinal (for caching shapes).
    public int mask() {
        int mask = 0;
        for (Direction side : Direction.values()) {
            if (sides[side.ordinal()] != null) {
                mask |= 1 << side.ordinal();
            }
        }
        return mask;
    }

    // What one side drops when its facade comes off: the blank facade, and the block it was dressed in beside it.
    public List<ItemStack> drops(Direction side) {
        Facade facade = sides[side.ordinal()];
        if (facade == null) {
            return List.of();
        }
        List<ItemStack> drops = new ArrayList<>(2);
        drops.add(new ItemStack(ModItems.CONDUIT_FACADE.get()));
        if (facade.target() != null) {
            drops.add(new ItemStack(facade.target().getBlock()));
        }
        return drops;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ConduitFacades facades && Arrays.equals(sides, facades.sides);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(sides);
    }
}
