/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.conduit.item;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

// A stack travelling through one item conduit: it enters on `from`, crosses the core at progress 0.5,
// and leaves on `to` at progress 1. The destination (sink conduit + side) is only known on the server.
public final class ItemPacket {
    public static final Codec<ItemPacket> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("stack").forGetter(p -> p.stack),
            Direction.CODEC.fieldOf("from").forGetter(p -> p.from),
            Direction.CODEC.fieldOf("to").forGetter(p -> p.to),
            Codec.FLOAT.fieldOf("progress").forGetter(p -> p.progress),
            BlockPos.CODEC.optionalFieldOf("dest_pos").forGetter(p -> Optional.ofNullable(p.destinationPos)),
            Direction.CODEC.optionalFieldOf("dest_side").forGetter(p -> Optional.ofNullable(p.destinationSide))
    ).apply(instance, (stack, from, to, progress, destPos, destSide) ->
            new ItemPacket(stack, from, to, progress, destPos.orElse(null), destSide.orElse(null))));

    public ItemStack stack;
    public Direction from;
    public Direction to;
    public float progress;
    public @Nullable BlockPos destinationPos;
    public @Nullable Direction destinationSide;

    public ItemPacket(ItemStack stack, Direction from, Direction to, float progress, @Nullable BlockPos destinationPos, @Nullable Direction destinationSide) {
        this.stack = stack;
        this.from = from;
        this.to = to;
        this.progress = progress;
        this.destinationPos = destinationPos;
        this.destinationSide = destinationSide;
    }

    // Position inside the block (0..1 on each axis): from the entry face to the centre, then to the exit face.
    public static Vec3 positionAt(Direction from, Direction to, float progress) {
        Vec3 centre = new Vec3(0.5, 0.5, 0.5);
        if (progress < 0.5F) {
            Vec3 entry = centre.add(Vec3.atLowerCornerOf(from.getUnitVec3i()).scale(0.5));
            return entry.lerp(centre, progress * 2.0F);
        }
        Vec3 exit = centre.add(Vec3.atLowerCornerOf(to.getUnitVec3i()).scale(0.5));
        return centre.lerp(exit, (progress - 0.5F) * 2.0F);
    }
}
