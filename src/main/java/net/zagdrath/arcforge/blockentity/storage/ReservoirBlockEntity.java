/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.storage;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.block.storage.ReservoirBlock;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// One block of a Reservoir: its own 32,000 mB share of the tank it belongs to (see ReservoirGroup). Every face takes and
// gives fluid for the whole group, with no rate limit beyond what's moved; a comparator reads the whole group. The share
// is synced to clients for ReservoirRenderer, and the block lights up with its fluid. Broken (or picked up), the block
// keeps its share on the item; placed again it rejoins whatever group it touches.
public class ReservoirBlockEntity extends BlockEntity implements FluidInteractable {
    public static final int CAPACITY = 32_000;
    private static final int SYNC_INTERVAL = 5;

    private final FilteredFluidTank tank = new FilteredFluidTank(CAPACITY, ReservoirBlockEntity::accepts, this::onTankChanged);
    // The group's tank, seen from this block: resolves the (lazily rebuilt) group on every call.
    private final ResourceHandler<FluidResource> groupView = new GroupView();
    private @Nullable ReservoirGroup group;

    private boolean syncPending;
    private long lastSyncTick = -SYNC_INTERVAL;

    public ReservoirBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.RESERVOIR.get(), pos, state);
    }

    // Any liquid; gases go in Pressurized Cylinders.
    public static boolean accepts(FluidResource resource) {
        return !resource.isEmpty() && !Gases.isGas(resource);
    }

    private void onTankChanged() {
        setChanged();
        syncPending = true;
    }

    // --- This block's own share ---

    FilteredFluidTank tank() {
        return tank;
    }

    public FluidResource ownFluid() {
        return tank.getAmount() > 0 ? tank.getResource(0) : FluidResource.EMPTY;
    }

    public int ownAmount() {
        return tank.getAmount();
    }

    // This block's share as a stack (what the renderer draws and the item keeps).
    public FluidStack getFluid() {
        return tank.getResource(0).toStack(tank.getAmount());
    }

    void setOwn(FluidResource fluid, int amount) {
        if (amount <= 0 || fluid.isEmpty()) {
            if (tank.getAmount() > 0) {
                tank.set(0, FluidResource.EMPTY, 0);
            }
        } else if (tank.getAmount() != amount || !tank.getResource(0).equals(fluid)) {
            tank.set(0, fluid, Math.min(amount, CAPACITY));
        }
    }

    // --- The group ---

    void joinGroup(ReservoirGroup group) {
        if (this.group != null && this.group != group) {
            this.group.invalidate();
        }
        this.group = group;
    }

    // The group this block is in, built if it's stale.
    public ReservoirGroup getGroup() {
        ReservoirGroup current = group;
        if (current == null || !current.isValid()) {
            current = ReservoirGroup.build(level, this);
        }
        return current;
    }

    // The fluid this block's group holds, without building it (for a neighbour deciding whether to join).
    FluidResource groupFluid() {
        ReservoirGroup current = group;
        return current != null && current.isValid() ? current.fluid() : ownFluid();
    }

    public int getComparatorSignal() {
        if (level == null || level.isClientSide()) {
            return Mth.floor(15.0 * tank.getAmount() / CAPACITY);
        }
        return getGroup().comparatorSignal();
    }

    // --- Ticking ---

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, ReservoirBlockEntity reservoir) {
        reservoir.getGroup().tick(level);
        reservoir.updateState(level, pos, state);
    }

    private void updateState(ServerLevel level, BlockPos pos, BlockState state) {
        if (syncPending && level.getGameTime() - lastSyncTick >= SYNC_INTERVAL) {
            syncPending = false;
            lastSyncTick = level.getGameTime();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        FluidStack fluid = getFluid();
        int light = fluid.isEmpty() ? 0 : Mth.clamp(fluid.getFluidType().getLightLevel(fluid), 0, 15);
        if (state.getValue(ReservoirBlock.LIGHT) != light) {
            level.setBlock(pos, state.setValue(ReservoirBlock.LIGHT, light), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    // Removed or unloaded: the rest of the group rebuilds without this block, which keeps its share.
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (group != null) {
            group.invalidate();
            group = null;
        }
    }

    // --- Capabilities ---

    // The whole group's tank, from every face.
    public ResourceHandler<FluidResource> getFluidHandler() {
        return groupView;
    }

    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return groupView;
    }

    private final class GroupView implements ResourceHandler<FluidResource> {
        private ResourceHandler<FluidResource> target() {
            return getGroup().handler();
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public FluidResource getResource(int index) {
            return target().getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            return target().getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            return target().getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            return accepts(resource);
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return isRemoved() ? 0 : target().insert(index, resource, amount, transaction);
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return isRemoved() ? 0 : target().extract(index, resource, amount, transaction);
        }
    }

    // --- Item form: the block's share travels in a data component (see the loot table) ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        FluidStack stack = components.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        if (!stack.isEmpty() && accepts(FluidResource.of(stack))) {
            tank.set(0, FluidResource.of(stack), Math.min(stack.getAmount(), CAPACITY));
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        FluidStack fluid = getFluid();
        if (!fluid.isEmpty()) {
            components.set(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.copyOf(fluid));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("fluid");
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("fluid"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("fluid"));
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
