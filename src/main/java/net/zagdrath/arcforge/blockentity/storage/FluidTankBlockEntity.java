/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.storage;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.storage.FluidTankBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.storage.FluidTankMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// One tank of any fluid. The GUI's bucket slot empties filled containers into the tank (or fills empty
// ones from it) and moves the result to the slot below. The fluid is synced to clients for the block
// entity renderer, and the tank lights up with the fluid's light level.
public class FluidTankBlockEntity extends StorageBlockEntity implements FluidInteractable {
    private static final int SYNC_INTERVAL = 5;

    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> inputView;
    private final ResourceHandler<FluidResource> outputView;
    private final ResourceHandler<FluidResource> automationView;
    private final ContainerData data;

    private boolean syncPending;
    private long lastSyncTick = -SYNC_INTERVAL;
    private int comparatorSignal = -1;

    public FluidTankBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: fill from every side, drain from the bottom.
        super(ModBlockEntityTypes.FLUID_TANK.get(), pos, state, ((FluidTankBlock) state.getBlock()).getTier(),
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT));
        this.tank = new FilteredFluidTank(tier.tankCapacity(), resource -> true, this::onTankChanged);
        this.inputView = new SidedResourceHandler<>(tank, true, false, tier.tankRate());
        this.outputView = new SidedResourceHandler<>(tank, false, true, tier.tankRate());
        this.automationView = new SidedResourceHandler<>(tank, true, true, tier.tankRate());
        this.data = new WideIntContainerData(FluidTankMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case FluidTankMenu.DATA_FLUID -> tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
                    case FluidTankMenu.DATA_AMOUNT -> tank.getAmount();
                    case FluidTankMenu.DATA_CAPACITY -> tank.getCapacity();
                    case FluidTankMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    @Override
    protected boolean isItemValid(int slot, ItemResource resource) {
        return slot == SLOT_IN && ItemAccess.forStack(resource.toStack(1)).getCapability(Capabilities.Fluid.ITEM) != null;
    }

    @Override
    protected ConduitType conduitType() {
        return ConduitType.LIQUID;
    }

    private void onTankChanged() {
        setChanged();
        syncPending = true;
    }

    public FluidStack getFluid() {
        return tank.getResource(0).toStack(tank.getAmount());
    }

    public int getCapacity() {
        return tank.getCapacity();
    }

    public int getComparatorSignal() {
        return Mth.floor(15.0 * tank.getAmount() / tank.getCapacity());
    }

    // --- Ticking ---

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, FluidTankBlockEntity tank) {
        tank.processContainer();
        tank.updateState(level, pos, state);
    }

    // Moves fluid between the container in the input slot and the tank, and the used container to the
    // output slot. Filled containers are emptied into the tank; empty ones are filled from it.
    private void processContainer() {
        ItemStack input = items.getStack(SLOT_IN);
        if (input.isEmpty()) {
            return;
        }
        ItemStacksResourceHandler scratch = new ItemStacksResourceHandler(1);
        scratch.set(0, ItemResource.of(input), 1);
        ResourceHandler<FluidResource> container = ItemAccess.forHandlerIndexStrict(scratch, 0).getCapability(Capabilities.Fluid.ITEM);
        if (container == null) {
            return;
        }

        try (Transaction tx = Transaction.openRoot()) {
            int moved = ResourceHandlerUtil.move(container, tank, resource -> true, Integer.MAX_VALUE, tx);
            if (moved == 0) {
                moved = ResourceHandlerUtil.move(tank, container, resource -> true, Integer.MAX_VALUE, tx);
            }
            if (moved == 0) {
                return;
            }
            ItemStack result = scratch.getResource(0).toStack(scratch.getAmountAsInt(0));
            ItemStack output = items.getStack(SLOT_OUT);
            boolean fits = result.isEmpty() || output.isEmpty()
                    || (ItemStack.isSameItemSameComponents(output, result) && output.getCount() + result.getCount() <= output.getMaxStackSize());
            if (!fits) {
                return;
            }
            tx.commit();

            items.setStack(SLOT_IN, input.copyWithCount(input.getCount() - 1));
            if (!result.isEmpty()) {
                items.setStack(SLOT_OUT, output.isEmpty() ? result : output.copyWithCount(output.getCount() + result.getCount()));
            }
        }
    }

    private void updateState(ServerLevel level, BlockPos pos, BlockState state) {
        if (syncPending && level.getGameTime() - lastSyncTick >= SYNC_INTERVAL) {
            syncPending = false;
            lastSyncTick = level.getGameTime();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }

        FluidStack fluid = getFluid();
        int light = fluid.isEmpty() ? 0 : Mth.clamp(fluid.getFluidType().getLightLevel(fluid), 0, 15);
        if (state.getValue(FluidTankBlock.LIGHT) != light) {
            level.setBlock(pos, state.setValue(FluidTankBlock.LIGHT, light), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        int signal = getComparatorSignal();
        if (signal != comparatorSignal) {
            comparatorSignal = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    // --- Capabilities ---

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
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

    // Buckets clicked on the block fill or drain the tank from any face.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return tank;
    }

    // --- Item form: the fluid travels in a data component ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        FluidStack stack = components.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        if (!stack.isEmpty()) {
            tank.set(0, FluidResource.of(stack), Math.min(stack.getAmount(), tank.getCapacity()));
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

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FluidTankMenu(containerId, inventory, worldPosition, items, data);
    }
}
