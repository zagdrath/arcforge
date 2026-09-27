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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.DieInput;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.PressingLane;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MetalPressMenu;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Presses ingots into plates, gears and rods with FE (arcforge:pressing recipes): 20 FE/t for 100 ticks,
// 2,000 FE an operation. What it makes depends on the die in its die slot, which is never used up and
// which only players can put in or take out. Speed upgrades make it faster (drawing FE just as much
// faster), Energy upgrades cut the FE per operation.
public class MetalPressBlockEntity extends MachineBlockEntity {
    public static final int SLOT_DIE = 0;
    public static final int SLOT_INPUT = 1;
    public static final int SLOT_OUTPUT = 2;
    public static final int MACHINE_SLOTS = 3;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final PressingLane lane = new PressingLane(SLOT_DIE, SLOT_INPUT, SLOT_OUTPUT);
    private final ContainerData data;
    private int usage;

    public MetalPressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.METAL_PRESS.get(), pos, state, MACHINE_SLOTS, MetalPressBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.PRESS_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.PRESS_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.itemInput = new DieInput(items, this::getLevel, SLOT_DIE, SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new DieInput(items, this::getLevel, SLOT_DIE, SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(MetalPressMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case MetalPressMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case MetalPressMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case MetalPressMenu.DATA_PROGRESS -> lane.getProgress();
                    case MetalPressMenu.DATA_TOTAL -> lane.getTotal();
                    case MetalPressMenu.DATA_USAGE -> usage;
                    case MetalPressMenu.DATA_STATUS -> status.ordinal();
                    case MetalPressMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case MetalPressMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // The die slot takes dies; the input slot anything some die presses (the die decides what it makes).
    // The client passes a null level and checks against the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return switch (slot) {
            case SLOT_DIE -> resource.toStack(1).is(ModItemTags.DIES);
            case SLOT_INPUT -> MachineRecipes.isPressingInput(level, resource.toStack(1));
            default -> false;
        };
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // FE/t while pressing: Speed draws it faster, Energy cuts it, so an operation costs base x 0.8^n.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.PRESS_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
        } else {
            PressingLane.Settings settings = new PressingLane.Settings(1.0 / speedMultiplier(), energyPerTick());
            PressingLane.State laneState = lane.tick(level, items, energy, settings);
            status = switch (laneState) {
                case WORKING -> MachineStatus.PRESSING;
                case IDLE -> MachineStatus.IDLE;
                case NO_DIE -> MachineStatus.NO_DIE;
                case NO_POWER -> MachineStatus.NO_POWER;
                case OUTPUT_FULL -> MachineStatus.OUTPUT_FULL;
            };
            if (laneState == PressingLane.State.WORKING) {
                usage = settings.energyPerTick();
                setChanged();
            }
        }
        setLit(status == MachineStatus.PRESSING);
        autoEject(level, itemOutput);
    }

    public PressingLane getLane() {
        return lane;
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
        lane.deserialize(input.childOrEmpty("lane"), items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        lane.serialize(output.child("lane"));
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.metal_press");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MetalPressMenu(containerId, inventory, worldPosition, items, data);
    }
}
