/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.machine.GeothermalPlantBlock;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnaceBrickWallBlock;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnaceBricksBlock;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnacePortBlock;
import net.zagdrath.arcforge.block.multiblock.CarbonizerBlock;
import net.zagdrath.arcforge.block.storage.EnergyCellBlock;
import net.zagdrath.arcforge.block.storage.FluidTankBlock;
import net.zagdrath.arcforge.block.storage.StorageBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Arcforge.MODID);

    public static final DeferredBlock<GeothermalPlantBlock> GEOTHERMAL_PLANT = BLOCKS.registerBlock("geothermal_plant",
            GeothermalPlantBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(GeothermalPlantBlock.LIT) ? 7 : 0));

    // --- Steelmaking ---

    public static final DeferredBlock<CarbonizerBlock> CARBONIZER = BLOCKS.registerBlock("carbonizer",
            CarbonizerBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(CarbonizerBlock.LIT) && state.getValue(CarbonizerBlock.DEPTH) == CarbonizerBlock.Depth.FRONT ? 7 : 0));

    public static final DeferredBlock<ArcforgeFurnaceBricksBlock> ARCFORGE_FURNACE_BRICKS = BLOCKS.registerBlock("arcforge_furnace_bricks",
            ArcforgeFurnaceBricksBlock::new, ModBlocks::furnaceBrickProperties);

    public static final DeferredBlock<ArcforgeFurnaceBrickWallBlock> ARCFORGE_FURNACE_BRICK_WALL = BLOCKS.registerBlock("arcforge_furnace_brick_wall",
            ArcforgeFurnaceBrickWallBlock::new, p -> furnaceBrickProperties(p).forceSolidOn());

    public static final DeferredBlock<ArcforgeFurnacePortBlock> ARCFORGE_FURNACE_PORT = BLOCKS.registerBlock("arcforge_furnace_port",
            ArcforgeFurnacePortBlock::new,
            p -> furnaceBrickProperties(p).lightLevel(state -> state.getValue(ArcforgeFurnacePortBlock.LIT) ? 9 : 0));

    public static final DeferredBlock<Block> COAL_COKE_BLOCK = BLOCKS.registerSimpleBlock("coal_coke_block",
            p -> p.mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops());

    public static final DeferredBlock<Block> STEEL_BLOCK = BLOCKS.registerSimpleBlock("steel_block",
            p -> p.mapColor(MapColor.METAL)
                    .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                    .strength(5.0F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    public static final DeferredBlock<LiquidBlock> CREOSOTE = BLOCKS.registerBlock("creosote",
            p -> new LiquidBlock(ModFluids.CREOSOTE.get(), p) {},
            p -> p.mapColor(MapColor.COLOR_BROWN)
                    .replaceable()
                    .noCollision()
                    .strength(100.0F)
                    .pushReaction(PushReaction.POPPED)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY));

    // arcforge:<tier>_<type>_conduit for every type and tier.
    private static final Map<ConduitType, Map<ConduitTier, DeferredBlock<ConduitBlock>>> CONDUITS = new EnumMap<>(ConduitType.class);

    static {
        for (ConduitType type : ConduitType.values()) {
            Map<ConduitTier, DeferredBlock<ConduitBlock>> byTier = new EnumMap<>(ConduitTier.class);
            for (ConduitTier tier : ConduitTier.values()) {
                byTier.put(tier, BLOCKS.registerBlock(conduitName(type, tier),
                        p -> type.hasActiveState() ? new ActiveConduitBlock(p, type, tier) : new ConduitBlock(p, type, tier),
                        ModBlocks::conduitProperties));
            }
            CONDUITS.put(type, byTier);
        }
    }

    // arcforge:<tier>_fluid_tank and arcforge:<tier>_energy_cell.
    private static final Map<ConduitTier, DeferredBlock<FluidTankBlock>> FLUID_TANKS = new EnumMap<>(ConduitTier.class);
    private static final Map<ConduitTier, DeferredBlock<EnergyCellBlock>> ENERGY_CELLS = new EnumMap<>(ConduitTier.class);

    // Glass body on a metal frame: breaks like glass, otherwise sounds like metal.
    private static final SoundType TANK_SOUND = new SoundType(1.0F, 1.0F, SoundEvents.GLASS_BREAK, SoundEvents.METAL_STEP,
            SoundEvents.METAL_PLACE, SoundEvents.METAL_HIT, SoundEvents.METAL_FALL);

    static {
        for (ConduitTier tier : ConduitTier.values()) {
            FLUID_TANKS.put(tier, BLOCKS.registerBlock(tier.getSerializedName() + "_fluid_tank",
                    p -> new FluidTankBlock(p, tier),
                    p -> p.mapColor(MapColor.METAL)
                            .strength(2.0F)
                            .sound(TANK_SOUND)
                            .noOcclusion()
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos, box) -> false)
                            .isRedstoneConductor((state, level, pos) -> false)));
            ENERGY_CELLS.put(tier, BLOCKS.registerBlock(tier.getSerializedName() + "_energy_cell",
                    p -> new EnergyCellBlock(p, tier),
                    p -> p.mapColor(MapColor.METAL)
                            .strength(3.0F)
                            .sound(SoundType.METAL)));
        }
    }

    private ModBlocks() {}

    public static String conduitName(ConduitType type, ConduitTier tier) {
        return tier.getSerializedName() + "_" + type.getSerializedName() + "_conduit";
    }

    private static BlockBehaviour.Properties furnaceBrickProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.TERRACOTTA_BROWN)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(2.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.MUD_BRICKS);
    }

    private static BlockBehaviour.Properties conduitProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL)
                .strength(1.0F)
                .sound(SoundType.METAL)
                .noOcclusion()
                .dynamicShape()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos, box) -> false)
                .isRedstoneConductor((state, level, pos) -> false);
    }

    public static DeferredBlock<ConduitBlock> conduit(ConduitType type, ConduitTier tier) {
        return CONDUITS.get(type).get(tier);
    }

    // Every conduit, ordered by type then tier.
    public static List<DeferredBlock<ConduitBlock>> allConduits() {
        List<DeferredBlock<ConduitBlock>> all = new ArrayList<>();
        for (ConduitType type : ConduitType.values()) {
            for (ConduitTier tier : ConduitTier.values()) {
                all.add(conduit(type, tier));
            }
        }
        return all;
    }

    public static DeferredBlock<FluidTankBlock> fluidTank(ConduitTier tier) {
        return FLUID_TANKS.get(tier);
    }

    public static DeferredBlock<EnergyCellBlock> energyCell(ConduitTier tier) {
        return ENERGY_CELLS.get(tier);
    }

    // Every fluid tank then every energy cell, each ordered by tier.
    public static List<DeferredBlock<? extends StorageBlock>> allStorage() {
        List<DeferredBlock<? extends StorageBlock>> all = new ArrayList<>();
        all.addAll(FLUID_TANKS.values());
        all.addAll(ENERGY_CELLS.values());
        return all;
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
