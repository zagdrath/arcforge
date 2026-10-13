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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.machine.FlareStackBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// The Flare Stack: any gas pushed into it (through any face but the top: a Pressurized Conduit, a machine's gas output
// or a pump) goes into its tank and burns off, up to flareStack.burnRate mB/t, so a refinery whose gas has nowhere else
// to go never stalls. It burns whatever it's given, fuel or not, and gives nothing back. Its flame (see
// FlareStackRenderer) is as tall as it burns; a small pilot flame always burns at the tip.
public class FlareStackBlockEntity extends BlockEntity implements ConduitConnectable {
    // The flame's size is synced to clients when it moves by this much.
    private static final float FLAME_STEP = 0.05F;

    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> input;
    private int burnt;
    private float flame;
    private float syncedFlame = -1.0F;

    public FlareStackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FLARE_STACK.get(), pos, state);
        this.tank = new FilteredFluidTank(ArcforgeConfig.FLARE_TANK_CAPACITY.getAsInt(), Gases::isGas, this::setChanged);
        this.input = new AutomationResourceHandler<>(tank, index -> true, index -> false);
    }

    public void serverTick(ServerLevel level) {
        burnt = 0;
        if (tank.getAmount() > 0) {
            int rate = ArcforgeConfig.FLARE_RATE.getAsInt();
            try (Transaction tx = Transaction.openRoot()) {
                burnt = tank.extract(0, tank.getResource(0), Math.min(rate, tank.getAmount()), tx);
                tx.commit();
            }
            setChanged();
            if (burnt > 0 && level.getGameTime() % 20 == 0) {
                ArcforgeAdvancements.produced(this, net.minecraft.world.item.ItemStack.EMPTY, null, "flaring");
            }
        }
        // The flame follows what it burns, easing down so a stream that comes in bursts doesn't flicker.
        float target = Math.min(1.0F, burnt / (float) Math.max(1, ArcforgeConfig.FLARE_RATE.getAsInt()) * 4.0F);
        flame = target > flame ? target : Math.max(target, flame - 0.02F);
        BlockState state = getBlockState();
        boolean lit = flame > 0.0F;
        if (state.getValue(FlareStackBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(FlareStackBlock.LIT, lit), Block.UPDATE_ALL);
        }
        if (Math.abs(flame - syncedFlame) >= FLAME_STEP || flame == 0.0F && syncedFlame != 0.0F) {
            syncedFlame = flame;
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // How big the flame is (0 to 1; a quarter of the burn rate is a full flame), as clients see it.
    public float getFlame() {
        return Math.max(0.0F, syncedFlame);
    }

    // mB it burned last tick.
    public int getBurnt() {
        return burnt;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    // Gas goes in through every face but the top.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        return side == Direction.UP ? null : input;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return type == ConduitType.GAS && side != Direction.UP ? ConnectionMode.INPUT : ConnectionMode.NONE;
    }

    // The stack and its flame, so they aren't culled when the base is off screen.
    public AABB getRenderBox() {
        return new AABB(worldPosition).expandTowards(0.0, 6.0, 0.0).inflate(0.5, 0.0, 0.5);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("tank"));
        syncedFlame = input.getFloatOr("flame", 0.0F);
        flame = syncedFlame;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
        output.putFloat("flame", Math.max(0.0F, syncedFlame));
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
