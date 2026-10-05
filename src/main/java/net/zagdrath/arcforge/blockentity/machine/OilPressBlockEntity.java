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
import net.zagdrath.arcforge.menu.machine.OilPressMenu;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.OilPressingRecipe;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Presses seeds into Seed Oil and Press Cake with FE (arcforge:oil_pressing recipes): Rapeseeds give the most oil, Flax
// Seeds less, and other seeds a little. Seeds come in through input faces; the oil and the cake leave through output
// faces (the oil pushed out, the cake with auto-eject). A full oil tank or cake slot pauses it. Speed upgrades make it
// faster (drawing FE just as much faster), Energy upgrades cut the FE per seed.
public class OilPressBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_CAKE = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank oil;
    private final ResourceHandler<FluidResource> oilOutput;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;

    public OilPressBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: top input, bottom output, back energy.
        super(ModBlockEntityTypes.OIL_PRESS.get(), pos, state, MACHINE_SLOTS, OilPressBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.OIL_PRESS_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.OIL_PRESS_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.oil = new FilteredFluidTank(ArcforgeConfig.OIL_PRESS_OIL_TANK.getAsInt(), resource -> true, this::setChanged);
        this.oilOutput = new AutomationResourceHandler<>(oil, index -> false, index -> true);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_CAKE);
        this.data = new WideIntContainerData(OilPressMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case OilPressMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case OilPressMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case OilPressMenu.DATA_USAGE -> usage;
                    case OilPressMenu.DATA_PROGRESS -> progress;
                    case OilPressMenu.DATA_TOTAL -> total;
                    case OilPressMenu.DATA_OIL_FLUID -> oil.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(oil.getResource(0).getFluid()) : -1;
                    case OilPressMenu.DATA_OIL -> oil.getAmount();
                    case OilPressMenu.DATA_OIL_CAPACITY -> oil.getCapacity();
                    case OilPressMenu.DATA_STATUS -> status.ordinal();
                    case OilPressMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case OilPressMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // The input slot takes anything with an oil pressing recipe; nothing goes into the cake slot. The client passes a
    // null level and checks against the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && MachineRecipes.isOilPressInput(level, resource.toStack(1));
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // FE/t while pressing: Speed draws it faster, Energy cuts it.
    public int energyPerTick(OilPressingRecipe recipe) {
        double base = (double) recipe.totalEnergy() / recipe.time();
        return (int) Math.ceil(base * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public int ticksFor(OilPressingRecipe recipe) {
        return UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        ItemStack input = items.getStack(SLOT_INPUT);
        Optional<RecipeHolder<OilPressingRecipe>> recipe = input.isEmpty() ? Optional.empty() : MachineRecipes.oilPressing(level, input);
        total = recipe.map(holder -> ticksFor(holder.value())).orElse(0);
        if (recipe.isEmpty() && progress != 0) {
            progress = 0;
            setChanged();
        }

        if (!canRun(level)) {
            status = stoppedStatus();
        } else if (recipe.isEmpty()) {
            status = MachineStatus.IDLE;
        } else if (!fits(recipe.get().value())) {
            status = MachineStatus.OUTPUT_FULL;
        } else if (!energy.consume(energyPerTick(recipe.get().value()))) {
            status = MachineStatus.NO_POWER;
        } else {
            status = MachineStatus.PRESSING;
            usage = energyPerTick(recipe.get().value());
            if (++progress >= total) {
                progress = 0;
                press(level, recipe.get().value());
            }
            setChanged();
        }
        setLit(status == MachineStatus.PRESSING);

        outputs.pushFluid(level, pos, getFacing(), sideConfig, oilOutput, ArcforgeConfig.OIL_PRESS_OUTPUT_RATE.getAsInt(), null);
        autoEject(level, itemOutput);
    }

    // Whether the oil and (if it might come) the cake fit.
    private boolean fits(OilPressingRecipe recipe) {
        FluidStack result = recipe.result().create();
        try (Transaction tx = Transaction.openRoot()) {
            if (oil.insert(0, FluidResource.of(result), result.getAmount(), tx) != result.getAmount()) {
                return false;
            }
        }
        if (recipe.byproduct().isEmpty() || recipe.byproductChance() <= 0) {
            return true;
        }
        ItemStack cake = recipe.byproduct().get().create();
        ItemStack held = items.getStack(SLOT_CAKE);
        return held.isEmpty() || (ItemStack.isSameItemSameComponents(held, cake) && held.getCount() + cake.getCount() <= held.getMaxStackSize());
    }

    private void press(ServerLevel level, OilPressingRecipe recipe) {
        FluidStack result = recipe.result().create();
        try (Transaction tx = Transaction.openRoot()) {
            oil.insert(0, FluidResource.of(result), result.getAmount(), tx);
            tx.commit();
        }
        ItemStack input = items.getStack(SLOT_INPUT);
        items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
        List<ItemStack> made = List.of();
        if (recipe.byproduct().isPresent() && level.getRandom().nextFloat() < recipe.byproductChance()) {
            ItemStack cake = recipe.byproduct().get().create();
            made = List.of(cake.copy());
            ItemStack held = items.getStack(SLOT_CAKE);
            items.setStack(SLOT_CAKE, held.isEmpty() ? cake : held.copyWithCount(held.getCount() + cake.getCount()));
        }
        controlState.completed(made, List.of(result.copy()), 1, 0);
    }

    public FilteredFluidTank getOil() {
        return oil;
    }

    public int getUsage() {
        return usage;
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
        return mode == null || mode == SideMode.OUTPUT ? oilOutput : null;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    // An empty bucket takes Seed Oil.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return oilOutput;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        oil.deserialize(input.childOrEmpty("oil"));
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        oil.serialize(output.child("oil"));
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.oil_press");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new OilPressMenu(containerId, inventory, worldPosition, items, data);
    }
}
