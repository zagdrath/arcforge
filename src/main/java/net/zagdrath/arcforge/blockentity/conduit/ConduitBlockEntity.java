/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.conduit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Containers;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.conduit.ConduitFacades;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.conduit.SideSetting;
import net.zagdrath.arcforge.conduit.item.ItemPacket;
import net.zagdrath.arcforge.conduit.network.ActiveConduitNetwork;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.item.tool.MachineSettings;
import net.zagdrath.arcforge.item.tool.SettingsCopyable;
import net.zagdrath.arcforge.network.ConduitContentsPayload;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;

// Per-block conduit state: the wrench setting of each side, plus what this conduit holds (its share of
// the network's energy/heat/fluid, items in transit and items stored). All transfer logic lives in
// ConduitNetworkManager.
public class ConduitBlockEntity extends BlockEntity implements SettingsCopyable {
    // Fluid and item conduits sync their contents to clients at most this often.
    private static final int CONTENTS_SYNC_INTERVAL = 5;
    // A fluid level change is only sent once it moves the drawn level by at least 1/FILL_SYNC_STEPS of a full conduit.
    private static final int FILL_SYNC_STEPS = 32;
    // Thermal conduits tint their glow by temperature in steps this big.
    private static final int HEAT_TINT_STEP = 50;

    // What the conduit's model draws: each side's filter look (see packFilterModes).
    public static final ModelProperty<Integer> FILTER_MODES = new ModelProperty<>();
    // The dye colour for the model (ConduitDyeModel picks the _dyed pieces): 0 undyed, else the dye id + 1.
    public static final ModelProperty<Integer> COLOR = new ModelProperty<>();
    // The Conduit Facades on its sides (see ConduitFacadeModel).
    public static final ModelProperty<ConduitFacades> FACADES = new ModelProperty<>();

