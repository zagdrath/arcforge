/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.farming.CompostBinBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// The Compost Bin. Its fill level is the block's LEVEL (0-7, 8 = ready), as on a vanilla composter, and each plant
// item raises it by the layers its minecraft:compostable component rolls, as in a vanilla composter. Players drop items straight in; hoppers
// and conduits fill the input slot, which it works through one item every itemInterval ticks. readyDelay ticks after
// it's full, compostPerBatch Compost goes into the output slot and the bin reads "ready" until that's taken, by hand
// (use it) or pulled out by a hopper or conduit; then it starts again from empty.
public class CompostBinBlockEntity extends BlockEntity implements ConduitConnectable {
    public static final int SLOT_INPUT = 0, SLOT_OUTPUT = 1, SLOTS = 2;

    private final FilteredItemHandler items;
    private final ResourceHandler<ItemResource> automation;
    private int itemTimer;
    private int readyTimer;

    public CompostBinBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.COMPOST_BIN.get(), pos, state);
        this.items = new FilteredItemHandler(SLOTS, (slot, resource) -> slot == SLOT_INPUT && resource.getComponents().has(DataComponents.COMPOSTABLE), this::setChanged);
        this.automation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
    }

    // Whether it's plant matter the bin takes.
    public static boolean compostable(ItemStack stack) {
        return stack.has(DataComponents.COMPOSTABLE);
    }

    public FilteredItemHandler getItems() {
        return items;
    }

    public int getLevelValue() {
        return getBlockState().getValue(CompostBinBlock.LEVEL);
    }

    // Hoppers and conduits: plant matter in, Compost out, on every face.
    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return automation;
    }

    // An auto conduit pulls Compost from underneath and feeds plant matter in everywhere else.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        if (type != ConduitType.ITEM) {
            return ConnectionMode.NONE;
        }
        return side == Direction.DOWN ? ConnectionMode.OUTPUT : ConnectionMode.INPUT;
    }

    // A player drops one item straight in. Returns whether it was taken (the level may or may not have risen).
    public boolean insertByHand(ServerLevel level, ItemStack stack) {
        int fill = getLevelValue();
        Compostable compostable = stack.get(DataComponents.COMPOSTABLE);
        if (fill >= CompostBinBlock.FULL || compostable == null) {
            return false;
        }
        compost(level, compostable);
        return true;
    }

    // Hands the ready Compost to the player (or drops it on top). Returns whether there was any.
    public boolean takeCompost(Level level, net.minecraft.world.entity.player.Player player) {
        ItemStack out = items.getStack(SLOT_OUTPUT);
        if (out.isEmpty()) {
            return false;
        }
        items.setStack(SLOT_OUTPUT, ItemStack.EMPTY);
        if (!player.getInventory().add(out)) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, out);
        }
        level.playSound(null, worldPosition, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        setFill(level, 0);
        return true;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        int fill = state.getValue(CompostBinBlock.LEVEL);
        if (fill == CompostBinBlock.READY) {
            // Emptied by a hopper or conduit: start over.
            if (items.getStack(SLOT_OUTPUT).isEmpty()) {
                setFill(level, 0);
            }
            return;
        }
        if (fill == CompostBinBlock.FULL) {
            if (++readyTimer >= ArcforgeConfig.COMPOST_BIN_READY_DELAY.getAsInt()) {
                readyTimer = 0;
                items.setStack(SLOT_OUTPUT, new ItemStack(ModItems.COMPOST.get(), ArcforgeConfig.COMPOST_BIN_YIELD.getAsInt()));
                setFill(level, CompostBinBlock.READY);
                level.playSound(null, pos, SoundEvents.COMPOSTER_READY, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            setChanged();
            return;
        }
        ItemStack input = items.getStack(SLOT_INPUT);
        if (input.isEmpty()) {
            itemTimer = 0;
            return;
        }
        if (++itemTimer >= ArcforgeConfig.COMPOST_BIN_ITEM_INTERVAL.getAsInt()) {
            itemTimer = 0;
            Compostable compostable = input.get(DataComponents.COMPOSTABLE);
            items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
            if (compostable != null) {
                compost(level, compostable);
            }
        }
        setChanged();
    }

    // One item into the heap: the level rises by the layers the item's component rolls (ComposterBlock.addLayer).
    private void compost(ServerLevel level, Compostable compostable) {
        int fill = getLevelValue();
        LootContext context = new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, getBlockState())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .create(LootContextParamSets.BLOCK_INTERACT)).create(java.util.Optional.empty());
        int layers = compostable.layers().get(context, 0);
        boolean rose = layers > 0;
        if (rose) {
            setFill(level, Math.min(CompostBinBlock.FULL, fill + layers));
        }
        level.levelEvent(LevelEvent.COMPOSTER_FILL, worldPosition, rose ? 1 : 0);
        level.playSound(null, worldPosition, rose ? SoundEvents.COMPOSTER_FILL_SUCCESS : SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        setChanged();
    }

    private void setFill(Level level, int fill) {
        BlockState state = getBlockState();
        if (state.getValue(CompostBinBlock.LEVEL) != fill) {
            level.setBlock(worldPosition, state.setValue(CompostBinBlock.LEVEL, fill), Block.UPDATE_ALL);
        }
    }

    // Whatever is in it drops when it's broken; the heap itself is lost, as with a composter.
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
        itemTimer = input.getIntOr("item_timer", 0);
        readyTimer = input.getIntOr("ready_timer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        items.serialize(output.child("items"));
        output.putInt("item_timer", itemTimer);
        output.putInt("ready_timer", readyTimer);
    }
}
