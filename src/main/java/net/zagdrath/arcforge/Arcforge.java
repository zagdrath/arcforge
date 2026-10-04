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
import net.zagdrath.arcforge.registry.ModParticleTypes;
import net.zagdrath.arcforge.registry.ModTriggers;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.config.ConfigMigration;
import net.zagdrath.arcforge.gametest.ArcforgeGameTests;
import net.zagdrath.arcforge.gametest.TestFixtures;
import net.zagdrath.arcforge.item.tool.FoundrySuit;
import net.zagdrath.arcforge.multiblock.PortStore;
import net.zagdrath.arcforge.network.ArcforgeNetwork;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModCapabilities;
import net.zagdrath.arcforge.registry.ModCreativeTabs;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModChunkLoading;
import net.zagdrath.arcforge.registry.ModDataMaps;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.registry.ModRecipes;
import net.zagdrath.arcforge.registry.ModSounds;
import net.zagdrath.arcforge.worldgen.ModWorldgen;

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
        ModSounds.register(modEventBus);
        ModParticleTypes.register(modEventBus);
        PortStore.register(modEventBus);
        FoundrySuit.register(modEventBus);
        ModDataComponents.register(modEventBus);
        ModCapabilities.register(modEventBus);
        ModDataMaps.register(modEventBus);
        ModTriggers.register(modEventBus);
        ModChunkLoading.register(modEventBus);
        ArcforgeNetwork.register(modEventBus);
        ModWorldgen.register(modEventBus);
        ArcforgeGameTests.register(modEventBus);
        TestFixtures.register(modEventBus);

        // The file name is fixed so it stays arcforge-common.toml whichever name the type has. Settings saved before the
        // sections were grouped move to their new places first.
        ConfigMigration.migrate(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(MODID + "-common.toml"));
        modContainer.registerConfig(localConfigType(), ArcforgeConfig.SPEC, MODID + "-common.toml");
        // Each player's own settings (toasts); only loaded on a client.
        modContainer.registerConfig(ModConfig.Type.CLIENT, net.zagdrath.arcforge.config.ArcforgeClientConfig.SPEC, MODID + "-client.toml");

        LOGGER.info("Arcforge initialized");
    }

    // FML 12.0.8 (NeoForge 26.3.0.37-beta) renamed ModConfig.Type.COMMON to LOCAL. Looked up by name, as
    // naming either constant would stop the jar loading on the other side of the rename.
    private static ModConfig.Type localConfigType() {
        for (ModConfig.Type type : ModConfig.Type.values()) {
            if (type.name().equals("LOCAL") || type.name().equals("COMMON")) {
                return type;
            }
        }
        throw new IllegalStateException("ModConfig.Type has neither LOCAL nor COMMON");
    }
}
