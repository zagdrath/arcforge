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
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;

// The Solar Thermal Array's tower casing. Formed, one casing draws the tower's base (PART base, at the
// bottom layer's minimum corner) and one the mast and yoke (upper_ns / upper_ew by the tracking axis, at the
// third layer's minimum corner); the rest draw nothing. Any casing on the outside can be a heat port.
public class SolarThermalArrayCasingBlock extends SolarBlock {
    public enum Part implements StringRepresentable {
        NONE("none"), BASE("base"), UPPER_NS("upper_ns"), UPPER_EW("upper_ew"), OTHER("other");

        private final String name;

        Part(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public SolarThermalArrayCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(PART, Part.NONE).setValue(MultiblockPorts.PORT, SideMode.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, PART, MultiblockPorts.PORT, MultiblockPorts.PORT_FACE);
    }
}
