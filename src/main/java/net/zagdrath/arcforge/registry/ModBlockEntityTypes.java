/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.CombustionPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.FireboxBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.GeothermalPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ThermoelectricPlantBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ArcforgeFurnaceBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.CarbonizerBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.EnergyCellBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.FluidTankBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.HeatCellBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitTier;

public final class ModBlockEntityTypes {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Arcforge.MODID);

    public static final Supplier<BlockEntityType<GeothermalPlantBlockEntity>> GEOTHERMAL_PLANT = BLOCK_ENTITY_TYPES.register("geothermal_plant",
            () -> new BlockEntityType<>(GeothermalPlantBlockEntity::new, ModBlocks.GEOTHERMAL_PLANT.get()));

    public static final Supplier<BlockEntityType<CombustionPlantBlockEntity>> COMBUSTION_PLANT = BLOCK_ENTITY_TYPES.register("combustion_plant",
            () -> new BlockEntityType<>(CombustionPlantBlockEntity::new, ModBlocks.COMBUSTION_PLANT.get()));

    public static final Supplier<BlockEntityType<FireboxBlockEntity>> FIREBOX = BLOCK_ENTITY_TYPES.register("firebox",
            () -> new BlockEntityType<>(FireboxBlockEntity::new, ModBlocks.FIREBOX.get()));

    public static final Supplier<BlockEntityType<ThermoelectricPlantBlockEntity>> THERMOELECTRIC_PLANT = BLOCK_ENTITY_TYPES.register("thermoelectric_plant",
            () -> new BlockEntityType<>(ThermoelectricPlantBlockEntity::new, ModBlocks.THERMOELECTRIC_PLANT.get()));

    // Energy and thermal conduits: state only, rendered entirely by their block models.
    public static final Supplier<BlockEntityType<ConduitBlockEntity>> CONDUIT = BLOCK_ENTITY_TYPES.register("conduit",
            () -> new BlockEntityType<>(ConduitBlockEntity::new, conduitBlocks(false)));

    // Item and fluid conduits: glass, with a block entity renderer drawing their contents.
    public static final Supplier<BlockEntityType<ConduitBlockEntity>> TRANSPARENT_CONDUIT = BLOCK_ENTITY_TYPES.register("transparent_conduit",
            () -> new BlockEntityType<>(ConduitBlockEntity::new, conduitBlocks(true)));

    public static final Supplier<BlockEntityType<FluidTankBlockEntity>> FLUID_TANK = BLOCK_ENTITY_TYPES.register("fluid_tank",
            () -> new BlockEntityType<>(FluidTankBlockEntity::new, tierBlocks(ModBlocks::fluidTank)));

    public static final Supplier<BlockEntityType<EnergyCellBlockEntity>> ENERGY_CELL = BLOCK_ENTITY_TYPES.register("energy_cell",
            () -> new BlockEntityType<>(EnergyCellBlockEntity::new, tierBlocks(ModBlocks::energyCell)));

    public static final Supplier<BlockEntityType<HeatCellBlockEntity>> HEAT_CELL = BLOCK_ENTITY_TYPES.register("heat_cell",
            () -> new BlockEntityType<>(HeatCellBlockEntity::new, tierBlocks(ModBlocks::heatCell)));

    // Every Carbonizer block has one; the structure's master block runs it.
    public static final Supplier<BlockEntityType<CarbonizerBlockEntity>> CARBONIZER = BLOCK_ENTITY_TYPES.register("carbonizer",
            () -> new BlockEntityType<>(CarbonizerBlockEntity::new, ModBlocks.CARBONIZER.get()));

    // On the furnace port only; bricks and walls are plain blocks that find the port.
    public static final Supplier<BlockEntityType<ArcforgeFurnaceBlockEntity>> ARCFORGE_FURNACE = BLOCK_ENTITY_TYPES.register("arcforge_furnace",
            () -> new BlockEntityType<>(ArcforgeFurnaceBlockEntity::new, ModBlocks.ARCFORGE_FURNACE_PORT.get()));

    private ModBlockEntityTypes() {}

    private static Block[] tierBlocks(Function<ConduitTier, DeferredBlock<? extends Block>> byTier) {
        return Arrays.stream(ConduitTier.values()).map(tier -> byTier.apply(tier).get()).toArray(Block[]::new);
    }

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
