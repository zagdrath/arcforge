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
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.machine.ArcCrusherBlock;
import net.zagdrath.arcforge.block.machine.InductionFurnaceBlock;
import net.zagdrath.arcforge.block.machine.MetalPressBlock;
import net.zagdrath.arcforge.block.machine.ElectricPumpBlock;
import net.zagdrath.arcforge.block.machine.SteamBoilerBlock;
import net.zagdrath.arcforge.block.machine.SteamTurbineBlock;
import net.zagdrath.arcforge.block.machine.CombustionPlantBlock;
import net.zagdrath.arcforge.block.multiblock.ArcCrushingArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.InductionFurnaceArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.MetalPressingArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.SteamBoilerArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.block.machine.FiberizerBlock;
import net.zagdrath.arcforge.block.machine.FireboxBlock;
import net.zagdrath.arcforge.block.machine.FuelBurnerBlock;
import net.zagdrath.arcforge.block.machine.GeothermalPlantBlock;
import net.zagdrath.arcforge.block.machine.InfuserBlock;
import net.zagdrath.arcforge.block.machine.MachineBlock;
import net.zagdrath.arcforge.block.machine.ThermoelectricPlantBlock;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnaceBrickWallBlock;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnaceBricksBlock;
import net.zagdrath.arcforge.block.multiblock.ArcforgeFurnacePortBlock;
import net.zagdrath.arcforge.block.multiblock.CarbonizerBlock;
import net.zagdrath.arcforge.block.storage.EnergyCellBlock;
import net.zagdrath.arcforge.block.storage.HeatCellBlock;
import net.zagdrath.arcforge.block.storage.PressurizedCylinderBlock;
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
                    .lightLevel(state -> state.getValue(MachineBlock.LIT) ? 7 : 0));

    public static final DeferredBlock<CombustionPlantBlock> COMBUSTION_PLANT = BLOCKS.registerBlock("combustion_plant",
            CombustionPlantBlock::new, p -> machineProperties(p, 13));

    public static final DeferredBlock<FireboxBlock> FIREBOX = BLOCKS.registerBlock("firebox",
            FireboxBlock::new, p -> machineProperties(p, 13));

    public static final DeferredBlock<ThermoelectricPlantBlock> THERMOELECTRIC_PLANT = BLOCKS.registerBlock("thermoelectric_plant",
            ThermoelectricPlantBlock::new, p -> machineProperties(p, 7));

    // --- Crushing ---

    public static final DeferredBlock<ArcCrusherBlock> ARC_CRUSHER = BLOCKS.registerBlock("arc_crusher",
            ArcCrusherBlock::new, p -> machineProperties(p, 8));

    public static final DeferredBlock<ArcCrushingArrayCasingBlock> ARC_CRUSHING_ARRAY_CASING = BLOCKS.registerBlock("arc_crushing_array_casing",
            ArcCrushingArrayCasingBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(ArcCrushingArrayCasingBlock.LIT) ? 8 : 0));

    // --- Smelting ---

    public static final DeferredBlock<InductionFurnaceBlock> INDUCTION_FURNACE = BLOCKS.registerBlock("induction_furnace",
            InductionFurnaceBlock::new, p -> machineProperties(p, 10));

    public static final DeferredBlock<InductionFurnaceArrayCasingBlock> INDUCTION_FURNACE_ARRAY_CASING = BLOCKS.registerBlock("induction_furnace_array_casing",
            InductionFurnaceArrayCasingBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(InductionFurnaceArrayCasingBlock.LIT) ? 12 : 0));

    // --- Pressing: no glow, lit only swaps the textures ---

    public static final DeferredBlock<MetalPressBlock> METAL_PRESS = BLOCKS.registerBlock("metal_press",
            MetalPressBlock::new, p -> machineProperties(p, 0));

    public static final DeferredBlock<MetalPressingArrayCasingBlock> METAL_PRESSING_ARRAY_CASING = BLOCKS.registerBlock("metal_pressing_array_casing",
            MetalPressingArrayCasingBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

    // --- Steam ---

    public static final DeferredBlock<SteamBoilerBlock> STEAM_BOILER = BLOCKS.registerBlock("steam_boiler",
            SteamBoilerBlock::new, p -> machineProperties(p, 6));

    public static final DeferredBlock<SteamTurbineBlock> STEAM_TURBINE = BLOCKS.registerBlock("steam_turbine",
            SteamTurbineBlock::new, p -> machineProperties(p, 4));

    // Slim: light passes around it.
    public static final DeferredBlock<ElectricPumpBlock> ELECTRIC_PUMP = BLOCKS.registerBlock("electric_pump",
            ElectricPumpBlock::new, p -> machineProperties(p, 0).noOcclusion());

    public static final DeferredBlock<SteamBoilerArrayCasingBlock> STEAM_BOILER_ARRAY_CASING = BLOCKS.registerBlock("steam_boiler_array_casing",
            SteamBoilerArrayCasingBlock::new, ModBlocks::steamCasingProperties);

    public static final DeferredBlock<SteamTurbineArrayCasingBlock> STEAM_TURBINE_ARRAY_CASING = BLOCKS.registerBlock("steam_turbine_array_casing",
            SteamTurbineArrayCasingBlock::new, ModBlocks::steamCasingProperties);

    // The steam arrays' windows; also fine as decorative glass.
    public static final DeferredBlock<PressureGlassBlock> PRESSURE_GLASS = BLOCKS.registerBlock("pressure_glass",
            PressureGlassBlock::new,
            p -> p.mapColor(MapColor.NONE)
                    .strength(1.5F, 12.0F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos, box) -> false));

    // --- Mineral wool ---

    public static final DeferredBlock<FiberizerBlock> FIBERIZER = BLOCKS.registerBlock("fiberizer",
            FiberizerBlock::new, p -> machineProperties(p, 10));

    // --- Creosote ---

    public static final DeferredBlock<FuelBurnerBlock> FUEL_BURNER = BLOCKS.registerBlock("fuel_burner",
            FuelBurnerBlock::new, p -> machineProperties(p, 13));

    public static final DeferredBlock<InfuserBlock> INFUSER = BLOCKS.registerBlock("infuser",
            InfuserBlock::new, p -> machineProperties(p, 4));

    // --- Treated wood: creosote-soaked, so like crimson and warped wood it doesn't burn ---

    // Oak's sounds and behaviour. Not registered with vanilla's lists, which only matter for signs.
    public static final BlockSetType TREATED_SET = new BlockSetType(Arcforge.MODID + ":treated");
    public static final WoodType TREATED_WOOD_TYPE = new WoodType(Arcforge.MODID + ":treated", TREATED_SET);

    public static final DeferredBlock<RotatedPillarBlock> TREATED_LOG = BLOCKS.registerBlock("treated_log",
            RotatedPillarBlock::new, p -> treatedWood(p).strength(2.0F));
    public static final DeferredBlock<RotatedPillarBlock> TREATED_WOOD = BLOCKS.registerBlock("treated_wood",
            RotatedPillarBlock::new, p -> treatedWood(p).strength(2.0F));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_TREATED_LOG = BLOCKS.registerBlock("stripped_treated_log",
            RotatedPillarBlock::new, p -> treatedWood(p).strength(2.0F));
    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_TREATED_WOOD = BLOCKS.registerBlock("stripped_treated_wood",
            RotatedPillarBlock::new, p -> treatedWood(p).strength(2.0F));
    public static final DeferredBlock<Block> TREATED_PLANKS = BLOCKS.registerSimpleBlock("treated_planks",
            p -> treatedWood(p).strength(2.0F, 3.0F));
    public static final DeferredBlock<StairBlock> TREATED_STAIRS = BLOCKS.registerBlock("treated_stairs",
            p -> new StairBlock(TREATED_PLANKS.get().defaultBlockState(), p), p -> treatedWood(p).strength(2.0F, 3.0F));
    public static final DeferredBlock<SlabBlock> TREATED_SLAB = BLOCKS.registerBlock("treated_slab",
            SlabBlock::new, p -> treatedWood(p).strength(2.0F, 3.0F));
    public static final DeferredBlock<FenceBlock> TREATED_FENCE = BLOCKS.registerBlock("treated_fence",
            FenceBlock::new, p -> treatedWood(p).forceSolidOn().strength(2.0F, 3.0F));
    public static final DeferredBlock<FenceGateBlock> TREATED_FENCE_GATE = BLOCKS.registerBlock("treated_fence_gate",
            p -> new FenceGateBlock(TREATED_WOOD_TYPE, p), p -> treatedWood(p).forceSolidOn().strength(2.0F, 3.0F));
    public static final DeferredBlock<DoorBlock> TREATED_DOOR = BLOCKS.registerBlock("treated_door",
            p -> new DoorBlock(TREATED_SET, p), p -> treatedWood(p).strength(3.0F).noOcclusion().pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<TrapDoorBlock> TREATED_TRAPDOOR = BLOCKS.registerBlock("treated_trapdoor",
            p -> new TrapDoorBlock(TREATED_SET, p),
            p -> treatedWood(p).strength(3.0F).noOcclusion().isValidSpawn((state, level, pos, entity) -> false));
    public static final DeferredBlock<PressurePlateBlock> TREATED_PRESSURE_PLATE = BLOCKS.registerBlock("treated_pressure_plate",
            p -> new PressurePlateBlock(TREATED_SET, p),
            p -> treatedWood(p).forceSolidOn().noCollision().strength(0.5F).pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<ButtonBlock> TREATED_BUTTON = BLOCKS.registerBlock("treated_button",
            p -> new ButtonBlock(TREATED_SET, 30, p), p -> p.noCollision().strength(0.5F).pushReaction(PushReaction.POPPED));

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

    // arcforge:<tier>_fluid_tank, arcforge:<tier>_energy_cell and arcforge:<tier>_heat_cell.
    private static final Map<ConduitTier, DeferredBlock<FluidTankBlock>> FLUID_TANKS = new EnumMap<>(ConduitTier.class);
    private static final Map<ConduitTier, DeferredBlock<EnergyCellBlock>> ENERGY_CELLS = new EnumMap<>(ConduitTier.class);
    private static final Map<ConduitTier, DeferredBlock<HeatCellBlock>> HEAT_CELLS = new EnumMap<>(ConduitTier.class);
    private static final Map<ConduitTier, DeferredBlock<PressurizedCylinderBlock>> PRESSURIZED_CYLINDERS = new EnumMap<>(ConduitTier.class);

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
        for (ConduitTier tier : ConduitTier.values()) {
            PRESSURIZED_CYLINDERS.put(tier, BLOCKS.registerBlock(tier.getSerializedName() + "_pressurized_cylinder",
                    p -> new PressurizedCylinderBlock(p, tier),
                    p -> p.mapColor(MapColor.METAL)
                            .strength(3.0F)
                            .sound(SoundType.METAL)
                            .noOcclusion()
                            .isSuffocating((state, level, pos) -> false)
                            .isViewBlocking((state, level, pos, box) -> false)
                            .isRedstoneConductor((state, level, pos) -> false)));
        }
        for (ConduitTier tier : ConduitTier.values()) {
            HEAT_CELLS.put(tier, BLOCKS.registerBlock(tier.getSerializedName() + "_heat_cell",
                    p -> new HeatCellBlock(p, tier),
                    p -> p.mapColor(MapColor.METAL)
                            .strength(3.0F)
                            .sound(SoundType.METAL)));
        }
    }

    private ModBlocks() {}

    public static String conduitName(ConduitType type, ConduitTier tier) {
        return tier.getSerializedName() + "_" + type.getSerializedName() + "_conduit";
    }

    // Like the Geothermal Plant: metal, needs a pickaxe, glows while lit.
    private static BlockBehaviour.Properties machineProperties(BlockBehaviour.Properties p, int litLight) {
        return p.mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .lightLevel(state -> state.getValue(MachineBlock.LIT) ? litLight : 0);
    }

    // Like oak (an axe breaks it), much darker, and never ignited by lava or fire.
    private static BlockBehaviour.Properties treatedWood(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.COLOR_BROWN)
                .instrument(NoteBlockInstrument.BASS)
                .sound(SoundType.WOOD);
    }

    // Every treated wood block, in the order of the Building Blocks tab.
    public static List<DeferredBlock<? extends Block>> treatedWoodSet() {
        return List.of(TREATED_LOG, TREATED_WOOD, STRIPPED_TREATED_LOG, STRIPPED_TREATED_WOOD, TREATED_PLANKS, TREATED_STAIRS, TREATED_SLAB,
                TREATED_FENCE, TREATED_FENCE_GATE, TREATED_DOOR, TREATED_TRAPDOOR, TREATED_PRESSURE_PLATE, TREATED_BUTTON);
    }

    private static BlockBehaviour.Properties furnaceBrickProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.TERRACOTTA_BROWN)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(2.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.MUD_BRICKS);
    }

    // Steam array casings: metal, need a pickaxe. Formed ones are drawn as one see-through surface.
    private static BlockBehaviour.Properties steamCasingProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL)
                .isRedstoneConductor((state, level, pos) -> false);
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

    public static DeferredBlock<HeatCellBlock> heatCell(ConduitTier tier) {
        return HEAT_CELLS.get(tier);
    }

    public static DeferredBlock<PressurizedCylinderBlock> pressurizedCylinder(ConduitTier tier) {
        return PRESSURIZED_CYLINDERS.get(tier);
    }

    // Every fluid tank, then every pressurized cylinder, energy cell and heat cell, each ordered by tier.
    public static List<DeferredBlock<? extends StorageBlock>> allStorage() {
        List<DeferredBlock<? extends StorageBlock>> all = new ArrayList<>();
        all.addAll(FLUID_TANKS.values());
        all.addAll(PRESSURIZED_CYLINDERS.values());
        all.addAll(ENERGY_CELLS.values());
        all.addAll(HEAT_CELLS.values());
        return all;
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
