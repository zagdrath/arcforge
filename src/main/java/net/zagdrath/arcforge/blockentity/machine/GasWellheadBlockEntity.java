/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.machine.GasWellheadBlock;
import net.zagdrath.arcforge.block.multiblock.WellHeadBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.machine.MachineOutputs;
import net.zagdrath.arcforge.oil.OilDeposits;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// The Gas Wellhead: on a gas well, it flows Natural Gas out of the deposit's gas cap under the cap's own pressure, with
// no FE: gasWellhead.flowPerTick mB/t from a full cap, slowing in a straight line to minShare of that as it empties,
// and tricklePerSecond mB/s once it's spent, for good (as a Pumpjack's oil). Several on one cap share it. The gas goes
// into its tank and out of its flow line (the face it faces) into whatever takes it there; nothing goes in.
public class GasWellheadBlockEntity extends BlockEntity implements ConduitConnectable {
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> output;
    private final MachineOutputs outputs = new MachineOutputs();
    // mB/t it flowed last tick, the fraction of a mB carried to the next, and whether it sits on a gas well.
    private double rate;
    private double pending;
    private boolean onWell;

    public GasWellheadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.GAS_WELLHEAD.get(), pos, state);
        this.tank = new FilteredFluidTank(ArcforgeConfig.GAS_WELLHEAD_TANK_CAPACITY.getAsInt(), resource -> resource.is(ModFluids.NATURAL_GAS.get()),
                this::setChanged);
        this.output = new AutomationResourceHandler<>(tank, index -> false, index -> true);
    }

    public void serverTick(ServerLevel level) {
        rate = 0.0;
        BlockPos well = worldPosition.below();
        onWell = WellHeadBlock.isGasWell(level.getBlockState(well));
        OilDeposits.Deposit deposit = onWell ? OilDeposits.at(level, well) : null;
        if (deposit != null && tank.getSpace() > 0) {
            flow(level, deposit, well);
        }
        Direction facing = getBlockState().getValue(GasWellheadBlock.FACING);
        if (tank.getAmount() > 0) {
            outputs.pushFluid(level, worldPosition, facing, output, ArcforgeConfig.GAS_WELLHEAD_OUTPUT_RATE.getAsInt());
        }
    }

    private void flow(ServerLevel level, OilDeposits.Deposit deposit, BlockPos well) {
        OilDeposits deposits = OilDeposits.get(level.getServer());
        long full = OilDeposits.gasAmount(deposit, level.getSeed());
        long left = deposits.gasRemaining(deposit, level.getSeed());
        double max = ArcforgeConfig.GAS_WELLHEAD_RATE.getAsInt();
        double trickle = ArcforgeConfig.GAS_WELLHEAD_TRICKLE.getAsInt() / 20.0;
        if (left > 0 && full > 0) {
            double minShare = ArcforgeConfig.GAS_WELLHEAD_MIN_SHARE.getAsDouble();
            rate = Math.max(trickle, max * (minShare + (1.0 - minShare) * left / (double) full));
        } else {
            rate = trickle;
        }
        pending = Math.min(pending + rate, rate + 1.0);
        int whole = (int) Math.min(tank.getSpace(), Math.floor(pending));
        if (whole <= 0) {
            return;
        }
        pending -= whole;
        if (left > 0) {
            long taken = deposits.takeGas(deposit, level.getSeed(), whole);
            whole = (int) Math.max(taken, Math.min(whole, Math.round(trickle)));
        }
        try (Transaction tx = Transaction.openRoot()) {
            tank.insert(0, FluidResource.of(ModFluids.NATURAL_GAS.get()), whole, tx);
            tx.commit();
        }
        if (level.getGameTime() % 20 == 0) {
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.NATURAL_GAS.get(), "gas_well");
        }
        setChanged();
    }

    public double getRate() {
        return rate;
    }

    public boolean isOnWell() {
        return onWell;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    // Gas comes out of the flow line (and an unsided query sees the tank); nothing goes in.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        return side == null || side == getBlockState().getValue(GasWellheadBlock.FACING) ? output : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return type == ConduitType.GAS && side == getBlockState().getValue(GasWellheadBlock.FACING) ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("tank"));
        pending = input.getDoubleOr("pending", 0.0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
        output.putDouble("pending", pending);
    }
}
