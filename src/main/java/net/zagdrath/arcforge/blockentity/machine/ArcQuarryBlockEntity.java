/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.machine.ArcQuarryBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.ArcforgeFakePlayer;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.MineRules;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.quarry.BlockFilter;
import net.zagdrath.arcforge.machine.quarry.QuarrySettings;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ArcQuarryMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModChunkLoading;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Arc Quarry, run from the centre of its 3x3x3 (see ArcQuarryBlock and ArcQuarryBoundingBlock). It mines a
// (2r+1)² area from maxY down to minY, keeping only the blocks its filter picks (see BlockFilter and MineRules).
// A scan walks the area top-down and keeps the matching positions as its targets; mining then takes them in turn,
// one every ticksPerBlock ticks for energyPerBlock FE (x silkTouchMultiplier with Silk Touch), re-checking each
// against the world first. With Replace on, each mined space is filled from the replace slot. Drops go into its
// 27-slot buffer and out of its Output faces. It pauses, and says why, when it's out of power, full, out of
// replace blocks, or (without chunk loading) waiting for an unloaded chunk.
public class ArcQuarryBlockEntity extends MachineBlockEntity {
    public static final int SLOT_REPLACE = 0;
    public static final int FIRST_BUFFER = 1;
    public static final int BUFFER_SLOTS = 27;
    public static final int MACHINE_SLOTS = FIRST_BUFFER + BUFFER_SLOTS;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);
    // It counts as working (lit, and its loop playing) this long after it last mined a block.
    private static final int LIT_TICKS = 40;
    // The client's scan progress is refreshed this often while scanning.
    private static final int SCAN_SYNC_INTERVAL = 10;
    private static final Codec<long[]> LONGS = Codec.LONG_STREAM.xmap(stream -> stream.toArray(), Arrays::stream);

    public enum State {
        IDLE, SCANNING, MINING, FINISHED, STOPPED;

        public static State byId(int id) {
            State[] values = values();
            return id >= 0 && id < values.length ? values[id] : IDLE;
        }
    }

    private final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private QuarrySettings settings = QuarrySettings.defaults();
    private State quarryState = State.IDLE;
    // The scan: its cursor over the area, whether to mine when it's done, and whether the targets are out of date.
    private long scanCursor;
    private boolean mineAfterScan;
    private boolean stale = true;
    private int unscanned;
    private final LongArrayList targets = new LongArrayList();
    private int targetIndex;
    private int minedCount;
    private Map<Block, Integer> scanCounts = new LinkedHashMap<>();
    private int cooldown;
    private long lastMined = Long.MIN_VALUE / 2;
    private @Nullable BlockPos lastTarget;
    private Block lastReplace = net.minecraft.world.level.block.Blocks.COBBLESTONE;
    private boolean removing;
    private final Set<Long> forcedChunks = new HashSet<>();
    private @Nullable ItemStack silkTool;
    // Client side: what the server last sent for the GUI and the renderer.
    private int clientTargets;
    private int clientScanPercent;

    public ArcQuarryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ARC_QUARRY.get(), pos, state, MACHINE_SLOTS, ArcQuarryBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.OUTPUT, SideMode.OUTPUT, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.QUARRY_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.QUARRY_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_REPLACE, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, ArcQuarryBlockEntity::isBuffer);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_REPLACE, ArcQuarryBlockEntity::isBuffer);
        this.data = new WideIntContainerData(ArcQuarryMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case ArcQuarryMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case ArcQuarryMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case ArcQuarryMenu.DATA_STATE -> quarryState.ordinal();
                    case ArcQuarryMenu.DATA_TARGETS -> targets.size();
                    case ArcQuarryMenu.DATA_MINED -> minedCount;
                    case ArcQuarryMenu.DATA_SCAN_PERCENT -> scanPercent();
                    case ArcQuarryMenu.DATA_STATUS -> status.ordinal();
                    case ArcQuarryMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ArcQuarryMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
        status = MachineStatus.IDLE;
    }

    private static boolean isBuffer(int slot) {
        return slot >= FIRST_BUFFER && slot < MACHINE_SLOTS;
    }

    // The replace slot takes blocks; the buffer takes nothing by hand.
    public static boolean isItemValid(int slot, ItemResource resource) {
        return slot == SLOT_REPLACE && resource.getItem() instanceof BlockItem;
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, ArcQuarryBlockEntity::isItemValid, UPGRADES, () -> {});
    }

    // --- Settings ---

    public QuarrySettings getSettings() {
        return settings;
    }

    // Changes the settings (already clamped; see QuarrySettings). Changing the area or filter stops it and makes the
    // scan out of date.
    public void setSettings(QuarrySettings updated) {
        if (updated.equals(settings)) {
            return;
        }
        boolean areaOrFilter = updated.radius() != settings.radius() || updated.minY() != settings.minY() || updated.maxY() != settings.maxY()
                || !updated.filter().equals(settings.filter()) || updated.deny() != settings.deny();
        settings = updated;
        if (areaOrFilter) {
            stale = true;
            if (quarryState == State.MINING || quarryState == State.SCANNING) {
                quarryState = State.STOPPED;
                releaseChunks();
            }
        }
        changed(true);
    }

    public State getQuarryState() {
        return quarryState;
    }

    public int getMinedCount() {
        return minedCount;
    }

    public int getTargetCount() {
        return level != null && level.isClientSide() ? clientTargets : targets.size();
    }

    public int getTargetIndex() {
        return targetIndex;
    }

    public Map<Block, Integer> getScanCounts() {
        return scanCounts;
    }

    public boolean isStale() {
        return stale;
    }

    public int getUnscanned() {
        return unscanned;
    }

    public @Nullable BlockPos getLastTarget() {
        return lastTarget;
    }

    public Block getLastReplace() {
        return lastReplace;
    }

    public boolean isRemoving() {
        return removing;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public int scanPercent() {
        if (level != null && level.isClientSide()) {
            return clientScanPercent;
        }
        long volume = settings.areaVolume();
        return volume <= 0 ? 100 : (int) Math.min(100, scanCursor * 100 / volume);
    }

    // The status line, with the replace block named when it has run out.
    public Component statusText() {
        return status == MachineStatus.OUT_OF_REPLACE
                ? Component.translatable("gui.arcforge.status.out_of_replace", lastReplace.getName())
                : status.getDescription();
    }

    // The area it mines, as a box of whole blocks (for the outline).
    public AABB area() {
        int r = settings.radius();
        return new AABB(worldPosition.getX() - r, settings.minY(), worldPosition.getZ() - r,
                worldPosition.getX() + r + 1, settings.maxY() + 1, worldPosition.getZ() + r + 1);
    }

    // --- Buttons ---

    // Start, or Stop while it's working.
    public void toggleRunning() {
        if (quarryState == State.MINING || quarryState == State.SCANNING) {
            quarryState = State.STOPPED;
            releaseChunks();
        } else if (!stale && quarryState == State.STOPPED && targetIndex < targets.size()) {
            quarryState = State.MINING;
            aimAtNext();
        } else {
            startScan(true);
        }
        changed(true);
    }

    // Stop and forget the progress, to start again from the top layer.
    public void reset() {
        quarryState = State.IDLE;
        targets.clear();
        targetIndex = 0;
        minedCount = 0;
        scanCursor = 0;
        stale = true;
        lastTarget = null;
        releaseChunks();
        changed(true);
    }

    // Scan without mining (the settings screen's Scan button).
    public void scanOnly() {
        startScan(false);
        changed(true);
    }

    private void startScan(boolean thenMine) {
        quarryState = State.SCANNING;
        mineAfterScan = thenMine;
        scanCursor = 0;
        unscanned = 0;
        targets.clear();
        targetIndex = 0;
        scanCounts = new LinkedHashMap<>();
    }

    // --- Ticking ---

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        switch (quarryState) {
            case SCANNING -> {
                scan(level);
                status = MachineStatus.SCANNING;
            }
            case MINING -> {
                if (!redstoneAllows(level)) {
                    status = MachineStatus.DISABLED;
                } else if (cooldown > 0) {
                    cooldown--;
                } else {
                    mine(level);
                }
            }
            case IDLE -> status = MachineStatus.IDLE;
            case FINISHED -> status = MachineStatus.FINISHED;
            case STOPPED -> status = MachineStatus.STOPPED;
        }
        updateChunks(level);
        setLit(quarryState == State.MINING && level.getGameTime() - lastMined < LIT_TICKS);
        autoEject(level, itemOutput);
    }

    // The position the scan cursor points at: layers from the top, then rows along z, then x.
    private BlockPos scanPos(long index) {
        int side = settings.side();
        long perLayer = (long) side * side;
        int layer = (int) (index / perLayer);
        int inLayer = (int) (index % perLayer);
        return new BlockPos(worldPosition.getX() - settings.radius() + inLayer % side, settings.maxY() - layer,
                worldPosition.getZ() - settings.radius() + inLayer / side);
    }

    private void scan(ServerLevel level) {
        long volume = settings.areaVolume();
        int budget = ArcforgeConfig.QUARRY_SCAN_PER_TICK.getAsInt();
        // Consecutive positions share a chunk (rows run along x), so look each chunk up once and read from it.
        LevelChunk chunk = null;
        long chunkKey = Long.MIN_VALUE;
        while (budget-- > 0 && scanCursor < volume) {
            BlockPos target = scanPos(scanCursor++);
            if (ArcQuarryBlock.isPart(worldPosition, target)) {
                continue;
            }
            long key = ChunkPos.pack(target.getX() >> 4, target.getZ() >> 4);
            if (key != chunkKey) {
                chunkKey = key;
                chunk = level.getChunkSource().getChunkNow(target.getX() >> 4, target.getZ() >> 4);
            }
            if (chunk == null) {
                unscanned++;
                continue;
            }
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(target.getY()));
            if (section.hasOnlyAir()) {
                continue;
            }
            BlockState state = section.getBlockState(target.getX() & 15, target.getY() & 15, target.getZ() & 15);
            if (isTarget(level, target, state)) {
                targets.add(target.asLong());
                scanCounts.merge(state.getBlock(), 1, Integer::sum);
            }
        }
        if (scanCursor >= volume) {
            stale = false;
            targetIndex = 0;
            quarryState = mineAfterScan ? (targets.isEmpty() ? State.FINISHED : State.MINING) : State.IDLE;
            aimAtNext();
            if (quarryState != State.MINING) {
                releaseChunks();
            }
            changed(true);
        } else if (level.getGameTime() % SCAN_SYNC_INTERVAL == 0) {
            changed(true);
        }
    }

    // The beam (and the outline's layer) points at the block it will mine next, or nothing once it's done.
    private void aimAtNext() {
        lastTarget = quarryState == State.MINING && targetIndex < targets.size() ? BlockPos.of(targets.getLong(targetIndex)) : null;
    }

    // Whether the quarry would mine this block now.
    public boolean isTarget(Level level, BlockPos target, BlockState state) {
        // Cheapest first: air, then the filter, then the rules that look at the world.
        return !state.isAir() && !ArcQuarryBlock.isPart(worldPosition, target) && BlockFilter.matches(settings, state)
                && MineRules.canQuarry(level, target, state);
    }

    // FE per block: Silk Touch multiplies it, Energy upgrades cut it.
    public int energyPerBlock() {
        double silk = settings.silkTouch() ? ArcforgeConfig.QUARRY_SILK_MULTIPLIER.getAsDouble() : 1.0;
        return (int) Math.ceil(ArcforgeConfig.QUARRY_ENERGY_PER_BLOCK.getAsInt() * silk * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public int ticksPerBlock() {
        return Math.max(1, UpgradeType.time(ArcforgeConfig.QUARRY_TICKS_PER_BLOCK.getAsInt(), upgrades(UpgradeType.SPEED)));
    }

    // A netherite pickaxe with Silk Touch, never stored or damaged.
    private ItemStack silkTool(ServerLevel level) {
        if (silkTool == null) {
            silkTool = new ItemStack(Items.NETHERITE_PICKAXE);
            silkTool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        }
        return silkTool;
    }

    private void mine(ServerLevel level) {
        if (targetIndex >= targets.size()) {
            quarryState = State.FINISHED;
            status = MachineStatus.FINISHED;
            releaseChunks();
            changed(true);
            return;
        }
        BlockPos target = BlockPos.of(targets.getLong(targetIndex));
        if (!level.isLoaded(target)) {
            status = MachineStatus.WAITING_CHUNK;
            return;
        }
        BlockState state = level.getBlockState(target);
        // The world may have changed since the scan.
        if (!isTarget(level, target, state)) {
            targetIndex++;
            aimAtNext();
            setChanged();
            return;
        }
        int cost = energyPerBlock();
        if (energy.getAmountAsInt() < cost) {
            status = MachineStatus.NO_POWER;
            return;
        }
        ItemStack replace = items.getStack(SLOT_REPLACE);
        if (settings.replace() && replace.isEmpty()) {
            status = MachineStatus.OUT_OF_REPLACE;
            return;
        }
        ItemStack tool = settings.silkTouch() ? silkTool(level) : BlockBreakerBlockEntity.virtualTool(state);
        FakePlayer player = ArcforgeFakePlayer.at(level, worldPosition, getFacing(), tool.copy());
        if (NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, target, state, player)).isCanceled()) {
            targetIndex++;
            aimAtNext();
            setChanged();
            return;
        }
        List<ItemStack> drops = Block.getDrops(state, level, target, null, player, tool);
        if (!fitsAll(drops)) {
            status = MachineStatus.OUTPUT_FULL;
            return;
        }
        energy.consume(cost);
        state.spawnAfterBreak(level, target, tool, true);
        level.removeBlock(target, false);
        level.levelEvent(2001, target, Block.getId(state));
        if (settings.replace() && replace.getItem() instanceof BlockItem blockItem) {
            lastReplace = blockItem.getBlock();
            level.setBlock(target, blockItem.getBlock().defaultBlockState(), Block.UPDATE_ALL);
            items.setStack(SLOT_REPLACE, replace.copyWithCount(replace.getCount() - 1));
        }
        drops.forEach(this::insert);
        minedCount++;
        targetIndex++;
        aimAtNext();
        lastMined = level.getGameTime();
        cooldown = ticksPerBlock() - 1;
        status = MachineStatus.MINING;
        changed(true);
    }

    // Whether all of these fit in the buffer together.
    private boolean fitsAll(List<ItemStack> drops) {
        ItemStack[] slots = new ItemStack[BUFFER_SLOTS];
        for (int i = 0; i < BUFFER_SLOTS; i++) {
            slots[i] = items.getStack(FIRST_BUFFER + i).copy();
        }
        for (ItemStack drop : drops) {
            int left = drop.getCount();
            for (int i = 0; i < BUFFER_SLOTS && left > 0; i++) {
                if (slots[i].isEmpty()) {
                    slots[i] = drop.copyWithCount(Math.min(left, drop.getMaxStackSize()));
                    left -= slots[i].getCount();
                } else if (ItemStack.isSameItemSameComponents(slots[i], drop)) {
                    int moved = Math.min(left, slots[i].getMaxStackSize() - slots[i].getCount());
                    slots[i].grow(moved);
                    left -= moved;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    private void insert(ItemStack drop) {
        int left = drop.getCount();
        for (int i = FIRST_BUFFER; i < MACHINE_SLOTS && left > 0; i++) {
            ItemStack slot = items.getStack(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, drop)) {
                int moved = Math.min(left, slot.getMaxStackSize() - slot.getCount());
                items.setStack(i, slot.copyWithCount(slot.getCount() + moved));
                left -= moved;
            }
        }
        for (int i = FIRST_BUFFER; i < MACHINE_SLOTS && left > 0; i++) {
            if (items.getStack(i).isEmpty()) {
                int moved = Math.min(left, drop.getMaxStackSize());
                items.setStack(i, drop.copyWithCount(moved));
                left -= moved;
            }
        }
    }

    // Saves, and with sync sends what the client shows (settings, state, the beam's target) to it.
    private void changed(boolean sync) {
        setChanged();
        if (sync && level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- Chunk loading ---

    // While scanning or mining (with chunkLoading on): keeps its own chunk and the one it's working in loaded.
    private void updateChunks(ServerLevel level) {
        if (!ArcforgeConfig.QUARRY_CHUNK_LOADING.getAsBoolean()) {
            releaseChunks();
            return;
        }
        Set<Long> wanted = new HashSet<>();
        if (quarryState == State.SCANNING || quarryState == State.MINING) {
            wanted.add(ChunkPos.pack(worldPosition));
            BlockPos at = quarryState == State.SCANNING ? (scanCursor < settings.areaVolume() ? scanPos(scanCursor) : null)
                    : targetIndex < targets.size() ? BlockPos.of(targets.getLong(targetIndex)) : null;
            if (at != null) {
                wanted.add(ChunkPos.pack(at));
            }
        }
        for (long chunk : new ArrayList<>(forcedChunks)) {
            if (!wanted.contains(chunk)) {
                ModChunkLoading.QUARRY.forceChunk(level, worldPosition, ChunkPos.unpack(chunk).x(), ChunkPos.unpack(chunk).z(), false, true);
                forcedChunks.remove(chunk);
            }
        }
        for (long chunk : wanted) {
            if (forcedChunks.add(chunk)) {
                ModChunkLoading.QUARRY.forceChunk(level, worldPosition, ChunkPos.unpack(chunk).x(), ChunkPos.unpack(chunk).z(), true, true);
            }
        }
    }

    private void releaseChunks() {
        if (level instanceof ServerLevel serverLevel) {
            for (long chunk : forcedChunks) {
                ModChunkLoading.QUARRY.forceChunk(serverLevel, worldPosition, ChunkPos.unpack(chunk).x(), ChunkPos.unpack(chunk).z(), false, true);
            }
        }
        forcedChunks.clear();
    }

    // --- Removal: the whole machine goes, and its parts drop nothing ---

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        removing = true;
        releaseChunks();
        super.preRemoveSideEffects(pos, state);
        if (level != null && !level.isClientSide()) {
            ArcQuarryBlock.removeBoundingBlocks(level, pos);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide()) {
            CLIENT_LOADED.remove(worldPosition);
        }
        super.setRemoved();
    }

    // --- Faces: the whole 3x3x3 acts as one block ---

    // The face of a part that looks out of the machine, as the machine's side; null for faces between parts (and
    // every face of the main block, which is the centre).
    public @Nullable Direction outerSide(BlockPos part, @Nullable Direction side) {
        if (side == null) {
            return null;
        }
        return ArcQuarryBlock.isPart(worldPosition, part.relative(side)) ? null : side;
    }

    public @Nullable ResourceHandler<ItemResource> itemHandlerAt(BlockPos part, @Nullable Direction side) {
        if (side == null) {
            return itemAutomation;
        }
        Direction outer = outerSide(part, side);
        return outer == null ? null : getItemHandler(outer);
    }

    public @Nullable EnergyHandler energyHandlerAt(BlockPos part, @Nullable Direction side) {
        if (side == null) {
            return energy;
        }
        Direction outer = outerSide(part, side);
        return outer == null ? null : getEnergyHandler(outer);
    }

    public ConnectionMode conduitConnectionAt(BlockPos part, Direction side, ConduitType type) {
        Direction outer = outerSide(part, side);
        return outer == null ? ConnectionMode.NONE : getConduitConnection(outer, type);
    }

    // The machine's own faces (by world direction). Input faces feed the replace slot; output faces give the buffer.
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> itemInput;
            case OUTPUT -> itemOutput;
            default -> null;
        };
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case FLUID, GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    // For the pushes out of Output faces: faces of the whole cube, not of this block.
    @Override
    protected void autoEject(ServerLevel level, ResourceHandler<ItemResource> output) {
        if (isAutoEject() && level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            for (Direction direction : Direction.values()) {
                if (sideConfig.get(getFacing(), direction) == SideMode.OUTPUT) {
                    // The middle part on that face of the cube.
                    outputs.pushItemsFrom(level, worldPosition.relative(direction), direction, output);
                }
            }
        }
    }

    // --- Client: the emitter's spin (6° a tick while lit, still otherwise) ---

    private float spin;
    private double spinTime = -1.0;

    public float advanceSpin(double time, boolean lit) {
        if (spinTime >= 0.0 && lit) {
            spin = (float) ((spin + (time - spinTime) * 6.0) % 360.0);
        }
        spinTime = time;
        return spin;
    }

    // --- The client's list of loaded quarries, for the area outline ---

    private static final Set<BlockPos> CLIENT_LOADED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public static Set<BlockPos> clientLoaded() {
        return CLIENT_LOADED;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) {
            CLIENT_LOADED.add(worldPosition.immutable());
        }
    }

    // --- Components: the settings go with the item ---

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(ModDataComponents.QUARRY_SETTINGS.get(), settings);
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        QuarrySettings saved = components.get(ModDataComponents.QUARRY_SETTINGS.get());
        if (saved != null) {
            settings = saved;
            stale = true;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("settings");
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        loadShared(input);
        stale = input.getBooleanOr("stale", true);
        mineAfterScan = input.getBooleanOr("mine_after_scan", false);
        scanCursor = input.getLongOr("scan_cursor", 0L);
        unscanned = input.getIntOr("unscanned", 0);
        targets.clear();
        input.read("targets", LONGS).ifPresent(saved -> targets.addElements(0, saved));
        targetIndex = input.getIntOr("target_index", 0);
        cooldown = input.getIntOr("cooldown", 0);
        // A scan interrupted before the targets were saved starts again.
        if (quarryState == State.MINING && targets.isEmpty() && stale) {
            quarryState = State.STOPPED;
        }
    }

    // What both the save and the client get.
    private void loadShared(ValueInput input) {
        settings = input.read("settings", QuarrySettings.CODEC).orElseGet(QuarrySettings::defaults);
        quarryState = State.byId(input.getIntOr("quarry_state", 0));
        minedCount = input.getIntOr("mined", 0);
        lastTarget = input.read("last_target", BlockPos.CODEC).orElse(null);
        lastReplace = input.read("last_replace", Identifier.CODEC).map(BuiltInRegistries.BLOCK::getValue).orElse(net.minecraft.world.level.block.Blocks.COBBLESTONE);
        scanCounts = new LinkedHashMap<>();
        input.read("scan_counts", Codec.unboundedMap(Identifier.CODEC, Codec.INT)).ifPresent(counts ->
                counts.forEach((id, count) -> scanCounts.put(BuiltInRegistries.BLOCK.getValue(id), count)));
        clientTargets = input.getIntOr("target_count", 0);
        clientScanPercent = input.getIntOr("scan_percent", 0);
        status = MachineStatus.byId(input.getIntOr("status", MachineStatus.IDLE.ordinal()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        saveShared(output);
        output.putBoolean("stale", stale);
        output.putBoolean("mine_after_scan", mineAfterScan);
        output.putLong("scan_cursor", scanCursor);
        output.putInt("unscanned", unscanned);
        output.store("targets", LONGS, targets.toLongArray());
        output.putInt("target_index", targetIndex);
        output.putInt("cooldown", cooldown);
    }

    private void saveShared(ValueOutput output) {
        output.store("settings", QuarrySettings.CODEC, settings);
        output.putInt("quarry_state", quarryState.ordinal());
        output.putInt("mined", minedCount);
        if (lastTarget != null) {
            output.store("last_target", BlockPos.CODEC, lastTarget);
        }
        output.store("last_replace", Identifier.CODEC, BuiltInRegistries.BLOCK.getKey(lastReplace));
        Map<Identifier, Integer> counts = new LinkedHashMap<>();
        scanCounts.forEach((block, count) -> counts.put(BuiltInRegistries.BLOCK.getKey(block), count));
        output.store("scan_counts", Codec.unboundedMap(Identifier.CODEC, Codec.INT), counts);
        output.putInt("target_count", targets.size());
        output.putInt("scan_percent", scanPercent());
        output.putInt("status", status.ordinal());
    }

    // The client gets only what it shows (not the buffer or the target list).
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(), LogUtils.getLogger())) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, registries);
            saveShared(output);
            return output.buildResult();
        }
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        loadShared(input);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection connection, ValueInput input) {
        loadShared(input);
    }

    // The faces are the whole cube's: every part's capabilities and conduits change.
    @Override
    protected void onSideConfigChanged() {
        super.onSideConfigChanged();
        if (level != null) {
            for (BlockPos part : ArcQuarryBlock.positions(worldPosition)) {
                level.invalidateCapabilities(part);
                net.zagdrath.arcforge.block.conduit.ConduitBlock.refreshAround(level, part);
            }
            changed(true);
        }
    }


    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.arc_quarry");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ArcQuarryMenu(containerId, inventory, worldPosition, items, data);
    }
}
