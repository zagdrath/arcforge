/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.machine.GeothermalPlantBlock;
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

    private ModBlocks() {}

    public static String conduitName(ConduitType type, ConduitTier tier) {
        return tier.getSerializedName() + "_" + type.getSerializedName() + "_conduit";
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

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
