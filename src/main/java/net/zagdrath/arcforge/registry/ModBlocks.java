/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.registry;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.arcforge.block.experience.XpDrainBlock;
import net.zagdrath.arcforge.block.experience.XpShowerBlock;
import net.zagdrath.arcforge.block.farming.greenhouse.GreenhouseControllerBlock;
import net.zagdrath.arcforge.block.farming.greenhouse.GreenhouseFrameBlock;
import net.zagdrath.arcforge.block.farming.greenhouse.GrowLampBlock;
import net.zagdrath.arcforge.block.farming.greenhouse.PlantingBedBlock;
import net.zagdrath.arcforge.block.logistics.ChunkLoaderBlock;
import net.zagdrath.arcforge.block.logistics.QuantumTunnelBlock;
import net.zagdrath.arcforge.block.machine.AirSeparatorBlock;
import net.zagdrath.arcforge.block.machine.HaberReactorBlock;
import net.zagdrath.arcforge.block.multiblock.BiogasDigesterControllerBlock;
import net.zagdrath.arcforge.block.multiblock.DigesterCasingBlock;
import net.zagdrath.arcforge.block.multiblock.GasTurbineArrayCasingBlock;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.block.farming.ArcforgeCropBlock;
import net.zagdrath.arcforge.block.farming.CompostBinBlock;
import net.zagdrath.arcforge.block.farming.CopperSprinklerBlock;
import net.zagdrath.arcforge.block.farming.ResinTapBlock;
import net.zagdrath.arcforge.block.farming.FarmMachineBlock;
import net.zagdrath.arcforge.block.farming.FertilizerSpreaderBlock;
import net.zagdrath.arcforge.block.farming.MillstoneBlock;
import net.zagdrath.arcforge.block.machine.GrainDryerBlock;
import net.zagdrath.arcforge.block.machine.VulcanizerBlock;
import net.zagdrath.arcforge.block.machine.MillBlock;
import net.zagdrath.arcforge.block.machine.OilPressBlock;
import net.zagdrath.arcforge.block.machine.SeedExtractorBlock;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.block.farming.ScarecrowBlock;
import net.zagdrath.arcforge.block.farming.TrellisBlock;
import net.zagdrath.arcforge.block.farming.WildCropBlock;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.block.fluid.SulfuricAcidBlock;
import net.zagdrath.arcforge.block.multiblock.ThermalEvaporatorCasingBlock;
import net.zagdrath.arcforge.block.multiblock.ThermalEvaporatorControllerBlock;
import net.zagdrath.arcforge.block.machine.ChemicalReactorBlock;
import net.zagdrath.arcforge.block.machine.ElectrolyzerBlock;
import net.zagdrath.arcforge.block.machine.FermenterBlock;
import net.zagdrath.arcforge.block.machine.SecurityTerminalBlock;
import net.zagdrath.arcforge.block.machine.VacuumCollectorBlock;
import net.zagdrath.arcforge.block.machine.ArcQuarryBoundingBlock;
import net.zagdrath.arcforge.block.machine.ArcQuarryBlock;
import net.zagdrath.arcforge.block.machine.BlockPlacerBlock;
import net.zagdrath.arcforge.block.machine.BlockBreakerBlock;
import net.zagdrath.arcforge.block.machine.AssemblerBlock;
import net.zagdrath.arcforge.block.storage.ReservoirBlock;
import net.zagdrath.arcforge.chemistry.OreSlurry;
import net.zagdrath.arcforge.block.machine.ArcCrusherBlock;
import net.zagdrath.arcforge.block.machine.InductionFurnaceBlock;
import net.zagdrath.arcforge.block.machine.MetalPressBlock;
import net.zagdrath.arcforge.block.machine.ArcMelterBlock;
import net.zagdrath.arcforge.block.machine.ElectricPumpBlock;
import net.zagdrath.arcforge.block.machine.CombustionPlantBlock;
import net.zagdrath.arcforge.block.multiblock.ArcCrushingArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.DistillationArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.DistillationArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.InductionFurnaceArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.MetalPressingArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.PressureGlassBlock;
import net.zagdrath.arcforge.block.multiblock.SolarCollectorBlock;
import net.zagdrath.arcforge.block.multiblock.SolarThermalArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SolarThermalArrayControllerBlock;
import net.zagdrath.arcforge.block.multiblock.SteamBoilerArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SteamTurbineArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.SuperheaterArrayCasingBlock;
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
import net.zagdrath.arcforge.block.multiblock.CondenserArrayCasingBlock;
import net.zagdrath.arcforge.block.multiblock.TrayLevelCasingBlock;
import net.zagdrath.arcforge.block.storage.CrateBlock;
import net.zagdrath.arcforge.block.storage.EnergyCellBlock;
import net.zagdrath.arcforge.block.storage.HeatCellBlock;
import net.zagdrath.arcforge.block.storage.PressurizedCylinderBlock;
import net.zagdrath.arcforge.block.redstone.ThrottleLeverBlock;
import net.zagdrath.arcforge.block.logistics.ChargepadBlock;
import net.zagdrath.arcforge.block.logistics.MeterBlock;
import net.zagdrath.arcforge.machine.meter.MeterKind;
import net.zagdrath.arcforge.block.storage.FluidTankBlock;
import net.zagdrath.arcforge.block.storage.StorageBlock;
import net.zagdrath.arcforge.block.storage.VaultBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;

