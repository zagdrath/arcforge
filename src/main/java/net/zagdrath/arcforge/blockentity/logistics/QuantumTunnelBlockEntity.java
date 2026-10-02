/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.logistics;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.block.conduit.ConduitBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitTier;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineOutputs;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.logistics.QuantumTunnelMenu;
import net.zagdrath.arcforge.quantum.Frequency;
import net.zagdrath.arcforge.quantum.QuantumFaces;
import net.zagdrath.arcforge.quantum.QuantumFrequencies;
import net.zagdrath.arcforge.quantum.QuantumResource;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModCapabilities;
import net.zagdrath.arcforge.security.Owned;
import net.zagdrath.arcforge.security.Ownership;

// A Quantum Tunnel: a window onto one frequency (see Frequency), shared with every other tunnel on it, in any
// dimension. Each face is set, for each resource, to NONE, INPUT (FE, heat, liquid, gas or items put into the face go
// into the frequency) or OUTPUT (the face gives them out, and pushes them into the block it touches every tick, at an
// Arcforged conduit's rate). The tunnel acts for its owner: on a private frequency its owner must be the frequency's
// owner or trusted by them, or the tunnel does nothing.
public class QuantumTunnelBlockEntity extends BlockEntity implements Owned, MenuProvider, ConduitConnectable {
    private static final int ITEMS_PER_TICK = 64;

    private final Ownership ownership = new Ownership(this::setChanged);
    private Frequency.@Nullable Key key;
    private long faces;
    private final EnergyHandler[] energyViews = new EnergyHandler[6];
    private final HeatHandler[] heatViews = new HeatHandler[6];
    @SuppressWarnings("unchecked")
    private final ResourceHandler<FluidResource>[] fluidViews = new ResourceHandler[6];
    @SuppressWarnings("unchecked")
    private final ResourceHandler<ItemResource>[] itemViews = new ResourceHandler[6];
    @SuppressWarnings("unchecked")
    private final BlockCapabilityCache<?, @Nullable Direction>[][] targets = new BlockCapabilityCache[QuantumResource.values().length][6];
    private final ContainerData data;
    // Client side: whether the tunnel is on a frequency it may use (synced; the core glows brighter).
    private boolean linked;

