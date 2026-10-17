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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.WasteCompactorMenu;
import net.zagdrath.arcforge.nuclear.safety.Contamination;
import net.zagdrath.arcforge.recipe.CompactingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Waste Compactor: low-level waste crushed into an empty Waste Drum, which comes out a Low-Level Waste Drum
// (arcforge:compacting recipes: Used HEPA Filters, worn Hazmat Suit pieces), at nuclear.reprocessing.compactorEnergy
// FE/t. Any contaminated item at all (see Contamination) goes too, nuclear.reprocessing.contaminatedPerDrum of them to a
// drum. Speed upgrades make it faster, Energy upgrades cut the FE.
public class WasteCompactorBlockEntity extends MachineBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_DRUM = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int MACHINE_SLOTS = 3;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);
    private static final int TIME = CompactingRecipe.DEFAULT_TIME;

    private final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;

    public WasteCompactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.WASTE_COMPACTOR.get(), pos, state, MACHINE_SLOTS, WasteCompactorBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.INPUT, SideMode.NONE, SideMode.ENERGY, SideMode.NONE), SIDE_MODES);
        int fe = Math.max(1, ArcforgeConfig.COMPACTOR_ENERGY.getAsInt());
        this.energy = new ConsumerEnergyHandler(fe * 400, fe * 4, this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT || slot == SLOT_DRUM, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT || slot == SLOT_DRUM, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(WasteCompactorMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case WasteCompactorMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case WasteCompactorMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case WasteCompactorMenu.DATA_PROGRESS -> progress;
                    case WasteCompactorMenu.DATA_TOTAL -> total;
                    case WasteCompactorMenu.DATA_USAGE -> usage;
                    case WasteCompactorMenu.DATA_STATUS -> status.ordinal();
                    case WasteCompactorMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case WasteCompactorMenu.DATA_SIDE_CONFIG -> packedSides();
                    default -> 0;
                };
            }
        };
    }

    // The input slot takes compactable waste or anything contaminated; the drum slot empty Waste Drums.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        ItemStack stack = resource.toStack(1);
        return switch (slot) {
            case SLOT_INPUT -> MachineRecipes.isCompactorInput(level, stack) || Contamination.isContaminated(stack);
            case SLOT_DRUM -> stack.is(ModItems.WASTE_DRUM.get());
            default -> false;
        };
    }

    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.COMPACTOR_ENERGY.getAsInt() * speedMultiplier() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        status = canRun(level) ? work(level) : stoppedStatus();
        setLit(status == MachineStatus.COMPACTING);
        autoEject(level, itemOutput);
    }

    private MachineStatus work(ServerLevel level) {
        ItemStack input = items.getStack(SLOT_INPUT);
        if (input.isEmpty()) {
            progress = 0;
            total = 0;
            return MachineStatus.IDLE;
        }
        RecipeHolder<CompactingRecipe> holder = MachineRecipes.compacting(level, input).orElse(null);
        int count;
        ItemStack result;
        int time;
        if (holder != null) {
            count = holder.value().input().count();
            result = holder.value().result().create();
            time = holder.value().time();
        } else if (Contamination.isContaminated(input)) {
            // As many as a drum takes, or one of anything that doesn't stack.
            count = Math.min(ArcforgeConfig.CONTAMINATED_PER_DRUM.getAsInt(), input.getMaxStackSize());
            result = new ItemStack(ModItems.LOW_LEVEL_WASTE_DRUM.get());
            time = TIME;
        } else {
            progress = 0;
            total = 0;
            return MachineStatus.IDLE;
        }
        total = UpgradeType.time(time, upgrades(UpgradeType.SPEED));
        if (input.getCount() < count) {
            return MachineStatus.MISSING_ITEMS;
        }
        if (!items.getStack(SLOT_DRUM).is(ModItems.WASTE_DRUM.get())) {
            return MachineStatus.NO_DRUM;
        }
        if (!fits(items.getStack(SLOT_OUTPUT), result)) {
            return MachineStatus.OUTPUT_FULL;
        }
        int fe = energyPerTick();
        if (!energy.consume(fe)) {
            return MachineStatus.NO_POWER;
        }
        usage = fe;
        progress++;
        if (progress >= total) {
            items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - count));
            ItemStack drum = items.getStack(SLOT_DRUM);
            items.setStack(SLOT_DRUM, drum.copyWithCount(drum.getCount() - 1));
            ItemStack current = items.getStack(SLOT_OUTPUT);
            items.setStack(SLOT_OUTPUT, current.isEmpty() ? result.copy() : current.copyWithCount(current.getCount() + result.getCount()));
            controlState.completed(result.copy(), count + 1);
            ArcforgeAdvancements.produced(this, result, null, "compacting");
            progress = 0;
        }
        setChanged();
        return MachineStatus.COMPACTING;
    }

    private static boolean fits(ItemStack slot, ItemStack result) {
        return slot.isEmpty() || ItemStack.isSameItemSameComponents(slot, result) && slot.getCount() + result.getCount() <= slot.getMaxStackSize();
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    public int getUsage() {
        return usage;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

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

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL, GAS, FLUID -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.waste_compactor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new WasteCompactorMenu(containerId, inventory, worldPosition, items, data);
    }
}
