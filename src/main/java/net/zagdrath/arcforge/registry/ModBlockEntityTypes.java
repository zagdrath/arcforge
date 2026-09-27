/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;

public final class ModBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Arcforge.MODID);

    public static final Supplier<BlockEntityType<GeothermalPlantBlockEntity>> GEOTHERMAL_PLANT = BLOCK_ENTITY_TYPES.register("geothermal_plant",
            () -> new BlockEntityType<>(GeothermalPlantBlockEntity::new, ModBlocks.GEOTHERMAL_PLANT.get()));

    // Energy and thermal conduits: state only, rendered entirely by their block models.
    public static final Supplier<BlockEntityType<ConduitBlockEntity>> CONDUIT = BLOCK_ENTITY_TYPES.register("conduit",
            () -> new BlockEntityType<>(ConduitBlockEntity::new, conduitBlocks(false)));

    // Item and liquid conduits: glass, with a block entity renderer drawing their contents.
    public static final Supplier<BlockEntityType<ConduitBlockEntity>> TRANSPARENT_CONDUIT = BLOCK_ENTITY_TYPES.register("transparent_conduit",
            () -> new BlockEntityType<>(ConduitBlockEntity::new, conduitBlocks(true)));

    private ModBlockEntityTypes() {}

    private static Block[] conduitBlocks(boolean transparent) {
        return ModBlocks.allConduits().stream()
                .map(DeferredBlock::get)
                .filter(block -> block.getConduitType().isTransparent() == transparent)
                .toArray(Block[]::new);
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITY_TYPES.register(modEventBus);
    }
}
