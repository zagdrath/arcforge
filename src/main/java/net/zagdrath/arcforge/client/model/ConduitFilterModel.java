/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.math.Quadrant;

import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;

// Item, fluid and pressurized conduits with the Conduit Filter sleeve on each filtered arm. The conduits' own
// models are plain multiparts, so after baking each of their block state models is wrapped in this one, which
// adds arcforge:block/conduit/filter_attachment_<look> for the sides the block entity reports (its model data,
// see ConduitBlockEntity.packFilterModes). The sleeve is built facing north like the arm models and baked once
// per side with the arms' rotations; its LED is grey, green or red like the filter item.
public final class ConduitFilterModel extends DelegateBlockStateModel {
    private static final Map<FilterSettings.Mode, Map<Direction, StandaloneModelKey<BlockStateModelPart>>> KEYS = new EnumMap<>(FilterSettings.Mode.class);

    static {
        for (FilterSettings.Mode mode : FilterSettings.Mode.values()) {
            Map<Direction, StandaloneModelKey<BlockStateModelPart>> sides = new EnumMap<>(Direction.class);
            for (Direction side : Direction.values()) {
                String name = Arcforge.MODID + ":conduit_filter_" + mode.getSerializedName() + "_" + side.getSerializedName();
                sides.put(side, new StandaloneModelKey<>(() -> name));
            }
            KEYS.put(mode, sides);
        }
    }

    // Each look's sleeve, by side.
    private final Map<FilterSettings.Mode, Map<Direction, BlockStateModelPart>> sleeves;
    private final int sleeveFlags;

    private ConduitFilterModel(BlockStateModel delegate, Map<FilterSettings.Mode, Map<Direction, BlockStateModelPart>> sleeves, int sleeveFlags) {
        super(delegate);
        this.sleeves = sleeves;
        this.sleeveFlags = sleeveFlags;
    }

    private static Identifier model(FilterSettings.Mode mode) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/conduit/filter_attachment_" + mode.getSerializedName());
    }

    // The multipart arms' rotations: north as built, then y for the sides and x for up and down.
    private static BlockModelRotation rotation(Direction side) {
        return BlockModelRotation.get(switch (side) {
            case NORTH -> Quadrant.fromXYZAngles(Quadrant.R0, Quadrant.R0, Quadrant.R0);
            case EAST -> Quadrant.fromXYZAngles(Quadrant.R0, Quadrant.R90, Quadrant.R0);
            case SOUTH -> Quadrant.fromXYZAngles(Quadrant.R0, Quadrant.R180, Quadrant.R0);
            case WEST -> Quadrant.fromXYZAngles(Quadrant.R0, Quadrant.R270, Quadrant.R0);
            case UP -> Quadrant.fromXYZAngles(Quadrant.R270, Quadrant.R0, Quadrant.R0);
            case DOWN -> Quadrant.fromXYZAngles(Quadrant.R90, Quadrant.R0, Quadrant.R0);
        });
    }

    public static void registerStandalone(ModelEvent.RegisterStandalone event) {
        KEYS.forEach((mode, sides) -> sides.forEach((side, key) ->
                event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(model(mode), rotation(side)))));
    }

    // Wraps the block state models of every conduit that takes filters.
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        var result = event.getBakingResult();
        Map<FilterSettings.Mode, Map<Direction, BlockStateModelPart>> sleeves = new EnumMap<>(FilterSettings.Mode.class);
        int flags = 0;
        for (var mode : KEYS.entrySet()) {
            Map<Direction, BlockStateModelPart> sides = new EnumMap<>(Direction.class);
            for (var side : mode.getValue().entrySet()) {
                BlockStateModelPart part = result.standaloneModels().get(side.getValue());
                if (part != null) {
                    sides.put(side.getKey(), part);
                    flags |= part.materialFlags();
                }
            }
            sleeves.put(mode.getKey(), sides);
        }
        int sleeveFlags = flags;
        result.blockStateModels().replaceAll((state, model) ->
                state.getBlock() instanceof ConduitBlock conduit && ConduitBlockEntity.acceptsFilters(conduit.getConduitType())
                        ? new ConduitFilterModel(model, sleeves, sleeveFlags)
                        : model);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        super.collectParts(level, pos, state, random, parts);
        Integer packed = level.getModelData(pos).get(ConduitBlockEntity.FILTER_MODES);
        if (packed == null || packed == 0) {
            return;
        }
        FilterSettings.Mode[] modes = FilterSettings.Mode.values();
        for (Direction side : Direction.values()) {
            int look = (packed >>> (side.ordinal() * 2)) & 3;
            if (look > 0 && ConduitBlock.mode(state, side).isPort()) {
                BlockStateModelPart sleeve = sleeves.get(modes[look - 1]).get(side);
                if (sleeve != null) {
                    parts.add(sleeve);
                }
            }
        }
    }

    @Override
    @BakedQuad.MaterialFlags
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return super.materialFlags(level, pos, state) | sleeveFlags;
    }
}
