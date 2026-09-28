/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.storage;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.storage.CrateBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.storage.CrateMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;

// A Crate: tier.crateSlots() ordinary slots (vanilla stack sizes), open to automation through its input and
// output faces. Items that can't go inside container items (filled Crates and Vaults, shulker boxes) are kept
// out, so storage never nests. Broken, it spills its items like a chest; picked up with the Wrench (or swapped by
// a Storage Upgrade) it keeps them, the Wrench's drop carrying them as minecraft:container.
public class CrateBlockEntity extends StorageBlockEntity {
    public static final int DATA_SIDE_CONFIG = 0;
    public static final int DATA_VALUES = 1;

    private final ResourceHandler<ItemResource> inputView;
    private final ResourceHandler<ItemResource> outputView;
    private final ResourceHandler<ItemResource> automationView;
    private final ContainerData data;
    // Players with the GUI open, for the open and close sounds.
    private int openers;

    public CrateBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: everything goes in, the bottom lets it out (a hopper underneath empties it).
        super(ModBlockEntityTypes.CRATE.get(), pos, state, ((CrateBlock) state.getBlock()).getTier(),
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT),
                ((CrateBlock) state.getBlock()).getTier().crateSlots());
        this.inputView = new AutomationResourceHandler<>(items, slot -> true, slot -> false);
        this.outputView = new AutomationResourceHandler<>(items, slot -> false, slot -> true);
        this.automationView = new AutomationResourceHandler<>(items, slot -> true, slot -> true);
        this.data = new WideIntContainerData(DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return index == DATA_SIDE_CONFIG ? sideConfig.pack() : 0;
            }
        };
    }

    // No storage inside storage. The client menu uses this too.
    public static boolean accepts(int slot, ItemResource resource) {
        return resource.toStack(1).canFitInsideContainerItems();
    }

    @Override
    protected boolean isItemValid(int slot, ItemResource resource) {
        return accepts(slot, resource);
    }

    @Override
    protected ConduitType conduitType() {
        return ConduitType.ITEM;
    }

    @Override
    protected boolean canFill(ItemStack stack) {
        return false;
    }

    // Vanilla's container formula: 0 when empty, then 1-15 by how full the slots are.
    @Override
    public int getComparatorSignal() {
        float fill = 0.0F;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.getStack(slot);
            if (!stack.isEmpty()) {
                fill += (float) stack.getCount() / stack.getMaxStackSize();
            }
        }
        return Mth.lerpDiscrete(fill / items.size(), 0, 15);
    }

    // Input faces only take items in, output faces only let them out, none gives nothing; unsided access gets both.
    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return automationView;
        }
        return switch (mode) {
            case INPUT -> inputView;
            case OUTPUT -> outputView;
            default -> null;
        };
    }

    // --- Removal: spills when broken, keeps its items when wrenched or upgraded ---

    @Override
    protected boolean dropsSlotItems() {
        return !isDismantled() && !isUpgrading();
    }

    // --- Item form ---

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        List<ItemStack> stacks = new ArrayList<>(items.size());
        for (int slot = 0; slot < items.size(); slot++) {
            stacks.add(items.getStack(slot));
        }
        ItemContainerContents contents = ItemContainerContents.fromItems(stacks);
        if (!contents.equals(ItemContainerContents.EMPTY)) {
            components.set(DataComponents.CONTAINER, contents);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        ItemContainerContents contents = components.get(DataComponents.CONTAINER);
        if (contents != null) {
            NonNullList<ItemStack> stacks = NonNullList.withSize(items.size(), ItemStack.EMPTY);
            contents.copyInto(stacks);
            for (int slot = 0; slot < stacks.size(); slot++) {
                items.setStack(slot, stacks.get(slot));
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("items");
    }

    // --- Menu ---

    public void startOpen(Player player) {
        if (openers++ == 0) {
            playSound(SoundEvents.BARREL_OPEN);
        }
    }

    public void stopOpen(Player player) {
        if (openers > 0 && --openers == 0) {
            playSound(SoundEvents.BARREL_CLOSE);
        }
    }

    private void playSound(SoundEvent sound) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        }
    }

    @Override
    public Component getDisplayName() {
        Component custom = components().get(DataComponents.CUSTOM_NAME);
        return custom != null ? custom : getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CrateMenu(containerId, inventory, this, data);
    }
}
