/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client.model;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.DynamicBlockStateModel;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.registry.ModDataComponents;

// A dyed conduit is coloured in its own texture, not wrapped in bands: an Energy or Thermodynamic Conduit's core
// stripe turns the dye's colour (dim when idle, lit when it carries power or heat), and the glass of Item, Fluid
// and Pressurized Conduits takes a tint of it. Each conduit blockstate part is
// {"type": "arcforge:dyeable", "model": <plain piece>, "x"/"y", "dyed": <the piece's _dyed model>}: the _dyed
// model is the same piece with the dye region cut out of its texture (<tier>_<kind>_dyed) and an extra layer
// on the same faces showing a greyscale mask of that region (block/conduit/dye/<kind>) at tint index 1.
public final class ConduitDyeModel {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "dyeable");

    private ConduitDyeModel() {}

    public record Unbaked(Variant variant, Identifier dyed) implements CustomUnbakedBlockStateModel {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Variant.MAP_CODEC.forGetter(Unbaked::variant),
                Identifier.CODEC.fieldOf("dyed").forGetter(Unbaked::dyed))
                .apply(instance, Unbaked::new));

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return MAP_CODEC;
        }

        // The dyed piece, turned like the plain one.
        private Variant dyedVariant() {
            return variant.withModel(dyed);
        }

        @Override
        public void resolveDependencies(Resolver resolver) {
            variant.resolveDependencies(resolver);
            dyedVariant().resolveDependencies(resolver);
        }

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            return new Baked(variant.bake(baker), dyedVariant().bake(baker));
        }
    }

    private static final class Baked implements DynamicBlockStateModel {
        private final BlockStateModelPart plain;
        private final BlockStateModelPart dyed;

        Baked(BlockStateModelPart plain, BlockStateModelPart dyed) {
            this.plain = plain;
            this.dyed = dyed;
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random, List<BlockStateModelPart> parts) {
            Integer color = level.getModelData(pos).get(ConduitBlockEntity.COLOR);
            parts.add(color != null && color != 0 ? dyed : plain);
        }

        @Override
        public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
            return null;
        }

        @Override
        public Material.Baked particleMaterial() {
            return plain.particleMaterial();
        }

        @Override
        @BakedQuad.MaterialFlags
        public int materialFlags() {
            return plain.materialFlags() | dyed.materialFlags();
        }
    }

    // How much of the dye's colour a lit Thermodynamic Conduit's stripe shows, from 100 °C up to 1,200 °C: it
    // brightens as it heats, the way the undyed one runs from red to white.
    private static final int HEAT_COLD = 100, HEAT_HOT = 1_200;
    private static final float HEAT_DIM = 0.55F;

    // Tint index 1 on conduit blocks: the dye's colour, over the greyscale mask (which carries the lit/idle shading).
    public enum Tint implements BlockTintSource {
        INSTANCE;

        @Override
        public int color(BlockState state) {
            return 0xFFFFFFFF;
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            if (!(level.getBlockEntity(pos) instanceof ConduitBlockEntity conduit) || conduit.getColor() == null) {
                return 0xFFFFFFFF;
            }
            int color = conduit.getColor().getTextureDiffuseColor() | 0xFF000000;
            if (conduit.getConduitType() == ConduitType.THERMAL && state.hasProperty(ActiveConduitBlock.ACTIVE)
                    && state.getValue(ActiveConduitBlock.ACTIVE)) {
                float t = Mth.clamp((conduit.getHeatTemperature() - HEAT_COLD) / (float) (HEAT_HOT - HEAT_COLD), 0.0F, 1.0F);
                return scale(color, Mth.lerp(t, HEAT_DIM, 1.0F));
            }
            return color;
        }

        private static int scale(int color, float factor) {
            return 0xFF000000
                    | (int) ((color >> 16 & 0xFF) * factor) << 16
                    | (int) ((color >> 8 & 0xFF) * factor) << 8
                    | (int) ((color & 0xFF) * factor);
        }
    }

    // The item tint (arcforge:conduit_color): the stack's dye colour, or the default. Dyed conduit items use their
    // _dyed item model, whose tints are [constant white, this].
    public record ItemTint(int defaultColor) implements ItemTintSource {
        public static final Identifier ID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "conduit_color");
        public static final MapCodec<ItemTint> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("default", -1).forGetter(ItemTint::defaultColor))
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