import org.jspecify.annotations.Nullable;
import net.zagdrath.arcforge.block.farming.ClocheBlock;
import net.zagdrath.arcforge.blockentity.farming.ClocheBlockEntity;

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

    // The Firebox Array: a hollow box of casings round a fire (see FireboxArrayStructure), like the other arrays' casings.
    public static final DeferredBlock<net.zagdrath.arcforge.block.multiblock.FireboxArrayCasingBlock> FIREBOX_ARRAY_CASING = BLOCKS.registerBlock(
            "firebox_array_casing", net.zagdrath.arcforge.block.multiblock.FireboxArrayCasingBlock::new, ModBlocks::columnProperties);
    public static final DeferredBlock<net.zagdrath.arcforge.block.multiblock.FireboxArrayControllerBlock> FIREBOX_ARRAY_CONTROLLER =
            BLOCKS.registerBlock("firebox_array_controller", net.zagdrath.arcforge.block.multiblock.FireboxArrayControllerBlock::new,
                    p -> columnProperties(p).lightLevel(state -> state.getValue(net.zagdrath.arcforge.block.multiblock.FireboxArrayControllerBlock.LIT) ? 13 : 0));

    // The Battery Array: a box of casings (see BatteryArrayStructure) round Lithium Cells and Power Regulators.
    public static final DeferredBlock<net.zagdrath.arcforge.block.multiblock.BatteryArrayCasingBlock> BATTERY_ARRAY_CASING = BLOCKS.registerBlock(
            "battery_array_casing", net.zagdrath.arcforge.block.multiblock.BatteryArrayCasingBlock::new, ModBlocks::columnProperties);
    public static final DeferredBlock<net.zagdrath.arcforge.block.multiblock.BatteryArrayControllerBlock> BATTERY_ARRAY_CONTROLLER =
            BLOCKS.registerBlock("battery_array_controller", net.zagdrath.arcforge.block.multiblock.BatteryArrayControllerBlock::new,
                    ModBlocks::columnProperties);

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

    // A desk console, not a full block.
    public static final DeferredBlock<SecurityTerminalBlock> SECURITY_TERMINAL = BLOCKS.registerBlock("security_terminal",
            SecurityTerminalBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.5F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion());

    // A lever with a signal of 0-15. Only its gauge LED glows (in the model), so it gives no light.
    public static final DeferredBlock<ThrottleLeverBlock> THROTTLE_LEVER = BLOCKS.registerBlock("throttle_lever",
            ThrottleLeverBlock::new, p -> p.mapColor(MapColor.METAL).noCollision().strength(0.5F).sound(SoundType.METAL)
                    .pushReaction(PushReaction.POPPED));

    // Meters pass one kind of flow from their left side to their right and read its rate.
    public static final DeferredBlock<MeterBlock> ENERGY_METER = meter("energy_meter", MeterKind.ENERGY);
    public static final DeferredBlock<MeterBlock> HEAT_METER = meter("heat_meter", MeterKind.HEAT);
    public static final DeferredBlock<MeterBlock> FLUID_METER = meter("fluid_meter", MeterKind.FLUID);
    public static final DeferredBlock<MeterBlock> GAS_METER = meter("gas_meter", MeterKind.GAS);

    // Charges the FE items of players standing on it.
    public static final DeferredBlock<ChargepadBlock> CHARGEPAD = BLOCKS.registerBlock("chargepad",
            ChargepadBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.5F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion());

    // The Reservoir: glass in a thin steel frame; touching Reservoirs merge into one tank. No tool needed to keep its fluid.
    public static final DeferredBlock<ReservoirBlock> RESERVOIR = BLOCKS.registerBlock("reservoir",
            ReservoirBlock::new, p -> p.mapColor(MapColor.NONE).strength(2.0F, 6.0F).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos, box) -> false));

    // Liquid Experience: the XP Drain grate laid on a tank, and the XP Shower hung from a ceiling.
    public static final DeferredBlock<XpDrainBlock> XP_DRAIN = BLOCKS.registerBlock("xp_drain",
            XpDrainBlock::new, p -> p.mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredBlock<XpShowerBlock> XP_SHOWER = BLOCKS.registerBlock("xp_shower",
            XpShowerBlock::new, p -> p.mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion()
                    .pushReaction(PushReaction.POPPED));
    public static final DeferredBlock<LiquidBlock> LIQUID_EXPERIENCE = BLOCKS.registerBlock("liquid_experience",
            p -> new LiquidBlock(ModFluids.LIQUID_EXPERIENCE.get(), p) {},
            p -> liquidProperties(p, MapColor.COLOR_LIGHT_GREEN).lightLevel(state -> 10));

    // The Quantum Tunnel: a steel frame round a glowing core; the Chunk Loader: a globe on a plinth.
    public static final DeferredBlock<QuantumTunnelBlock> QUANTUM_TUNNEL = BLOCKS.registerBlock("quantum_tunnel",
            QuantumTunnelBlock::new, p -> p.mapColor(MapColor.METAL).strength(5.0F, 1_200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion().lightLevel(state -> 6));
    public static final DeferredBlock<ChunkLoaderBlock> CHUNK_LOADER = BLOCKS.registerBlock("chunk_loader",
            ChunkLoaderBlock::new, p -> p.mapColor(MapColor.METAL).strength(5.0F, 1_200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion().lightLevel(state -> state.getValue(ChunkLoaderBlock.ACTIVE) ? 5 : 0));

    private static DeferredBlock<MeterBlock> meter(String name, MeterKind kind) {
        return BLOCKS.registerBlock(name, properties -> new MeterBlock(kind, properties),
                p -> p.mapColor(MapColor.METAL).strength(3.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL));
    }

    public static List<DeferredBlock<MeterBlock>> meters() {
        return List.of(ENERGY_METER, HEAT_METER, FLUID_METER, GAS_METER);
    }

    // Slim: light passes around it.
    public static final DeferredBlock<ElectricPumpBlock> ELECTRIC_PUMP = BLOCKS.registerBlock("electric_pump",
            ElectricPumpBlock::new, p -> machineProperties(p, 0).noOcclusion());

    // A glass vessel; its status light glows while it ferments.
    public static final DeferredBlock<FermenterBlock> FERMENTER = BLOCKS.registerBlock("fermenter",
            FermenterBlock::new, p -> machineProperties(p, 5).noOcclusion());

    // The molten crucible glows while it melts.
    public static final DeferredBlock<ArcMelterBlock> ARC_MELTER = BLOCKS.registerBlock("arc_melter",
            ArcMelterBlock::new, p -> machineProperties(p, 10));

    public static final DeferredBlock<ChemicalReactorBlock> CHEMICAL_REACTOR = BLOCKS.registerBlock("chemical_reactor",
            ChemicalReactorBlock::new, p -> machineProperties(p, 6));

    // The cell's status LED glows while it splits water.
    public static final DeferredBlock<ElectrolyzerBlock> ELECTROLYZER = BLOCKS.registerBlock("electrolyzer",
            ElectrolyzerBlock::new, p -> machineProperties(p, 4));

    // Automation: crafting, breaking, placing and collecting.
    public static final DeferredBlock<AssemblerBlock> ASSEMBLER = BLOCKS.registerBlock("assembler",
            AssemblerBlock::new, p -> machineProperties(p, 4));

    public static final DeferredBlock<BlockBreakerBlock> BLOCK_BREAKER = BLOCKS.registerBlock("block_breaker",
            BlockBreakerBlock::new, p -> machineProperties(p, 0));

    public static final DeferredBlock<BlockPlacerBlock> BLOCK_PLACER = BLOCKS.registerBlock("block_placer",
            BlockPlacerBlock::new, p -> machineProperties(p, 0));

    public static final DeferredBlock<VacuumCollectorBlock> VACUUM_COLLECTOR = BLOCKS.registerBlock("vacuum_collector",
            VacuumCollectorBlock::new, p -> machineProperties(p, 4));

    // The Arc Quarry: its main block (the centre of the 3x3x3, which draws it all) and the invisible parts around it.
    // Pistons can't move either.
    public static final DeferredBlock<ArcQuarryBlock> ARC_QUARRY = BLOCKS.registerBlock("arc_quarry",
            ArcQuarryBlock::new, p -> machineProperties(p, 6).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredBlock<ArcQuarryBoundingBlock> ARC_QUARRY_BOUNDING = BLOCKS.registerBlock("arc_quarry_bounding",
            ArcQuarryBoundingBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.5F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)
                    .noOcclusion().noLootTable().pushReaction(PushReaction.IMMOVEABLE)
                    // Its shape depends on where its main block is, so it can't be cached per state.
                    .dynamicShape());

    public static final DeferredBlock<SteamBoilerArrayCasingBlock> STEAM_BOILER_ARRAY_CASING = BLOCKS.registerBlock("steam_boiler_array_casing",
            SteamBoilerArrayCasingBlock::new, ModBlocks::steamCasingProperties);

    public static final DeferredBlock<SteamTurbineArrayCasingBlock> STEAM_TURBINE_ARRAY_CASING = BLOCKS.registerBlock("steam_turbine_array_casing",
            SteamTurbineArrayCasingBlock::new, ModBlocks::steamCasingProperties);

    // Burns fuel straight to FE (Hardened tier).
    public static final DeferredBlock<GasTurbineArrayCasingBlock> GAS_TURBINE_ARRAY_CASING = BLOCKS.registerBlock("gas_turbine_array_casing",
            GasTurbineArrayCasingBlock::new, ModBlocks::steamCasingProperties);

    // Upgrades steam with heat; its coils glow while they work.
    public static final DeferredBlock<SuperheaterArrayCasingBlock> SUPERHEATER_ARRAY_CASING = BLOCKS.registerBlock("superheater_array_casing",
            SuperheaterArrayCasingBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .lightLevel(state -> state.getValue(SuperheaterArrayCasingBlock.LIT) ? 6 : 0));

    // Turns Exhaust Steam back into water; no glow, lit only swaps the textures.
    public static final DeferredBlock<CondenserArrayCasingBlock> CONDENSER_ARRAY_CASING = BLOCKS.registerBlock("condenser_array_casing",
            CondenserArrayCasingBlock::new,
            p -> p.mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));

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

    // --- Ores: silver, nickel, tungsten (its ore is wolframite), fluorite, bismuth and arcite ---
    // Each has an ore and a deepslate ore (dropping the raw item; see the loot tables), a raw block and a
    // storage block. Arcite glows (ores 5, raw block 9, block 12) and needs a diamond pickaxe.

    public static final DeferredBlock<DropExperienceBlock> SILVER_ORE = ore("silver_ore", ConstantInt.ZERO, false, 0, null);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_SILVER_ORE = ore("deepslate_silver_ore", ConstantInt.ZERO, true, 0, null);
    public static final DeferredBlock<Block> RAW_SILVER_BLOCK = BLOCKS.registerSimpleBlock("raw_silver_block", p -> storageProperties(p, SoundType.STONE, MapColor.RAW_IRON, 0));
    public static final DeferredBlock<Block> SILVER_BLOCK = BLOCKS.registerSimpleBlock("silver_block", p -> storageProperties(p, SoundType.METAL, MapColor.METAL, 0));

    public static final DeferredBlock<DropExperienceBlock> NICKEL_ORE = ore("nickel_ore", ConstantInt.ZERO, false, 0, null);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_NICKEL_ORE = ore("deepslate_nickel_ore", ConstantInt.ZERO, true, 0, null);
    public static final DeferredBlock<Block> RAW_NICKEL_BLOCK = BLOCKS.registerSimpleBlock("raw_nickel_block", p -> storageProperties(p, SoundType.STONE, MapColor.RAW_IRON, 0));
    public static final DeferredBlock<Block> NICKEL_BLOCK = BLOCKS.registerSimpleBlock("nickel_block", p -> storageProperties(p, SoundType.METAL, MapColor.METAL, 0));

    public static final DeferredBlock<DropExperienceBlock> WOLFRAMITE_ORE = ore("wolframite_ore", ConstantInt.ZERO, false, 0, null);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_WOLFRAMITE_ORE = ore("deepslate_wolframite_ore", ConstantInt.ZERO, true, 0, null);
    public static final DeferredBlock<Block> RAW_WOLFRAMITE_BLOCK = BLOCKS.registerSimpleBlock("raw_wolframite_block", p -> storageProperties(p, SoundType.STONE, MapColor.RAW_IRON, 0));
    public static final DeferredBlock<Block> TUNGSTEN_BLOCK = BLOCKS.registerSimpleBlock("tungsten_block", p -> storageProperties(p, SoundType.METAL, MapColor.METAL, 0));

    public static final DeferredBlock<DropExperienceBlock> FLUORITE_ORE = ore("fluorite_ore", UniformInt.of(2, 5), false, 0, null);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_FLUORITE_ORE = ore("deepslate_fluorite_ore", UniformInt.of(2, 5), true, 0, null);
    public static final DeferredBlock<Block> RAW_FLUORITE_BLOCK = BLOCKS.registerSimpleBlock("raw_fluorite_block", p -> storageProperties(p, SoundType.STONE, MapColor.RAW_IRON, 0));
    public static final DeferredBlock<Block> FLUORITE_BLOCK = BLOCKS.registerSimpleBlock("fluorite_block", p -> storageProperties(p, SoundType.AMETHYST, MapColor.COLOR_PURPLE, 0));

    public static final DeferredBlock<DropExperienceBlock> BISMUTH_ORE = ore("bismuth_ore", UniformInt.of(2, 5), false, 0, null);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_BISMUTH_ORE = ore("deepslate_bismuth_ore", UniformInt.of(2, 5), true, 0, null);
    public static final DeferredBlock<Block> RAW_BISMUTH_BLOCK = BLOCKS.registerSimpleBlock("raw_bismuth_block", p -> storageProperties(p, SoundType.STONE, MapColor.RAW_IRON, 0));
    public static final DeferredBlock<Block> BISMUTH_BLOCK = BLOCKS.registerSimpleBlock("bismuth_block", p -> storageProperties(p, SoundType.METAL, MapColor.METAL, 0));

    public static final DeferredBlock<DropExperienceBlock> ARCITE_ORE = ore("arcite_ore", UniformInt.of(3, 7), false, 5, MapColor.DIAMOND);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_ARCITE_ORE = ore("deepslate_arcite_ore", UniformInt.of(3, 7), true, 5, MapColor.DIAMOND);
    public static final DeferredBlock<Block> RAW_ARCITE_BLOCK = BLOCKS.registerSimpleBlock("raw_arcite_block", p -> storageProperties(p, SoundType.STONE, MapColor.DIAMOND, 9));
    public static final DeferredBlock<Block> ARCITE_BLOCK = BLOCKS.registerSimpleBlock("arcite_block", p -> storageProperties(p, SoundType.AMETHYST, MapColor.DIAMOND, 12));

    // Sulfur, through the Nether: it drops Sulfur Dust (see its loot table) and needs a stone pickaxe.
    // Halite: rock salt in large flat beds, in stone and deepslate (see ModWorldgen.ConfigBed). Softer than the metal ores.
    public static final DeferredBlock<DropExperienceBlock> HALITE_ORE = ore("halite_ore", UniformInt.of(0, 1), false, 0, MapColor.TERRACOTTA_WHITE);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_HALITE_ORE = ore("deepslate_halite_ore", UniformInt.of(0, 1), true, 0, null);
    public static final DeferredBlock<Block> RAW_ROCK_SALT_BLOCK = BLOCKS.registerSimpleBlock("raw_rock_salt_block",
            p -> storageProperties(p, SoundType.CALCITE, MapColor.TERRACOTTA_WHITE, 0));
    // Spodumene: the lithium ore, in stone and deepslate, with richer pegmatite veins under mountains and badlands. Needs a
    // stone pickaxe, like lapis.
    public static final DeferredBlock<DropExperienceBlock> SPODUMENE_ORE = ore("spodumene_ore", UniformInt.of(0, 2), false, 0, null);
    public static final DeferredBlock<DropExperienceBlock> DEEPSLATE_SPODUMENE_ORE = ore("deepslate_spodumene_ore", UniformInt.of(0, 2), true, 0, null);
    public static final DeferredBlock<Block> RAW_SPODUMENE_BLOCK = BLOCKS.registerSimpleBlock("raw_spodumene_block",
            p -> storageProperties(p, SoundType.STONE, MapColor.COLOR_LIGHT_GREEN, 0));
    // Graphite, baked from Coal Coke or Carbon Dust in the Arcforge Furnace, for Graphite Anodes.
    public static final DeferredBlock<Block> GRAPHITE_BLOCK = BLOCKS.registerSimpleBlock("graphite_block",
            p -> storageProperties(p, SoundType.STONE, MapColor.COLOR_BLACK, 0));
    public static final DeferredBlock<DropExperienceBlock> NETHER_SULFUR_ORE = BLOCKS.registerBlock("nether_sulfur_ore",
            p -> new DropExperienceBlock(UniformInt.of(1, 3), p), p -> p
                    .mapColor(MapColor.NETHER)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .strength(3.0F, 3.0F)
                    .sound(SoundType.NETHER_ORE)
                    .requiresCorrectToolForDrops());

    // Ores: stone 3.0 or deepslate 4.5 hardness, the right tool to drop anything; xp as given.
    private static DeferredBlock<DropExperienceBlock> ore(String name, IntProvider xp, boolean deepslate, int light, @Nullable MapColor color) {
        return BLOCKS.registerBlock(name, p -> new DropExperienceBlock(xp, p), p -> p
                .mapColor(color != null ? color : deepslate ? MapColor.DEEPSLATE : MapColor.STONE)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(deepslate ? 4.5F : 3.0F, 3.0F)
                .sound(deepslate ? SoundType.DEEPSLATE : SoundType.STONE)
                .requiresCorrectToolForDrops()
                .lightLevel(state -> light));
    }

    // Raw and storage blocks: 5.0 hardness, 6.0 blast resistance.
    private static BlockBehaviour.Properties storageProperties(BlockBehaviour.Properties p, SoundType sound, MapColor color, int light) {
        return p.mapColor(color)
                .strength(5.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(sound)
                .lightLevel(state -> light);
    }

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

    // The Distillation Array's products. Naphtha burns where it lies, like a flammable block.
    // Ethanol burns where it lies, like Naphtha.
    public static final DeferredBlock<LiquidBlock> ETHANOL = BLOCKS.registerBlock("ethanol",
            p -> new LiquidBlock(ModFluids.ETHANOL.get(), p) {
                @Override
                public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return 300;
                }

                @Override
                public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return 60;
                }
            },
            p -> liquidProperties(p, MapColor.SAND));

    public static final DeferredBlock<LiquidBlock> NAPHTHA = BLOCKS.registerBlock("naphtha",
            p -> new LiquidBlock(ModFluids.NAPHTHA.get(), p) {
                @Override
                public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return 300;
                }

                @Override
                public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return 60;
                }
            },
            p -> liquidProperties(p, MapColor.SAND));

    public static final DeferredBlock<LiquidBlock> LIGHT_OIL = BLOCKS.registerBlock("light_oil",
            p -> new LiquidBlock(ModFluids.LIGHT_OIL.get(), p) {},
            p -> liquidProperties(p, MapColor.GOLD));

    public static final DeferredBlock<LiquidBlock> HEAVY_OIL = BLOCKS.registerBlock("heavy_oil",
            p -> new LiquidBlock(ModFluids.HEAVY_OIL.get(), p) {},
            p -> liquidProperties(p, MapColor.COLOR_BROWN));

    // The Oil Press's Seed Oil.
    public static final DeferredBlock<LiquidBlock> SEED_OIL = BLOCKS.registerBlock("seed_oil",
            p -> new LiquidBlock(ModFluids.SEED_OIL.get(), p) {},
            p -> liquidProperties(p, MapColor.GOLD));

    // Farm chemistry: Nutrient Solution and Biodiesel. Biodiesel burns where it lies, like Ethanol.
    public static final DeferredBlock<LiquidBlock> NUTRIENT_SOLUTION = BLOCKS.registerBlock("nutrient_solution",
            p -> new LiquidBlock(ModFluids.NUTRIENT_SOLUTION.get(), p) {},
            p -> liquidProperties(p, MapColor.COLOR_LIGHT_GREEN));
    public static final DeferredBlock<LiquidBlock> BIODIESEL = BLOCKS.registerBlock("biodiesel",
            p -> new LiquidBlock(ModFluids.BIODIESEL.get(), p) {
                @Override
                public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return 200;
                }

                @Override
                public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
                    return 30;
                }
            },
            p -> liquidProperties(p, MapColor.GOLD));

    // The Chemical Reactor's fluids. Sulfuric Acid hurts whatever wades in; the slurries are harmless.
    public static final DeferredBlock<LiquidBlock> SULFURIC_ACID = BLOCKS.registerBlock("sulfuric_acid",
            p -> new SulfuricAcidBlock(ModFluids.SULFURIC_ACID.get(), p),
            p -> liquidProperties(p, MapColor.COLOR_LIGHT_GREEN));

    // Salt and chlor-alkali chemistry: Brine is harmless; Lye and Hydrochloric Acid burn like Sulfuric Acid.
    public static final DeferredBlock<LiquidBlock> BRINE = BLOCKS.registerBlock("brine",
            p -> new LiquidBlock(ModFluids.BRINE.get(), p) {},
            p -> liquidProperties(p, MapColor.COLOR_LIGHT_BLUE));
    public static final DeferredBlock<LiquidBlock> LITHIUM_BRINE = BLOCKS.registerBlock("lithium_brine",
            p -> new LiquidBlock(ModFluids.LITHIUM_BRINE.get(), p) {},
            p -> liquidProperties(p, MapColor.COLOR_LIGHT_GREEN));
    public static final DeferredBlock<LiquidBlock> LYE = BLOCKS.registerBlock("lye",
            p -> new SulfuricAcidBlock(ModFluids.LYE.get(), ModDamageTypes.LYE, p),
            p -> liquidProperties(p, MapColor.SNOW));
    public static final DeferredBlock<LiquidBlock> HYDROCHLORIC_ACID = BLOCKS.registerBlock("hydrochloric_acid",
            p -> new SulfuricAcidBlock(ModFluids.HYDROCHLORIC_ACID.get(), ModDamageTypes.HYDROCHLORIC_ACID, p),
            p -> liquidProperties(p, MapColor.COLOR_YELLOW));

    // The Thermal Evaporator Array: a 3x3x9 steel tower (see ThermalEvaporatorStructure), like the other arrays' casings.
    public static final DeferredBlock<ThermalEvaporatorCasingBlock> THERMAL_EVAPORATOR_CASING = BLOCKS.registerBlock("thermal_evaporator_casing",
            ThermalEvaporatorCasingBlock::new, ModBlocks::columnProperties);
    public static final DeferredBlock<ThermalEvaporatorControllerBlock> THERMAL_EVAPORATOR_CONTROLLER = BLOCKS.registerBlock(
            "thermal_evaporator_controller", ThermalEvaporatorControllerBlock::new,
            p -> columnProperties(p).lightLevel(state -> state.getValue(ThermalEvaporatorControllerBlock.LIT) ? 7 : 0));
    // Seawater, pumped from oceans and beaches.
    public static final DeferredBlock<LiquidBlock> SEAWATER = BLOCKS.registerBlock("seawater",
            p -> new LiquidBlock(ModFluids.SEAWATER.get(), p) {},
            p -> liquidProperties(p, MapColor.WATER));
    // Latex, from the Oil Press and the Resin Tap.
    public static final DeferredBlock<LiquidBlock> LATEX = BLOCKS.registerBlock("latex",
            p -> new LiquidBlock(ModFluids.LATEX.get(), p) {},
            p -> liquidProperties(p, MapColor.SNOW));
    // Nine Rubber, pressed into a block: soft and quiet underfoot.
    public static final DeferredBlock<Block> RUBBER_BLOCK = BLOCKS.registerSimpleBlock("rubber_block",
            p -> p.mapColor(MapColor.COLOR_BLACK).strength(0.8F, 3.0F).sound(SoundType.WOOL));
    // Nine Salt, packed: brittle like calcite, a pickaxe to drop it.
    public static final DeferredBlock<Block> SALT_BLOCK = BLOCKS.registerSimpleBlock("salt_block",
            p -> p.mapColor(MapColor.SNOW).instrument(NoteBlockInstrument.BASEDRUM).strength(1.5F, 3.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.CALCITE));

    private static final Map<OreSlurry, DeferredBlock<LiquidBlock>> SLURRY_BLOCKS = registerSlurryBlocks();

    public static DeferredBlock<LiquidBlock> slurryBlock(OreSlurry slurry) {
        return SLURRY_BLOCKS.get(slurry);
    }

    private static Map<OreSlurry, DeferredBlock<LiquidBlock>> registerSlurryBlocks() {
        Map<OreSlurry, DeferredBlock<LiquidBlock>> blocks = new EnumMap<>(OreSlurry.class);
        for (OreSlurry slurry : OreSlurry.values()) {
            blocks.put(slurry, BLOCKS.registerBlock(slurry.fluidName(),
                    p -> new LiquidBlock(ModFluids.slurry(slurry).source().get(), p) {},
                    p -> liquidProperties(p, MapColor.TERRACOTTA_BROWN)));
        }
        return blocks;
    }

    // --- Distillation ---

    public static final DeferredBlock<DistillationArrayCasingBlock> DISTILLATION_ARRAY_CASING = BLOCKS.registerBlock("distillation_array_casing",
            DistillationArrayCasingBlock::new, ModBlocks::columnProperties);

    // Glass round a tray: see-through, so it neither hides its neighbours' faces nor blocks light or view.
    public static final DeferredBlock<TrayLevelCasingBlock> TRAY_LEVEL_CASING = BLOCKS.registerBlock("tray_level_casing",
            TrayLevelCasingBlock::new, p -> columnProperties(p)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos, box) -> false));

    public static final DeferredBlock<DistillationArrayControllerBlock> DISTILLATION_ARRAY_CONTROLLER = BLOCKS.registerBlock("distillation_array_controller",
            DistillationArrayControllerBlock::new, ModBlocks::columnProperties);

    // --- Solar Thermal Array ---

    public static final DeferredBlock<SolarThermalArrayCasingBlock> SOLAR_THERMAL_ARRAY_CASING = BLOCKS.registerBlock("solar_thermal_array_casing",
            SolarThermalArrayCasingBlock::new, ModBlocks::columnProperties);

    public static final DeferredBlock<SolarThermalArrayControllerBlock> SOLAR_THERMAL_ARRAY_CONTROLLER = BLOCKS.registerBlock("solar_thermal_array_controller",
            SolarThermalArrayControllerBlock::new, ModBlocks::columnProperties);

    public static final DeferredBlock<SolarCollectorBlock> SOLAR_COLLECTOR = BLOCKS.registerBlock("solar_collector",
            SolarCollectorBlock::new, p -> columnProperties(p).sound(SoundType.GLASS));

    // Asphalt: made with pitch; walking, running and riding on it are about 30% faster.
    public static final DeferredBlock<Block> ASPHALT = BLOCKS.registerSimpleBlock("asphalt", ModBlocks::asphaltProperties);
    public static final DeferredBlock<StairBlock> ASPHALT_STAIRS = BLOCKS.registerBlock("asphalt_stairs",
            p -> new StairBlock(ASPHALT.get().defaultBlockState(), p), ModBlocks::asphaltProperties);
    public static final DeferredBlock<SlabBlock> ASPHALT_SLAB = BLOCKS.registerBlock("asphalt_slab",
            SlabBlock::new, ModBlocks::asphaltProperties);

    // --- Farming ---

    // The Compost Bin: treated wood like the rest of the building set, so an axe breaks it and fire doesn't take it.
    public static final DeferredBlock<CompostBinBlock> COMPOST_BIN = BLOCKS.registerBlock("compost_bin",
            CompostBinBlock::new, p -> treatedWood(p).strength(0.6F).noOcclusion());
    // Loam: dirt worked with compost. A hoe tills it into Loam Farmland (FarmingEvents).
    public static final DeferredBlock<Block> LOAM = BLOCKS.registerSimpleBlock("loam",
            p -> p.mapColor(MapColor.DIRT).strength(0.5F).sound(SoundType.ROOTED_DIRT));
    public static final DeferredBlock<LoamFarmlandBlock> LOAM_FARMLAND = BLOCKS.registerBlock("loam_farmland",
            p -> new LoamFarmlandBlock(LOAM.get(), false, p), ModBlocks::farmlandProperties);
    public static final DeferredBlock<LoamFarmlandBlock> IRRIGATED_LOAM_FARMLAND = BLOCKS.registerBlock("irrigated_loam_farmland",
            p -> new LoamFarmlandBlock(LOAM.get(), true, p), p -> farmlandProperties(p).sound(SoundType.COPPER));

    // Crops: Flax, Rapeseed and Sorghum grow like wheat, but two blocks tall; Hops climb a Trellis. Each has a wild clump that worldgen places.
    public static final DeferredBlock<ArcforgeCropBlock> FLAX = BLOCKS.registerBlock("flax",
            p -> new ArcforgeCropBlock(() -> ModItems.FLAX_SEEDS.get(), 2, p), ModBlocks::cropProperties);
    public static final DeferredBlock<ArcforgeCropBlock> RAPESEED = BLOCKS.registerBlock("rapeseed",
            p -> new ArcforgeCropBlock(() -> ModItems.RAPESEEDS.get(), 4, p), ModBlocks::cropProperties);
    public static final DeferredBlock<ArcforgeCropBlock> SORGHUM = BLOCKS.registerBlock("sorghum",
            p -> new ArcforgeCropBlock(() -> ModItems.SORGHUM_SEEDS.get(), 4, p), ModBlocks::cropProperties);
    // Soybeans: a low bush, one block tall at every age (tallFromAge past the last). A legume (#arcforge:legumes).
    public static final DeferredBlock<ArcforgeCropBlock> SOYBEANS = BLOCKS.registerBlock("soybeans",
            p -> new ArcforgeCropBlock(() -> ModItems.SOYBEANS.get(), 8, p), ModBlocks::cropProperties);
    // Rubber Dandelion: a low rosette whose fat roots hold latex. One block tall at every age, like Soybeans.
    public static final DeferredBlock<ArcforgeCropBlock> RUBBER_DANDELION = BLOCKS.registerBlock("rubber_dandelion",
            p -> new ArcforgeCropBlock(() -> ModItems.RUBBER_DANDELION_SEEDS.get(), 8, p), ModBlocks::cropProperties);
    // Not solid, so farmland under it stays farmland.
    public static final DeferredBlock<TrellisBlock> TRELLIS = BLOCKS.registerBlock("trellis",
            TrellisBlock::new, p -> treatedWood(p).strength(0.6F).noOcclusion().forceSolidOff().randomTicks());
    public static final DeferredBlock<WildCropBlock> WILD_FLAX = BLOCKS.registerBlock("wild_flax", p -> new WildCropBlock(true, p), ModBlocks::wildCropProperties);
    public static final DeferredBlock<WildCropBlock> WILD_RAPESEED = BLOCKS.registerBlock("wild_rapeseed", p -> new WildCropBlock(true, p), ModBlocks::wildCropProperties);
    public static final DeferredBlock<WildCropBlock> WILD_SORGHUM = BLOCKS.registerBlock("wild_sorghum", p -> new WildCropBlock(true, p), ModBlocks::wildCropProperties);
    public static final DeferredBlock<WildCropBlock> WILD_HOPS = BLOCKS.registerBlock("wild_hops", p -> new WildCropBlock(false, p), ModBlocks::wildCropProperties);
    public static final DeferredBlock<WildCropBlock> WILD_SOYBEANS = BLOCKS.registerBlock("wild_soybeans", p -> new WildCropBlock(false, p), ModBlocks::wildCropProperties);
    public static final DeferredBlock<WildCropBlock> WILD_RUBBER_DANDELION = BLOCKS.registerBlock("wild_rubber_dandelion",
            p -> new WildCropBlock(false, p), ModBlocks::wildCropProperties);

    // Rustic farming machines: unpowered, treated wood with copper and iron fittings.
    public static final DeferredBlock<FarmMachineBlock> PLANTER = BLOCKS.registerBlock("planter",
            p -> new FarmMachineBlock(FarmMachineBlock.Kind.PLANTER, p), p -> treatedWood(p).strength(1.5F).noOcclusion());
    public static final DeferredBlock<FarmMachineBlock> HARVESTER = BLOCKS.registerBlock("harvester",
            p -> new FarmMachineBlock(FarmMachineBlock.Kind.HARVESTER, p), p -> treatedWood(p).strength(1.5F).noOcclusion());
    public static final DeferredBlock<FertilizerSpreaderBlock> FERTILIZER_SPREADER = BLOCKS.registerBlock("fertilizer_spreader",
            FertilizerSpreaderBlock::new, p -> treatedWood(p).strength(1.5F).noOcclusion());
    public static final DeferredBlock<CopperSprinklerBlock> COPPER_SPRINKLER = BLOCKS.registerBlock("copper_sprinkler",
            CopperSprinklerBlock::new, p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0F, 6.0F).sound(SoundType.COPPER).noOcclusion());
    // The Resin Tap: a small spout and cup hung on the side of a log; it breaks with the log under it.
    public static final DeferredBlock<ResinTapBlock> RESIN_TAP = BLOCKS.registerBlock("resin_tap",
            ResinTapBlock::new, p -> treatedWood(p).strength(0.8F).noOcclusion().forceSolidOff().pushReaction(PushReaction.POPPED));
    // Not solid, so it can stand on farmland among the crops.
    public static final DeferredBlock<ScarecrowBlock> SCARECROW = BLOCKS.registerBlock("scarecrow",
            ScarecrowBlock::new, p -> treatedWood(p).strength(0.8F).noOcclusion().forceSolidOff().pushReaction(PushReaction.POPPED));

    // Farm processing. The Millstone is rustic (stone on a treated-wood frame, broken with a pickaxe); the Mill, Oil
    // Press and Seed Extractor are FE machines, and the Grain Dryer runs on heat.
    public static final DeferredBlock<MillstoneBlock> MILLSTONE = BLOCKS.registerBlock("millstone",
            MillstoneBlock::new, p -> p.mapColor(MapColor.STONE).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion());
    public static final DeferredBlock<MillBlock> MILL = BLOCKS.registerBlock("mill",
            MillBlock::new, p -> machineProperties(p, 0));
    public static final DeferredBlock<OilPressBlock> OIL_PRESS = BLOCKS.registerBlock("oil_press",
            OilPressBlock::new, p -> machineProperties(p, 0));
    public static final DeferredBlock<SeedExtractorBlock> SEED_EXTRACTOR = BLOCKS.registerBlock("seed_extractor",
            SeedExtractorBlock::new, p -> machineProperties(p, 0));
    // The heating element glows while it dries.
    public static final DeferredBlock<GrainDryerBlock> GRAIN_DRYER = BLOCKS.registerBlock("grain_dryer",
            GrainDryerBlock::new, p -> machineProperties(p, 7));
    // The Vulcanizer cures Raw Rubber with Sulfur in a heated press; its platens glow while it works.
    public static final DeferredBlock<VulcanizerBlock> VULCANIZER = BLOCKS.registerBlock("vulcanizer",
            VulcanizerBlock::new, p -> machineProperties(p, 7));

    // Farm chemistry. The Air Separator runs on FE; the Haber Reactor on FE and heat (its catalyst bed glows while it
    // runs). The Biogas Digester is a 3x3x3 of Biogas Digester Casings with one controller (see BiogasDigesterStructure).
    public static final DeferredBlock<AirSeparatorBlock> AIR_SEPARATOR = BLOCKS.registerBlock("air_separator",
            AirSeparatorBlock::new, p -> machineProperties(p, 0));
    public static final DeferredBlock<HaberReactorBlock> HABER_REACTOR = BLOCKS.registerBlock("haber_reactor",
            HaberReactorBlock::new, p -> machineProperties(p, 7));
    public static final DeferredBlock<DigesterCasingBlock> DIGESTER_CASING = BLOCKS.registerBlock("digester_casing",
            DigesterCasingBlock::new, ModBlocks::columnProperties);
    public static final DeferredBlock<BiogasDigesterControllerBlock> BIOGAS_DIGESTER_CONTROLLER = BLOCKS.registerBlock("biogas_digester_controller",
            BiogasDigesterControllerBlock::new, ModBlocks::columnProperties);

    // Automated farms, each growing a plant inside (see ClocheBlockEntity): the Glass Cloche is a glass bell on a
    // treated-wood planter; the Grow Chamber and Hydroponic Cell are steel frames glazed with Pressure Glass, lit by
    // their grow lamps while they grow.
    public static final DeferredBlock<ClocheBlock> GLASS_CLOCHE = BLOCKS.registerBlock("glass_cloche",
            p -> new ClocheBlock(ClocheBlockEntity.Kind.GLASS_CLOCHE, p), p -> treatedWood(p).strength(1.5F).sound(SoundType.GLASS).noOcclusion());
    public static final DeferredBlock<ClocheBlock> GROW_CHAMBER = BLOCKS.registerBlock("grow_chamber",
            p -> new ClocheBlock(ClocheBlockEntity.Kind.GROW_CHAMBER, p), p -> glazedMachine(machineProperties(p, 10)));
    public static final DeferredBlock<ClocheBlock> HYDROPONIC_CELL = BLOCKS.registerBlock("hydroponic_cell",
            p -> new ClocheBlock(ClocheBlockEntity.Kind.HYDROPONIC_CELL, p), p -> glazedMachine(machineProperties(p, 10)));

    // The Greenhouse Array (see GreenhouseStructure): a Greenhouse Frame and Pressure Glass building with one controller,
    // Planting Beds in its floor and Grow Lamps under its roof.
    public static final DeferredBlock<GreenhouseFrameBlock> GREENHOUSE_FRAME = BLOCKS.registerBlock("greenhouse_frame",
            GreenhouseFrameBlock::new, ModBlocks::columnProperties);
    public static final DeferredBlock<GreenhouseControllerBlock> GREENHOUSE_CONTROLLER = BLOCKS.registerBlock("greenhouse_controller",
            GreenhouseControllerBlock::new, p -> columnProperties(p).lightLevel(state -> state.getValue(GreenhouseControllerBlock.LIT) ? 4 : 0));
    public static final DeferredBlock<PlantingBedBlock> PLANTING_BED = BLOCKS.registerBlock("planting_bed",
            PlantingBedBlock::new, ModBlocks::columnProperties);
    public static final DeferredBlock<GrowLampBlock> GROW_LAMP = BLOCKS.registerBlock("grow_lamp",
            GrowLampBlock::new, p -> p.mapColor(MapColor.METAL).strength(1.5F, 6.0F).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(state -> state.getValue(GrowLampBlock.LIT) ? 15 : 0));

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

    // Like wheat: no collision, broken at a touch, pushed off by pistons.
    private static BlockBehaviour.Properties cropProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.PLANT)
                .noCollision()
                .randomTicks()
                .instabreak()
                .sound(SoundType.CROP)
                .pushReaction(PushReaction.POPPED);
    }

    // Like a flower: replaceable, set off-centre, no collision.
    private static BlockBehaviour.Properties wildCropProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.PLANT)
                .replaceable()
                .noCollision()
                .instabreak()
                .sound(SoundType.GRASS)
                .offsetType(BlockBehaviour.OffsetType.XZ)
                .pushReaction(PushReaction.POPPED);
    }

    // Like vanilla farmland: random ticks for moisture, a shovel breaks it, and it suffocates like a full block.
    private static BlockBehaviour.Properties farmlandProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.DIRT)
                .randomTicks()
                .strength(0.6F)
                .sound(SoundType.GRAVEL)
                .isSuffocating((state, level, pos) -> true);
    }

    // A full-block machine you can see into: light and view pass through its glass.
    private static BlockBehaviour.Properties glazedMachine(BlockBehaviour.Properties p) {
        return p.noOcclusion().isViewBlocking((state, level, pos, box) -> false).isSuffocating((state, level, pos) -> false);
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

    private static BlockBehaviour.Properties liquidProperties(BlockBehaviour.Properties p, MapColor color) {
        return p.mapColor(color)
                .replaceable()
                .noCollision()
                .strength(100.0F)
                .pushReaction(PushReaction.POPPED)
                .noLootTable()
                .liquid()
                .sound(SoundType.EMPTY);
    }

    private static BlockBehaviour.Properties columnProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL)
                .strength(3.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    // The game multiplies an entity's speed by the speed factor every tick, so 1.2 makes it about 30%
    // faster in the end (soul sand's 0.4 about 40% slower).
    private static BlockBehaviour.Properties asphaltProperties(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .strength(1.5F, 6.0F)
                .requiresCorrectToolForDrops()
                .speedFactor(1.2F);
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

    // arcforge:<tier>_lithium_cell and arcforge:<tier>_power_regulator: what fills a Battery Array.
    private static final Map<ConduitTier, DeferredBlock<net.zagdrath.arcforge.block.multiblock.LithiumCellBlock>> LITHIUM_CELLS =
            new EnumMap<>(ConduitTier.class);
    private static final Map<ConduitTier, DeferredBlock<net.zagdrath.arcforge.block.multiblock.PowerRegulatorBlock>> POWER_REGULATORS =
            new EnumMap<>(ConduitTier.class);

    static {
        for (ConduitTier tier : ConduitTier.values()) {
            LITHIUM_CELLS.put(tier, BLOCKS.registerBlock(tier.getSerializedName() + "_lithium_cell",
                    p -> new net.zagdrath.arcforge.block.multiblock.LithiumCellBlock(p, tier),
                    p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
            POWER_REGULATORS.put(tier, BLOCKS.registerBlock(tier.getSerializedName() + "_power_regulator",
                    p -> new net.zagdrath.arcforge.block.multiblock.PowerRegulatorBlock(p, tier),
                    p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
        }
    }

    public static DeferredBlock<net.zagdrath.arcforge.block.multiblock.LithiumCellBlock> lithiumCell(ConduitTier tier) {
        return LITHIUM_CELLS.get(tier);
    }

    public static DeferredBlock<net.zagdrath.arcforge.block.multiblock.PowerRegulatorBlock> powerRegulator(ConduitTier tier) {
        return POWER_REGULATORS.get(tier);
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

    // arcforge:<tier>_crate and arcforge:<tier>_vault.
    private static final Map<ConduitTier, DeferredBlock<CrateBlock>> CRATES = new EnumMap<>(ConduitTier.class);
    private static final Map<ConduitTier, DeferredBlock<VaultBlock>> VAULTS = new EnumMap<>(ConduitTier.class);

    static {
        for (ConduitTier tier : ConduitTier.values()) {
            CRATES.put(tier, BLOCKS.registerBlock(tier.getSerializedName() + "_crate",
                    p -> new CrateBlock(p, tier),
                    p -> p.mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.METAL)));
        }
        for (ConduitTier tier : ConduitTier.values()) {
            VAULTS.put(tier, BLOCKS.registerBlock(tier.getSerializedName() + "_vault",
                    p -> new VaultBlock(p, tier),
                    p -> p.mapColor(MapColor.METAL).strength(2.5F).sound(SoundType.METAL)));
        }
    }

    public static DeferredBlock<CrateBlock> crate(ConduitTier tier) {
        return CRATES.get(tier);
    }

    public static DeferredBlock<VaultBlock> vault(ConduitTier tier) {
        return VAULTS.get(tier);
    }

    // Crates, then Vaults, each ordered by tier.
    public static List<DeferredBlock<? extends StorageBlock>> allCratesAndVaults() {
        List<DeferredBlock<? extends StorageBlock>> all = new ArrayList<>();
        all.addAll(CRATES.values());
        all.addAll(VAULTS.values());
        return all;
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
        // The single-block Steam Boiler and Steam Turbine were removed in 2.0: placed ones load as loose
        // array casings.
        BLOCKS.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_boiler"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_boiler_array_casing"));
        BLOCKS.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_turbine"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "steam_turbine_array_casing"));
        // Removed with the Evaporation Pond: its blocks load as the Thermal Evaporator Array's (ponds need rebuilding as towers).
        BLOCKS.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "pond_liner"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "thermal_evaporator_casing"));
        BLOCKS.addAlias(Identifier.fromNamespaceAndPath(Arcforge.MODID, "pond_outlet"), Identifier.fromNamespaceAndPath(Arcforge.MODID, "thermal_evaporator_controller"));
        BLOCKS.register(modEventBus);
    }
}
