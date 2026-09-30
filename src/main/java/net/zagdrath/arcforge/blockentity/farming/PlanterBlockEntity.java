/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.farming.TrellisBlock;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;

// The Planter: 9 slots of seeds. Each run plants one seed on every empty farmland in the square in front of it, taking
// from the first slot that has a seed that will grow there: any crop or stem seed (wheat, beetroot, carrots, potatoes,
// melon and pumpkin seeds, Flax, Rapeseed, Sorghum...), and Hop Seeds into a bare Trellis standing on farmland.
// Hoppers and conduits fill it from any side; nothing comes out except by hand (sneak-use with an empty hand).
public class PlanterBlockEntity extends FarmMachineBlockEntity {
    public static final int SLOTS = 9;

    private final ResourceHandler<ItemResource> automation;

    public PlanterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.PLANTER.get(), pos, state, SLOTS, (slot, resource) -> isSeed(resource.toStack(1)));
        this.automation = new AutomationResourceHandler<>(items, slot -> true, slot -> false);
    }

    // Seeds the Planter takes: block items that place a crop or a stem, and Hop Seeds.
    public static boolean isSeed(ItemStack stack) {
        if (stack.is(ModItems.HOP_SEEDS.get())) {
            return true;
        }
        return stack.getItem() instanceof BlockItem item && (item.getBlock() instanceof CropBlock || item.getBlock() instanceof StemBlock);
    }

    @Override
    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return automation;
    }

    @Override
    protected ConnectionMode conduitMode() {
        return ConnectionMode.INPUT;
    }

    @Override
    protected int radius() {
        return ArcforgeConfig.PLANTER_RADIUS.getAsInt();
    }

    @Override
    public int run(ServerLevel level) {
        int planted = 0;
        for (BlockPos column : area()) {
            for (int dy = 0; dy >= -1; dy--) {
                if (plantAt(level, column.above(dy))) {
                    planted++;
                    break;
                }
            }
        }
        if (planted > 0) {
            level.playSound(null, worldPosition, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 0.9F);
        }
        return planted;
    }

    // Plants the first seed that fits at pos (air over farmland, or a bare bottom Trellis on farmland).
    private boolean plantAt(ServerLevel level, BlockPos pos) {
        BlockState here = level.getBlockState(pos);
        if (!(level.getBlockState(pos.below()).getBlock() instanceof FarmlandBlock)) {
            return false;
        }
        boolean bareTrellis = here.getBlock() instanceof TrellisBlock && here.getValue(TrellisBlock.AGE) == TrellisBlock.BARE;
        if (!here.isAir() && !bareTrellis) {
            return false;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = items.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            BlockState plant;
            if (bareTrellis) {
                if (!stack.is(ModItems.HOP_SEEDS.get())) {
                    continue;
                }
                plant = here.setValue(TrellisBlock.AGE, TrellisBlock.SHOOT);
            } else {
                if (!(stack.getItem() instanceof BlockItem item)) {
                    continue;
                }
                plant = item.getBlock().defaultBlockState();
                if (!plant.canSurvive(level, pos)) {
                    continue;
                }
            }
            level.setBlock(pos, plant, Block.UPDATE_ALL);
            items.setStack(slot, stack.copyWithCount(stack.getCount() - 1));
            return true;
        }
        return false;
    }
}
