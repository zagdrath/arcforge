/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.FermenterMenu;
import net.zagdrath.arcforge.recipe.FermentingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Ferments crops and water into Ethanol with FE (arcforge:fermenting recipes): a crop and 100 mB of water become
// 20-60 mB of Ethanol over 200 ticks, now and then with some Bone Meal. Crops and water come in through input faces,
// Ethanol and the byproduct leave through output faces; a full tank or byproduct slot pauses it. Speed upgrades make
// it faster (drawing FE just as much faster), Energy upgrades cut the FE per operation.
public class FermenterBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_BYPRODUCT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank water;
    private final FilteredFluidTank ethanol;
    private final ResourceHandler<FluidResource> waterInput;
    private final ResourceHandler<FluidResource> ethanolOutput;
    private final ResourceHandler<FluidResource> fluidInteraction;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;

    public FermenterBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: top input, bottom output, back energy.
        super(ModBlockEntityTypes.FERMENTER.get(), pos, state, MACHINE_SLOTS, FermenterBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.FERMENTER_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.FERMENTER_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.water = new FilteredFluidTank(ArcforgeConfig.FERMENTER_WATER_TANK.getAsInt(), resource -> resource.is(Fluids.WATER), this::setChanged);
        this.ethanol = new FilteredFluidTank(ArcforgeConfig.FERMENTER_ETHANOL_TANK.getAsInt(), resource -> true, this::setChanged);
        this.waterInput = new AutomationResourceHandler<>(water, index -> true, index -> false);
        this.ethanolOutput = new AutomationResourceHandler<>(ethanol, index -> false, index -> true);
        this.fluidInteraction = new CombinedResourceHandler<>(ethanolOutput, waterInput);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_BYPRODUCT);
        this.data = new WideIntContainerData(FermenterMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case FermenterMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case FermenterMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case FermenterMenu.DATA_USAGE -> usage;
                    case FermenterMenu.DATA_PROGRESS -> progress;
                    case FermenterMenu.DATA_TOTAL -> total;
                    case FermenterMenu.DATA_WATER -> water.getAmount();
                    case FermenterMenu.DATA_WATER_CAPACITY -> water.getCapacity();
                    case FermenterMenu.DATA_ETHANOL_FLUID -> ethanol.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(ethanol.getResource(0).getFluid()) : -1;
                    case FermenterMenu.DATA_ETHANOL -> ethanol.getAmount();
                    case FermenterMenu.DATA_ETHANOL_CAPACITY -> ethanol.getCapacity();
                    case FermenterMenu.DATA_STATUS -> status.ordinal();
                    case FermenterMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case FermenterMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // The input slot takes any fermenting ingredient; nothing goes into the byproduct slot. The client passes a null
    // level and checks against the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && MachineRecipes.isFermenterInput(level, resource.toStack(1));
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // FE/t while fermenting: Speed draws it faster, Energy cuts it.
    public int energyPerTick(FermentingRecipe recipe) {
        double base = (double) recipe.totalEnergy() / recipe.time();
        return (int) Math.ceil(base * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public int ticksFor(FermentingRecipe recipe) {
        return UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        ItemStack input = items.getStack(SLOT_INPUT);
        Optional<RecipeHolder<FermentingRecipe>> recipe = input.isEmpty() ? Optional.empty() : MachineRecipes.fermenting(level, input);
        total = recipe.map(holder -> ticksFor(holder.value())).orElse(0);
        if (recipe.isEmpty() && progress != 0) {
            progress = 0;
            setChanged();
        }

        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
        } else if (recipe.isEmpty()) {
            status = MachineStatus.IDLE;
        } else if (water.getAmount() < recipe.get().value().fluidInput().amount()) {
            status = MachineStatus.NO_WATER;
        } else if (!fits(recipe.get().value())) {
            status = MachineStatus.OUTPUT_FULL;
        } else if (!energy.consume(energyPerTick(recipe.get().value()))) {
            status = MachineStatus.NO_POWER;
        } else {
            status = MachineStatus.FERMENTING;
            usage = energyPerTick(recipe.get().value());
            if (++progress >= total) {
                progress = 0;
                ferment(level, recipe.get().value());
            }
            setChanged();
        }
        setLit(status == MachineStatus.FERMENTING);

        outputs.pushFluid(level, pos, getFacing(), sideConfig, ethanolOutput, ArcforgeConfig.MELTER_OUTPUT_RATE.getAsInt(), null);
        autoEject(level, itemOutput);
    }

    // Whether the ethanol and (if it might come) the byproduct fit.
    private boolean fits(FermentingRecipe recipe) {
        FluidStack result = recipe.result().create();
        try (Transaction tx = Transaction.openRoot()) {
            if (ethanol.insert(0, FluidResource.of(result), result.getAmount(), tx) != result.getAmount()) {
                return false;
            }
        }
        if (recipe.byproduct().isEmpty() || recipe.byproductChance() <= 0) {
            return true;
        }
        ItemStack byproduct = recipe.byproduct().get().create();
        ItemStack held = items.getStack(SLOT_BYPRODUCT);
        return held.isEmpty() || (ItemStack.isSameItemSameComponents(held, byproduct) && held.getCount() + byproduct.getCount() <= held.getMaxStackSize());
    }

    private void ferment(ServerLevel level, FermentingRecipe recipe) {
        FluidStack result = recipe.result().create();
        try (Transaction tx = Transaction.openRoot()) {
            water.extract(0, water.getResource(0), recipe.fluidInput().amount(), tx);
            ethanol.insert(0, FluidResource.of(result), result.getAmount(), tx);
            tx.commit();
        }
        ItemStack input = items.getStack(SLOT_INPUT);
        items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
        if (recipe.byproduct().isPresent() && level.getRandom().nextFloat() < recipe.byproductChance()) {
            ItemStack byproduct = recipe.byproduct().get().create();
            ItemStack held = items.getStack(SLOT_BYPRODUCT);
            items.setStack(SLOT_BYPRODUCT, held.isEmpty() ? byproduct : held.copyWithCount(held.getCount() + byproduct.getCount()));
        }
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    public FilteredFluidTank getEthanol() {
        return ethanol;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees everything. ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return new CombinedResourceHandler<>(itemInput, itemOutput);
        }
        return mode == SideMode.INPUT ? itemInput : mode == SideMode.OUTPUT ? itemOutput : null;
    }

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return fluidInteraction;
        }
        return mode == SideMode.INPUT ? waterInput : mode == SideMode.OUTPUT ? ethanolOutput : null;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    // A water bucket fills the water tank; an empty bucket takes Ethanol.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidInteraction;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM, FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        water.deserialize(input.childOrEmpty("water"));
        ethanol.deserialize(input.childOrEmpty("ethanol"));
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        water.serialize(output.child("water"));
        ethanol.serialize(output.child("ethanol"));
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.fermenter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FermenterMenu(containerId, inventory, worldPosition, items, data);
    }
}
