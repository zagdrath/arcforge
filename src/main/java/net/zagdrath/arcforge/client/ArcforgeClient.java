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
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
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
import net.zagdrath.arcforge.client.renderer.item.CellChargeProperty;
import net.zagdrath.arcforge.client.renderer.item.FluidTankContentsRenderer;
import net.zagdrath.arcforge.client.screen.machine.CombustionGeneratorScreen;
import net.zagdrath.arcforge.client.screen.machine.FireboxScreen;
import net.zagdrath.arcforge.client.screen.machine.GeothermalPlantScreen;
import net.zagdrath.arcforge.client.screen.machine.ThermoelectricPlantScreen;
import net.zagdrath.arcforge.client.screen.multiblock.ArcforgeFurnaceScreen;
import net.zagdrath.arcforge.client.screen.multiblock.CarbonizerScreen;
import net.zagdrath.arcforge.client.screen.storage.EnergyCellScreen;
import net.zagdrath.arcforge.client.screen.storage.FluidTankScreen;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModMenuTypes;

// Client-only entrypoint; never loaded on dedicated servers.
@Mod(value = Arcforge.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Arcforge.MODID, value = Dist.CLIENT)
public class ArcforgeClient {
    public ArcforgeClient(ModContainer container) {
        // Mods screen > Arcforge > Config
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.GEOTHERMAL_PLANT.get(), GeothermalPlantScreen::new);
        event.register(ModMenuTypes.COMBUSTION_GENERATOR.get(), CombustionGeneratorScreen::new);
        event.register(ModMenuTypes.FIREBOX.get(), FireboxScreen::new);
        event.register(ModMenuTypes.THERMOELECTRIC_PLANT.get(), ThermoelectricPlantScreen::new);
        event.register(ModMenuTypes.FLUID_TANK.get(), FluidTankScreen::new);
        event.register(ModMenuTypes.ENERGY_CELL.get(), EnergyCellScreen::new);
        event.register(ModMenuTypes.CARBONIZER.get(), CarbonizerScreen::new);
        event.register(ModMenuTypes.ARCFORGE_FURNACE.get(), ArcforgeFurnaceScreen::new);
    }

    // Creosote's textures are already coloured, so it renders untinted.
    @SubscribeEvent
    static void registerFluidModels(RegisterFluidModelsEvent event) {
        event.register(new FluidModel.Unbaked(
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/creosote_still")),
                new Material(Identifier.fromNamespaceAndPath(Arcforge.MODID, "block/fluid/creosote_flow")),
                null,
                null), ModFluids.CREOSOTE, ModFluids.FLOWING_CREOSOTE);
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

    // Glass conduits (item and fluid) and fluid tanks draw their contents; everything else is pure block models.
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntityTypes.TRANSPARENT_CONDUIT.get(), ConduitRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.FLUID_TANK.get(), FluidTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.ARCFORGE_FURNACE.get(), ArcforgeFurnaceRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntityTypes.CARBONIZER.get(), CarbonizerDoorRenderer::new);
    }
}
