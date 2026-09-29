/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.storage.VaultBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.item.tool.MachineSettings;
import net.zagdrath.arcforge.item.tool.SettingsCardData;
import net.zagdrath.arcforge.item.tool.SettingsCopyable;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.storage.VaultMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.storage.VaultContents;
import net.zagdrath.arcforge.storage.VaultStorage;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;

import com.mojang.serialization.Codec;

// A Vault: one stackable item type in bulk (see VaultStorage), shown on its front by VaultRenderer. Conduits and
// hoppers use its input and output faces; players use the front (VaultBlock) or the GUI. What it holds, and its
// lock and void settings, stay with it when it's broken or picked up (the arcforge:vault_contents component).
// Clients are sent the contents for the front display: at once when the type or a setting changes, and at most
// every SYNC_INTERVAL ticks as the amount changes, so a busy conduit doesn't send a packet per item.
public class VaultBlockEntity extends StorageBlockEntity implements SettingsCopyable {
    public static final int DATA_AMOUNT = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_FLAGS = 2;
    public static final int DATA_SIDE_CONFIG = 3;
    public static final int DATA_VALUES = 4;
    public static final int FLAG_LOCKED = 1;
    public static final int FLAG_VOID = 2;

    private static final int SYNC_INTERVAL = 4;
    // Two right-clicks on the front this close together put in every matching item the player carries.
    private static final int DOUBLE_CLICK_TICKS = 10;
    // Holding left-click takes a stack at most this often (in survival the game repeats the click every tick).
    private static final int TAKE_INTERVAL = 5;

    private final VaultStorage storage;
    private final ResourceHandler<ItemResource> inputView;
    private final ResourceHandler<ItemResource> outputView;
    private final ResourceHandler<ItemResource> automationView;
    private final ContainerData data;

    private boolean syncPending;
    private long lastSyncTick = -SYNC_INTERVAL;
    private @Nullable UUID lastClicker;
    private long lastClickTick;
    private @Nullable UUID lastTaker;
    private long lastTakeTick;

