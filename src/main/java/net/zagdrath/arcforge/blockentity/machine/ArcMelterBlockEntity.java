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
import net.zagdrath.arcforge.menu.machine.ArcMelterMenu;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.MeltingRecipe;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Melts rock into lava with FE (arcforge:melting recipes): 50 FE per mB at 100 FE/t, so cobblestone
// (250 mB) takes 125 ticks and 12,500 FE. The lava goes into a 4,000 mB tank and is pushed out of output
// faces every tick (the bottom by default, straight into a Geothermal Plant below); a full tank pauses it.
// Speed upgrades make it faster (drawing FE just as much faster), Energy upgrades cut the FE per mB.
public class ArcMelterBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_INPUT = 0;
    public static final int MACHINE_SLOTS = 1;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> fluidOutput;
    private final ResourceHandler<ItemResource> itemInput;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;
    // What's melting now (for Jade), or empty.
    private ItemStack melting = ItemStack.EMPTY;
    private int meltingAmount;

    public ArcMelterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ARC_MELTER.get(), pos, state, MACHINE_SLOTS, ArcMelterBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.MELTER_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.MELTER_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.tank = new FilteredFluidTank(ArcforgeConfig.MELTER_TANK_CAPACITY.getAsInt(), resource -> true, this::setChanged);
        this.fluidOutput = new AutomationResourceHandler<>(tank, index -> false, index -> true);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.data = new WideIntContainerData(ArcMelterMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case ArcMelterMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case ArcMelterMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case ArcMelterMenu.DATA_USAGE -> usage;
                    case ArcMelterMenu.DATA_PROGRESS -> progress;
                    case ArcMelterMenu.DATA_TOTAL -> total;
                    case ArcMelterMenu.DATA_FLUID -> tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
                    case ArcMelterMenu.DATA_FLUID_AMOUNT -> tank.getAmount();
                    case ArcMelterMenu.DATA_FLUID_CAPACITY -> tank.getCapacity();
                    case ArcMelterMenu.DATA_STATUS -> status.ordinal();
                    case ArcMelterMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ArcMelterMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // The input slot takes anything with a melting recipe. The client passes a null level and checks
    // against the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && MachineRecipes.isMelterInput(level, resource.toStack(1));
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // FE/t while melting: Speed draws it faster, Energy cuts it, so an operation costs base x 0.8^n.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.MELTER_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // Ticks to melt one item of this recipe, after Speed upgrades.
    public int ticksFor(MeltingRecipe recipe) {
        return UpgradeType.time(recipe.baseTicks(), upgrades(UpgradeType.SPEED));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        ItemStack input = items.getStack(SLOT_INPUT);
        Optional<RecipeHolder<MeltingRecipe>> recipe = input.isEmpty() ? Optional.empty() : MachineRecipes.melting(level, input);
        if (recipe.isEmpty()) {
            if (progress != 0 || !melting.isEmpty()) {
                progress = 0;
                setChanged();
            }
            total = 0;
            melting = ItemStack.EMPTY;
            meltingAmount = 0;
        } else {
            MeltingRecipe value = recipe.get().value();
            // Progress carries over between items of the same kind, and starts over for another one.
            if (!melting.isEmpty() && !ItemStack.isSameItem(melting, input)) {
                progress = 0;
            }
            melting = input.copyWithCount(1);
            meltingAmount = value.result().amount();
            total = ticksFor(value);
        }

        if (!canRun(level)) {
            status = stoppedStatus();
        } else if (recipe.isEmpty()) {
            status = MachineStatus.IDLE;
        } else if (!fits(recipe.get().value().result().create())) {
            status = MachineStatus.TANK_FULL;
        } else if (!energy.consume(energyPerTick())) {
            status = MachineStatus.NO_POWER;
        } else {
            status = MachineStatus.MELTING;
            usage = energyPerTick();
            if (++progress >= total) {
                progress = 0;
                melt(recipe.get().value().result().create());
            }
            setChanged();
        }
        setLit(status == MachineStatus.MELTING);

        // The lava's only way out, so this is always on.
        outputs.pushFluid(level, pos, getFacing(), sideConfig, fluidOutput, ArcforgeConfig.MELTER_OUTPUT_RATE.getAsInt(), null);
    }

    // Whether all of this fits in the tank right now.
    private boolean fits(FluidStack result) {
        try (Transaction tx = Transaction.openRoot()) {
            return tank.insert(0, FluidResource.of(result), result.getAmount(), tx) == result.getAmount();
        }
    }

    private void melt(FluidStack result) {
        try (Transaction tx = Transaction.openRoot()) {
            tank.insert(0, FluidResource.of(result), result.getAmount(), tx);
            tx.commit();
        }
        ItemStack input = items.getStack(SLOT_INPUT);
        items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
        controlState.completed(List.of(), List.of(result.copy()), 1, 0);
    }

    public FilteredFluidTank getTank() {
        return tank;
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

    // The item being melted and the mB it makes, or empty and 0.
    public ItemStack getMelting() {
        return melting;
    }

    public int getMeltingAmount() {
        return meltingAmount;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    // Input faces take things to melt; nothing comes back out.
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? itemInput : null;
    }

    // Lava is drawn out of output faces; nothing goes in.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.OUTPUT ? fluidOutput : null;
    }

    // FE goes in on energy faces.
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    // An empty bucket takes lava out from any face.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidOutput;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        tank.deserialize(input.childOrEmpty("tank"));
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        tank.serialize(output.child("tank"));
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.arc_melter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ArcMelterMenu(containerId, inventory, worldPosition, items, data);
    }
}
