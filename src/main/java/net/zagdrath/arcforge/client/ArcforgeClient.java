/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import java.util.List;

import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.block.Block;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.zagdrath.arcforge.client.renderer.blockentity.ChunkLoaderRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.PlantingBedRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.QuantumTunnelRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.ReservoirRenderer;
import net.zagdrath.arcforge.client.screen.logistics.ChunkLoaderScreen;
import net.zagdrath.arcforge.client.screen.logistics.QuantumTunnelScreen;
import net.zagdrath.arcforge.client.screen.multiblock.GreenhouseScreen;
import net.zagdrath.arcforge.registry.ModParticleTypes;
import net.zagdrath.arcforge.client.sound.GasTurbineArraySound;
import net.zagdrath.arcforge.client.screen.multiblock.GasTurbineArrayScreen;
import net.zagdrath.arcforge.client.renderer.blockentity.GasTurbineArrayRenderer;
import net.zagdrath.arcforge.client.particle.HeatHazeParticle;
import net.zagdrath.arcforge.block.conduit.ActiveConduitBlock;
import net.zagdrath.arcforge.blockentity.multiblock.GasTurbineArrayBlockEntity;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.multiblock.SteamTurbineArrayBlockEntity;
import net.zagdrath.arcforge.client.model.ConduitCoverModel;
import net.zagdrath.arcforge.client.model.ConduitFilterModel;
import net.zagdrath.arcforge.client.model.ConduitDyeModel;
import net.zagdrath.arcforge.client.model.PortNozzleModel;
import net.zagdrath.arcforge.client.model.PortedModel;
import net.zagdrath.arcforge.client.renderer.JetpackLayer;
import net.zagdrath.arcforge.client.renderer.blockentity.ArcforgeFurnaceRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.CarbonizerDoorRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.ConduitRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.DistillationArrayRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.FluidTankRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.ThrottleLeverRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.SolarThermalArrayRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.SteamBoilerArrayRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.SteamTurbineArrayRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.ArcQuarryRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.VaultRenderer;
import net.zagdrath.arcforge.client.model.ConnectedModel;
import net.zagdrath.arcforge.client.gui.StructureRenderer;
import net.zagdrath.arcforge.client.handbook.EngineersHandbookScreen;
import net.zagdrath.arcforge.client.screen.machine.FermenterScreen;
import net.zagdrath.arcforge.client.screen.machine.GrainDryerScreen;
import net.zagdrath.arcforge.client.screen.machine.VulcanizerScreen;
import net.zagdrath.arcforge.client.screen.machine.MillScreen;
import net.zagdrath.arcforge.client.screen.machine.OilPressScreen;
import net.zagdrath.arcforge.client.screen.machine.SeedExtractorScreen;
import net.zagdrath.arcforge.client.screen.machine.SecurityTerminalScreen;
import net.zagdrath.arcforge.client.screen.multiblock.DistillationArrayScreen;
import net.zagdrath.arcforge.client.screen.multiblock.SolarThermalArrayScreen;
import net.zagdrath.arcforge.client.screen.tool.ArcToolScreen;
import net.zagdrath.arcforge.client.sound.MachineLoopSound;
import net.zagdrath.arcforge.client.sound.TurbineArraySound;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.item.tool.EngineersHandbookItem;
import net.zagdrath.arcforge.multiblock.PortStore;
import net.zagdrath.arcforge.client.renderer.item.CellChargeProperty;
import net.zagdrath.arcforge.client.renderer.item.FilterModeProperty;
import net.zagdrath.arcforge.client.renderer.item.FluidTankContentsRenderer;
import net.zagdrath.arcforge.client.screen.machine.ArcCrusherScreen;
import net.zagdrath.arcforge.client.screen.machine.InductionFurnaceScreen;
import net.zagdrath.arcforge.client.screen.machine.MetalPressScreen;
import net.zagdrath.arcforge.client.screen.machine.ArcMelterScreen;
import net.zagdrath.arcforge.client.screen.machine.ChemicalReactorScreen;
import net.zagdrath.arcforge.client.screen.machine.ElectrolyzerScreen;
import net.zagdrath.arcforge.client.screen.machine.VacuumCollectorScreen;
import net.zagdrath.arcforge.client.screen.machine.ArcQuarryScreen;
import net.zagdrath.arcforge.client.screen.machine.ArcQuarryConfigScreen;
import net.zagdrath.arcforge.client.screen.machine.BlockPlacerScreen;
import net.zagdrath.arcforge.client.screen.machine.BlockBreakerScreen;
import net.zagdrath.arcforge.client.screen.machine.AssemblerScreen;
import net.zagdrath.arcforge.client.screen.machine.ElectricPumpScreen;
import net.zagdrath.arcforge.client.screen.machine.SteamBoilerScreen;
import net.zagdrath.arcforge.client.screen.machine.CombustionPlantScreen;
import net.zagdrath.arcforge.client.screen.multiblock.ArcCrushingArrayScreen;
import net.zagdrath.arcforge.client.screen.multiblock.InductionFurnaceArrayScreen;
import net.zagdrath.arcforge.client.screen.multiblock.MetalPressingArrayScreen;
import net.zagdrath.arcforge.client.screen.multiblock.SteamTurbineArrayScreen;
import net.zagdrath.arcforge.client.screen.multiblock.CondenserArrayScreen;
import net.zagdrath.arcforge.client.screen.multiblock.SuperheaterArrayScreen;
import net.zagdrath.arcforge.client.screen.machine.FireboxScreen;
import net.zagdrath.arcforge.client.screen.machine.FiberizerScreen;
import net.zagdrath.arcforge.client.screen.machine.FuelBurnerScreen;
import net.zagdrath.arcforge.client.screen.machine.GeothermalPlantScreen;
import net.zagdrath.arcforge.client.screen.machine.InfuserScreen;
import net.zagdrath.arcforge.client.screen.machine.ThermoelectricPlantScreen;
import net.zagdrath.arcforge.client.screen.conduit.ConduitFilterScreen;
import net.zagdrath.arcforge.client.screen.multiblock.ArcforgeFurnaceScreen;
import net.zagdrath.arcforge.client.screen.multiblock.CarbonizerScreen;
import net.zagdrath.arcforge.client.screen.storage.CrateScreen;
import net.zagdrath.arcforge.client.screen.storage.EnergyCellScreen;
import net.zagdrath.arcforge.client.screen.storage.FluidTankScreen;
import net.zagdrath.arcforge.client.screen.storage.HeatCellScreen;
import net.zagdrath.arcforge.client.screen.storage.PressurizedCylinderScreen;
import net.zagdrath.arcforge.client.screen.storage.VaultScreen;
import net.zagdrath.arcforge.network.JetpackStatePayload;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.sound.MachineSounds;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.chemistry.OreSlurry;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.steam.GasTints;
import net.zagdrath.arcforge.steam.SteamGrade;
import net.zagdrath.arcforge.client.renderer.blockentity.ClocheRenderer;
import net.zagdrath.arcforge.client.screen.machine.ClocheScreen;