    public QuantumTunnelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.QUANTUM_TUNNEL.get(), pos, state);
        for (Direction face : Direction.values()) {
            int i = face.get3DDataValue();
            energyViews[i] = new EnergyView(face);
            heatViews[i] = new HeatView(face);
            fluidViews[i] = new FluidView(face);
            itemViews[i] = new ItemView(face);
        }
        this.data = new WideIntContainerData(QuantumTunnelMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                Frequency frequency = frequency();
                if (index == QuantumTunnelMenu.DATA_FACES_LOW) {
                    return QuantumFaces.low(faces);
                }
                if (index == QuantumTunnelMenu.DATA_FACES_HIGH) {
                    return QuantumFaces.high(faces);
                }
                if (frequency == null) {
                    return index == QuantumTunnelMenu.DATA_FLUID_ID || index == QuantumTunnelMenu.DATA_GAS_ID ? -1 : 0;
                }
                return switch (index) {
                    case QuantumTunnelMenu.DATA_LINKED -> 1;
                    case QuantumTunnelMenu.DATA_ENERGY -> frequency.energy().getAmountAsInt();
                    case QuantumTunnelMenu.DATA_ENERGY_CAPACITY -> frequency.energy().getCapacityAsInt();
                    case QuantumTunnelMenu.DATA_HEAT -> frequency.heat().getStored();
                    case QuantumTunnelMenu.DATA_HEAT_CAPACITY -> frequency.heat().getCapacity();
                    case QuantumTunnelMenu.DATA_TEMPERATURE -> frequency.heat().getTemperature();
                    case QuantumTunnelMenu.DATA_FLUID -> frequency.fluid().getAmount();
                    case QuantumTunnelMenu.DATA_FLUID_CAPACITY -> frequency.fluid().getCapacity();
                    case QuantumTunnelMenu.DATA_FLUID_ID -> fluidId(frequency.fluid().getResource(0), frequency.fluid().getAmount());
                    case QuantumTunnelMenu.DATA_GAS -> frequency.gas().getAmount();
                    case QuantumTunnelMenu.DATA_GAS_CAPACITY -> frequency.gas().getCapacity();
                    case QuantumTunnelMenu.DATA_GAS_ID -> fluidId(frequency.gas().getResource(0), frequency.gas().getAmount());
                    case QuantumTunnelMenu.DATA_ITEMS -> usedSlots(frequency);
                    case QuantumTunnelMenu.DATA_ITEM_SLOTS -> frequency.items().size();
                    default -> 0;
                };
            }
        };
    }

    private static int fluidId(FluidResource resource, int amount) {
        return amount > 0 ? BuiltInRegistries.FLUID.getId(resource.getFluid()) : -1;
    }

    private static int usedSlots(Frequency frequency) {
        int used = 0;
        for (int slot = 0; slot < frequency.items().size(); slot++) {
            if (frequency.items().getAmountAsInt(slot) > 0) {
                used++;
            }
        }
        return used;
    }

    // --- Ownership ---

    @Override
    public Ownership ownership() {
        return ownership;
    }

    // --- The frequency ---

    public Optional<Frequency.Key> getKey() {
        return Optional.ofNullable(key);
    }

    // The frequency, if it still exists and the tunnel's owner may use it. Server side only.
    public @Nullable Frequency frequency() {
        if (key == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        MinecraftServer server = serverLevel.getServer();
        Frequency frequency = QuantumFrequencies.get(server).get(key);
        if (frequency == null) {
            return null;
        }
        return owner() == null || frequency.canUse(owner(), server) ? frequency : null;
    }

    public void setKey(Frequency.@Nullable Key key) {
        this.key = key;
        changed();
    }

    public boolean isLinked() {
        return level != null && level.isClientSide() ? linked : frequency() != null;
    }

    // --- Faces ---

    public long getFaces() {
        return faces;
    }

    public int mode(Direction face, QuantumResource resource) {
        return QuantumFaces.get(faces, face, resource);
    }

    public void setMode(Direction face, QuantumResource resource, int mode) {
        faces = QuantumFaces.with(faces, face, resource, mode);
        changed();
        if (level != null && !level.isClientSide()) {
            level.invalidateCapabilities(worldPosition);
            ConduitBlock.refreshAround(level, worldPosition);
        }
    }

    public void cycleMode(Direction face, QuantumResource resource) {
        setMode(face, resource, QuantumFaces.next(mode(face, resource)));
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return switch (mode(side, QuantumResource.of(type))) {
            case QuantumFaces.INPUT -> ConnectionMode.INPUT;
            case QuantumFaces.OUTPUT -> ConnectionMode.OUTPUT;
            default -> ConnectionMode.NONE;
        };
    }

    // --- Ticking: output faces push into what they touch ---

    private boolean wasLinked;

    public void serverTick(ServerLevel level, BlockPos pos) {
        Frequency frequency = frequency();
        boolean nowLinked = frequency != null;
        if (nowLinked != wasLinked) {
            wasLinked = nowLinked;
            level.sendBlockUpdated(pos, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        if (frequency == null || faces == 0) {
            return;
        }
        for (Direction face : Direction.values()) {
            if (!QuantumFaces.any(faces, face, QuantumFaces.OUTPUT)) {
                continue;
            }
            // Never back into another tunnel on the same frequency.
            if (level.getBlockEntity(pos.relative(face)) instanceof QuantumTunnelBlockEntity other && key != null && key.equals(other.key)) {
                continue;
            }
            if (mode(face, QuantumResource.ENERGY) == QuantumFaces.OUTPUT) {
                EnergyHandler target = target(level, pos, face, QuantumResource.ENERGY, Capabilities.Energy.BLOCK);
                if (target != null) {
                    EnergyHandlerUtil.move(frequency.energy(), target, ConduitTier.ARCFORGED.energyPerTick(), null);
                }
            }
            if (mode(face, QuantumResource.HEAT) == QuantumFaces.OUTPUT) {
                HeatHandler target = target(level, pos, face, QuantumResource.HEAT, ModCapabilities.HEAT);
                if (target != null) {
                    MachineOutputs.moveHeat(frequency.heat(), target, ConduitTier.ARCFORGED.heatPerTick());
                }
            }
            if (mode(face, QuantumResource.FLUID) == QuantumFaces.OUTPUT || mode(face, QuantumResource.GAS) == QuantumFaces.OUTPUT) {
                ResourceHandler<FluidResource> target = target(level, pos, face, QuantumResource.FLUID, Capabilities.Fluid.BLOCK);
                if (target != null) {
                    if (mode(face, QuantumResource.FLUID) == QuantumFaces.OUTPUT) {
                        ResourceHandlerUtil.move(frequency.fluid(), target, resource -> true, ConduitTier.ARCFORGED.fluidPerTick(), null);
                    }
                    if (mode(face, QuantumResource.GAS) == QuantumFaces.OUTPUT) {
                        ResourceHandlerUtil.move(frequency.gas(), target, resource -> true, ConduitTier.ARCFORGED.gasPerTick(), null);
                    }
                }
            }
            if (mode(face, QuantumResource.ITEMS) == QuantumFaces.OUTPUT) {
                ResourceHandler<ItemResource> target = target(level, pos, face, QuantumResource.ITEMS, Capabilities.Item.BLOCK);
                if (target != null) {
                    ResourceHandlerUtil.move(frequency.items(), target, resource -> true, ITEMS_PER_TICK, null);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <H> @Nullable H target(ServerLevel level, BlockPos pos, Direction face, QuantumResource resource, BlockCapability<H, @Nullable Direction> capability) {
        int i = face.get3DDataValue();
        if (targets[resource.ordinal()][i] == null) {
            targets[resource.ordinal()][i] = BlockCapabilityCache.create(capability, level, pos.relative(face), face.getOpposite());
        }
        return ((BlockCapabilityCache<H, @Nullable Direction>) targets[resource.ordinal()][i]).getCapability();
    }

    // --- Capabilities: a face set to NONE for a resource doesn't offer it ---

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        return side != null && mode(side, QuantumResource.ENERGY) != QuantumFaces.NONE ? energyViews[side.get3DDataValue()] : null;
    }

    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        return side != null && mode(side, QuantumResource.HEAT) != QuantumFaces.NONE ? heatViews[side.get3DDataValue()] : null;
    }

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        return side != null && (mode(side, QuantumResource.FLUID) != QuantumFaces.NONE || mode(side, QuantumResource.GAS) != QuantumFaces.NONE)
                ? fluidViews[side.get3DDataValue()] : null;
    }

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return side != null && mode(side, QuantumResource.ITEMS) != QuantumFaces.NONE ? itemViews[side.get3DDataValue()] : null;
    }

    private final class EnergyView implements EnergyHandler {
        private final Direction face;

        EnergyView(Direction face) {
            this.face = face;
        }

        @Override
        public long getAmountAsLong() {
            Frequency frequency = frequency();
            return frequency != null ? frequency.energy().getAmountAsLong() : 0;
        }

        @Override
        public long getCapacityAsLong() {
            Frequency frequency = frequency();
            return frequency != null ? frequency.energy().getCapacityAsLong() : 0;
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            Frequency frequency = frequency();
            return frequency != null && mode(face, QuantumResource.ENERGY) == QuantumFaces.INPUT ? frequency.energy().insert(amount, transaction) : 0;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            Frequency frequency = frequency();
            return frequency != null && mode(face, QuantumResource.ENERGY) == QuantumFaces.OUTPUT ? frequency.energy().extract(amount, transaction) : 0;
        }
    }

    private final class HeatView implements HeatHandler {
        private final Direction face;

        HeatView(Direction face) {
            this.face = face;
        }

        @Override
        public int getHeat() {
            Frequency frequency = frequency();
            return frequency != null ? frequency.heat().getStored() : 0;
        }

        @Override
        public int getMaxHeat() {
            Frequency frequency = frequency();
            return frequency != null ? frequency.heat().getCapacity() : 0;
        }

        @Override
        public int getTemperature() {
            Frequency frequency = frequency();
            return frequency != null ? frequency.heat().getTemperature() : HeatBuffer.AMBIENT_CELSIUS;
        }

        @Override
        public int receiveHeat(int amount, boolean simulate) {
            Frequency frequency = frequency();
            return frequency != null && mode(face, QuantumResource.HEAT) == QuantumFaces.INPUT
                    ? frequency.heat().input(Integer.MAX_VALUE).receiveHeat(amount, simulate) : 0;
        }

        @Override
        public int extractHeat(int amount, boolean simulate) {
            Frequency frequency = frequency();
            return frequency != null && mode(face, QuantumResource.HEAT) == QuantumFaces.OUTPUT
                    ? frequency.heat().output().extractHeat(amount, simulate) : 0;
        }
    }

    // Two slots: the frequency's liquid tank (index 0, the face's FLUID mode) and its gas tank (index 1, its GAS mode).
    private final class FluidView implements ResourceHandler<FluidResource> {
        private final Direction face;

        FluidView(Direction face) {
            this.face = face;
        }

        private @Nullable ResourceHandler<FluidResource> tank(int index, int mode) {
            Frequency frequency = frequency();
            if (frequency == null || index < 0 || index > 1) {
                return null;
            }
            QuantumResource resource = index == 0 ? QuantumResource.FLUID : QuantumResource.GAS;
            if (mode >= 0 && mode(face, resource) != mode) {
                return null;
            }
            return index == 0 ? frequency.fluid() : frequency.gas();
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public FluidResource getResource(int index) {
            ResourceHandler<FluidResource> tank = tank(index, -1);
            return tank != null ? tank.getResource(0) : FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            ResourceHandler<FluidResource> tank = tank(index, -1);
            return tank != null ? tank.getAmountAsLong(0) : 0;
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            ResourceHandler<FluidResource> tank = tank(index, -1);
            return tank != null ? tank.getCapacityAsLong(0, resource) : 0;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            ResourceHandler<FluidResource> tank = tank(index, -1);
            return tank != null && tank.isValid(0, resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            ResourceHandler<FluidResource> tank = tank(index, QuantumFaces.INPUT);
            return tank != null ? tank.insert(0, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            ResourceHandler<FluidResource> tank = tank(index, QuantumFaces.OUTPUT);
            return tank != null ? tank.extract(0, resource, amount, transaction) : 0;
        }
    }

    private final class ItemView implements ResourceHandler<ItemResource> {
        private final Direction face;

        ItemView(Direction face) {
            this.face = face;
        }

        private @Nullable ResourceHandler<ItemResource> items(int mode) {
            Frequency frequency = frequency();
            return frequency != null && (mode < 0 || mode(face, QuantumResource.ITEMS) == mode) ? frequency.items() : null;
        }

        @Override
        public int size() {
            ResourceHandler<ItemResource> items = items(-1);
            return items != null ? items.size() : 0;
        }

        @Override
        public ItemResource getResource(int index) {
            ResourceHandler<ItemResource> items = items(-1);
            return items != null && index < items.size() ? items.getResource(index) : ItemResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            ResourceHandler<ItemResource> items = items(-1);
            return items != null && index < items.size() ? items.getAmountAsLong(index) : 0;
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            ResourceHandler<ItemResource> items = items(-1);
            return items != null && index < items.size() ? items.getCapacityAsLong(index, resource) : 0;
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            ResourceHandler<ItemResource> items = items(-1);
            return items != null && index < items.size() && items.isValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            ResourceHandler<ItemResource> items = items(QuantumFaces.INPUT);
            return items != null && index < items.size() ? items.insert(index, resource, amount, transaction) : 0;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            ResourceHandler<ItemResource> items = items(QuantumFaces.OUTPUT);
            return items != null && index < items.size() ? items.extract(index, resource, amount, transaction) : 0;
        }
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ownership.load(input);
        key = input.read("frequency", Frequency.Key.CODEC).orElse(null);
        faces = input.getLongOr("faces", 0L);
        linked = input.getBooleanOr("linked", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ownership.save(output);
        if (key != null) {
            output.store("frequency", Frequency.Key.CODEC, key);
        }
        output.putLong("faces", faces);
    }

    // Clients get the faces (for the rings) and whether it's linked (for the core).
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("faces", faces);
        tag.putBoolean("linked", frequency() != null);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new QuantumTunnelMenu(containerId, inventory, worldPosition, data);
    }
}
