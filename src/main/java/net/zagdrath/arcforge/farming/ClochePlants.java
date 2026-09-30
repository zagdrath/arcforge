/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.farming;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.recipe.ClocheRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModRecipes;

// What grows in the Glass Cloche, Grow Chamber and Hydroponic Cell: the arcforge:cloche recipe for the seed and soil, or,
// when none names the seed, any vanilla-style crop (a block item whose block is a CropBlock, as most modded crops are):
// it grows in the soils of #arcforge:cloche_soils/farmland over cloche.fallbackTime, and its harvest is what the grown
// crop block drops (its loot table). Recipes win over the fallback, so a pack can tune any crop.
public final class ClochePlants {
    // Where fallback crops grow: the farmland-like soils (dirt and Loam).
    public static final TagKey<Item> FARMLAND_SOILS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Arcforge.MODID, "cloche_soils/farmland"));

    private ClochePlants() {}

    // A plant that grows: from a recipe (null for a fallback crop), its time at the Glass Cloche's speed, and how it's drawn.
    public record Plant(@Nullable RecipeHolder<ClocheRecipe> recipe, Item seed, int time, ClocheRecipe.Render render) {
        // What identifies it: the recipe, or the seed for a fallback crop. Progress starts over when this changes.
        public Identifier key() {
            return recipe != null ? recipe.id().identifier() : BuiltInRegistries.ITEM.getKey(seed);
        }

        // The block drawn at this much growth (0..1), or null if it has none. For "age" growth, the age property steps
        // with the growth; otherwise the renderer scales it (see scales()).
        public @Nullable BlockState stateAt(float growth) {
            BlockState base = baseState();
            if (base == null) {
                return null;
            }
            if (recipe == null && base.getBlock() instanceof CropBlock crop) {
                return crop.getStateForAge(Math.round(Math.clamp(growth, 0.0F, 1.0F) * crop.getMaxAge()));
            }
            IntegerProperty age = age(base);
            if (age == null || !render.grow().equals("age")) {
                return base;
            }
            int min = age.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElse(0);
            int max = age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0);
            return base.setValue(age, min + Math.round(Math.clamp(growth, 0.0F, 1.0F) * (max - min)));
        }

        // Whether the renderer grows it in size instead (no age property to step, or "grow": "scale").
        public boolean scales() {
            BlockState base = baseState();
            if (recipe == null && base != null && base.getBlock() instanceof CropBlock) {
                return false;
            }
            return base == null || !render.grow().equals("age") || age(base) == null;
        }

        private @Nullable BlockState baseState() {
            Block block = render.block().orElseGet(() -> seed instanceof BlockItem item ? item.getBlock() : null);
            return block == null ? null : withProperties(block.defaultBlockState(), render.properties());
        }

        // One harvest: the recipe's results (each rolled) and seed copies, or a fallback crop's drops when grown.
        public List<ItemStack> harvest(ServerLevel level, BlockPos pos, RandomSource random) {
            List<ItemStack> out = new ArrayList<>();
            if (recipe != null) {
                for (ClocheRecipe.Output output : recipe.value().results()) {
                    if (output.chance() >= 1.0F || random.nextFloat() < output.chance()) {
                        out.add(output.item().create());
                    }
                }
                if (recipe.value().seedOutput() > 0) {
                    out.add(new ItemStack(seed, recipe.value().seedOutput()));
                }
            } else if (seed instanceof BlockItem item && item.getBlock() instanceof CropBlock crop) {
                out.addAll(Block.getDrops(crop.getStateForAge(crop.getMaxAge()), level, pos, null));
            }
            out.removeIf(ItemStack::isEmpty);
            return out;
        }
    }

    // What grows from this seed in this soil (the soil is ignored hydroponically), or null.
    public static @Nullable Plant find(@Nullable Level level, ItemStack seed, ItemStack soil, boolean hydroponic) {
        if (seed.isEmpty()) {
            return null;
        }
        Optional<RecipeHolder<ClocheRecipe>> recipe = MachineRecipes.cloche(level, seed, soil, hydroponic);
        if (recipe.isPresent()) {
            ClocheRecipe value = recipe.get().value();
            return new Plant(recipe.get(), seed.getItem(), value.time(), value.render().orElse(ClocheRecipe.Render.DEFAULT));
        }
        // No recipe names the seed at all: a vanilla-style crop grows in farmland soils.
        if (!MachineRecipes.isClocheSeed(level, seed) && isFallbackCrop(seed) && (hydroponic || soil.is(FARMLAND_SOILS))) {
            return new Plant(null, seed.getItem(), ArcforgeConfig.CLOCHE_FALLBACK_TIME.getAsInt(), ClocheRecipe.Render.DEFAULT);
        }
        return null;
    }

    public static boolean isFallbackCrop(ItemStack seed) {
        return seed.getItem() instanceof BlockItem item && item.getBlock() instanceof CropBlock;
    }

    // Anything that could grow in some farm: what the seed slot takes.
    public static boolean isSeed(@Nullable Level level, ItemStack stack) {
        return MachineRecipes.isClocheSeed(level, stack) || isFallbackCrop(stack);
    }

    // The state with each named property set to its named value (unknown names or values are left alone).
    public static BlockState withProperties(BlockState state, Map<String, String> properties) {
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if (property != null) {
                state = set(state, property, entry.getValue());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    private static @Nullable IntegerProperty age(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integer && property.getName().equals("age")) {
                return integer;
            }
        }
        return null;
    }
}
