/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidStackTemplate;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.AirSeparatorMenu;
import net.zagdrath.arcforge.recipe.AirSeparatingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Separates the air round it into Nitrogen and Oxygen with FE (arcforge:air_separating recipes, by dimension: 80 mB of
// Nitrogen and 20 mB of Oxygen every 2 s at 80 FE/t; there's none for the End, so it can't work there). It needs no
// input. The recipe's primary gas goes into its Nitrogen tank and the secondary into its Oxygen tank, and each is pushed
// out of its own faces every tick: Oxygen out of Oxygen faces, Nitrogen out of Gas Output faces. When one tank is full
// the gas that doesn't fit goes back into the air, so it keeps making the other (it's only air); it stops when both are
// full. A second source of Oxygen besides the Electrolyzer, for oxy-fuel and the Arcforge Furnace. Speed upgrades make
// it faster (drawing FE just as much faster); Energy upgrades cut the FE per operation.
public class AirSeparatorBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int MACHINE_SLOTS = 0;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.OXYGEN, SideMode.GAS_OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank nitrogen;
    private final FilteredFluidTank oxygen;
    private final ResourceHandler<FluidResource> nitrogenOutput;
    private final ResourceHandler<FluidResource> oxygenOutput;
    private final ResourceHandler<FluidResource> fluidAll;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;
    // The recipe running now (progress starts over when it changes), or null.
    private @Nullable Identifier current;
    // The recipe for where it stands, for the GUI and Jade (null in the End).
    private @Nullable AirSeparatingRecipe shown;

    public AirSeparatorBlockEntity(BlockPos pos, BlockState state) {
        // Defaults: Oxygen out of the left, Nitrogen out of the right, FE into the back; the top is the air intake.
        super(ModBlockEntityTypes.AIR_SEPARATOR.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.OXYGEN, SideMode.GAS_OUTPUT, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.AIR_SEPARATOR_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.AIR_SEPARATOR_MAX_INPUT.getAsInt(),
                this::setChanged);
        int capacity = ArcforgeConfig.AIR_SEPARATOR_GAS_CAPACITY.getAsInt();
        this.nitrogen = new FilteredFluidTank(capacity, Gases::isGas, this::setChanged);
        this.oxygen = new FilteredFluidTank(capacity, Gases::isGas, this::setChanged);
        this.nitrogenOutput = new AutomationResourceHandler<>(nitrogen, index -> false, index -> true);
        this.oxygenOutput = new AutomationResourceHandler<>(oxygen, index -> false, index -> true);
        this.fluidAll = new CombinedResourceHandler<>(nitrogenOutput, oxygenOutput);
        this.data = new WideIntContainerData(AirSeparatorMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case AirSeparatorMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case AirSeparatorMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case AirSeparatorMenu.DATA_USAGE -> usage;
                    case AirSeparatorMenu.DATA_PROGRESS -> progress;
                    case AirSeparatorMenu.DATA_TOTAL -> total;
                    case AirSeparatorMenu.DATA_PRIMARY_FLUID -> fluidId(nitrogen);
                    case AirSeparatorMenu.DATA_PRIMARY -> nitrogen.getAmount();
                    case AirSeparatorMenu.DATA_SECONDARY_FLUID -> fluidId(oxygen);
                    case AirSeparatorMenu.DATA_SECONDARY -> oxygen.getAmount();
                    case AirSeparatorMenu.DATA_GAS_CAPACITY -> nitrogen.getCapacity();
                    case AirSeparatorMenu.DATA_RECIPE_PRIMARY -> shown != null ? shown.primary().amount() : 0;
                    case AirSeparatorMenu.DATA_RECIPE_SECONDARY -> shown != null ? shown.secondary().map(FluidStackTemplate::amount).orElse(0) : 0;
                    case AirSeparatorMenu.DATA_STATUS -> status.ordinal();
                    case AirSeparatorMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case AirSeparatorMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    private static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    // A client-side copy of the (upgrade) slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> false, UPGRADES, () -> {});
    }

    // FE/t for this recipe: Speed draws it faster, Energy cuts it, so an operation costs base x 0.8^n.
    public int energyPerTick(AirSeparatingRecipe recipe) {
        return (int) Math.ceil(recipe.baseEnergyPerTick() * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public int ticksFor(AirSeparatingRecipe recipe) {
        return UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        RecipeHolder<AirSeparatingRecipe> holder = MachineRecipes.airSeparating(level, level.dimension()).orElse(null);
        Identifier id = holder != null ? holder.id().identifier() : null;
        if (id == null || !id.equals(current)) {
            if (progress != 0) {
                progress = 0;
                setChanged();
            }
            current = id;
        }
        shown = holder != null ? holder.value() : null;
        total = holder != null ? ticksFor(holder.value()) : 0;

        if (!canRun(level)) {
            status = stoppedStatus();
        } else if (holder == null) {
            status = MachineStatus.NO_AIR;
        } else if (!hasRoom(holder.value())) {
            status = MachineStatus.OUTPUT_FULL;
        } else if (!energy.consume(energyPerTick(holder.value()))) {
            status = MachineStatus.NO_POWER;
        } else {
            status = MachineStatus.SEPARATING;
            usage = energyPerTick(holder.value());
            if (++progress >= total) {
                progress = 0;
                finish(level, holder.value());
            }
            setChanged();
        }
        setLit(status == MachineStatus.SEPARATING);

        // The gases' only way out, so this is always on.
        int rate = ArcforgeConfig.AIR_SEPARATOR_OUTPUT_RATE.getAsInt();
        push(level, pos, SideMode.GAS_OUTPUT, nitrogenOutput, rate);
        push(level, pos, SideMode.OXYGEN, oxygenOutput, rate);
    }

    // Moves up to max mB out of the faces in this mode (shared across them).
    private void push(ServerLevel level, BlockPos pos, SideMode mode, ResourceHandler<FluidResource> source, int max) {
        int budget = max;
        for (Direction direction : Direction.values()) {
            if (budget <= 0) {
                return;
            }
            if (sideConfig.get(getFacing(), direction) == mode) {
                budget -= outputs.pushFluid(level, pos, direction, source, budget);
            }
        }
    }

    // Whether any of an operation's gas has somewhere to go: it only stops when neither tank can take any.
    private boolean hasRoom(AirSeparatingRecipe recipe) {
        if (room(nitrogen, recipe.primary().create()) > 0) {
            return true;
        }
        return recipe.secondary().isPresent() && room(oxygen, recipe.secondary().get().create()) > 0;
    }

    private static int room(FilteredFluidTank tank, FluidStack fluid) {
        try (Transaction tx = Transaction.openRoot()) {
            return tank.insert(0, FluidResource.of(fluid), fluid.getAmount(), tx);
        }
    }

    private void finish(ServerLevel level, AirSeparatingRecipe recipe) {
        int vented = 0;
        // What went into the tanks (not back into the air), for the statistics.
        List<FluidStack> kept = new ArrayList<>();
        try (Transaction tx = Transaction.openRoot()) {
            FluidStack primary = recipe.primary().create();
            int primaryKept = nitrogen.insert(0, FluidResource.of(primary), primary.getAmount(), tx);
            vented += primary.getAmount() - primaryKept;
            kept.add(primary.copyWithAmount(primaryKept));
            if (recipe.secondary().isPresent()) {
                FluidStack secondary = recipe.secondary().get().create();
                int secondaryKept = oxygen.insert(0, FluidResource.of(secondary), secondary.getAmount(), tx);
                vented += secondary.getAmount() - secondaryKept;
                kept.add(secondary.copyWithAmount(secondaryKept));
            }
            tx.commit();
        }
        controlState.completed(List.of(), kept, 0, 0);
        ArcforgeAdvancements.produced(this, ItemStack.EMPTY, recipe.primary().create().getFluid(), null);
        recipe.secondary().ifPresent(secondary -> ArcforgeAdvancements.produced(this, ItemStack.EMPTY, secondary.create().getFluid(), null));
        // Whatever didn't fit went back into the air: a puff from the top.
        if (vented > 0) {
            level.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05, worldPosition.getZ() + 0.5,
                    2, 0.15, 0.05, 0.15, 0.01);
        }
    }

    // The primary gas's tank (Nitrogen, for air).
    public FilteredFluidTank getNitrogen() {
        return nitrogen;
    }

    // The secondary gas's tank (Oxygen, for air).
    public FilteredFluidTank getOxygen() {
        return oxygen;
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

    // FE used last tick.
    public int getUsage() {
        return usage;
    }

    // The recipe for where it stands, or null (the End).
    public @Nullable AirSeparatingRecipe getShownRecipe() {
        return shown;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    // Oxygen faces drain the Oxygen; Gas Output faces the Nitrogen. Unsided, both (for Jade).
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return fluidAll;
        }
        return switch (mode) {
            case OXYGEN -> oxygenOutput;
            case GAS_OUTPUT -> nitrogenOutput;
            default -> null;
        };
    }

    // Gas Cartridges fill from the Nitrogen first, then the Oxygen.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidAll;
    }

    // FE goes in on energy faces.
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case GAS -> mode == SideMode.OXYGEN || mode == SideMode.GAS_OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ITEM, FLUID, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        nitrogen.deserialize(input.childOrEmpty("nitrogen"));
        oxygen.deserialize(input.childOrEmpty("oxygen"));
        progress = input.getIntOr("progress", 0);
        current = input.getString("recipe").map(Identifier::tryParse).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        nitrogen.serialize(output.child("nitrogen"));
        oxygen.serialize(output.child("oxygen"));
        output.putInt("progress", progress);
        if (current != null) {
            output.putString("recipe", current.toString());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.air_separator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new AirSeparatorMenu(containerId, inventory, worldPosition, items, data);
    }
}
