/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterBlockStateModels;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.renderer.blockentity.ArcforgeFurnaceRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.CarbonizerDoorRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.ConduitRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.FluidTankRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.SteamBoilerArrayRenderer;
import net.zagdrath.arcforge.client.renderer.blockentity.SteamTurbineArrayRenderer;
import net.zagdrath.arcforge.client.model.ConnectedModel;
import net.zagdrath.arcforge.client.gui.StructureRenderer;
import net.zagdrath.arcforge.client.handbook.EngineersHandbookScreen;
import net.zagdrath.arcforge.item.tool.EngineersHandbookItem;
import net.zagdrath.arcforge.client.renderer.item.CellChargeProperty;
import net.zagdrath.arcforge.client.renderer.item.FluidTankContentsRenderer;
import net.zagdrath.arcforge.client.screen.machine.ArcCrusherScreen;
import net.zagdrath.arcforge.client.screen.machine.InductionFurnaceScreen;
import net.zagdrath.arcforge.client.screen.machine.MetalPressScreen;
import net.zagdrath.arcforge.client.screen.machine.ElectricPumpScreen;
import net.zagdrath.arcforge.client.screen.machine.SteamBoilerScreen;
import net.zagdrath.arcforge.client.screen.machine.SteamTurbineScreen;
import net.zagdrath.arcforge.client.screen.machine.CombustionPlantScreen;
import net.zagdrath.arcforge.client.screen.multiblock.ArcCrushingArrayScreen;
import net.zagdrath.arcforge.client.screen.multiblock.InductionFurnaceArrayScreen;
import net.zagdrath.arcforge.client.screen.multiblock.MetalPressingArrayScreen;
import net.zagdrath.arcforge.client.screen.multiblock.SteamTurbineArrayScreen;
import net.zagdrath.arcforge.client.screen.machine.FireboxScreen;
import net.zagdrath.arcforge.client.screen.machine.FiberizerScreen;
import net.zagdrath.arcforge.client.screen.machine.FuelBurnerScreen;
import net.zagdrath.arcforge.client.screen.machine.GeothermalPlantScreen;
import net.zagdrath.arcforge.client.screen.machine.InfuserScreen;
import net.zagdrath.arcforge.client.screen.machine.ThermoelectricPlantScreen;
import net.zagdrath.arcforge.client.screen.multiblock.ArcforgeFurnaceScreen;
import net.zagdrath.arcforge.client.screen.multiblock.CarbonizerScreen;
import net.zagdrath.arcforge.client.screen.storage.EnergyCellScreen;
import net.zagdrath.arcforge.client.screen.storage.FluidTankScreen;
import net.zagdrath.arcforge.client.screen.storage.HeatCellScreen;
import net.zagdrath.arcforge.client.screen.storage.PressurizedCylinderScreen;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.steam.SteamGrade;

