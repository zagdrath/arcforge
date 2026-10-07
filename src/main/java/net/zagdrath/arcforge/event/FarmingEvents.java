/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.bus.api.EventPriority;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.block.farming.ScarecrowBlock;
import net.zagdrath.arcforge.farming.CropRotation;
import net.zagdrath.arcforge.farming.LoamGrowth;

// Loam Farmland can't be trampled, nor can a mob trample any farmland near a Scarecrow, and crops growing on Loam use
// its nutrients (LoamGrowth). A ripe crop broken by hand counts for crop rotation (CropRotation), as the Harvester, Sickle
// and Scythe do through CropHarvest. A hoe tills Loam through LoamBlock.getToolModifiedState.
@EventBusSubscriber(modid = Arcforge.MODID)
public final class FarmingEvents {
    private FarmingEvents() {}

    @SubscribeEvent
    static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getState().getBlock() instanceof LoamFarmlandBlock) {
            event.setCanceled(true);
            return;
        }
        // A Scarecrow nearby keeps mobs (not players) off the farmland.
        if (!(event.getEntity() instanceof Player) && ScarecrowBlock.protects(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onCropGrew(CropGrowEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LoamGrowth.onCropGrew(level, event.getPos(), level.getRandom());
        }
    }

    // Last, so a cancelled break (a protected block, an Arc Tool out of FE) doesn't count.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBreak(BreakBlockEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) {
            CropRotation.onHarvested(level, event.getPos(), event.getState(), event.getPlayer() instanceof ServerPlayer player ? player : null);
        }
    }
}