// Client-only entrypoint; never loaded on dedicated servers.
@Mod(value = Arcforge.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public class ArcforgeClient {
    public ArcforgeClient(ModContainer container) {
        // Mods screen > Arcforge > Config
        container.registerExtensionPoint(IConfigScreenFactory.class, ArcforgeConfigScreen::create);
        EngineersHandbookItem.opener = EngineersHandbookScreen::open;
        SteamTurbineArrayBlockEntity.clientSoundHook = TurbineArraySound::keepPlaying;
        GasTurbineArrayBlockEntity.clientHook = GasTurbineArraySound::clientTick;
        MachineSounds.clientHook = MachineLoopSound::keepPlaying;
        JetpackStatePayload.clientHandler = JetpackClient::onState;
        net.zagdrath.arcforge.network.ArcQuarryFinishedPayload.clientHandler = ArcQuarryToast::show;
        // Re-mesh blocks whose ports changed, and forget the ports on leaving a world.
        // (The ports aren't block state, so the section is marked dirty itself: marking the block would skip it,
        // as its model hasn't changed.)
        PortStore.clientRemesh = positions -> Minecraft.getInstance().execute(() -> {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                for (BlockPos pos : positions) {
                    int x = SectionPos.blockToSectionCoord(pos.getX());
                    int y = SectionPos.blockToSectionCoord(pos.getY());
                    int z = SectionPos.blockToSectionCoord(pos.getZ());
                    level.setSectionRangeDirty(x, y, z, x, y, z);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> PortStore.clearClient());
        // Canister and cartridge fill bars take the fluid's colour from its model.
        PortableStorageItem.fluidTint = stack -> {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(stack.getFluid().defaultFluidState());
            return model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(stack) | 0xFF000000 : -1;
        };
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.ARC_TOOL.get(), ArcToolScreen::new);
        event.register(ModMenuTypes.SECURITY_TERMINAL.get(), SecurityTerminalScreen::new);
        event.register(ModMenuTypes.METER.get(), net.zagdrath.arcforge.client.screen.logistics.MeterScreen::new);
        event.register(ModMenuTypes.QUANTUM_TUNNEL.get(), QuantumTunnelScreen::new);
        event.register(ModMenuTypes.CHUNK_LOADER.get(), ChunkLoaderScreen::new);
        event.register(ModMenuTypes.GEOTHERMAL_PLANT.get(), GeothermalPlantScreen::new);
        event.register(ModMenuTypes.COMBUSTION_PLANT.get(), CombustionPlantScreen::new);
        event.register(ModMenuTypes.FIREBOX.get(), FireboxScreen::new);
        event.register(ModMenuTypes.THERMOELECTRIC_PLANT.get(), ThermoelectricPlantScreen::new);
        event.register(ModMenuTypes.ARC_CRUSHER.get(), ArcCrusherScreen::new);
        event.register(ModMenuTypes.ARC_CRUSHING_ARRAY.get(), ArcCrushingArrayScreen::new);
        event.register(ModMenuTypes.INDUCTION_FURNACE.get(), InductionFurnaceScreen::new);
        event.register(ModMenuTypes.INDUCTION_FURNACE_ARRAY.get(), InductionFurnaceArrayScreen::new);
        event.register(ModMenuTypes.METAL_PRESS.get(), MetalPressScreen::new);
        event.register(ModMenuTypes.METAL_PRESSING_ARRAY.get(), MetalPressingArrayScreen::new);
        event.register(ModMenuTypes.STEAM_BOILER_ARRAY.get(), SteamBoilerScreen::array);
        event.register(ModMenuTypes.ELECTRIC_PUMP.get(), ElectricPumpScreen::new);
        event.register(ModMenuTypes.ARC_MELTER.get(), ArcMelterScreen::new);
        event.register(ModMenuTypes.FERMENTER.get(), FermenterScreen::new);
        event.register(ModMenuTypes.MILL.get(), MillScreen::new);
        event.register(ModMenuTypes.OIL_PRESS.get(), OilPressScreen::new);
        event.register(ModMenuTypes.SEED_EXTRACTOR.get(), SeedExtractorScreen::new);
        event.register(ModMenuTypes.GRAIN_DRYER.get(), GrainDryerScreen::new);
        event.register(ModMenuTypes.VULCANIZER.get(), VulcanizerScreen::new);
        event.register(ModMenuTypes.AIR_SEPARATOR.get(), net.zagdrath.arcforge.client.screen.machine.AirSeparatorScreen::new);
        event.register(ModMenuTypes.HABER_REACTOR.get(), net.zagdrath.arcforge.client.screen.machine.HaberReactorScreen::new);
        event.register(ModMenuTypes.BIOGAS_DIGESTER.get(), net.zagdrath.arcforge.client.screen.multiblock.BiogasDigesterScreen::new);
        event.register(ModMenuTypes.THERMAL_EVAPORATOR.get(), net.zagdrath.arcforge.client.screen.multiblock.ThermalEvaporatorScreen::new);
        event.register(ModMenuTypes.FIREBOX_ARRAY.get(), net.zagdrath.arcforge.client.screen.multiblock.FireboxArrayScreen::new);
        event.register(ModMenuTypes.BATTERY_ARRAY.get(), net.zagdrath.arcforge.client.screen.multiblock.BatteryArrayScreen::new);
        event.register(ModMenuTypes.GLASS_CLOCHE.get(), ClocheScreen::new);
        event.register(ModMenuTypes.GROW_CHAMBER.get(), ClocheScreen::new);
        event.register(ModMenuTypes.HYDROPONIC_CELL.get(), ClocheScreen::new);
        event.register(ModMenuTypes.GREENHOUSE.get(), GreenhouseScreen::new);
        event.register(ModMenuTypes.CHEMICAL_REACTOR.get(), ChemicalReactorScreen::new);
        event.register(ModMenuTypes.ELECTROLYZER.get(), ElectrolyzerScreen::new);
        event.register(ModMenuTypes.SIFTER.get(), net.zagdrath.arcforge.client.screen.machine.SifterScreen::new);
        event.register(ModMenuTypes.DIAMOND_PRESS.get(), net.zagdrath.arcforge.client.screen.machine.DiamondPressScreen::new);
        event.register(ModMenuTypes.CARBON_RECLAIMER.get(), net.zagdrath.arcforge.client.screen.machine.CarbonReclaimerScreen::new);
        event.register(ModMenuTypes.GASIFIER.get(), net.zagdrath.arcforge.client.screen.machine.GasifierScreen::new);
        event.register(ModMenuTypes.FISCHER_TROPSCH_REACTOR.get(), net.zagdrath.arcforge.client.screen.machine.FischerTropschReactorScreen::new);
        event.register(ModMenuTypes.ASSEMBLER.get(), AssemblerScreen::new);
        event.register(ModMenuTypes.BLOCK_BREAKER.get(), BlockBreakerScreen::new);
        event.register(ModMenuTypes.TREE_CUTTER.get(), net.zagdrath.arcforge.client.screen.machine.TreeCutterScreen::new);
        event.register(ModMenuTypes.HYDROTHERMAL_CARBONIZER.get(), net.zagdrath.arcforge.client.screen.machine.HydrothermalCarbonizerScreen::new);
        event.register(ModMenuTypes.BLOCK_PLACER.get(), BlockPlacerScreen::new);
        event.register(ModMenuTypes.VACUUM_COLLECTOR.get(), VacuumCollectorScreen::new);
        event.register(ModMenuTypes.ARC_QUARRY.get(), ArcQuarryScreen::new);
        event.register(ModMenuTypes.ARC_QUARRY_CONFIG.get(), ArcQuarryConfigScreen::new);
        event.register(ModMenuTypes.STEAM_TURBINE_ARRAY.get(), SteamTurbineArrayScreen::new);
        event.register(ModMenuTypes.GAS_TURBINE_ARRAY.get(), GasTurbineArrayScreen::new);
        event.register(ModMenuTypes.SUPERHEATER_ARRAY.get(), SuperheaterArrayScreen::new);
        event.register(ModMenuTypes.CONDENSER_ARRAY.get(), CondenserArrayScreen::new);
        event.register(ModMenuTypes.FIBERIZER.get(), FiberizerScreen::new);
        event.register(ModMenuTypes.FUEL_BURNER.get(), FuelBurnerScreen::new);
        event.register(ModMenuTypes.INFUSER.get(), InfuserScreen::new);
        event.register(ModMenuTypes.FLUID_TANK.get(), FluidTankScreen::new);
        event.register(ModMenuTypes.PRESSURIZED_CYLINDER.get(), PressurizedCylinderScreen::new);
        event.register(ModMenuTypes.ENERGY_CELL.get(), EnergyCellScreen::new);
        event.register(ModMenuTypes.HEAT_CELL.get(), HeatCellScreen::new);
        event.register(ModMenuTypes.CARBONIZER.get(), CarbonizerScreen::new);
        event.register(ModMenuTypes.ARCFORGE_FURNACE.get(), ArcforgeFurnaceScreen::new);
        event.register(ModMenuTypes.DISTILLATION_ARRAY.get(), DistillationArrayScreen::new);
        event.register(ModMenuTypes.SOLAR_THERMAL_ARRAY.get(), SolarThermalArrayScreen::new);
        event.register(ModMenuTypes.CONDUIT_FILTER.get(), ConduitFilterScreen::new);
        event.register(ModMenuTypes.CRATE.get(), CrateScreen::new);
        event.register(ModMenuTypes.VAULT.get(), VaultScreen::new);
    }

    // Lit Pressurized and Thermodynamic Conduits glow in the colour of what they hold.
    @SubscribeEvent
    static void registerBlockTints(RegisterColorHandlersEvent.BlockTintSources event) {
        // Tint index 0 is the thermal glow, index 1 a dyed conduit's colour (its _dyed pieces; see ConduitDyeModel).
        for (ConduitTier tier : ConduitTier.values()) {
            for (ConduitType type : ConduitType.values()) {
                Block conduit = ModBlocks.conduit(type, tier).get();
                BlockTintSource glow = conduit.defaultBlockState().hasProperty(ActiveConduitBlock.ACTIVE) ? ConduitTints.INSTANCE : ConduitTints.UNLIT;
                event.register(List.of(glow, ConduitDyeModel.Tint.INSTANCE), conduit);
            }
        }
    }

    // The structure viewer caches block sprites, which a resource reload replaces.
    @SubscribeEvent
    static void addReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Arcforge.MODID, "structure_renderer"), (ResourceManagerReloadListener) resources -> StructureRenderer.clearCache());
    }

    // Creosote's and the distillation products' textures are already coloured, so they render untinted.
    @SubscribeEvent
    static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(liquidModel("creosote"), ModFluids.CREOSOTE, ModFluids.FLOWING_CREOSOTE);
        event.register(liquidModel("naphtha"), ModFluids.NAPHTHA, ModFluids.FLOWING_NAPHTHA);
        event.register(liquidModel("ethanol"), ModFluids.ETHANOL, ModFluids.FLOWING_ETHANOL);
        event.register(liquidModel("light_oil"), ModFluids.LIGHT_OIL, ModFluids.FLOWING_LIGHT_OIL);
        event.register(liquidModel("heavy_oil"), ModFluids.HEAVY_OIL, ModFluids.FLOWING_HEAVY_OIL);
        event.register(liquidModel("seed_oil"), ModFluids.SEED_OIL, ModFluids.FLOWING_SEED_OIL);
        event.register(liquidModel("nutrient_solution"), ModFluids.NUTRIENT_SOLUTION, ModFluids.FLOWING_NUTRIENT_SOLUTION);
        event.register(liquidModel("biodiesel"), ModFluids.BIODIESEL, ModFluids.FLOWING_BIODIESEL);
        event.register(liquidModel("sulfuric_acid"), ModFluids.SULFURIC_ACID, ModFluids.FLOWING_SULFURIC_ACID);
        // Seawater is vanilla water's own textures (still, flowing and the overlay seen through glass), tinted a deeper
        // green-blue than any biome's water so the two can be told apart.
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.withDefaultNamespace("block/water_still")),
                new Material(Identifier.withDefaultNamespace("block/water_flow")),
                new Material(Identifier.withDefaultNamespace("block/water_overlay")),
                FluidTintSources.constant(SEAWATER_TINT)), ModFluids.SEAWATER, ModFluids.FLOWING_SEAWATER);
        event.register(liquidModel("brine"), ModFluids.BRINE, ModFluids.FLOWING_BRINE);
        event.register(liquidModel("lithium_brine"), ModFluids.LITHIUM_BRINE, ModFluids.FLOWING_LITHIUM_BRINE);
        event.register(liquidModel("latex"), ModFluids.LATEX, ModFluids.FLOWING_LATEX);
        event.register(liquidModel("liquid_experience"), ModFluids.LIQUID_EXPERIENCE, ModFluids.FLOWING_LIQUID_EXPERIENCE);
        event.register(liquidModel("lye"), ModFluids.LYE, ModFluids.FLOWING_LYE);
        event.register(liquidModel("hydrochloric_acid"), ModFluids.HYDROCHLORIC_ACID, ModFluids.FLOWING_HYDROCHLORIC_ACID);
        // The slurries share one greyscale texture, tinted per metal.
        for (OreSlurry slurry : OreSlurry.values()) {
            ModFluids.Slurry fluids = ModFluids.slurry(slurry);
            event.register(slurryModel(slurry), fluids.source(), fluids.flowing());
        }
        // The steam grades share one greyscale texture, tinted per grade.
        event.register(steamModel(SteamGrade.STEAM), ModFluids.STEAM, ModFluids.FLOWING_STEAM);
        event.register(steamModel(SteamGrade.HIGH_PRESSURE), ModFluids.HIGH_PRESSURE_STEAM, ModFluids.FLOWING_HIGH_PRESSURE_STEAM);
        event.register(steamModel(SteamGrade.SUPERHEATED), ModFluids.SUPERHEATED_STEAM, ModFluids.FLOWING_SUPERHEATED_STEAM);
        // Exhaust Steam is steam gone grey (not a grade).
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/steam_still")),
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/steam_flow")),
                null,
                FluidTintSources.constant(EXHAUST_STEAM_TINT)), ModFluids.EXHAUST_STEAM, ModFluids.FLOWING_EXHAUST_STEAM);
        // Hydrogen and Oxygen share the steam texture too: near-white and pale blue.
        event.register(gasModel(HYDROGEN_TINT), ModFluids.HYDROGEN, ModFluids.FLOWING_HYDROGEN);
        event.register(gasModel(OXYGEN_TINT), ModFluids.OXYGEN, ModFluids.FLOWING_OXYGEN);
        event.register(gasModel(CARBON_DIOXIDE_TINT), ModFluids.CARBON_DIOXIDE, ModFluids.FLOWING_CARBON_DIOXIDE);
        event.register(gasModel(NITROGEN_TINT), ModFluids.NITROGEN, ModFluids.FLOWING_NITROGEN);
        event.register(gasModel(AMMONIA_TINT), ModFluids.AMMONIA, ModFluids.FLOWING_AMMONIA);
        event.register(gasModel(BIOGAS_TINT), ModFluids.BIOGAS, ModFluids.FLOWING_BIOGAS);
        event.register(gasModel(CHLORINE_TINT), ModFluids.CHLORINE, ModFluids.FLOWING_CHLORINE);
        event.register(gasModel(ETHYLENE_TINT), ModFluids.ETHYLENE, ModFluids.FLOWING_ETHYLENE);
        event.register(gasModel(SYNGAS_TINT), ModFluids.SYNGAS, ModFluids.FLOWING_SYNGAS);
    }

    private static FluidModel.Unbaked liquidModel(String name) {
        return new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/" + name + "_still")),
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/" + name + "_flow")),
                null,
                null);
    }

    // The gases' tints are in GasTints, shared with the gas API's colours.
    private static final int EXHAUST_STEAM_TINT = GasTints.EXHAUST_STEAM;
    // Seawater: vanilla water tinted a deep sea green-blue (vanilla's default water is 0x3F76E4).
    public static final int SEAWATER_TINT = 0xFF2A8C9E;
    public static final int HYDROGEN_TINT = GasTints.HYDROGEN;
    public static final int OXYGEN_TINT = GasTints.OXYGEN;
    public static final int CARBON_DIOXIDE_TINT = GasTints.CARBON_DIOXIDE;
    public static final int NITROGEN_TINT = GasTints.NITROGEN;
    public static final int AMMONIA_TINT = GasTints.AMMONIA;
    public static final int BIOGAS_TINT = GasTints.BIOGAS;
    public static final int CHLORINE_TINT = GasTints.CHLORINE;
    public static final int ETHYLENE_TINT = GasTints.ETHYLENE;
    public static final int SYNGAS_TINT = GasTints.SYNGAS;

    private static FluidModel.Unbaked gasModel(int tint) {
        return new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/steam_still")),
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/steam_flow")),
                null,
                FluidTintSources.constant(tint));
    }

    private static FluidModel.Unbaked steamModel(SteamGrade grade) {
        return new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/steam_still")),
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/steam_flow")),
                null,
                FluidTintSources.constant(grade.tint()));
    }

    private static FluidModel.Unbaked slurryModel(OreSlurry slurry) {
        return new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/slurry_still")),
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/slurry_flow")),
                null,
                FluidTintSources.constant(slurry.tint()));
    }

    // Fog in the colour of the liquid when the camera is inside one: dark and murky in creosote and Heavy
    // Oil, a little clearer in the lighter oils.
    @SubscribeEvent
    static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(liquidFog(0x1A1109, 3.0F), ModFluids.CREOSOTE_TYPE.get());
        event.registerFluidType(liquidFog(0xE0C080, 8.0F), ModFluids.NAPHTHA_TYPE.get());
        event.registerFluidType(liquidFog(0xE8DCA0, 10.0F), ModFluids.ETHANOL_TYPE.get());
        event.registerFluidType(liquidFog(0xC89A20, 5.0F), ModFluids.LIGHT_OIL_TYPE.get());
        event.registerFluidType(liquidFog(0x2A1A0C, 2.0F), ModFluids.HEAVY_OIL_TYPE.get());
        event.registerFluidType(liquidFog(0xC8961E, 4.0F), ModFluids.SEED_OIL_TYPE.get());
        event.registerFluidType(liquidFog(0xC2D066, 6.0F), ModFluids.SULFURIC_ACID_TYPE.get());
        event.registerFluidType(liquidFog(EXHAUST_STEAM_TINT & 0xFFFFFF, 6.0F), ModFluids.EXHAUST_STEAM_TYPE.get());
        event.registerFluidType(liquidFog(HYDROGEN_TINT & 0xFFFFFF, 6.0F), ModFluids.HYDROGEN_TYPE.get());
        event.registerFluidType(liquidFog(OXYGEN_TINT & 0xFFFFFF, 6.0F), ModFluids.OXYGEN_TYPE.get());
        event.registerFluidType(liquidFog(CARBON_DIOXIDE_TINT & 0xFFFFFF, 6.0F), ModFluids.CARBON_DIOXIDE_TYPE.get());
        event.registerFluidType(liquidFog(NITROGEN_TINT & 0xFFFFFF, 6.0F), ModFluids.NITROGEN_TYPE.get());
        event.registerFluidType(liquidFog(AMMONIA_TINT & 0xFFFFFF, 6.0F), ModFluids.AMMONIA_TYPE.get());
        event.registerFluidType(liquidFog(BIOGAS_TINT & 0xFFFFFF, 6.0F), ModFluids.BIOGAS_TYPE.get());
        event.registerFluidType(liquidFog(CHLORINE_TINT & 0xFFFFFF, 5.0F), ModFluids.CHLORINE_TYPE.get());
        event.registerFluidType(liquidFog(ETHYLENE_TINT & 0xFFFFFF, 6.0F), ModFluids.ETHYLENE_TYPE.get());
        event.registerFluidType(liquidFog(SYNGAS_TINT & 0xFFFFFF, 6.0F), ModFluids.SYNGAS_TYPE.get());
        event.registerFluidType(liquidFog(0xE8E4D6, 2.0F), ModFluids.LATEX_TYPE.get());
        event.registerFluidType(liquidFog(0xB6F07A, 6.0F), ModFluids.LIQUID_EXPERIENCE_TYPE.get());
        event.registerFluidType(liquidFog(SEAWATER_TINT & 0xFFFFFF, 12.0F), ModFluids.SEAWATER_TYPE.get());
        event.registerFluidType(liquidFog(0xC8DCE4, 10.0F), ModFluids.BRINE_TYPE.get());
        event.registerFluidType(liquidFog(0xB8DCCC, 10.0F), ModFluids.LITHIUM_BRINE_TYPE.get());
        event.registerFluidType(liquidFog(0xE4E6DA, 8.0F), ModFluids.LYE_TYPE.get());
        event.registerFluidType(liquidFog(0xE2DC9A, 7.0F), ModFluids.HYDROCHLORIC_ACID_TYPE.get());
        event.registerFluidType(liquidFog(0x7FB84A, 5.0F), ModFluids.NUTRIENT_SOLUTION_TYPE.get());
        event.registerFluidType(liquidFog(0xD8A838, 4.0F), ModFluids.BIODIESEL_TYPE.get());
        for (OreSlurry slurry : OreSlurry.values()) {
            event.registerFluidType(liquidFog(slurry.tint() & 0xFFFFFF, 3.0F), ModFluids.slurry(slurry).type().get());
        }
    }

    private static IClientFluidTypeExtensions liquidFog(int color, float distance) {
        return new IClientFluidTypeExtensions() {
            @Override
            public void modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector4f fluidFogColor) {
                fluidFogColor.set((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
            }

            @Override
            public void modifyFogRender(Camera camera, @Nullable FogEnvironment environment, float renderDistance, float partialTick, FogData fogData) {
                fogData.environmentalStart = 0.0F;
                fogData.environmentalEnd = distance;
                fogData.skyEnd = fogData.environmentalEnd;
                fogData.cloudEnd = fogData.environmentalEnd;
            }
        };
    }

    // Machine GUIs check their slots against these recipes (see MachineRecipes).
    @SubscribeEvent
    static void onRecipesReceived(RecipesReceivedEvent event) {
        MachineRecipes.setClientRecipes(event.getRecipeMap());
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MachineRecipes.setClientRecipes(RecipeMap.EMPTY);
    }

    // Fluid tank items draw the fluid they carry (see items/<tier>_fluid_tank.json).
    @SubscribeEvent
    static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(JetpackLayer.LAYER, JetpackLayer::createLayer);
    }

    // The worn Jetpack's pack, on players (both skin models) and armor stands.
    @SubscribeEvent
    @SuppressWarnings({ "unchecked", "rawtypes" })
    static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerModelType skin : event.getSkins()) {
            AvatarRenderer<?> renderer = event.getPlayerRenderer(skin);
            if (renderer != null) {
                renderer.addLayer(new JetpackLayer(renderer, event.getEntityModels()));
            }
        }
        if (event.getRenderer(EntityType.ARMOR_STAND) instanceof LivingEntityRenderer renderer) {
            renderer.addLayer(new JetpackLayer(renderer, event.getEntityModels()));
        }
    }

    @SubscribeEvent
    static void registerSpecialRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(FluidTankContentsRenderer.ID, FluidTankContentsRenderer.Unbaked.MAP_CODEC);
    }

    // Energy cell items light up to match their charge (see items/<tier>_energy_cell.json).
    @SubscribeEvent
    static void registerItemModelProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(CellChargeProperty.ID, CellChargeProperty.MAP_CODEC);
    }

    // Conduit Filters show an unset, allowlist or denylist LED (see items/conduit_filter.json).
    @SubscribeEvent
    static void registerSelectItemModelProperties(RegisterSelectItemModelPropertyEvent event) {
        event.register(FilterModeProperty.ID, FilterModeProperty.TYPE);
    }

    // Connected textures for the steam arrays and Pressure Glass (see ConnectedModel).
    @SubscribeEvent
    static void registerModelLoaders(ModelEvent.RegisterLoaders event) {
        event.register(ConnectedModel.ID, ConnectedModel.Loader.INSTANCE);
    }

    // Connected textures, the port plates and nozzles of multiblocks (see PortedModel, PortNozzleModel), and dyed
    // conduit pieces (ConduitDyeModel).
    @SubscribeEvent
    static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(ConnectedModel.ID, ConnectedModel.BlockStateUnbaked.MAP_CODEC);
        event.registerModel(PortedModel.ID, PortedModel.Unbaked.MAP_CODEC);
        event.registerModel(PortNozzleModel.ID, PortNozzleModel.Unbaked.MAP_CODEC);
        event.registerModel(ConduitDyeModel.ID, ConduitDyeModel.Unbaked.MAP_CODEC);
    }

    // The Gas Turbine Array's rotor pieces and the Solar Thermal Array's trough, receiver and control panel,
    // drawn by their renderers; the Conduit Filter sleeves, added to the conduit models.
    @SubscribeEvent
    static void registerStandaloneModels(ModelEvent.RegisterStandalone event) {
        event.register(ThrottleLeverRenderer.ARM, SimpleUnbakedStandaloneModel.quadCollection(ThrottleLeverRenderer.ARM_MODEL));
        event.register(ArcQuarryRenderer.EMITTER, SimpleUnbakedStandaloneModel.quadCollection(ArcQuarryRenderer.EMITTER_MODEL));
        event.register(SolarThermalArrayRenderer.MIRROR, SimpleUnbakedStandaloneModel.quadCollection(SolarThermalArrayRenderer.MIRROR_MODEL));
        event.register(SolarThermalArrayRenderer.RECEIVER, SimpleUnbakedStandaloneModel.quadCollection(SolarThermalArrayRenderer.RECEIVER_MODEL));
        event.register(SolarThermalArrayRenderer.PANEL, SimpleUnbakedStandaloneModel.quadCollection(SolarThermalArrayRenderer.PANEL_MODEL));
        event.register(SolarThermalArrayRenderer.PANEL_ON, SimpleUnbakedStandaloneModel.quadCollection(SolarThermalArrayRenderer.PANEL_ON_MODEL));
        for (int i = 0; i < GasTurbineArrayRenderer.KEYS.size(); i++) {
            event.register(GasTurbineArrayRenderer.KEYS.get(i),
                    SimpleUnbakedStandaloneModel.quadCollection(GasTurbineArrayRenderer.model(GasTurbineArrayRenderer.MODELS.get(i))));
        }
        ConduitFilterModel.registerStandalone(event);
        ConduitCoverModel.registerStandalone(event);
    }

    // The Gas Turbine Array's exhaust haze.
    @SubscribeEvent
    static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticleTypes.HEAT_HAZE.get(), HeatHazeParticle.Provider::new);
    }

    // Conduit Filter sleeves on filtered conduit arms (see ConduitFilterModel).
    @SubscribeEvent
    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        ConduitFilterModel.wrap(event);
        ConduitCoverModel.wrap(event);
    }

    // A dyed conduit item's colour.
    @SubscribeEvent
    static void registerItemTints(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(ConduitDyeModel.ItemTint.ID, ConduitDyeModel.ItemTint.MAP_CODEC);
    }

    // Glass conduits (item and fluid) and fluid tanks draw their contents, and Vaults their front display;
    // everything else is pure block models.
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntityTypes.TRANSPARENT_CONDUIT.get(), ConduitRenderer::new);
        // Pressurized conduits keep the plain conduit block entity (they have a lit state) but show their gas.
        event.registerBlockEntityRenderer(ModBlockEntityTypes.CONDUIT.get(), ConduitRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.FLUID_TANK.get(), FluidTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.RESERVOIR.get(), ReservoirRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.QUANTUM_TUNNEL.get(), QuantumTunnelRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.CHUNK_LOADER.get(), ChunkLoaderRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.THROTTLE_LEVER.get(), ThrottleLeverRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.VAULT.get(), VaultRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.ARCFORGE_FURNACE.get(), ArcforgeFurnaceRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.CARBONIZER.get(), CarbonizerDoorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.STEAM_BOILER_ARRAY.get(), SteamBoilerArrayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.STEAM_TURBINE_ARRAY.get(), SteamTurbineArrayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.GAS_TURBINE_ARRAY.get(), GasTurbineArrayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.METER.get(), net.zagdrath.arcforge.client.renderer.blockentity.MeterRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.DISTILLATION_ARRAY.get(), DistillationArrayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.SOLAR_THERMAL_ARRAY.get(), SolarThermalArrayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.ARC_QUARRY.get(), ArcQuarryRenderer::new);
        // The Firebox Array draws its firebrick lining and the fire on its floor.
        event.registerBlockEntityRenderer(ModBlockEntityTypes.FIREBOX_ARRAY.get(),
                net.zagdrath.arcforge.client.renderer.blockentity.FireboxArrayRenderer::new);
        // The Thermal Evaporator Array draws the fluid column in its core and its salt bed.
        event.registerBlockEntityRenderer(ModBlockEntityTypes.THERMAL_EVAPORATOR.get(),
                net.zagdrath.arcforge.client.renderer.blockentity.ThermalEvaporatorRenderer::new);
        // The automated farms draw the soil and the plant growing inside.
        event.registerBlockEntityRenderer(ModBlockEntityTypes.GLASS_CLOCHE.get(), ClocheRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.GROW_CHAMBER.get(), ClocheRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.HYDROPONIC_CELL.get(), ClocheRenderer::new);
        // The Greenhouse Array's beds draw their soil and crop.
        event.registerBlockEntityRenderer(ModBlockEntityTypes.PLANTING_BED.get(), PlantingBedRenderer::new);
    }
}
