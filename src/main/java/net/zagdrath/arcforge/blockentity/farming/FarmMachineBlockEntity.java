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
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.farming.FarmMachineBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.farming.CropHarvest;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// The Planter and Harvester: a treated-wood box on legs with no GUI and no power. Each run works the square in front of
// it (CropHarvest.areaInFront), at its own level and one below, so it works whether it stands on the ground or on the
// farmland's level. It runs once on each rising redstone pulse; with no redstone attached (nothing next to it that
// gives or carries a signal) it runs every timerTicks instead.
public abstract class FarmMachineBlockEntity extends BlockEntity implements ConduitConnectable {
    protected final FilteredItemHandler items;
    private boolean wasPowered;
    private int timer;

    protected FarmMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots, FilteredItemHandler.SlotFilter filter) {
        super(type, pos, state);
        this.items = new FilteredItemHandler(slots, filter, this::setChanged);
    }

    public FilteredItemHandler getItems() {
        return items;
    }

    public abstract ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side);

    // One pass over the area. Returns how many blocks it worked.
    public abstract int run(ServerLevel level);

    protected abstract int radius();

    // The crop positions in front of it, at its own level and one below.
    public List<BlockPos> area() {
        return CropHarvest.areaInFront(worldPosition, getBlockState().getValue(FarmMachineBlock.FACING), radius());
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered && !wasPowered) {
            run(level);
        }
        if (powered != wasPowered) {
            wasPowered = powered;
            setChanged();
        }
        if (powered || redstoneAttached(level, pos)) {
            timer = 0;
            return;
        }
        if (++timer >= ArcforgeConfig.MACHINE_TIMER_TICKS.getAsInt()) {
            timer = 0;
            run(level);
            setChanged();
        }
    }

    // Whether it runs on redstone pulses (something next to it gives or carries a signal) rather than its timer.
    public boolean onRedstone() {
        return level != null && (level.hasNeighborSignal(worldPosition) || redstoneAttached(level, worldPosition));
    }

    // Ticks until the timer next runs it (when it isn't on redstone).
    public int ticksUntilRun() {
        return Math.max(0, ArcforgeConfig.MACHINE_TIMER_TICKS.getAsInt() - timer);
    }

    // Whether anything next to it gives or carries a signal (dust, a lever, a repeater, a comparator, a button...).
    public static boolean redstoneAttached(Level level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (level.getBlockState(pos.relative(side)).isSignalSource()) {
                return true;
            }
        }
        return false;
    }

    // A player puts a stack in by hand. Returns how many went in.
    public int insertByHand(ItemStack stack) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = items.insert(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
            return inserted;
        }
    }

    // Everything in it goes to the player (or drops on top). Returns whether there was anything.
    public boolean giveAll(Level level, Player player) {
        boolean any = false;
        for (int slot = 0; slot < items.size(); slot++) {
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

    // Conduits connect on every face: the Planter takes seeds in, the Harvester gives its harvest out.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return type == ConduitType.ITEM ? conduitMode() : ConnectionMode.NONE;
    }

    protected abstract ConnectionMode conduitMode();

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (int slot = 0; slot < items.size(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStack(slot));
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int slots = items.size();
        items.deserialize(input.childOrEmpty("items"));
        items.ensureSize(slots);
        wasPowered = input.getBooleanOr("was_powered", false);
        timer = input.getIntOr("timer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        items.serialize(output.child("items"));
        output.putBoolean("was_powered", wasPowered);
        output.putInt("timer", timer);
    }
}
