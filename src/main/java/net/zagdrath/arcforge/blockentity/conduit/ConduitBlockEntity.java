/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.conduit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.conduit.SideSetting;
import net.zagdrath.arcforge.conduit.item.ItemPacket;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.item.tool.MachineSettings;
import net.zagdrath.arcforge.item.tool.SettingsCopyable;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;

// Per-block conduit state: the wrench setting of each side, plus what this conduit holds (its share of
// the network's energy/heat/fluid, items in transit and items stored). All transfer logic lives in
// ConduitNetworkManager.
public class ConduitBlockEntity extends BlockEntity implements SettingsCopyable {
    // Fluid and item conduits sync their contents to clients at most this often.
    private static final int CONTENTS_SYNC_INTERVAL = 5;
    // Thermal conduits tint their glow by temperature in steps this big.
    private static final int HEAT_TINT_STEP = 50;

    // What the conduit's model draws: each side's filter look (see packFilterModes).
    public static final ModelProperty<Integer> FILTER_MODES = new ModelProperty<>();
    // The sheath colour for the model: 0 unsheathed, else the dye id + 1.
    public static final ModelProperty<Integer> COLOR = new ModelProperty<>();

    private final SideSetting[] settings = new SideSetting[Direction.values().length];
    // The plastic sheath's colour, or null: sheathed conduits connect only to their own colour (or to unsheathed ones).
    private @Nullable DyeColor color;
    // The Conduit Filter on each side (ItemStack.EMPTY if none).
    private final ItemStack[] filters = new ItemStack[Direction.values().length];
    private final List<ItemPacket> packets = new ArrayList<>();
    // Items pulled in with nowhere to go yet; shown resting in the conduit's core.
    private final List<ItemStack> storedItems = new ArrayList<>();
    // Energy (FE) or heat (HU) held by this conduit, for energy and thermal conduits.
    private int stored;
    // Thermal conduits: the temperature (°C) of the heat held, so a rebuilt network remembers it.
    private int heatTemperature = HeatBuffer.AMBIENT_CELSIUS;
    private FluidStack fluid = FluidStack.EMPTY;
    private float itemSpeed;
    private boolean syncPending;
    // Game time is never negative, so the first sync is always allowed. (Long.MIN_VALUE would overflow the subtraction.)
    private long lastSyncTick = -CONTENTS_SYNC_INTERVAL;
    // The glow tint last sent to clients (server) or last drawn (client); see glowKey.
    private int syncedGlow = Integer.MIN_VALUE;

    public ConduitBlockEntity(BlockPos pos, BlockState state) {
        super(typeFor(state), pos, state);
        Arrays.fill(settings, SideSetting.AUTO);
        Arrays.fill(filters, ItemStack.EMPTY);
    }

    private static net.minecraft.world.level.block.entity.BlockEntityType<ConduitBlockEntity> typeFor(BlockState state) {
        return state.getBlock() instanceof ConduitBlock conduit && conduit.getConduitType().isTransparent()
                ? ModBlockEntityTypes.TRANSPARENT_CONDUIT.get()
                : ModBlockEntityTypes.CONDUIT.get();
    }

    public ConduitType getConduitType() {
        return ((ConduitBlock) getBlockState().getBlock()).getConduitType();
    }

    // --- Side settings (changed with the wrench) ---

    public SideSetting getSetting(Direction side) {
        return settings[side.ordinal()];
    }

    public void setSetting(Direction side, SideSetting setting) {
        if (settings[side.ordinal()] != setting) {
            settings[side.ordinal()] = setting;
            setChanged();
        }
    }

    public boolean isSideDisabled(Direction side) {
        return getSetting(side) == SideSetting.DISABLED;
    }

    // --- Filters (item, fluid and pressurized conduits; see ConduitFilterItem) ---

    // Whether this conduit type takes Conduit Filters.
    public boolean acceptsFilters() {
        return acceptsFilters(getConduitType());
    }

    public static boolean acceptsFilters(ConduitType type) {
        return type == ConduitType.ITEM || type == ConduitType.FLUID || type == ConduitType.GAS;
    }

