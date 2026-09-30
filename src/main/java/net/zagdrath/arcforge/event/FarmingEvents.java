/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.farming.LoamGrowth;

// Loam Farmland can't be trampled, and crops growing on it use its nutrients (LoamGrowth). A hoe tills Loam through
// data/neoforge/data_maps/block_transformer/block_transform_appenders.json (a rule added to minecraft:hoe).
@EventBusSubscriber(modid = Arcforge.MODID)
public final class FarmingEvents {
    private FarmingEvents() {}

    @SubscribeEvent
    static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getState().getBlock() instanceof LoamFarmlandBlock) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onCropGrew(CropGrowEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LoamGrowth.onCropGrew(level, event.getPos(), level.getRandom());
        }
    }
}