    // A Conduit Cover as saved before facades: the clear pane (no look) or a copied look. It loads as a facade on every
    // side, blank or dressed in the look.
    private record OldCover(Optional<BlockState> look) {
        static final Codec<OldCover> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.optionalFieldOf("look").forGetter(OldCover::look))
                .apply(i, OldCover::new));
    }

    private final SideSetting[] settings = new SideSetting[Direction.values().length];
    // The plastic sheath's colour, or null: sheathed conduits connect only to their own colour (or to unsheathed ones).
    private @Nullable DyeColor color;
    private ConduitFacades facades = ConduitFacades.NONE;
    // The Conduit Filter on each side (ItemStack.EMPTY if none).
    private final ItemStack[] filters = new ItemStack[Direction.values().length];
    private final List<ItemPacket> packets = new ArrayList<>();
    // Items pulled in with nowhere to go yet; shown resting in the conduit's core.
    private final List<ItemStack> storedItems = new ArrayList<>();
    // Energy (FE) or heat (HU) held by this conduit, for energy and thermal conduits, while it's in no network.
    private int stored;
    // The network pooling it while it's in one, and its place in that network's members (see getStored).
    private @Nullable ActiveConduitNetwork<?> pool;
    private int poolIndex;
    // Thermal conduits: the temperature (°C) of the heat held, so a rebuilt network remembers it.
    private int heatTemperature = HeatBuffer.AMBIENT_CELSIUS;
    private FluidStack fluid = FluidStack.EMPTY;
    // Fluid and Pressurized conduits: the fluid or gas moving through, drawn even when this conduit's share of the tank is
    // empty (a fast network can pass all of it straight through in a tick). EMPTY when idle.
    private Fluid flowingGas = Fluids.EMPTY;
    private float itemSpeed;
    // Queued with the network manager to send contents to clients; syncNow: without waiting for CONTENTS_SYNC_INTERVAL.
    private boolean syncPending;
    private boolean syncNow;
    // Game time is never negative, so the first sync is always allowed. (Long.MIN_VALUE would overflow the subtraction.)
    private long lastSyncTick = -CONTENTS_SYNC_INTERVAL;
    // The fluid and fill step (see fillStep) last sent, so level changes too small to see aren't sent.
    private Fluid syncedFluid = Fluids.EMPTY;
    private int syncedFillStep = -1;
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

    public ConduitTier getTier() {
        return ((ConduitBlock) getBlockState().getBlock()).getTier();
    }

    // The fluid (or gas) this conduit holds when full (see ConduitTier.fluidBuffer).
    public int fluidBuffer() {
        return getConduitType() == ConduitType.GAS ? getTier().gasBuffer() : getTier().fluidBuffer();
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
        return ModelData.builder().with(FILTER_MODES, packFilterModes()).with(COLOR, color == null ? 0 : color.getId() + 1).with(FACADES, facades).build();
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
    public static boolean colorsMatch(@Nullable DyeColor a, @Nullable DyeColor b) {
        return a == null || b == null || a == b;
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

    // Its share of its network's pool, or what it held when it left one.
    public int getStored() {
        return pool != null ? pool.shareOf(poolIndex) : stored;
    }

    public void setStored(int amount) {
        if (amount != stored) {
            stored = amount;
            markUnsaved();
        }
    }

    // Called by the network pooling this conduit's energy or heat (see ActiveConduitNetwork).
    public void joinPool(ActiveConduitNetwork<?> network, int index) {
        pool = network;
        poolIndex = index;
    }

    // The network is taken apart and hands back this conduit's share.
    public void leavePool(ActiveConduitNetwork<?> network, int share) {
        if (pool == network) {
            pool = null;
            setStored(share);
        }
    }

    public int getHeatTemperature() {
        return heatTemperature;
    }

    public void setHeatTemperature(int celsius) {
        if (celsius != heatTemperature) {
            heatTemperature = celsius;
            markUnsaved();
            syncGlow();
        }
    }

    // What the network moves through a conduit only needs saving: conduits give no comparator output, so unlike
    // setChanged this doesn't update neighbours.
    private void markUnsaved() {
        if (level != null) {
            level.blockEntityChanged(worldPosition);
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

    public Fluid getFlowing() {
        return flowingGas;
    }

    public void setFlowing(Fluid moving) {
        if (moving != flowingGas) {
            flowingGas = moving;
            markContentsChanged(true);
        }
    }

    // Records a content change. Items sync at the end of this tick so clients see packets enter and leave;
    // fluid levels change every tick and are throttled, and only sent once the drawn level moves (see fillStep).
    public void markContentsChanged(boolean immediate) {
        markUnsaved();
        if (!getConduitType().showsContents() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        syncNow |= immediate;
        if (!syncPending) {
            syncPending = true;
            ConduitNetworkManager.get(serverLevel).queueSync(this);
        }
    }

    // Called by the network manager at the end of each tick while a sync is queued. Sends the contents to the players
    // tracking this chunk unless it's too soon (see CONTENTS_SYNC_INTERVAL); returns whether it's done.
    public boolean flushSync(long now) {
        if (!(level instanceof ServerLevel serverLevel) || isRemoved()) {
            syncPending = false;
            return true;
        }
        if (!syncNow && now - lastSyncTick < CONTENTS_SYNC_INTERVAL) {
            return false;
        }
        boolean force = syncNow;
        syncPending = false;
        syncNow = false;
        int fillStep = fillStep();
        if (!force && fluid.getFluid() == syncedFluid && fillStep == syncedFillStep) {
            return true;
        }
        syncedFluid = fluid.getFluid();
        syncedFillStep = fillStep;
        lastSyncTick = now;
        // Built only if someone's watching, and sent only to connections that know it (not the game tests' fake ones).
        ConduitContentsPayload payload = null;
        for (ServerPlayer player : serverLevel.getChunkSource().chunkMap.getPlayers(ChunkPos.containing(worldPosition), false)) {
            if (player.connection.hasChannel(ConduitContentsPayload.TYPE)) {
                if (payload == null) {
                    payload = ConduitContentsPayload.of(this);
                    ConduitNetworkManager.get(serverLevel).countSync(worldPosition);
                }
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
        return true;
    }

    // How full the conduit is drawn, in FILL_SYNC_STEPS steps (-1 when empty).
    private int fillStep() {
        if (fluid.isEmpty()) {
            return -1;
        }
        return (int) Math.min(FILL_SYNC_STEPS, (long) fluid.getAmount() * FILL_SYNC_STEPS / fluidBuffer());
    }

    // Client: the contents the server sent (see ConduitContentsPayload).
    public void applyContents(FluidStack fluid, Fluid flowingGas, List<ItemPacket> packets, List<ItemStack> storedItems, float itemSpeed) {
        this.fluid = fluid;
        this.flowingGas = flowingGas;
        this.packets.clear();
        this.packets.addAll(packets);
        this.storedItems.clear();
        this.storedItems.addAll(storedItems);
        this.itemSpeed = itemSpeed;
        syncGlow();
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
        // Facades drop blank, each with the block it was dressed in beside it.
        for (Direction side : Direction.values()) {
            facades.drops(side).forEach(drop -> Block.popResource(level, pos, drop));
        }
        facades = ConduitFacades.NONE;
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

    // --- Conduit Facades ---

    public ConduitFacades getFacades() {
        return facades;
    }

    public void setFacade(Direction side, ConduitFacades.@Nullable Facade facade) {
        setFacades(facades.with(side, facade));
    }

    // Mounts, dresses or takes off facades. Only the model and the shape change: the conduit keeps its connections and
    // network.
    public void setFacades(ConduitFacades facades) {
        if (facades.equals(this.facades)) {
            return;
        }
        this.facades = facades;
        setChanged();
        if (level != null) {
            requestModelDataUpdate();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
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
        flowingGas = input.read("flowing_gas", BuiltInRegistries.FLUID.byNameCodec()).orElse(Fluids.EMPTY);
        itemSpeed = input.getFloatOr("item_speed", 0.0F);
        int filterModesBefore = packFilterModes();
        ConduitFacades facadesBefore = facades;
        facades = input.read("facades", ConduitFacades.CODEC)
                .or(() -> input.read("cover", OldCover.CODEC).map(old -> ConduitFacades.all(new ConduitFacades.Facade(old.look().orElse(null)))))
                .orElse(ConduitFacades.NONE);
        Arrays.fill(filters, ItemStack.EMPTY);
        input.listOrEmpty("filters", InstalledFilter.CODEC).forEach(installed -> {
            if (installed.side() >= 0 && installed.side() < filters.length) {
                filters[installed.side()] = installed.stack();
            }
        });
        if (level != null && level.isClientSide()) {
            syncGlow();
            // The sleeves on the arms and the facades are part of the block model: redraw when their looks change.
            if (packFilterModes() != filterModesBefore || !facades.equals(facadesBefore)) {
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
        writeShared(output);
        int share = getStored();
        if (share > 0) {
            output.putInt("stored", share);
        }
        if (pool != null) {
            pool.onMemberSaved();
        }
    }

    // What both the save and clients get: everything but the energy/heat share, which no client draws.
    private void writeShared(ValueOutput output) {
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
        if (!fluid.isEmpty()) {
            output.store("fluid", FluidStack.OPTIONAL_CODEC, fluid);
        }
        if (!facades.isEmpty()) {
            output.store("facades", ConduitFacades.CODEC, facades);
        }
        if (flowingGas != Fluids.EMPTY) {
            output.store("flowing_gas", BuiltInRegistries.FLUID.byNameCodec(), flowingGas);
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

    // Sent when the chunk loads and on full updates (connections, sheath, filters, facades, glow); contents changes
    // go by ConduitContentsPayload instead.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), Arcforge.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            writeShared(output);
            return output.buildResult();
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
