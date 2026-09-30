/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.farming.CropHarvest;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;

// The Harvester: each run harvests every ripe crop in the square in front of it (its own level, one below, and one
// above for a tall Trellis) into its 18 slots and replants it with
// a seed from its own drops (CropHarvest). A bearing Trellis has its Hop Cones picked and the vine left to bear again.
// A crop whose harvest won't fit is left standing. Hoppers and conduits pull the harvest out of any side; a player
// takes it all by using it with an empty hand.
public class HarvesterBlockEntity extends FarmMachineBlockEntity {
    public static final int SLOTS = 18;

    private final ResourceHandler<ItemResource> automation;

    public HarvesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.HARVESTER.get(), pos, state, SLOTS, (slot, resource) -> true);
        this.automation = new AutomationResourceHandler<>(items, slot -> false, slot -> true);
    }

    @Override
    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return automation;
    }

    @Override
    protected ConnectionMode conduitMode() {
        return ConnectionMode.OUTPUT;
    }

    @Override
    protected int radius() {
        return ArcforgeConfig.HARVESTER_RADIUS.getAsInt();
    }

    @Override
    public int run(ServerLevel level) {
        CropHarvest.Sink sink = new CropHarvest.Sink() {
            @Override
            public boolean fits(List<ItemStack> stacks) {
                // Tried in a transaction that is never committed, so nothing actually moves.
                try (Transaction tx = Transaction.openRoot()) {
                    for (ItemStack stack : stacks) {
                        if (items.insert(ItemResource.of(stack), stack.getCount(), tx) < stack.getCount()) {
                            return false;
                        }
                    }
                    return true;
                }
            }

            @Override
            public void put(ItemStack stack) {
                try (Transaction tx = Transaction.openRoot()) {
                    items.insert(ItemResource.of(stack), stack.getCount(), tx);
                    tx.commit();
                }
            }
        };
        int harvested = 0;
        for (BlockPos column : area()) {
            // Its own level and one below, and one above for the top of a two-high Trellis.
            for (int dy = 1; dy >= -1; dy--) {
                if (CropHarvest.harvest(level, column.above(dy), sink)) {
                    harvested++;
                }
            }
        }
        if (harvested > 0) {
            level.playSound(null, worldPosition, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return harvested;
    }
}
