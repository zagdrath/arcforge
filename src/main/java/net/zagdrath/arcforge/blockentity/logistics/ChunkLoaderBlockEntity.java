/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.logistics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.zagdrath.arcforge.block.logistics.ChunkLoaderBlock;
import net.zagdrath.arcforge.chunkloading.ChunkLoaderStatus;
import net.zagdrath.arcforge.chunkloading.ChunkLoaders;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.logistics.ChunkLoaderMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModChunkLoading;
import net.zagdrath.arcforge.security.Owned;
import net.zagdrath.arcforge.security.Ownership;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;

// A Chunk Loader keeps the chunks round it loaded with chunk tickets (ModChunkLoading.LOADER) for the player who placed
// it: its own chunk at radius 0, up to 5x5 chunks at radius 2. It stops, and lets them go, when a redstone signal turns
// it off, when its owner would go over logistics.chunkLoader.chunksPerPlayer, while its owner is offline (with
// requireOwnerOnline), when it can't pay energyPerChunk FE per chunk per tick (if that's above 0), and when it's broken.
// Its tickets and owner are kept in ChunkLoaders, so they survive restarts and count toward the owner's limit.
public class ChunkLoaderBlockEntity extends BlockEntity implements Owned, MenuProvider {
    public static final int MAX_RADIUS = 2;

    private final Ownership ownership = new Ownership(this::setChanged);
    private final ConsumerEnergyHandler energy;
    private final ContainerData data;
    private int radius;
    private ChunkLoaderStatus status = ChunkLoaderStatus.NO_OWNER;
    // The chunks it holds tickets for (from ChunkLoaders when it first ticks).
    private @Nullable Set<Long> held;
    private int ownerTotal;

