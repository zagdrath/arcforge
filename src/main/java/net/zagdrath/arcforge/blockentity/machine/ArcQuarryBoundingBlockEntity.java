/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.machine.ArcQuarryBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;

// One of an Arc Quarry's 26 parts: remembers where its main block is, and hands its outer faces' capabilities and
// conduit connections to it (inner faces, between parts, have none). Removed by anything but a player (an
// explosion, a command), it takes the whole machine with it and the item drops once.
public class ArcQuarryBoundingBlockEntity extends BlockEntity implements ConduitConnectable {
    private BlockPos mainOffset = BlockPos.ZERO;

    public ArcQuarryBoundingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ARC_QUARRY_BOUNDING.get(), pos, state);
    }

    public void setMain(BlockPos main) {
        mainOffset = main.subtract(worldPosition);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    public BlockPos mainPos() {
        return worldPosition.offset(mainOffset);
    }

    public @Nullable ArcQuarryBlockEntity main() {
        return level != null && level.getBlockEntity(mainPos()) instanceof ArcQuarryBlockEntity quarry ? quarry : null;
    }

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        ArcQuarryBlockEntity quarry = main();
        return quarry != null ? quarry.itemHandlerAt(worldPosition, side) : null;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        ArcQuarryBlockEntity quarry = main();
        return quarry != null ? quarry.energyHandlerAt(worldPosition, side) : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        ArcQuarryBlockEntity quarry = main();
        return quarry != null ? quarry.conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        ArcQuarryBlockEntity quarry = main();
        if (level != null && !level.isClientSide() && quarry != null && !quarry.isRemoving()
                && level.getBlockState(mainPos()).getBlock() instanceof ArcQuarryBlock) {
            level.destroyBlock(mainPos(), true);
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

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        mainOffset = input.read("main", BlockPos.CODEC).orElse(BlockPos.ZERO);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("main", BlockPos.CODEC, mainOffset);
    }
}