// Client-only entrypoint; never loaded on dedicated servers.
@Mod(value = Arcforge.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public class ArcforgeClient {
    public ArcforgeClient(ModContainer container) {
        // Mods screen > Arcforge > Config
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        EngineersHandbookItem.opener = EngineersHandbookScreen::open;
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
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
        event.register(ModMenuTypes.STEAM_BOILER.get(), SteamBoilerScreen::new);
        event.register(ModMenuTypes.STEAM_BOILER_ARRAY.get(), SteamBoilerScreen::array);
        event.register(ModMenuTypes.STEAM_TURBINE.get(), SteamTurbineScreen::new);
        event.register(ModMenuTypes.ELECTRIC_PUMP.get(), ElectricPumpScreen::new);
        event.register(ModMenuTypes.STEAM_TURBINE_ARRAY.get(), SteamTurbineArrayScreen::new);
        event.register(ModMenuTypes.FIBERIZER.get(), FiberizerScreen::new);
        event.register(ModMenuTypes.FUEL_BURNER.get(), FuelBurnerScreen::new);
        event.register(ModMenuTypes.INFUSER.get(), InfuserScreen::new);
        event.register(ModMenuTypes.FLUID_TANK.get(), FluidTankScreen::new);
        event.register(ModMenuTypes.PRESSURIZED_CYLINDER.get(), PressurizedCylinderScreen::new);
        event.register(ModMenuTypes.ENERGY_CELL.get(), EnergyCellScreen::new);
        event.register(ModMenuTypes.HEAT_CELL.get(), HeatCellScreen::new);
        event.register(ModMenuTypes.CARBONIZER.get(), CarbonizerScreen::new);
        event.register(ModMenuTypes.ARCFORGE_FURNACE.get(), ArcforgeFurnaceScreen::new);
    }

    // The structure viewer caches block sprites, which a resource reload replaces.
    @SubscribeEvent
    static void addReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(Arcforge.MODID, "structure_renderer"), (ResourceManagerReloadListener) resources -> StructureRenderer.clearCache());
    }

    // Creosote's textures are already coloured, so it renders untinted.
    @SubscribeEvent
    static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/creosote_still")),
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/creosote_flow")),
                null,
                null), ModFluids.CREOSOTE, ModFluids.FLOWING_CREOSOTE);
        // The steam grades share one greyscale texture, tinted per grade.
        event.register(steamModel(SteamGrade.STEAM), ModFluids.STEAM, ModFluids.FLOWING_STEAM);
        event.register(steamModel(SteamGrade.HIGH_PRESSURE), ModFluids.HIGH_PRESSURE_STEAM, ModFluids.FLOWING_HIGH_PRESSURE_STEAM);
        event.register(steamModel(SteamGrade.SUPERHEATED), ModFluids.SUPERHEATED_STEAM, ModFluids.FLOWING_SUPERHEATED_STEAM);
    }

    private static FluidModel.Unbaked steamModel(SteamGrade grade) {
        return new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/steam_still")),
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/steam_flow")),
                null,
                FluidTintSources.constant(grade.tint()));
    }

    // Dark, murky fog when the camera is inside creosote.
    @SubscribeEvent
    static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public void modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector4f fluidFogColor) {
                fluidFogColor.set(0x1A / 255.0F, 0x11 / 255.0F, 0x09 / 255.0F, 1.0F);
            }

            @Override
            public void modifyFogRender(Camera camera, @Nullable FogEnvironment environment, float renderDistance, float partialTick, FogData fogData) {
                fogData.environmentalStart = 0.0F;
                fogData.environmentalEnd = 3.0F;
                fogData.skyEnd = fogData.environmentalEnd;
                fogData.cloudEnd = fogData.environmentalEnd;
            }
        }, ModFluids.CREOSOTE_TYPE.get());
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
    static void registerSpecialRenderers(RegisterSpecialModelRendererEvent event) {
        event.register(FluidTankContentsRenderer.ID, FluidTankContentsRenderer.Unbaked.MAP_CODEC);
    }

    // Energy cell items light up to match their charge (see items/<tier>_energy_cell.json).
    @SubscribeEvent
    static void registerItemModelProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(CellChargeProperty.ID, CellChargeProperty.MAP_CODEC);
    }

    // Connected textures for the steam arrays and Pressure Glass (see ConnectedModel).
    @SubscribeEvent
    static void registerModelLoaders(ModelEvent.RegisterLoaders event) {
        event.register(ConnectedModel.ID, ConnectedModel.Loader.INSTANCE);
    }

    @SubscribeEvent
    static void registerBlockStateModels(RegisterBlockStateModels event) {
        event.registerModel(ConnectedModel.ID, ConnectedModel.BlockStateUnbaked.MAP_CODEC);
    }

    // The Steam Turbine Array's rotor pieces, drawn by its renderer.
    @SubscribeEvent
    static void registerStandaloneModels(ModelEvent.RegisterStandalone event) {
        event.register(SteamTurbineArrayRenderer.ROTOR_SHAFT, SimpleUnbakedStandaloneModel.quadCollection(SteamTurbineArrayRenderer.ROTOR_SHAFT_MODEL));
        event.register(SteamTurbineArrayRenderer.ROTOR_BLADES, SimpleUnbakedStandaloneModel.quadCollection(SteamTurbineArrayRenderer.ROTOR_BLADES_MODEL));
    }

    // Glass conduits (item and fluid) and fluid tanks draw their contents; everything else is pure block models.
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntityTypes.TRANSPARENT_CONDUIT.get(), ConduitRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.FLUID_TANK.get(), FluidTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.ARCFORGE_FURNACE.get(), ArcforgeFurnaceRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.CARBONIZER.get(), CarbonizerDoorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.STEAM_BOILER_ARRAY.get(), SteamBoilerArrayRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.STEAM_TURBINE_ARRAY.get(), SteamTurbineArrayRenderer::new);
    }
}
