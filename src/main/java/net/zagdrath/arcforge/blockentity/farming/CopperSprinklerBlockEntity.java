/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.farming.CopperSprinklerBlock;
import net.zagdrath.arcforge.block.farming.TrellisBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.farming.CropHarvest;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// The Copper Sprinkler: a copper spray head with a small water tank (sprinklerTankCapacity), filled from any side by a
// pipe, tank, conduit or bucket. While it has water it RUNS, using sprinklerWaterPerTick, and every sprinklerInterval
// ticks it goes over the farmland in the square around it (sprinklerRadius, from its own level down to 3 below): each
// is set fully moist, and each crop on one has a sprinklerGrowthChance of an extra growth tick (a random tick, so it
// grows at the crop's own pace, fires the usual growth events and uses Loam nutrients). The spray is client-side
// particles (CopperSprinklerBlock.animateTick) while RUNNING.
public class CopperSprinklerBlockEntity extends BlockEntity implements ConduitConnectable, FluidInteractable {
    public static final FluidResource WATER = FluidResource.of(Fluids.WATER);
    private static final int DEPTH = 3;

    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> input;
    private int timer;

    public CopperSprinklerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.COPPER_SPRINKLER.get(), pos, state);
        this.tank = new FilteredFluidTank(ArcforgeConfig.SPRINKLER_TANK_CAPACITY.getAsInt(), resource -> resource.getFluid() == Fluids.WATER, this::setChanged);
        this.input = new AutomationResourceHandler<>(tank, index -> true, index -> false);
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    public ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        return input;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return input;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return type == ConduitType.FLUID ? ConnectionMode.INPUT : ConnectionMode.NONE;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        int use = ArcforgeConfig.SPRINKLER_WATER_PER_TICK.getAsInt();
        boolean running = tank.getAmount() >= use;
        if (running) {
            try (Transaction tx = Transaction.openRoot()) {
                tank.extract(0, WATER, use, tx);
                tx.commit();
            }
            if (++timer >= ArcforgeConfig.SPRINKLER_INTERVAL.getAsInt()) {
                timer = 0;
                water(level);
            }
        } else {
            timer = 0;
        }
        if (state.getValue(CopperSprinklerBlock.RUNNING) != running) {
            level.setBlock(pos, state.setValue(CopperSprinklerBlock.RUNNING, running), Block.UPDATE_CLIENTS);
        }
    }

    // The farmland it waters.
    public List<BlockPos> farmland(Level level) {
        List<BlockPos> farmland = new ArrayList<>();
        for (BlockPos column : CropHarvest.square(worldPosition, ArcforgeConfig.SPRINKLER_RADIUS.getAsInt())) {
            for (int dy = 0; dy >= -DEPTH; dy--) {
                BlockPos pos = column.above(dy);
                if (level.getBlockState(pos).getBlock() instanceof FarmlandBlock) {
                    farmland.add(pos);
                    break;
                }
            }
        }
        return farmland;
    }

    // One pass: moistens the farmland and gives the crops on it their chance of a growth tick. Returns how many
    // farmland blocks it watered.
    public int water(ServerLevel level) {
        double growthChance = ArcforgeConfig.SPRINKLER_GROWTH_CHANCE.getAsDouble();
        List<BlockPos> farmland = farmland(level);
        for (BlockPos soilPos : farmland) {
            BlockState soil = level.getBlockState(soilPos);
            if (soil.getValue(FarmlandBlock.MOISTURE) < FarmlandBlock.MAX_MOISTURE) {
                level.setBlock(soilPos, soil.setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
            }
            BlockPos cropPos = soilPos.above();
            BlockState crop = level.getBlockState(cropPos);
            if (isGrowing(crop) && crop.isRandomlyTicking() && level.getRandom().nextDouble() < growthChance) {
                crop.randomTick(level, cropPos, level.getRandom());
            }
        }
        return farmland.size();
    }

    private static boolean isGrowing(BlockState state) {
        return state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock || state.getBlock() instanceof TrellisBlock;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("tank"));
        timer = input.getIntOr("timer", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
        output.putInt("timer", timer);
    }
}