    public ChunkLoaderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CHUNK_LOADER.get(), pos, state);
        int capacity = ArcforgeConfig.CHUNK_LOADER_ENERGY_CAPACITY.getAsInt();
        this.energy = new ConsumerEnergyHandler(capacity, capacity, this::setChanged);
        this.data = new WideIntContainerData(ChunkLoaderMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case ChunkLoaderMenu.DATA_RADIUS -> radius;
                    case ChunkLoaderMenu.DATA_STATUS -> status.ordinal();
                    case ChunkLoaderMenu.DATA_LOADED -> held != null ? held.size() : 0;
                    case ChunkLoaderMenu.DATA_OWNER_TOTAL -> ownerTotal;
                    case ChunkLoaderMenu.DATA_LIMIT -> ArcforgeConfig.CHUNK_LOADER_PLAYER_LIMIT.getAsInt();
                    case ChunkLoaderMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case ChunkLoaderMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case ChunkLoaderMenu.DATA_COST -> energyCost();
                    default -> 0;
                };
            }
        };
    }

    @Override
    public Ownership ownership() {
        return ownership;
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        int clamped = Mth.clamp(radius, 0, MAX_RADIUS);
        if (clamped != this.radius) {
            this.radius = clamped;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    public ChunkLoaderStatus getStatus() {
        return status;
    }

    public int chunkCount() {
        return (2 * radius + 1) * (2 * radius + 1);
    }

    // FE per tick at the current radius (0 when FE isn't required).
    public int energyCost() {
        return ArcforgeConfig.CHUNK_LOADER_ENERGY_PER_CHUNK.getAsInt() * chunkCount();
    }

    // The chunks it would load: (2 radius + 1)^2 round its own.
    public List<Long> area() {
        ChunkPos centre = ChunkPos.unpack(ChunkPos.pack(worldPosition));
        List<Long> chunks = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                chunks.add(ChunkPos.pack(centre.x() + dx, centre.z() + dz));
            }
        }
        return chunks;
    }

    // Client side: the globe's turn, which only advances while the loader is active (so it stops, and starts again, where
    // it was).
    private float globeAngle;
    private float globeTime = -1.0F;

    public float advanceGlobe(float now, boolean active, float degreesPerTick) {
        if (globeTime >= 0.0F && active) {
            globeAngle = (globeAngle + Math.max(0.0F, now - globeTime) * degreesPerTick) % 360.0F;
        }
        globeTime = now;
        return globeAngle;
    }

    public Set<Long> getHeld() {
        return held != null ? Set.copyOf(held) : Set.of();
    }

    // --- Ticking ---

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        ChunkLoaders loaders = ChunkLoaders.get(level.getServer());
        GlobalPos here = GlobalPos.of(level.dimension(), pos);
        if (held == null) {
            ChunkLoaders.Entry entry = loaders.get(here);
            held = new HashSet<>(entry != null ? entry.chunks() : List.of());
        }
        UUID owner = owner();
        List<Long> wanted = List.of();
        if (owner == null) {
            status = ChunkLoaderStatus.NO_OWNER;
        } else if (level.hasNeighborSignal(pos)) {
            status = ChunkLoaderStatus.DISABLED;
        } else if (ArcforgeConfig.CHUNK_LOADER_REQUIRE_ONLINE.getAsBoolean() && level.getServer().getPlayerList().getPlayer(owner) == null) {
            status = ChunkLoaderStatus.OWNER_OFFLINE;
        } else if (loaders.chunksOf(owner, here) + chunkCount() > ArcforgeConfig.CHUNK_LOADER_PLAYER_LIMIT.getAsInt()) {
            status = ChunkLoaderStatus.LIMIT;
        } else if (!energy.consume(energyCost())) {
            status = ChunkLoaderStatus.NO_POWER;
        } else {
            status = ChunkLoaderStatus.ACTIVE;
            wanted = area();
        }
        apply(level, wanted);
        if (owner != null) {
            loaders.put(here, owner, List.copyOf(held));
            ownerTotal = loaders.chunksOf(owner, null);
        }
        boolean active = status == ChunkLoaderStatus.ACTIVE;
        if (state.getValue(ChunkLoaderBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(ChunkLoaderBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    // Takes tickets for the wanted chunks and gives back the rest.
    private void apply(ServerLevel level, List<Long> wanted) {
        if (held == null) {
            held = new HashSet<>();
        }
        for (long chunk : List.copyOf(held)) {
            if (!wanted.contains(chunk)) {
                ModChunkLoading.LOADER.forceChunk(level, worldPosition, ChunkPos.unpack(chunk).x(), ChunkPos.unpack(chunk).z(), false, true);
                held.remove(chunk);
            }
        }
        for (long chunk : wanted) {
            if (held.add(chunk)) {
                ModChunkLoading.LOADER.forceChunk(level, worldPosition, ChunkPos.unpack(chunk).x(), ChunkPos.unpack(chunk).z(), true, true);
            }
        }
    }

    // Broken (not just unloaded): every ticket goes, and the loader leaves the registry.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            if (held == null) {
                ChunkLoaders.Entry entry = ChunkLoaders.get(serverLevel.getServer()).get(GlobalPos.of(serverLevel.dimension(), pos));
                held = new HashSet<>(entry != null ? entry.chunks() : List.of());
            }
            apply(serverLevel, List.of());
            ChunkLoaders.get(serverLevel.getServer()).remove(GlobalPos.of(serverLevel.dimension(), pos));
        }
    }

    // --- Energy (only when FE is required) ---

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        return ArcforgeConfig.CHUNK_LOADER_ENERGY_PER_CHUNK.getAsInt() > 0 ? energy : null;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ownership.load(input);
        energy.deserialize(input.childOrEmpty("energy"));
        radius = Mth.clamp(input.getIntOr("radius", 0), 0, MAX_RADIUS);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ownership.save(output);
        energy.serialize(output.child("energy"));
        output.putInt("radius", radius);
    }

    // Clients get the radius, for the area outline.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("radius", radius);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ChunkLoaderMenu(containerId, inventory, worldPosition, data);
    }
}
