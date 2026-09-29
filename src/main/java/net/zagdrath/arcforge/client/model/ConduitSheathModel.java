/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.math.Quadrant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.DelegateBlockStateModel;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.registry.ModDataComponents;

// A sheathed conduit's plastic bands: one round the core and one on each connected arm, tinted by the conduit's
// colour (tint index 1, so it never meets the gas and thermal glow at index 0). Added to every conduit's model.
public final class ConduitSheathModel extends DelegateBlockStateModel {
    private static final StandaloneModelKey<BlockStateModelPart> CORE = new StandaloneModelKey<>(() -> Arcforge.MODID + ":conduit_sheath_core");
    private static final Map<Direction, StandaloneModelKey<BlockStateModelPart>> ARMS = new EnumMap<>(Direction.class);

    static {
        for (Direction side : Direction.values()) {
            String name = Arcforge.MODID + ":conduit_sheath_arm_" + side.getSerializedName();
            ARMS.put(side, new StandaloneModelKey<>(() -> name));
        }
    }

    private final BlockStateModelPart core;
    private final Map<Direction, BlockStateModelPart> arms;
    private final int flags;

    private ConduitSheathModel(BlockStateModel delegate, BlockStateModelPart core, Map<Direction, BlockStateModelPart> arms, int flags) {
        super(delegate);
        this.core = core;
        this.arms = arms;
        this.flags = flags;
    }

    // The north arm as built, turned to each side (as the filter sleeves are).
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
        event.register(CORE, SimpleUnbakedStandaloneModel.simpleModelWrapper(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/conduit/sheath_core"),
                BlockModelRotation.IDENTITY));
        ARMS.forEach((side, key) -> event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(
                Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/conduit/sheath_arm"), rotation(side))));
    }

    public static void wrap(ModelEvent.ModifyBakingResult event) {
        var result = event.getBakingResult();
        BlockStateModelPart core = result.standaloneModels().get(CORE);
        if (core == null) {
            return;
        }
        Map<Direction, BlockStateModelPart> arms = new EnumMap<>(Direction.class);
        int flags = core.materialFlags();
        for (var arm : ARMS.entrySet()) {
            BlockStateModelPart part = result.standaloneModels().get(arm.getValue());
            if (part != null) {
                arms.put(arm.getKey(), part);
                flags |= part.materialFlags();
            }
        }
        int sheathFlags = flags;
        result.blockStateModels().replaceAll((state, model) ->
                state.getBlock() instanceof ConduitBlock ? new ConduitSheathModel(model, core, arms, sheathFlags) : model);
    }

    @Override
    public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
        super.collectParts(level, pos, state, random, parts);
        Integer color = level.getModelData(pos).get(ConduitBlockEntity.COLOR);
        if (color == null || color == 0) {
            return;
        }
        parts.add(core);
        for (Direction side : Direction.values()) {
            BlockStateModelPart arm = arms.get(side);
            if (arm != null && ConduitBlock.mode(state, side) != net.zagdrath.arcforge.conduit.ConnectionMode.NONE) {
                parts.add(arm);
            }
        }
    }

    @Override
    public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        return super.materialFlags(level, pos, state) | flags;
    }

    // Tint index 1 on conduit blocks: the sheath's dye colour.
    public enum Tint implements BlockTintSource {
        INSTANCE;

        @Override
        public int color(BlockState state) {
            return 0xFFFFFFFF;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit && conduit.getColor() != null
                    ? conduit.getColor().getTextureDiffuseColor() | 0xFF000000 : 0xFFFFFFFF;
        }
    }

    // The item tint (arcforge:conduit_color): the stack's sheath colour, or the default.
    public record ItemTint(int defaultColor) implements ItemTintSource {
        public static final Identifier ID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "conduit_color");
        public static final MapCodec<ItemTint> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                com.mojang.serialization.Codec.INT.optionalFieldOf("default", -1).forGetter(ItemTint::defaultColor))
                .apply(i, ItemTint::new));

        @Override
        public int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
            DyeColor color = stack.get(ModDataComponents.CONDUIT_COLOR.get());
            return color != null ? color.getTextureDiffuseColor() | 0xFF000000 : defaultColor;
        }

        @Override
        public MapCodec<ItemTint> type() {
            return MAP_CODEC;
        }
    }
}
