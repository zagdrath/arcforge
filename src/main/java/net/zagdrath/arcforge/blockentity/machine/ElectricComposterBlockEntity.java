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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.blockentity.farming.CompostBinBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ElectricComposterMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Electric Composter: the Compost Bin's heap, run on FE. It takes the same plant matter (anything with the vanilla
// minecraft:compostable component) at the same chances, each item raising a heap of HEAP layers by what its component
// rolls; a full heap gives compostBin.compostPerBatch Compost straight into the output slot and a new heap starts. It
// works an item every electricComposter.ticksPerItem ticks (the bin takes compostBin.itemInterval, then waits
// readyDelay and needs emptying), so it is several times faster. Speed upgrades work more items a tick (drawing FE just as
// much faster), Energy upgrades cut the FE.
public class ElectricComposterBlockEntity extends MachineBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int MACHINE_SLOTS = 2;
    // Layers to a full heap, as the Compost Bin's (and a vanilla composter's) seven.
    public static final int HEAP = 7;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;
    // The heap's layers (0 to HEAP), and the items' worth of work done toward the next one.
    private int heap;
    private double work;
    private int usage;

    public ElectricComposterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.ELECTRIC_COMPOSTER.get(), pos, state, MACHINE_SLOTS, ElectricComposterBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.ELECTRIC_COMPOSTER_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.ELECTRIC_COMPOSTER_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(ElectricComposterMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case ElectricComposterMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case ElectricComposterMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case ElectricComposterMenu.DATA_HEAP -> heap;
                    case ElectricComposterMenu.DATA_USAGE -> usage;
                    case ElectricComposterMenu.DATA_STATUS -> status.ordinal();
                    case ElectricComposterMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ElectricComposterMenu.DATA_SIDE_CONFIG -> packedSides();
                    default -> 0;
                };
            }
        };
    }

    // The input takes what the Compost Bin takes.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && CompostBinBlockEntity.compostable(resource.toStack(1));
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // FE/t while composting: Speed draws it faster, Energy cuts it.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.ELECTRIC_COMPOSTER_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // Items worked a tick: one per ticksPerItem, times the Speed upgrades.
    public double itemsPerTick() {
        return speedMultiplier() / ArcforgeConfig.ELECTRIC_COMPOSTER_TICKS_PER_ITEM.getAsInt();
    }

    private static int compostPerHeap() {
        return ArcforgeConfig.COMPOST_BIN_YIELD.getAsInt();
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        if (!canRun(level)) {
            status = stoppedStatus();
        } else {
            status = compost(level);
        }
        setLit(status == MachineStatus.COMPOSTING);
        autoEject(level, itemOutput);
    }

    private MachineStatus compost(ServerLevel level) {
        if (heap >= HEAP && !emptyHeap()) {
            return MachineStatus.OUTPUT_FULL;
        }
        ItemStack input = items.getStack(SLOT_INPUT);
        if (input.isEmpty()) {
            work = 0;
            return MachineStatus.IDLE;
        }
        int cost = energyPerTick();
        if (energy.getAmountAsInt() < cost) {
            return MachineStatus.NO_POWER;
        }
        energy.consume(cost);
        usage = cost;
        work += itemsPerTick();
        int worked = 0;
        int made = 0;
        boolean rose = false;
        while (work >= 1.0 && heap < HEAP) {
            input = items.getStack(SLOT_INPUT);
            if (input.isEmpty()) {
                break;
            }
            work -= 1.0;
            Compostable compostable = input.get(DataComponents.COMPOSTABLE);
            items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
            worked++;
            int layers = compostable != null ? compostable.layers().get(lootContext(level), 0) : 0;
            if (layers > 0) {
                heap = Math.min(HEAP, heap + layers);
                rose = true;
            }
            if (heap >= HEAP) {
                if (!emptyHeap()) {
                    break;
                }
                made += compostPerHeap();
            }
        }
        if (worked > 0) {
            controlState.completed(made > 0 ? new ItemStack(ModItems.COMPOST.get(), made) : ItemStack.EMPTY, worked);
            if (rose && level.getRandom().nextInt(4) == 0) {
                level.playSound(null, worldPosition, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 0.4F, 1.1F);
            }
        }
        setChanged();
        return MachineStatus.COMPOSTING;
    }

    // A full heap into the output as Compost, if it fits. Returns whether it went.
    private boolean emptyHeap() {
        ItemStack out = items.getStack(SLOT_OUTPUT);
        int count = compostPerHeap();
        if (!out.isEmpty() && (!out.is(ModItems.COMPOST.get()) || out.getCount() + count > out.getMaxStackSize())) {
            return false;
        }
        items.setStack(SLOT_OUTPUT, new ItemStack(ModItems.COMPOST.get(), out.getCount() + count));
        heap = 0;
        setChanged();
        return true;
    }

    // What a compostable's layer roll draws on (ComposterBlock.addItem's context).
    private LootContext lootContext(ServerLevel level) {
        return new LootContext.Builder(new LootParams.Builder(level)
                .withParameter(LootContextParams.BLOCK_STATE, getBlockState())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                .create(LootContextParamSets.BLOCK_INTERACT)).create(Optional.empty());
    }

    public int getHeap() {
        return heap;
    }

    public int getUsage() {
        return usage;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

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

    // FE goes in on energy faces.
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
            case FLUID, GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        heap = input.getIntOr("heap", 0);
        work = input.getDoubleOr("work", 0.0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        output.putInt("heap", heap);
        output.putDouble("work", work);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.electric_composter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ElectricComposterMenu(containerId, inventory, worldPosition, items, data);
    }
}
