/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.farming.CropHarvest;
import net.zagdrath.arcforge.item.farming.FertilizerItem;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// The Fertilizer Spreader: a treated-wood hopper on legs with an iron spinner under it, holding 9 slots of fertilizer
// (Compost, Wood Ash, Basic Slag, Mixed Fertilizer: any FertilizerItem). Every spreaderInterval ticks it checks the
// Loam Farmland in the square centred on it (spreaderRadius, 2 = 5x5), from its own level down to 3 below, and gives
// each one whose nutrients are below spreaderThreshold one fertilizer, from the first slot that has any. It needs no
// redstone. Hoppers and conduits fill it from any side.
public class FertilizerSpreaderBlockEntity extends BlockEntity implements ConduitConnectable {
    public static final int SLOTS = 9;
    private static final int DEPTH = 3;

    private final FilteredItemHandler items;
    private final ResourceHandler<ItemResource> automation;
    private int timer;

    public FertilizerSpreaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FERTILIZER_SPREADER.get(), pos, state);
        this.items = new FilteredItemHandler(SLOTS, (slot, resource) -> resource.toStack(1).getItem() instanceof FertilizerItem, this::setChanged);
        this.automation = new AutomationResourceHandler<>(items, slot -> true, slot -> false);
    }

    public FilteredItemHandler getItems() {
        return items;
    }

    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return automation;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return type == ConduitType.ITEM ? ConnectionMode.INPUT : ConnectionMode.NONE;
    }

    // The Loam Farmland it looks after.
    public List<BlockPos> soil(Level level) {
        List<BlockPos> soil = new ArrayList<>();
        for (BlockPos column : CropHarvest.square(worldPosition, ArcforgeConfig.SPREADER_RADIUS.getAsInt())) {
            for (int dy = 0; dy >= -DEPTH; dy--) {
                BlockPos pos = column.above(dy);
                if (level.getBlockState(pos).getBlock() instanceof LoamFarmlandBlock) {
                    soil.add(pos);
                    break;
                }
            }
        }
        return soil;
    }

    public int fertilizerCount() {
        int count = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            count += items.getStack(slot).getCount();
        }
        return count;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (++timer < ArcforgeConfig.SPREADER_INTERVAL.getAsInt()) {
            return;
        }
        timer = 0;
        spread(level);
        setChanged();
    }

    // One pass: tops up every low Loam Farmland it has fertilizer for. Returns how many it fed.
    public int spread(ServerLevel level) {
        int threshold = ArcforgeConfig.SPREADER_THRESHOLD.getAsInt();
        int fed = 0;
        for (BlockPos pos : soil(level)) {
            BlockState soil = level.getBlockState(pos);
            if (soil.getValue(LoamFarmlandBlock.NUTRIENTS) >= threshold) {
                continue;
            }
            int slot = firstFertilizer();
            if (slot < 0) {
                break;
            }
            ItemStack stack = items.getStack(slot);
            FertilizerItem fertilizer = (FertilizerItem) stack.getItem();
            FertilizerItem.fertilize(level, pos, soil, fertilizer.nutrients());
            items.setStack(slot, stack.copyWithCount(stack.getCount() - 1));
            fed++;
        }
        return fed;
    }

    private int firstFertilizer() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (items.getStack(slot).getItem() instanceof FertilizerItem fertilizer && fertilizer.nutrients() > 0) {
                return slot;
            }
        }
        return -1;
    }

    public int insertByHand(ItemStack stack) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = items.insert(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
            return inserted;
        }
    }

    public boolean giveAll(Level level, Player player) {
        boolean any = false;
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = items.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            any = true;
            items.setStack(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(stack)) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
            }
        }
        return any;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (int slot = 0; slot < SLOTS; slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStack(slot));
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.deserialize(input.childOrEmpty("items"));
        items.ensureSize(SLOTS);
        timer = input.getIntOr("timer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        items.serialize(output.child("items"));
        output.putInt("timer", timer);
    }
}
