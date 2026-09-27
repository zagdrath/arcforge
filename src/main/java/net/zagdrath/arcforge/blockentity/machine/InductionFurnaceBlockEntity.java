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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.SmeltingLane;
import net.zagdrath.arcforge.machine.StoredExperience;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.InductionFurnaceMenu;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Smelts anything with a furnace recipe (vanilla or modded) using FE: half the recipe's cooking time
// (100 ticks for a normal recipe) at 20 FE/t, so 2,000 FE an item. Holds the recipes' XP until a player
// takes the output, like a vanilla furnace. Speed upgrades make it faster (drawing FE just as much
// faster), Energy upgrades cut the FE per item.
public class InductionFurnaceBlockEntity extends MachineBlockEntity {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.ENERGY);

    private final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final SmeltingLane lane = new SmeltingLane(SLOT_INPUT, SLOT_OUTPUT);
    private final StoredExperience experience = new StoredExperience(this::setChanged);
    private final ContainerData data;
    private int usage;

    public InductionFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.INDUCTION_FURNACE.get(), pos, state, MACHINE_SLOTS, InductionFurnaceBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.INDUCTION_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.INDUCTION_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_OUTPUT);
        this.data = new WideIntContainerData(InductionFurnaceMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case InductionFurnaceMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case InductionFurnaceMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case InductionFurnaceMenu.DATA_PROGRESS -> lane.getProgress();
                    case InductionFurnaceMenu.DATA_TOTAL -> lane.getTotal();
                    case InductionFurnaceMenu.DATA_USAGE -> usage;
                    case InductionFurnaceMenu.DATA_STATUS -> status.ordinal();
                    case InductionFurnaceMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case InductionFurnaceMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // The input slot takes anything with a smelting recipe. The client passes a null level and checks
    // against the recipes the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && MachineRecipes.isSmeltingInput(level, resource.toStack(1));
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // FE/t while smelting: Speed draws it faster, Energy cuts it, so an item costs base x 0.8^n.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.INDUCTION_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        if (!redstoneMode.canRun(level.hasNeighborSignal(pos))) {
            status = MachineStatus.DISABLED;
        } else {
            SmeltingLane.Settings settings = new SmeltingLane.Settings(
                    ArcforgeConfig.INDUCTION_TIME_MULTIPLIER.getAsDouble() / speedMultiplier(), energyPerTick());
            SmeltingLane.State laneState = lane.tick(level, items, energy, experience, settings);
            status = switch (laneState) {
                case WORKING -> MachineStatus.SMELTING;
                case IDLE -> MachineStatus.IDLE;
                case NO_POWER -> MachineStatus.NO_POWER;
                case OUTPUT_FULL -> MachineStatus.OUTPUT_FULL;
            };
            if (laneState == SmeltingLane.State.WORKING) {
                usage = settings.energyPerTick();
                setChanged();
            }
        }
        setLit(status == MachineStatus.SMELTING);
        autoEject(level, itemOutput);
    }

    // Pays the stored smelting XP out to a player who took the output.
    public void awardExperience(Player player) {
        if (level instanceof ServerLevel serverLevel) {
            experience.award(serverLevel, player.position());
        }
    }

    public SmeltingLane getLane() {
        return lane;
    }

    public StoredExperience getExperience() {
        return experience;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    // Broken or picked up: the XP drops where it stood.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            experience.award(serverLevel, Vec3.atCenterOf(pos));
        }
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
            case FLUID, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        lane.deserialize(input.childOrEmpty("lane"));
        experience.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        lane.serialize(output.child("lane"));
        experience.serialize(output);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.induction_furnace");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new InductionFurnaceMenu(containerId, inventory, worldPosition, items, data, this::awardExperience);
    }
}
