/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.client.renderer.blockentity.ConduitRenderer;
import net.zagdrath.arcforge.client.screen.machine.GeothermalPlantScreen;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
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
    }

    // Only glass conduits (item and liquid) need a renderer; energy and thermal are pure block models.
    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntityTypes.TRANSPARENT_CONDUIT.get(), ConduitRenderer::new);
    }
}