    // The filter installed on a side (ItemStack.EMPTY if none), carrying its settings component.
    public ItemStack getFilter(Direction side) {
        return filters[side.ordinal()];
    }

    public boolean hasFilter(Direction side) {
        return !filters[side.ordinal()].isEmpty();
    }

    public void setFilter(Direction side, ItemStack filter) {
        if (!acceptsFilters()) {
            return;
        }
        filters[side.ordinal()] = filter.isEmpty() ? ItemStack.EMPTY : filter.copyWithCount(1);
        onFiltersChanged();
    }

    // Takes the filter off a side and returns it (ItemStack.EMPTY if there was none).
    public ItemStack clearFilter(Direction side) {
        ItemStack removed = filters[side.ordinal()];
        if (!removed.isEmpty()) {
            filters[side.ordinal()] = ItemStack.EMPTY;
            onFiltersChanged();
        }
        return removed;
    }

    // Whether the filter on this side lets the stack through when the network inserts into (true) or extracts
    // from (false) the machine there. Sides without a filter, or whose filter doesn't apply that way, allow everything.
    public boolean filterAllows(Direction side, ItemStack stack, boolean inserting) {
        FilterSettings settings = activeFilter(side, inserting);
        return settings == null || settings.matches(stack);
    }

    public boolean filterAllows(Direction side, Fluid fluid, boolean inserting) {
        FilterSettings settings = activeFilter(side, inserting);
        return settings == null || settings.matches(fluid);
    }

    private @Nullable FilterSettings activeFilter(Direction side, boolean inserting) {
        ItemStack filter = filters[side.ordinal()];
        if (filter.isEmpty()) {
            return null;
        }
        FilterSettings settings = FilterSettings.of(filter);
        return settings.appliesTo(inserting) ? settings : null;
    }

    // Each side's filter look (FilterSettings.Mode ordinal + 1, 0 for none) in 2 bits per side: what the model draws.
    public int packFilterModes() {
        int packed = 0;
        for (Direction side : Direction.values()) {
            ItemStack filter = filters[side.ordinal()];
            if (!filter.isEmpty()) {
                packed |= (FilterSettings.mode(filter).ordinal() + 1) << (side.ordinal() * 2);
            }
        }
        return packed;
    }

