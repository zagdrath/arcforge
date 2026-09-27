/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.block.multiblock;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

// A casing with a window onto a tray of the column: glass above a steel sill. Formed, the column's renderer
// draws the tray inside, the fraction that pools at its height (FRACTION, set when the column forms) and
// vapour rising while the column runs (LIT). See-through, so light gets in (sunlight too, once formed).
public class TrayLevelCasingBlock extends DistillationArrayCasingBlock {
    // Bottom to top.
    public enum Fraction implements StringRepresentable {
        HEAVY("heavy"), LIGHT("light"), NAPHTHA("naphtha"), VAPOR("vapor");

        private final String name;

        Fraction(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        // The fractions a column of this height shows, bottom to top: only those it makes.
        private static Fraction[] sequence(int height) {
            if (height >= 8) {
                return new Fraction[] { HEAVY, LIGHT, NAPHTHA, VAPOR };
            }
            return height >= 6 ? new Fraction[] { HEAVY, NAPHTHA, VAPOR } : new Fraction[] { NAPHTHA, VAPOR };
        }

        // What the tray at this layer (0 at the bottom) of a column this tall shows.
        public static Fraction at(int layer, int height) {
            Fraction[] sequence = sequence(height);
            return sequence[Math.min(sequence.length - 1, sequence.length * layer / height)];
        }
    }

    public static final EnumProperty<Fraction> FRACTION = EnumProperty.create("fraction", Fraction.class);

    public TrayLevelCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(FRACTION, Fraction.VAPOR).setValue(LIT, false));
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return state.getValue(FORMED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, FRACTION, LIT);
    }
}
