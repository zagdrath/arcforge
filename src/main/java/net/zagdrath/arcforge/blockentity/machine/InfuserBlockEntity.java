/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.EnumSet;
import java.util.List;
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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.BucketSlots;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.InfuserMenu;
import net.zagdrath.arcforge.recipe.InfusingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Soaks wood in a fluid under pressure (arcforge:infusing recipes): vanilla planks, logs and wood in
// creosote become treated wood, at 20 FE/t. Some recipes take an additive item instead of a fluid (Pine Resin in place of
// Creosote), from its additive slot. The recipe's fluid or additive is used up when an item is done. The tank fills from
// buckets in its bucket slot, pipes on input faces, or a held bucket. Speed upgrades make it faster (drawing FE just as
// much faster), Energy upgrades cut the FE per item. The additive slot came later: older saves move their upgrades up
// past it.
public class InfuserBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_BUCKET_IN = 0;
    public static final int SLOT_BUCKET_OUT = 1;
    public static final int SLOT_INPUT = 2;
    public static final int SLOT_OUTPUT = 3;
    // The additive (Pine Resin), for recipes that take one instead of a fluid.
    public static final int SLOT_ADDITIVE = 4;
    public static final int MACHINE_SLOTS = 5;
    // Saves from before the additive slot (layout 1) had the upgrade slots where it is now.
    private static final int SLOT_LAYOUT = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> fluidInput;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;

    public InfuserBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.INFUSER.get(), pos, state, MACHINE_SLOTS, InfuserBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.INFUSER_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.INFUSER_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.tank = new FilteredFluidTank(
                ArcforgeConfig.INFUSER_TANK_CAPACITY.getAsInt(),
                resource -> MachineRecipes.isInfuserFluid(level, resource),
                this::setChanged);
        this.fluidInput = new AutomationResourceHandler<>(tank, index -> true, index -> false);
        this.itemInput = new AutomationResourceHandler<>(items, InfuserBlockEntity::isInputSlot, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, InfuserBlockEntity::isOutputSlot);
        this.itemAutomation = new AutomationResourceHandler<>(items, InfuserBlockEntity::isInputSlot, InfuserBlockEntity::isOutputSlot);
        this.data = new WideIntContainerData(InfuserMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case InfuserMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case InfuserMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case InfuserMenu.DATA_FLUID -> tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
                    case InfuserMenu.DATA_FLUID_AMOUNT -> tank.getAmount();
                    case InfuserMenu.DATA_FLUID_CAPACITY -> tank.getCapacity();
                    case InfuserMenu.DATA_PROGRESS -> progress;
                    case InfuserMenu.DATA_TOTAL -> total;
                    case InfuserMenu.DATA_USAGE -> usage;
                    case InfuserMenu.DATA_STATUS -> status.ordinal();
                    case InfuserMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case InfuserMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    private static boolean isInputSlot(int slot) {
        return slot == SLOT_BUCKET_IN || slot == SLOT_INPUT || slot == SLOT_ADDITIVE;
    }

    private static boolean isOutputSlot(int slot) {
        return slot == SLOT_OUTPUT || slot == SLOT_BUCKET_OUT;
    }

    // The bucket slot takes full buckets of a fluid some recipe uses, the input slot anything with an
    // infusing recipe. The client passes a null level and checks against the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return switch (slot) {
            case SLOT_BUCKET_IN -> {
                FluidResource fluid = BucketSlots.contents(resource.toStack(1));
                yield fluid != null && MachineRecipes.isInfuserFluid(level, fluid);
            }
            case SLOT_INPUT -> MachineRecipes.isInfuserInput(level, resource.toStack(1));
            case SLOT_ADDITIVE -> MachineRecipes.isInfuserAdditive(level, resource.toStack(1));
            default -> false;
        };
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // FE/t while infusing: Speed draws it faster, Energy cuts it, so an item costs base x 0.8^n.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.INFUSER_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        BucketSlots.pour(items, SLOT_BUCKET_IN, SLOT_BUCKET_OUT, tank);
        usage = 0;
        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
        } else {
            status = work(level);
        }
        setLit(status == MachineStatus.INFUSING);
        autoEject(level, itemOutput);
    }

    private MachineStatus work(ServerLevel level) {
        ItemStack input = items.getStack(SLOT_INPUT);
        ItemStack additive = items.getStack(SLOT_ADDITIVE);
        FluidResource fluid = tank.getResource(0);
        RecipeHolder<InfusingRecipe> holder = input.isEmpty() ? null : MachineRecipes.infusing(level, input, fluid, additive).orElse(null);
        if (holder == null) {
            progress = 0;
            total = 0;
            return MachineStatus.IDLE;
        }
        InfusingRecipe recipe = holder.value();
        total = UpgradeType.time(recipe.time(), upgrades(UpgradeType.SPEED));
        ItemStack result = recipe.assemble(new SingleRecipeInput(input));
        if (!fits(items.getStack(SLOT_OUTPUT), result)) {
            return MachineStatus.OUTPUT_FULL;
        }
        if (recipe.additive().isPresent()) {
            if (!recipe.hasAdditive(additive)) {
                return MachineStatus.NO_ADDITIVE;
            }
        } else if (!recipe.usesFluid(fluid) || tank.getAmount() < recipe.fluidAmount()) {
            return MachineStatus.NO_FLUID;
        }
        int fe = energyPerTick();
        if (!energy.consume(fe)) {
            return MachineStatus.NO_POWER;
        }
        usage = fe;
        progress++;
        if (progress >= total) {
            if (recipe.additive().isPresent()) {
                items.setStack(SLOT_ADDITIVE, additive.copyWithCount(additive.getCount() - recipe.additive().get().count()));
            } else {
                try (Transaction tx = Transaction.openRoot()) {
                    tank.extract(0, fluid, recipe.fluidAmount(), tx);
                    tx.commit();
                }
            }
            items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
            ItemStack current = items.getStack(SLOT_OUTPUT);
            items.setStack(SLOT_OUTPUT, current.isEmpty() ? result : current.copyWithCount(current.getCount() + result.getCount()));
            progress = 0;
        }
        setChanged();
        return MachineStatus.INFUSING;
    }

    private static boolean fits(ItemStack slot, ItemStack result) {
        if (slot.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(slot, result) && slot.getCount() + result.getCount() <= slot.getMaxStackSize();
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    // Input faces take full buckets and wood; output faces give the treated wood and the empty buckets.
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> itemInput;
            case OUTPUT -> itemOutput;
            default -> null;
        };
    }

    // Input faces take fluid from pipes and fluid conduits; nothing can drain it back out.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? fluidInput : null;
    }

    // FE goes in on energy faces.
    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    // Held buckets fill the tank from any face, regardless of the side configuration.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidInput;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL, GAS -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        tank.deserialize(input.childOrEmpty("tank"));
        progress = input.getIntOr("progress", 0);
        if (input.getIntOr("slot_layout", 1) < SLOT_LAYOUT) {
            // Layout 1 was the four machine slots, then the upgrade slots: move the upgrades up past the additive slot.
            items.ensureSize(MACHINE_SLOTS + UPGRADE_SLOTS);
            for (int slot = MACHINE_SLOTS + UPGRADE_SLOTS - 1; slot > SLOT_ADDITIVE; slot--) {
                items.setStack(slot, items.getStack(slot - 1));
            }
            items.setStack(SLOT_ADDITIVE, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        tank.serialize(output.child("tank"));
        output.putInt("progress", progress);
        output.putInt("slot_layout", SLOT_LAYOUT);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.infuser");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new InfuserMenu(containerId, inventory, worldPosition, items, data);
    }
}
