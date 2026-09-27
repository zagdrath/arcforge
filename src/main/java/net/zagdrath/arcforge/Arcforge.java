/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.gametest.ArcforgeGameTests;
import net.zagdrath.arcforge.gametest.TestFixtures;
import net.zagdrath.arcforge.network.ArcforgeNetwork;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModCapabilities;
import net.zagdrath.arcforge.registry.ModCreativeTabs;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModDataMaps;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.registry.ModRecipes;

// Arcforge by Zagdrath. Must match the modId in META-INF/neoforge.mods.toml.
@Mod(Arcforge.MODID)
public class Arcforge {
    public static final String MODID = "arcforge";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Arcforge(IEventBus modEventBus, ModContainer modContainer) {
        ModFluids.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModRecipes.register(modEventBus);
        ModBlockEntityTypes.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModCapabilities.register(modEventBus);
        ModDataMaps.register(modEventBus);
        ArcforgeNetwork.register(modEventBus);
        ArcforgeGameTests.register(modEventBus);
        TestFixtures.register(modEventBus);

        modContainer.registerConfig(ModConfig.Type.COMMON, ArcforgeConfig.SPEC);

        LOGGER.info("Arcforge initialized");
    }
}
