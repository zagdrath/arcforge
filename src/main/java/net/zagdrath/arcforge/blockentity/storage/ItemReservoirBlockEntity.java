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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.interaction.Dismantleable;
import net.zagdrath.arcforge.menu.storage.ItemReservoirMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// One block of an Item Reservoir: its own itemReservoir.slotsPerBlock slots of the store it belongs to (see
// ItemReservoirStore). Every face takes and gives items for the whole store (hoppers, Item Conduits, other mods' item
// handlers); a comparator reads the whole store. Items that can't go inside container items (filled Crates and Vaults,
// shulker boxes) are kept out, so storage never nests. Broken, the block spills its own slots like a chest; picked up with
// the Wrench's Dismantle it keeps them, its drop carrying them as minecraft:container.
public class ItemReservoirBlockEntity extends BlockEntity implements Dismantleable, ConduitConnectable, MenuProvider {
    private final FilteredItemHandler items;
    // The store's inventory, seen from this block: resolves the (lazily rebuilt) store on every call.
    private final ResourceHandler<ItemResource> storeView = new StoreView();
    private @Nullable ItemReservoirStore store;
    private boolean dismantled;
    // Players with the GUI open, for the open and close sounds.
    private int openers;

    public ItemReservoirBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ITEM_RESERVOIR.get(), pos, state);
        this.items = new FilteredItemHandler(ArcforgeConfig.ITEM_RESERVOIR_SLOTS.getAsInt(), (slot, resource) -> accepts(resource), this::setChanged);
    }

    // No storage inside storage (the client menu uses this too).
    public static boolean accepts(ItemResource resource) {
        return resource.toStack(1).canFitInsideContainerItems();
    }

    // This block's own slots.
    public FilteredItemHandler items() {
        return items;
    }

    // --- The store ---

    void joinStore(ItemReservoirStore store) {
        if (this.store != null && this.store != store) {
            this.store.invalidate();
        }
        this.store = store;
    }

    // The structure changed: this block's store (and every member's) is built again when next asked for.
    void dropStore() {
        if (store != null) {
            store.invalidate();
            store = null;
        }
    }

    public ItemReservoirStore getStore() {
        ItemReservoirStore current = store;
        if (current == null || !current.isValid()) {
            current = ItemReservoirStore.build(level, this);
        }
        return current;
    }

    public int getComparatorSignal() {
        return getStore().comparatorSignal();
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, ItemReservoirBlockEntity reservoir) {
        reservoir.getStore().tick(level);
    }

    // Removed or unloaded: the rest of the store builds again without this block.
    @Override
    public void setRemoved() {
        super.setRemoved();
        dropStore();
    }

    // --- Capabilities ---

    public ResourceHandler<ItemResource> getItemHandler() {
        return storeView;
    }

    // Auto Item Conduits push into it (a Wrench-forced output side pulls); other conduits don't connect.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return type == ConduitType.ITEM ? ConnectionMode.INPUT : ConnectionMode.NONE;
    }

    private final class StoreView implements ResourceHandler<ItemResource> {
        private ResourceHandler<ItemResource> target() {
            return getStore().handler();
        }

        @Override
        public int size() {
            return target().size();
        }

        @Override
        public ItemResource getResource(int index) {
            return target().getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return target().getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            return target().getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            return target().isValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return isRemoved() ? 0 : target().insert(index, resource, amount, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            return isRemoved() ? 0 : target().extract(index, resource, amount, transaction);
        }
    }

    // --- Removal: spills when broken, keeps its items when wrenched ---

    @Override
    public void markDismantled() {
        dismantled = true;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !dismantled) {
            for (int slot = 0; slot < items.size(); slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStack(slot));
            }
        }
    }

    // --- Item form (the Wrench's drop) ---

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
        output.discard("contents");
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
        return new ItemReservoirMenu(containerId, inventory, this);
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.deserialize(input.childOrEmpty("contents"));
        // A raised itemReservoir.slotsPerBlock grows placed blocks; a lowered one keeps what they have.
        items.ensureSize(ArcforgeConfig.ITEM_RESERVOIR_SLOTS.getAsInt());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        items.serialize(output.child("contents"));
    }
}
