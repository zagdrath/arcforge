/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.zagdrath.arcforge.multiblock.FireboxArrayStructure;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;

// A block of the Firebox Array's box (see FireboxArrayStructure): a Firebox Array Casing or the controller. FORMED is set
// on every block of a formed box, which then draws as one connected skin (see ConnectedModel, "firebox_array"); INWARD,
// on a wall block, is the way into the box, the face its model leaves out so the renderer's firebrick lining shows.
public interface FireboxArrayPart extends MultiblockPart {
    BooleanProperty FORMED = BooleanProperty.create("formed");
    EnumProperty<Inward> INWARD = EnumProperty.create("inward", Inward.class);

    enum Inward implements StringRepresentable {
        NONE(null), DOWN(Direction.DOWN), UP(Direction.UP), NORTH(Direction.NORTH), SOUTH(Direction.SOUTH), WEST(Direction.WEST),
        EAST(Direction.EAST);

        private final @Nullable Direction direction;

        Inward(@Nullable Direction direction) {
            this.direction = direction;
        }

        public @Nullable Direction direction() {
            return direction;
        }

        public static Inward of(@Nullable Direction direction) {
            for (Inward inward : values()) {
                if (inward.direction == direction) {
                    return inward;
                }
            }
            return NONE;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    // The way into the box from this block, or null.
    static @Nullable Direction inward(BlockState state) {
        return state.hasProperty(INWARD) ? state.getValue(INWARD).direction() : null;
    }

    @Override
    default @Nullable MultiblockController findController(Level level, BlockPos pos) {
        return FireboxArrayStructure.findController(level, pos);
    }

    @Override
    default InteractionResult useWrench(UseOnContext context) {
        return FireboxArrayStructure.wrenchPart(context);
    }
}