    private void onFiltersChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            // Sends the filters to clients (the sleeves they draw, and the filter GUI's contents).
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // Unhooking a side (its arm no longer faces a machine, e.g. the machine was broken or the side disabled)
    // pops its filter out: a filter never stays on a bare pipe.
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        if (!(level instanceof ServerLevel) || !(state.getBlock() instanceof ConduitBlock)) {
            return;
        }
        for (Direction side : Direction.values()) {
            if (hasFilter(side) && !ConduitBlock.mode(state, side).isPort()) {
                Block.popResource(level, worldPosition, clearFilter(side));
            }
        }
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(FILTER_MODES, packFilterModes()).with(COLOR, color == null ? 0 : color.getId() + 1).build();
    }

    // --- Sheath colour ---

    public @Nullable DyeColor getColor() {
        return color;
    }

    public void setColor(@Nullable DyeColor color) {
        if (this.color != color) {
            this.color = color;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // Two conduits join unless both are sheathed in different colours.
    public static boolean colorsMatch(@Nullable ConduitBlockEntity a, @Nullable ConduitBlockEntity b) {
        return a == null || b == null || a.color == null || b.color == null || a.color == b.color;
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) {
        super.applyImplicitComponents(components);
        color = components.get(ModDataComponents.CONDUIT_COLOR.get());
    }

    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (color != null) {
            components.set(ModDataComponents.CONDUIT_COLOR.get(), color);
        }
    }

    // --- Energy / heat buffer ---

    public int getStored() {
        return stored;
    }

    public void setStored(int amount) {
        if (amount != stored) {
            stored = amount;
            setChanged();
        }
    }

    public int getHeatTemperature() {
        return heatTemperature;
    }

    public void setHeatTemperature(int celsius) {
        if (celsius != heatTemperature) {
            heatTemperature = celsius;
            setChanged();
            syncGlow();
        }
    }

    // What a gas or thermal conduit's glow is tinted by (see ConduitTints): which gas it holds, or how hot
    // its heat is. Clients are sent it when it changes, and redraw the conduit.
    public int glowKey() {
        return switch (getConduitType()) {
            case GAS -> fluid.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(fluid.getFluid());
            case THERMAL -> heatTemperature / HEAT_TINT_STEP;
            default -> 0;
        };
    }

    private static boolean hasGlowTint(ConduitType type) {
        return type == ConduitType.GAS || type == ConduitType.THERMAL;
    }

    private void syncGlow() {
        if (level == null || !hasGlowTint(getConduitType())) {
            return;
        }
        int key = glowKey();
        if (key != syncedGlow) {
            syncedGlow = key;
            // On the server this sends the update; on a client it redraws the conduit's section.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- Stored items ---

    public List<ItemStack> getStoredItems() {
        return storedItems;
    }

    // Adds as much of the stack as fits in the storage slots and returns how many were stored.
    // With `overflow`, items that do not fit get a slot of their own anyway (used for items already
    // inside the network, which must never be deleted).
    public int storeItems(ItemStack stack, boolean overflow) {
        int remaining = stack.getCount();
        for (ItemStack held : storedItems) {
            if (remaining <= 0) {
                break;
            }
            if (ItemStack.isSameItemSameComponents(held, stack)) {
                int moved = Math.min(remaining, held.getMaxStackSize() - held.getCount());
                if (moved > 0) {
                    held.grow(moved);
                    remaining -= moved;
                }
            }
        }
        while (remaining > 0 && (overflow || storedItems.size() < ConduitTier.ITEM_STORAGE_SLOTS)) {
            int moved = Math.min(remaining, stack.getMaxStackSize());
            storedItems.add(stack.copyWithCount(moved));
            remaining -= moved;
        }
        int storedCount = stack.getCount() - remaining;
        if (storedCount > 0) {
            markContentsChanged(true);
        }
        return storedCount;
    }

    // How many of this item could still be stored.
    public int storageSpaceFor(ItemStack stack) {
        int space = (ConduitTier.ITEM_STORAGE_SLOTS - storedItems.size()) * stack.getMaxStackSize();
        for (ItemStack held : storedItems) {
            if (ItemStack.isSameItemSameComponents(held, stack)) {
                space += held.getMaxStackSize() - held.getCount();
            }
        }
        return Math.max(0, space);
    }

    // --- Item packets ---

    public List<ItemPacket> getPackets() {
        return packets;
    }

    public float getItemSpeed() {
        return itemSpeed;
    }

    public void setItemSpeed(float speed) {
        if (speed != itemSpeed) {
            itemSpeed = speed;
            markContentsChanged(true);
        }
    }

    // --- Fluid share of the network tank ---

    public FluidStack getFluid() {
        return fluid;
    }

    public void setFluid(FluidStack stack) {
        if (!FluidStack.matches(stack, fluid)) {
            fluid = stack.copy();
            markContentsChanged(false);
            syncGlow();
        }
    }

    // Records a content change. Items sync right away so clients see packets enter and leave;
    // fluid levels change every tick and are throttled.
    public void markContentsChanged(boolean immediate) {
        setChanged();
        if (!getConduitType().showsContents()) {
            return;
        }
        syncPending = true;
        if (immediate) {
            flushSync(true);
        }
    }

    // Called by the network manager each tick.
    public void flushSync(boolean force) {
        if (!syncPending || level == null || level.isClientSide()) {
            return;
        }
        long now = level.getGameTime();
        if (force || now - lastSyncTick >= CONTENTS_SYNC_INTERVAL) {
            syncPending = false;
            lastSyncTick = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // Clients move item packets along between server updates.
    public static void clientTick(Level level, BlockPos pos, BlockState state, ConduitBlockEntity conduit) {
        for (ItemPacket packet : conduit.packets) {
            packet.progress = Math.min(1.0F, packet.progress + conduit.itemSpeed);
        }
    }

    // --- Lifecycle ---

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            ConduitNetworkManager.get(serverLevel).markDirty(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level instanceof ServerLevel serverLevel) {
            ConduitNetworkManager.get(serverLevel).markDirty(worldPosition);
        }
    }

    // Items in transit are dropped when the conduit is broken; they are never deleted.
    // Fluid moves into the connected neighbours, which pool it when their network is rebuilt
    // (only fluid beyond the remaining network's capacity is lost).
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level == null) {
            return;
        }
        for (ItemPacket packet : packets) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, packet.stack);
        }
        packets.clear();
        for (ItemStack stack : storedItems) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
        }
        storedItems.clear();
        // Filters drop with their settings.
        for (Direction side : Direction.values()) {
            if (hasFilter(side)) {
                Block.popResource(level, pos, filters[side.ordinal()]);
                filters[side.ordinal()] = ItemStack.EMPTY;
            }
        }
        if (!fluid.isEmpty()) {
            handOffFluid(pos, state);
        }
    }

    private void handOffFluid(BlockPos pos, BlockState state) {
        List<ConduitBlockEntity> neighbours = new ArrayList<>();
        for (Direction side : Direction.values()) {
            if (ConduitBlock.mode(state, side) == ConnectionMode.PIPE
                    && level.getBlockEntity(pos.relative(side)) instanceof ConduitBlockEntity neighbour
                    && (neighbour.fluid.isEmpty() || FluidStack.isSameFluidSameComponents(neighbour.fluid, fluid))) {
                neighbours.add(neighbour);
            }
        }
        int remaining = fluid.getAmount();
        for (int i = 0; i < neighbours.size(); i++) {
            ConduitBlockEntity neighbour = neighbours.get(i);
            int share = Math.ceilDiv(remaining, neighbours.size() - i);
            neighbour.setFluid(fluid.copyWithAmount(neighbour.fluid.getAmount() + share));
            remaining -= share;
        }
        fluid = FluidStack.EMPTY;
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.getInt("sides").ifPresentOrElse(this::unpackSettings, () -> migrateSettings(input.getIntOr("disabled_sides", 0)));
        DyeColor colorBefore = color;
        color = input.getInt("color").map(DyeColor::byId).orElse(null);
        if (level != null && level.isClientSide() && color != colorBefore) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        packets.clear();
        input.listOrEmpty("packets", ItemPacket.CODEC).forEach(packets::add);
        storedItems.clear();
        input.listOrEmpty("stored_items", ItemStack.OPTIONAL_CODEC).forEach(stack -> {
            if (!stack.isEmpty()) {
                storedItems.add(stack);
            }
        });
        stored = input.getIntOr("stored", 0);
        heatTemperature = input.getIntOr("heat_temperature", HeatBuffer.AMBIENT_CELSIUS);
        fluid = input.read("fluid", FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
        itemSpeed = input.getFloatOr("item_speed", 0.0F);
        int filterModesBefore = packFilterModes();
        Arrays.fill(filters, ItemStack.EMPTY);
        input.listOrEmpty("filters", InstalledFilter.CODEC).forEach(installed -> {
            if (installed.side() >= 0 && installed.side() < filters.length) {
                filters[installed.side()] = installed.stack();
            }
        });
        if (level != null && level.isClientSide()) {
            syncGlow();
            // The sleeves on the arms are part of the block model: redraw when their looks change.
            if (packFilterModes() != filterModesBefore) {
                requestModelDataUpdate();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // A filter as saved: the side (Direction ordinal) and the filter item with its settings.
    private record InstalledFilter(byte side, ItemStack stack) {
        static final Codec<InstalledFilter> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BYTE.fieldOf("side").forGetter(InstalledFilter::side),
                ItemStack.CODEC.fieldOf("stack").forGetter(InstalledFilter::stack))
                .apply(i, InstalledFilter::new));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("sides", packSettings());
        if (color != null) {
            output.putInt("color", color.getId());
        }
        if (!packets.isEmpty()) {
            var list = output.list("packets", ItemPacket.CODEC);
            packets.forEach(list::add);
        }
        if (!storedItems.isEmpty()) {
            var list = output.list("stored_items", ItemStack.OPTIONAL_CODEC);
            storedItems.forEach(list::add);
        }
        if (heatTemperature != HeatBuffer.AMBIENT_CELSIUS) {
            output.putInt("heat_temperature", heatTemperature);
        }
        if (stored > 0) {
            output.putInt("stored", stored);
        }
        if (!fluid.isEmpty()) {
            output.store("fluid", FluidStack.OPTIONAL_CODEC, fluid);
        }
        output.putFloat("item_speed", itemSpeed);
        if (packFilterModes() != 0) {
            var list = output.list("filters", InstalledFilter.CODEC);
            for (Direction side : Direction.values()) {
                if (hasFilter(side)) {
                    list.add(new InstalledFilter((byte) side.ordinal(), filters[side.ordinal()]));
                }
            }
        }
    }

    // --- Settings Card ---

    @Override
    public Identifier settingsKind() {
        return MachineSettings.kind(this);
    }

    @Override
    public void writeSettings(ValueOutput output) {
        output.putInt("sides", packSettings());
        ValueOutput.TypedOutputList<FilterSettings> filterList = output.list("filters", FilterSettings.CODEC);
        int[] filterSides = java.util.Arrays.stream(Direction.values()).filter(this::hasFilter).mapToInt(Direction::ordinal).toArray();
        for (int side : filterSides) {
            filterList.add(FilterSettings.of(filters[side]));
        }
        output.putIntArray("filter_sides", filterSides);
    }

    @Override
    public int readSettings(ValueInput input) {
        input.getInt("sides").ifPresent(this::unpackSettings);
        int skipped = 0;
        int[] sides = input.getIntArray("filter_sides").orElse(new int[0]);
        int index = 0;
        for (FilterSettings settings : input.listOrEmpty("filters", FilterSettings.CODEC)) {
            if (index >= sides.length) {
                break;
            }
            Direction side = Direction.values()[sides[index++]];
            if (!hasFilter(side)) {
                skipped++;
                continue;
            }
            ItemStack filter = getFilter(side).copy();
            filter.set(ModDataComponents.CONDUIT_FILTER.get(), settings);
            setFilter(side, filter);
        }
        setChanged();
        if (level != null) {
            ConduitBlock.refreshConnections(level, worldPosition);
        }
        return skipped;
    }

    @Override
    public List<Component> describe(ValueInput input) {
        List<Component> lines = new ArrayList<>();
        input.getInt("sides").ifPresent(packed -> {
            long forced = java.util.Arrays.stream(Direction.values()).filter(side -> ((packed >>> (side.ordinal() * 2)) & 3) != SideSetting.AUTO.ordinal()).count();
            lines.add(Component.translatable("settings.arcforge.sides", forced));
        });
        int filters = input.getIntArray("filter_sides").map(sides -> sides.length).orElse(0);
        if (filters > 0) {
            lines.add(Component.translatable("settings.arcforge.filters", filters));
        }
        return lines;
    }

    // Two bits per side.
    private int packSettings() {
        int packed = 0;
        for (Direction side : Direction.values()) {
            packed |= settings[side.ordinal()].ordinal() << (side.ordinal() * 2);
        }
        return packed;
    }

    private void unpackSettings(int packed) {
        for (Direction side : Direction.values()) {
            settings[side.ordinal()] = SideSetting.byId((packed >>> (side.ordinal() * 2)) & 3);
        }
    }

    // Saves from before side settings existed: a wrench-set input/output lived only in the block state,
    // and disabled conduit joints in a bit mask. Keep both as forced settings.
    private void migrateSettings(int disabledMask) {
        BlockState state = getBlockState();
        for (Direction side : Direction.values()) {
            SideSetting setting = SideSetting.AUTO;
            if ((disabledMask & (1 << side.ordinal())) != 0) {
                setting = SideSetting.DISABLED;
            } else if (state.getBlock() instanceof ConduitBlock) {
                ConnectionMode mode = ConduitBlock.mode(state, side);
                if (mode == ConnectionMode.INPUT) setting = SideSetting.INPUT;
                if (mode == ConnectionMode.OUTPUT) setting = SideSetting.OUTPUT;
            }
            settings[side.ordinal()] = setting;
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
}
