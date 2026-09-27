/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.storage;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.storage.PressurizedCylinderBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.storage.PressurizedCylinderMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// One tank of one gas (see Gases), filled and drained by Pressurized Conduits and gas machines. It has no
// bucket slots, since a gas can't go in a bucket, and it keeps its gas when broken (on the dropped item).
// The gauge on the model follows the fill level; auto-eject pushes gas out of output faces while the
// redstone mode allows it.
public class PressurizedCylinderBlockEntity extends StorageBlockEntity {
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> inputView;
    private final ResourceHandler<FluidResource> outputView;
    private final ResourceHandler<FluidResource> automationView;
    private final ContainerData data;
    private final Map<Direction, BlockCapabilityCache<ResourceHandler<FluidResource>, @Nullable Direction>> gasTargets = new EnumMap<>(Direction.class);
    private int comparatorSignal = -1;

    public PressurizedCylinderBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: fill from every side, drain from the bottom.
        super(ModBlockEntityTypes.PRESSURIZED_CYLINDER.get(), pos, state, ((PressurizedCylinderBlock) state.getBlock()).getTier(),
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT));
        this.tank = new FilteredFluidTank(tier.cylinderCapacity(), Gases::isGas, this::setChanged);
        this.inputView = new SidedResourceHandler<>(tank, true, false, tier.cylinderRate());
        this.outputView = new SidedResourceHandler<>(tank, false, true, tier.cylinderRate());
        this.automationView = new SidedResourceHandler<>(tank, true, true, tier.cylinderRate());
        this.data = new WideIntContainerData(PressurizedCylinderMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case PressurizedCylinderMenu.DATA_FLUID -> tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
                    case PressurizedCylinderMenu.DATA_AMOUNT -> tank.getAmount();
                    case PressurizedCylinderMenu.DATA_CAPACITY -> tank.getCapacity();
                    case PressurizedCylinderMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case PressurizedCylinderMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // No GUI slots.
    @Override
    protected boolean isItemValid(int slot, ItemResource resource) {
        return false;
    }

    @Override
    protected ConduitType conduitType() {
        return ConduitType.GAS;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    public FluidStack getGas() {
        return tank.getResource(0).toStack(tank.getAmount());
    }

    @Override
    public int getComparatorSignal() {
        return Mth.floor(15.0 * tank.getAmount() / tank.getCapacity());
    }

    // --- Ticking ---

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, PressurizedCylinderBlockEntity cylinder) {
        if (cylinder.isAutoEject() && cylinder.isRedstoneEnabled()) {
            cylinder.pushGas(level, pos);
        }
        cylinder.updateState(level, pos, state);
    }

    @Override
    public boolean isAutoEject() {
        return sideConfig.isAutoEject();
    }

    @Override
    public void setAutoEject(boolean autoEject) {
        sideConfig.setAutoEject(autoEject);
        setChanged();
    }

    // Pushes gas out of every output face into the block beside it, up to the tier rate in total.
    private void pushGas(ServerLevel level, BlockPos pos) {
        int budget = tier.cylinderRate();
        for (Direction direction : Direction.values()) {
            if (budget <= 0 || tank.getAmount() <= 0) {
                return;
            }
            if (modeFor(direction) != SideMode.OUTPUT) {
                continue;
            }
            ResourceHandler<FluidResource> target = gasTargets
                    .computeIfAbsent(direction, dir -> BlockCapabilityCache.create(Capabilities.Fluid.BLOCK, level, pos.relative(dir), dir.getOpposite()))
                    .getCapability();
            if (target != null) {
                budget -= ResourceHandlerUtil.move(tank, target, resource -> true, budget, null);
            }
        }
    }

    private void updateState(ServerLevel level, BlockPos pos, BlockState state) {
        int gauge = PressurizedCylinderBlock.level(tank.getAmount(), tank.getCapacity());
        if (state.getValue(PressurizedCylinderBlock.LEVEL) != gauge) {
            level.setBlock(pos, state.setValue(PressurizedCylinderBlock.LEVEL, gauge), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
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

    // --- Item form: the gas always travels in a data component (see the loot table) ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        FluidStack stack = components.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        if (!stack.isEmpty() && Gases.isGas(stack)) {
            tank.set(0, FluidResource.of(stack), Math.min(stack.getAmount(), tank.getCapacity()));
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        FluidStack gas = getGas();
        if (!gas.isEmpty()) {
            components.set(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.copyOf(gas));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("gas");
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("gas"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("gas"));
    }

    // --- Menu ---

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new PressurizedCylinderMenu(containerId, inventory, worldPosition, items, data);
    }
}
