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
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
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
import net.zagdrath.arcforge.heat.EnergyBalance;
import net.zagdrath.arcforge.item.tool.MachineSettings;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ElectrolyzerMenu;
import net.zagdrath.arcforge.recipe.ElectrolyzingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

import com.mojang.serialization.Codec;

// Splits water into Hydrogen and Oxygen with FE (arcforge:electrolyzing recipes): 100 mB of water makes 200 mB of
// hydrogen and 100 mB of oxygen for 120,000 FE, at 400 FE/t. The recipe's primary output goes into the hydrogen
// tank and its secondary into the oxygen tank, and both are pushed out of their own faces every tick (they have no
// other way out). Speed upgrades draw the FE faster; Energy upgrades cut the FE per operation, but never below
// EnergyBalance.minEnergyFor, so burning the hydrogen always gives back less than it cost.
public class ElectrolyzerBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int MACHINE_SLOTS = 0;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.HYDROGEN, SideMode.OXYGEN, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank water;
    private final FilteredFluidTank hydrogen;
    private final FilteredFluidTank oxygen;
    private final ResourceHandler<FluidResource> waterInput;
    private final ResourceHandler<FluidResource> hydrogenOutput;
    private final ResourceHandler<FluidResource> oxygenOutput;
    private final ResourceHandler<FluidResource> fluidAll;
    private final ResourceHandler<FluidResource> fluidInteraction;
    private final ContainerData data;

    // FE paid so far into the running operation, and what the whole operation costs.
    private int spent;
    private int cost;
    private int usage;
    // The recipe running now (what's paid starts over when it changes), or null.
    private @Nullable Identifier current;
    // The recipe for what's in the water tank (or for water, while it's empty), for the GUI and Jade.
    private @Nullable ElectrolyzingRecipe shown;
    // Venting (set per gas in the GUI): when that tank can't take an operation's output, the overflow is released so
    // splitting carries on, for when only the other gas is wanted. It still leaves through its faces as usual.
    private boolean ventHydrogen;
    private boolean ventOxygen;

    public ElectrolyzerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ELECTROLYZER.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.NONE, SideMode.HYDROGEN, SideMode.OXYGEN, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.ELECTROLYZER_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.ELECTROLYZER_MAX_INPUT.getAsInt(),
                this::setChanged);
        int gasCapacity = ArcforgeConfig.ELECTROLYZER_GAS_CAPACITY.getAsInt();
        this.water = new FilteredFluidTank(ArcforgeConfig.ELECTROLYZER_WATER_CAPACITY.getAsInt(),
                resource -> MachineRecipes.isElectrolyzerInput(level, resource), this::setChanged);
        this.hydrogen = new FilteredFluidTank(gasCapacity, Gases::isGas, this::setChanged);
        this.oxygen = new FilteredFluidTank(gasCapacity, Gases::isGas, this::setChanged);
        this.waterInput = new AutomationResourceHandler<>(water, index -> true, index -> false);
        this.hydrogenOutput = new AutomationResourceHandler<>(hydrogen, index -> false, index -> true);
        this.oxygenOutput = new AutomationResourceHandler<>(oxygen, index -> false, index -> true);
        this.fluidAll = new CombinedResourceHandler<>(waterInput, hydrogenOutput, oxygenOutput);
        // Buckets pour water in; Gas Cartridges fill from the hydrogen first, then the oxygen.
        this.fluidInteraction = new CombinedResourceHandler<>(hydrogenOutput, oxygenOutput, waterInput);
        this.data = new WideIntContainerData(ElectrolyzerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case ElectrolyzerMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case ElectrolyzerMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case ElectrolyzerMenu.DATA_USAGE -> usage;
                    case ElectrolyzerMenu.DATA_PROGRESS -> spent;
                    case ElectrolyzerMenu.DATA_TOTAL -> cost;
                    case ElectrolyzerMenu.DATA_WATER_FLUID -> fluidId(water);
                    case ElectrolyzerMenu.DATA_WATER -> water.getAmount();
                    case ElectrolyzerMenu.DATA_WATER_CAPACITY -> water.getCapacity();
                    case ElectrolyzerMenu.DATA_PRIMARY_FLUID -> fluidId(hydrogen);
                    case ElectrolyzerMenu.DATA_PRIMARY -> hydrogen.getAmount();
                    case ElectrolyzerMenu.DATA_SECONDARY_FLUID -> fluidId(oxygen);
                    case ElectrolyzerMenu.DATA_SECONDARY -> oxygen.getAmount();
                    case ElectrolyzerMenu.DATA_GAS_CAPACITY -> hydrogen.getCapacity();
                    case ElectrolyzerMenu.DATA_RECIPE_INPUT -> shown != null ? shown.input().amount() : 0;
                    case ElectrolyzerMenu.DATA_RECIPE_PRIMARY -> shown != null ? shown.primary().amount() : 0;
                    case ElectrolyzerMenu.DATA_RECIPE_SECONDARY -> shown != null ? shown.secondary().map(FluidStackTemplate::amount).orElse(0) : 0;
                    case ElectrolyzerMenu.DATA_COST -> shown != null ? costFor(shown) : 0;
                    case ElectrolyzerMenu.DATA_FLOOR -> shown != null ? EnergyBalance.minEnergyFor(shown) : 0;
                    case ElectrolyzerMenu.DATA_STATUS -> status.ordinal();
                    case ElectrolyzerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ElectrolyzerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    case ElectrolyzerMenu.DATA_VENT -> (ventHydrogen ? 1 : 0) | (ventOxygen ? 2 : 0);
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

    // FE for one operation of this recipe: Energy upgrades cut it, down to the balance floor.
    public int costFor(ElectrolyzingRecipe recipe) {
        int upgraded = EnergyBalance.ceil(recipe.totalEnergy() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
        return Math.max(upgraded, EnergyBalance.minEnergyFor(recipe));
    }

    // FE/t while splitting: Speed upgrades draw it faster.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.ELECTROLYZER_ENERGY_PER_TICK.getAsInt() * speedMultiplier());
    }

    // Ticks for one operation of this recipe.
    public int ticksFor(ElectrolyzingRecipe recipe) {
        return Math.max(1, (int) Math.ceil((double) costFor(recipe) / energyPerTick()));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        FluidResource input = water.getResource(0);
        Optional<RecipeHolder<ElectrolyzingRecipe>> found = water.getAmount() > 0 ? MachineRecipes.electrolyzing(level, input) : Optional.empty();
        RecipeHolder<ElectrolyzingRecipe> holder = found.orElse(null);
        Identifier id = holder != null ? holder.id().identifier() : null;
        if (id == null || !id.equals(current)) {
            if (spent != 0) {
                spent = 0;
                setChanged();
            }
            current = id;
        }
        shown = holder != null ? holder.value()
                : MachineRecipes.electrolyzing(level, FluidResource.of(Fluids.WATER)).map(RecipeHolder::value).orElse(null);
        cost = holder != null ? costFor(holder.value()) : 0;

        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
        } else if (holder == null || water.getAmount() < holder.value().input().amount()) {
            status = MachineStatus.IDLE;
        } else if (!fits(holder.value())) {
            status = MachineStatus.OUTPUT_FULL;
        } else {
            // The last tick of an operation takes only what's left, so it costs exactly `cost`.
            int draw = Math.min(energyPerTick(), Math.max(0, cost - spent));
            if (draw > 0 && !energy.consume(draw)) {
                status = MachineStatus.NO_POWER;
            } else {
                status = MachineStatus.SPLITTING;
                usage = draw;
                spent += draw;
                if (spent >= cost) {
                    spent = 0;
                    finish(level, holder.value());
                }
                setChanged();
            }
        }
        setLit(status == MachineStatus.SPLITTING);

        // The gases' only way out, so this is always on.
        int rate = ArcforgeConfig.ELECTROLYZER_GAS_OUTPUT_RATE.getAsInt();
        push(level, pos, SideMode.HYDROGEN, hydrogenOutput, rate);
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

    // Whether both outputs fit in their tanks right now.
    private boolean fits(ElectrolyzingRecipe recipe) {
        try (Transaction tx = Transaction.openRoot()) {
            FluidStack primary = recipe.primary().create();
            if (!ventHydrogen && hydrogen.insert(0, FluidResource.of(primary), primary.getAmount(), tx) != primary.getAmount()) {
                return false;
            }
            if (recipe.secondary().isPresent() && !ventOxygen) {
                FluidStack secondary = recipe.secondary().get().create();
                return oxygen.insert(0, FluidResource.of(secondary), secondary.getAmount(), tx) == secondary.getAmount();
            }
            return true;
        }
    }

    private void finish(ServerLevel level, ElectrolyzingRecipe recipe) {
        int vented = 0;
        try (Transaction tx = Transaction.openRoot()) {
            water.extract(0, water.getResource(0), recipe.input().amount(), tx);
            FluidStack primary = recipe.primary().create();
            vented += primary.getAmount() - hydrogen.insert(0, FluidResource.of(primary), primary.getAmount(), tx);
            if (recipe.secondary().isPresent()) {
                FluidStack secondary = recipe.secondary().get().create();
                vented += secondary.getAmount() - oxygen.insert(0, FluidResource.of(secondary), secondary.getAmount(), tx);
            }
            tx.commit();
        }
        ArcforgeAdvancements.produced(this, net.minecraft.world.item.ItemStack.EMPTY, recipe.primary().create().getFluid(), null);
        recipe.secondary().ifPresent(secondary -> ArcforgeAdvancements.produced(this, net.minecraft.world.item.ItemStack.EMPTY,
                secondary.create().getFluid(), null));
        // Whatever didn't fit was vented (only possible with venting on): a puff from the top.
        if (vented > 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.05,
                    worldPosition.getZ() + 0.5, 3, 0.15, 0.05, 0.15, 0.01);
        }
    }

    public boolean isVentingHydrogen() {
        return ventHydrogen;
    }

    public boolean isVentingOxygen() {
        return ventOxygen;
    }

    public void setVenting(boolean hydrogen, boolean on) {
        if (hydrogen) {
            ventHydrogen = on;
        } else {
            ventOxygen = on;
        }
        setChanged();
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    // The primary output's tank (hydrogen, for water).
    public FilteredFluidTank getHydrogen() {
        return hydrogen;
    }

    // The secondary output's tank (oxygen, for water).
    public FilteredFluidTank getOxygen() {
        return oxygen;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    // FE paid into the running operation, and what it costs in all (0 when idle).
    public int getSpent() {
        return spent;
    }

    public int getCost() {
        return cost;
    }

    // The recipe for what the water tank holds, or for water while it's empty; null if there is none.
    public @Nullable ElectrolyzingRecipe getShownRecipe() {
        return shown;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    // Input faces fill the water tank; Hydrogen and Oxygen faces drain their gas. Unsided, all three (for Jade).
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return fluidAll;
        }
        return switch (mode) {
            case INPUT -> waterInput;
            case HYDROGEN -> hydrogenOutput;
            case OXYGEN -> oxygenOutput;
            default -> null;
        };
    }

    // FE goes in on energy faces.
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidInteraction;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS -> mode == SideMode.HYDROGEN || mode == SideMode.OXYGEN ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ITEM, THERMAL -> ConnectionMode.NONE;
        };
    }

    // The Settings Card also copies the vent toggles.
    @Override
    public void writeSettings(ValueOutput output) {
        super.writeSettings(output);
        output.putBoolean("vent_hydrogen", ventHydrogen);
        output.putBoolean("vent_oxygen", ventOxygen);
    }

    @Override
    public int readSettings(ValueInput input) {
        input.read("vent_hydrogen", Codec.BOOL).ifPresent(on -> setVenting(true, on));
        input.read("vent_oxygen", Codec.BOOL).ifPresent(on -> setVenting(false, on));
        return super.readSettings(input);
    }

    @Override
    public List<Component> describe(ValueInput input) {
        List<Component> lines = new java.util.ArrayList<>(super.describe(input));
        input.read("vent_hydrogen", Codec.BOOL).ifPresent(on -> lines.add(Component.translatable("settings.arcforge.mode",
                Component.translatable("gui.arcforge.electrolyzer.vent_hydrogen"), MachineSettings.onOff(on))));
        input.read("vent_oxygen", Codec.BOOL).ifPresent(on -> lines.add(Component.translatable("settings.arcforge.mode",
                Component.translatable("gui.arcforge.electrolyzer.vent_oxygen"), MachineSettings.onOff(on))));
        return lines;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        water.deserialize(input.childOrEmpty("water"));
        hydrogen.deserialize(input.childOrEmpty("hydrogen"));
        oxygen.deserialize(input.childOrEmpty("oxygen"));
        spent = input.getIntOr("spent", 0);
        ventHydrogen = input.getBooleanOr("vent_hydrogen", false);
        ventOxygen = input.getBooleanOr("vent_oxygen", false);
        current = input.getString("recipe").map(Identifier::tryParse).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        water.serialize(output.child("water"));
        hydrogen.serialize(output.child("hydrogen"));
        oxygen.serialize(output.child("oxygen"));
        output.putInt("spent", spent);
        output.putBoolean("vent_hydrogen", ventHydrogen);
        output.putBoolean("vent_oxygen", ventOxygen);
        if (current != null) {
            output.putString("recipe", current.toString());
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.electrolyzer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ElectrolyzerMenu(containerId, inventory, worldPosition, items, data);
    }
}