    public VaultBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: in from the top, sides and back, out of the bottom; the front is the player's.
        super(ModBlockEntityTypes.VAULT.get(), pos, state, ((VaultBlock) state.getBlock()).getTier(),
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.NONE), 0);
        this.storage = new VaultStorage(tier.vaultCapacity(), this::onStorageChanged);
        this.inputView = new AutomationResourceHandler<>(storage, slot -> true, slot -> false);
        this.outputView = new AutomationResourceHandler<>(storage, slot -> false, slot -> true);
        this.automationView = new AutomationResourceHandler<>(storage, slot -> true, slot -> true);
        this.data = new WideIntContainerData(DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case DATA_AMOUNT -> storage.getAmount();
                    case DATA_CAPACITY -> storage.getCapacity();
                    case DATA_FLAGS -> (storage.isLocked() ? FLAG_LOCKED : 0) | (storage.isVoidMode() ? FLAG_VOID : 0);
                    case DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public VaultStorage getStorage() {
        return storage;
    }

    public ItemStack getTemplate() {
        return storage.getTemplate();
    }

    public int getAmount() {
        return storage.getAmount();
    }

    public int getCapacity() {
        return storage.getCapacity();
    }

    public boolean isLocked() {
        return storage.isLocked();
    }

    public boolean isVoidMode() {
        return storage.isVoidMode();
    }

    @Override
    protected boolean isItemValid(int slot, ItemResource resource) {
        return false;
    }

    @Override
    protected ConduitType conduitType() {
        return ConduitType.ITEM;
    }

    @Override
    protected boolean canFill(ItemStack stack) {
        return false;
    }

    // 0 when empty, then 1-15 by how full.
    @Override
    public int getComparatorSignal() {
        return storage.getAmount() == 0 ? 0 : 1 + (int) (14L * storage.getAmount() / storage.getCapacity());
    }

    // --- Settings Card ---

    @Override
    public Identifier settingsKind() {
        return MachineSettings.kind(this);
    }

    @Override
    public void writeSettings(ValueOutput output) {
        output.putBoolean("locked", isLocked());
        output.putBoolean("void", isVoidMode());
    }

    @Override
    public int readSettings(ValueInput input) {
        int skipped = 0;
        input.read("void", Codec.BOOL).ifPresent(this::setVoidMode);
        Optional<Boolean> locked = input.read("locked", Codec.BOOL);
        if (locked.isPresent() && locked.get() != isLocked()) {
            if (locked.get() && getTemplate().isEmpty()) {
                skipped++;
            } else {
                setLocked(locked.get());
            }
        }
        return skipped;
    }

    @Override
    public List<Component> describe(ValueInput input) {
        List<Component> lines = new ArrayList<>();
        input.read("locked", Codec.BOOL).ifPresent(on -> lines.add(Component.translatable("settings.arcforge.vault_lock", MachineSettings.onOff(on))));
        input.read("void", Codec.BOOL).ifPresent(on -> lines.add(Component.translatable("settings.arcforge.vault_void", MachineSettings.onOff(on))));
        return lines;
    }

    // --- Settings ---

    public void setLocked(boolean locked) {
        storage.setLocked(locked);
        updateStateFlags();
    }

    public void setVoidMode(boolean voidMode) {
        storage.setVoidMode(voidMode);
        updateStateFlags();
    }

    // The front's status lights (the LOCKED and VOID block state properties) follow the settings.
    private void updateStateFlags() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = getBlockState();
        BlockState flagged = state.setValue(VaultBlock.LOCKED, storage.isLocked()).setValue(VaultBlock.VOID, storage.isVoidMode());
        if (flagged != state) {
            level.setBlock(worldPosition, flagged, Block.UPDATE_CLIENTS);
        }
    }

    // --- Player use (see VaultBlock and MachineInteractionEvents) ---

    // Puts in as much of the stack as fits (all of it in void mode) and returns how many were taken.
    public int insert(ItemStack stack) {
        if (stack.isEmpty() || !storage.accepts(stack)) {
            return 0;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int taken = storage.insert(0, ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
            return taken;
        }
    }

    // Takes out up to `max` of the stored item.
    public ItemStack extract(int max) {
        ItemStack template = storage.getTemplate();
        if (storage.getAmount() == 0 || max <= 0) {
            return ItemStack.EMPTY;
        }
        try (Transaction tx = Transaction.openRoot()) {
            int taken = storage.extract(0, ItemResource.of(template), max, tx);
            tx.commit();
            return template.copyWithCount(taken);
        }
    }

    // Records a right-click on the front; true if it's the same player's second within DOUBLE_CLICK_TICKS.
    public boolean registerClick(Player player, long gameTime) {
        boolean isDouble = player.getUUID().equals(lastClicker) && gameTime - lastClickTick <= DOUBLE_CLICK_TICKS;
        lastClicker = isDouble ? null : player.getUUID();
        lastClickTick = gameTime;
        return isDouble;
    }

    // Whether this player may take another stack by left-clicking now (and records it if so).
    public boolean tryTake(Player player, long gameTime) {
        if (player.getUUID().equals(lastTaker) && gameTime - lastTakeTick < TAKE_INTERVAL) {
            return false;
        }
        lastTaker = player.getUUID();
        lastTakeTick = gameTime;
        return true;
    }

    // --- Capabilities ---

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

    // --- Syncing ---

    private void onStorageChanged(boolean typeChanged) {
        setChanged();
        syncPending = true;
        if (typeChanged) {
            flushSync();
        }
    }

    private void flushSync() {
        if (level != null && !level.isClientSide()) {
            syncPending = false;
            lastSyncTick = level.getGameTime();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, VaultBlockEntity vault) {
        if (vault.syncPending && level.getGameTime() - vault.lastSyncTick >= SYNC_INTERVAL) {
            vault.flushSync();
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Item form: the contents travel in arcforge:vault_contents, whether broken or wrenched (see the loot tables) ---

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        VaultContents contents = storage.toContents();
        if (!contents.isEmpty()) {
            components.set(ModDataComponents.VAULT_CONTENTS.get(), contents);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        VaultContents contents = components.getOrDefault(ModDataComponents.VAULT_CONTENTS.get(), VaultContents.EMPTY);
        storage.load(contents.template(), contents.amount(), contents.locked(), contents.voidMode());
        updateStateFlags();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("template");
        output.discard("amount");
        output.discard("locked");
        output.discard("void");
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        storage.load(input.read("template", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY), input.getIntOr("amount", 0),
                input.getBooleanOr("locked", false), input.getBooleanOr("void", false));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!storage.getTemplate().isEmpty()) {
            output.store("template", ItemStack.OPTIONAL_CODEC, storage.getTemplate());
        }
        output.putInt("amount", storage.getAmount());
        output.putBoolean("locked", storage.isLocked());
        output.putBoolean("void", storage.isVoidMode());
    }

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        Component custom = components().get(DataComponents.CUSTOM_NAME);
        return custom != null ? custom : getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new VaultMenu(containerId, inventory, this, data);
    }
}
