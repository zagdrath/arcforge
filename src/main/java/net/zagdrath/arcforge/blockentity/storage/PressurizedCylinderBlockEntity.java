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
import net.minecraft.world.item.ItemStack;
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
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.storage.PressurizedCylinderBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.item.storage.PortableStorageItem;
import net.zagdrath.arcforge.item.tool.JetpackItem;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.storage.PressurizedCylinderMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.CreativeFluidTank;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// One tank of one gas (see Gases), filled and drained by Pressurized Conduits and gas machines. A gas can't
// go in a bucket, so its item slots take Gas Cartridges: the top one is emptied into the cylinder, the
// bottom one filled from it. It keeps its gas when broken (on the dropped item).
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
    // Lined with PVC (a few PVC Sheets used on it): it can hold a corrosive gas (UF6, see Corrosion). The creative
    // cylinder holds anything.
    private boolean lined;

    public PressurizedCylinderBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: fill from every side, drain from the bottom.
        super(ModBlockEntityTypes.PRESSURIZED_CYLINDER.get(), pos, state, ((PressurizedCylinderBlock) state.getBlock()).getTier(),
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT));
        this.tank = creative ? new CreativeFluidTank(tier.cylinderCapacity(), Gases::isGas, this::setChanged)
                : new FilteredFluidTank(tier.cylinderCapacity(), this::holds, this::setChanged);
        this.inputView = new SidedResourceHandler<>(tank, true, false, rate(tier.cylinderRate()));
        this.outputView = new SidedResourceHandler<>(tank, false, true, rate(tier.cylinderRate()));
        this.automationView = new SidedResourceHandler<>(tank, true, true, rate(tier.cylinderRate()));
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

    @Override
    protected boolean isItemValid(int slot, ItemResource resource) {
        return accepts(resource);
    }

    // Gas Cartridges and Jetpacks.
    public static boolean accepts(ItemResource resource) {
        ItemStack stack = resource.toStack(1);
        return PortableStorageItem.is(stack, PortableStorageItem.Kind.GAS_CARTRIDGE) || stack.getItem() instanceof JetpackItem;
    }

    @Override
    protected boolean canFill(ItemStack stack) {
        if (stack.getItem() instanceof JetpackItem jetpack) {
            // Only while the cylinder holds a gas the jetpack takes.
            return JetpackItem.fluid(stack).getAmount() < jetpack.capacity()
                    && (tank.getAmount() == 0 || JetpackItem.accepts(stack, tank.getResource(0).getFluid()));
        }
        return stack.getItem() instanceof PortableStorageItem cartridge && cartridge.amount(stack) < cartridge.capacity();
    }

    @Override
    protected ConduitType conduitType() {
        return ConduitType.GAS;
    }

    // A gas, and if it's corrosive only once lined.
    private boolean holds(FluidResource resource) {
        return Gases.isGas(resource) && (lined || !net.zagdrath.arcforge.nuclear.Corrosion.isCorrosive(resource));
    }

    public boolean isLined() {
        return lined || creative;
    }

    // Lines it with PVC (see PressurizedCylinderBlock).
    public void line() {
        lined = true;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
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
        cylinder.moveGas(SLOT_IN, true);
        cylinder.moveGas(SLOT_OUT, false);
        if (cylinder.isAutoEject() && cylinder.isRedstoneEnabled()) {
            cylinder.pushGas(level, pos);
        }
        cylinder.updateState(level, pos, state);
    }

    // Empties the cartridge in a slot into the cylinder (drain) or fills it from the cylinder, in place; the
    // cartridge's own handler limits the rate.
    private void moveGas(int slot, boolean drain) {
        if (items.getStack(slot).isEmpty()) {
            return;
        }
        ResourceHandler<FluidResource> cartridge = ItemAccess.forHandlerIndexStrict(items, slot).getCapability(Capabilities.Fluid.ITEM);
        if (cartridge != null) {
            if (drain) {
                ResourceHandlerUtil.move(cartridge, tank, resource -> true, Integer.MAX_VALUE, null);
            } else {
                ResourceHandlerUtil.move(tank, cartridge, resource -> true, Integer.MAX_VALUE, null);
            }
        }
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
        int budget = rate(tier.cylinderRate());
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

    @Override
    public boolean clearContents() {
        if (!(tank instanceof CreativeFluidTank creativeTank)) {
            return false;
        }
        creativeTank.clear();
        return true;
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
        lined = components.get(ModDataComponents.PVC_LINED.get()) != null;
        FluidStack stack = components.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY).copy();
        if (!stack.isEmpty() && Gases.isGas(stack)) {
            tank.set(0, FluidResource.of(stack), Math.min(stack.getAmount(), tank.getCapacity()));
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (lined) {
            components.set(ModDataComponents.PVC_LINED.get(), net.minecraft.util.Unit.INSTANCE);
        }
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
        output.discard("pvc_lined");
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        lined = input.getBooleanOr("pvc_lined", false);
        tank.deserialize(input.childOrEmpty("gas"));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("pvc_lined", lined);
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
