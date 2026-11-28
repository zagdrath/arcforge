/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.zagdrath.arcforge.block.StonePillarBlock;

// Natural stones: nine rocks that generate in the world as granite and diorite do (blobs, layers and, for Kimberlite, pipes;
// see ArcforgeConfig "world.stones" and the stone_* worldgen files), each with a family to build with:
//   <stone>                     the natural rock; mined, it drops its cobbled form (Silk Touch keeps it), as stone does
//   cobbled_<stone>             smelts back into the natural rock
//   polished_<stone>
//   <stone>_bricks
//   small_<stone>_bricks
//   <stone>_pillar              a column on an axis, joining with its neighbours along it (StonePillarBlock)
//   chiseled_<stone>
// and every one of those seven in stairs, a slab and a wall (<stone>_stairs, cobbled_<stone>_slab, <stone>_brick_wall,
// small_<stone>_brick_stairs, <stone>_pillar_slab, chiseled_<stone>_wall, ...): 28 blocks a stone.
// What each is for (tags, crushing, calcining, sifting, fiberizing, flux) is all in data; STONES_HANDOFF.md lists it.
public final class ModStones {
    public enum Stone {
        LIMESTONE(MapColor.SAND, 1.5F, SoundType.STONE),
        DOLOMITE(MapColor.TERRACOTTA_WHITE, 1.5F, SoundType.STONE),
        SLATE(MapColor.DEEPSLATE, 1.5F, SoundType.DEEPSLATE),
        SERPENTINE(MapColor.COLOR_GREEN, 1.5F, SoundType.STONE),
        SYLVINITE(MapColor.TERRACOTTA_PINK, 1.0F, SoundType.STONE),
        KIMBERLITE(MapColor.TERRACOTTA_CYAN, 2.5F, SoundType.DEEPSLATE),
        PUMICE(MapColor.COLOR_LIGHT_GRAY, 0.8F, SoundType.TUFF),
        SCORIA(MapColor.TERRACOTTA_RED, 1.25F, SoundType.TUFF),
        TRAVERTINE(MapColor.TERRACOTTA_YELLOW, 1.5F, SoundType.STONE);

        private final MapColor color;
        private final float hardness;
        private final SoundType sound;

        Stone(MapColor color, float hardness, SoundType sound) {
            this.color = color;
            this.hardness = hardness;
            this.sound = sound;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        // Stone's resistance (6) whatever the hardness; pumice and sylvinite are soft, kimberlite tough.
        BlockBehaviour.Properties properties(BlockBehaviour.Properties p) {
            return p.mapColor(color).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
                    .strength(hardness, 6.0F).sound(sound);
        }
    }

    // One worked form of a stone and its three shapes.
    public record Variant(DeferredBlock<? extends Block> block, DeferredBlock<StairBlock> stairs, DeferredBlock<SlabBlock> slab,
            DeferredBlock<WallBlock> wall) {
        public List<DeferredBlock<? extends Block>> all() {
            return List.of(block, stairs, slab, wall);
        }
    }

    public record Family(Stone stone, Variant natural, Variant cobbled, Variant polished, Variant bricks, Variant smallBricks,
            Variant pillar, Variant chiseled) {
        // Every block of the family, in creative-tab order: each form followed by its stairs, slab and wall.
        public List<DeferredBlock<? extends Block>> all() {
            List<DeferredBlock<? extends Block>> out = new ArrayList<>();
            for (Variant v : List.of(natural, cobbled, polished, bricks, smallBricks, pillar, chiseled)) {
                out.addAll(v.all());
            }
            return out;
        }
    }

    private static final Map<Stone, Family> FAMILIES = new EnumMap<>(Stone.class);
    private static final List<DeferredItem<BlockItem>> ITEMS = new ArrayList<>();

    static {
        for (Stone stone : Stone.values()) {
            Family family = register(stone);
            FAMILIES.put(stone, family);
            for (DeferredBlock<? extends Block> block : family.all()) {
                ITEMS.add(ModItems.ITEMS.registerSimpleBlockItem(block));
            }
        }
    }

    private ModStones() {}

    // Loads the class, so its blocks and items join the registers before they're handed to the event bus.
    public static void bootstrap() {}

    public static Family family(Stone stone) {
        return FAMILIES.get(stone);
    }

    // Every stone block item, stone by stone, each family in creative-tab order.
    public static List<DeferredItem<BlockItem>> items() {
        return Collections.unmodifiableList(ITEMS);
    }

    private static Family register(Stone stone) {
        String id = stone.id();
        UnaryOperator<BlockBehaviour.Properties> props = stone::properties;
        UnaryOperator<BlockBehaviour.Properties> cobbledProps = p -> stone.properties(p).strength(stone.hardness + 0.5F, 6.0F);

        Variant natural = variant(ModBlocks.BLOCKS.registerSimpleBlock(id, props), id, props);
        Variant cobbled = variant(ModBlocks.BLOCKS.registerSimpleBlock("cobbled_" + id, cobbledProps), "cobbled_" + id, cobbledProps);
        Variant polished = variant(ModBlocks.BLOCKS.registerSimpleBlock("polished_" + id, props), "polished_" + id, props);
        Variant bricks = variant(ModBlocks.BLOCKS.registerSimpleBlock(id + "_bricks", props), id + "_brick", props);
        Variant smallBricks = variant(ModBlocks.BLOCKS.registerSimpleBlock("small_" + id + "_bricks", props), "small_" + id + "_brick",
                props);
        Variant pillar = variant(ModBlocks.BLOCKS.registerBlock(id + "_pillar", StonePillarBlock::new, props), id + "_pillar",
                props);
        Variant chiseled = variant(ModBlocks.BLOCKS.registerSimpleBlock("chiseled_" + id, props), "chiseled_" + id, props);
        return new Family(stone, natural, cobbled, polished, bricks, smallBricks, pillar, chiseled);
    }

    // A form's stairs, slab and wall, named from its shape stem (limestone_brick_stairs from "limestone_brick").
    private static Variant variant(DeferredBlock<? extends Block> base, String stem, UnaryOperator<BlockBehaviour.Properties> props) {
        DeferredBlock<StairBlock> stairs = stairs(stem + "_stairs", base, props);
        DeferredBlock<SlabBlock> slab = ModBlocks.BLOCKS.registerBlock(stem + "_slab", SlabBlock::new, props);
        DeferredBlock<WallBlock> wall = ModBlocks.BLOCKS.registerBlock(stem + "_wall", WallBlock::new, p -> props.apply(p).forceSolidOn());
        return new Variant(base, stairs, slab, wall);
    }

    private static DeferredBlock<StairBlock> stairs(String name, DeferredBlock<? extends Block> base, UnaryOperator<BlockBehaviour.Properties> props) {
        return ModBlocks.BLOCKS.registerBlock(name, p -> new StairBlock(base.get().defaultBlockState(), p), props);
    }
}
