/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.EnumSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.block.multiblock.InductionFurnaceArrayCasingBlock;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.SmeltingLane;
import net.zagdrath.arcforge.machine.StoredExperience;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.InductionFurnaceArrayMenu;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Induction Furnace Array (see CubeMultiblockBlockEntity). Three smelting lanes work in parallel,
// each twice as fast as an Induction Furnace and for 60% less FE an item. Smelting never doubles
// outputs. The recipes' XP is held until a player takes an output, like a vanilla furnace.
public class InductionFurnaceArrayBlockEntity extends CubeMultiblockBlockEntity {
    public static final int LANES = 3;
    public static final int MACHINE_SLOTS = LANES * 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);

    private final SmeltingLane[] lanes = new SmeltingLane[LANES];
    private final StoredExperience experience = new StoredExperience(this::setChanged);
    private final ContainerData data;

    public InductionFurnaceArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.INDUCTION_FURNACE_ARRAY.get(), pos, state, MACHINE_SLOTS, InductionFurnaceArrayBlockEntity::isItemValid, UPGRADES,
                ArcforgeConfig.INDUCTION_ARRAY_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.INDUCTION_ARRAY_MAX_INPUT.getAsInt(),
                new int[] { inputSlot(0), inputSlot(1), inputSlot(2) }, InductionFurnaceArrayBlockEntity::isOutputSlot);
        for (int lane = 0; lane < LANES; lane++) {
            lanes[lane] = new SmeltingLane(inputSlot(lane), outputSlot(lane));
        }
        this.data = new WideIntContainerData(InductionFurnaceArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                if (index >= InductionFurnaceArrayMenu.DATA_PROGRESS_FIRST && index < InductionFurnaceArrayMenu.DATA_PROGRESS_FIRST + LANES) {
                    return lanes[index - InductionFurnaceArrayMenu.DATA_PROGRESS_FIRST].getProgress();
                }
                if (index >= InductionFurnaceArrayMenu.DATA_TOTAL_FIRST && index < InductionFurnaceArrayMenu.DATA_TOTAL_FIRST + LANES) {
                    return lanes[index - InductionFurnaceArrayMenu.DATA_TOTAL_FIRST].getTotal();
                }
                return switch (index) {
                    case InductionFurnaceArrayMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case InductionFurnaceArrayMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case InductionFurnaceArrayMenu.DATA_USAGE -> usage;
                    case InductionFurnaceArrayMenu.DATA_SPEED_TENTHS -> speedTenths();
                    case InductionFurnaceArrayMenu.DATA_POWER_SAVING -> powerSaving();
                    case InductionFurnaceArrayMenu.DATA_STATUS -> status.ordinal();
                    case InductionFurnaceArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case InductionFurnaceArrayMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    public static int inputSlot(int lane) {
        return lane * 2;
    }

    public static int outputSlot(int lane) {
        return lane * 2 + 1;
    }

    private static boolean isOutputSlot(int slot) {
        return slot < MACHINE_SLOTS && slot % 2 == 1;
    }

    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot < MACHINE_SLOTS && slot % 2 == 0 && MachineRecipes.isSmeltingInput(level, resource.toStack(1));
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    // --- Running ---

    // FE/t per working lane: Speed draws it faster, Energy cuts it.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.INDUCTION_ARRAY_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // How much faster each lane is than an Induction Furnace, in tenths (20 = x2).
    private static int speedTenths() {
        return (int) Math.round(10.0 * ArcforgeConfig.INDUCTION_TIME_MULTIPLIER.getAsDouble()
                / ArcforgeConfig.INDUCTION_ARRAY_TIME_MULTIPLIER.getAsDouble());
    }

    // FE saved per item compared with an un-upgraded Induction Furnace, in percent (60 without upgrades).
    private int powerSaving() {
        double furnace = ArcforgeConfig.INDUCTION_ENERGY_PER_TICK.getAsInt() * ArcforgeConfig.INDUCTION_TIME_MULTIPLIER.getAsDouble();
        double array = ArcforgeConfig.INDUCTION_ARRAY_ENERGY_PER_TICK.getAsInt() * ArcforgeConfig.INDUCTION_ARRAY_TIME_MULTIPLIER.getAsDouble()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY));
        return (int) Math.round(100.0 * (1.0 - array / furnace));
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return InductionFurnaceArrayCasingBlock.STRUCTURE;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        tickPorts(level);
        if (!isFormed()) {
            return;
        }
        usage = 0;
        boolean working = false;
        if (!redstoneMode.canRun(isPowered(level))) {
            status = MachineStatus.DISABLED;
        } else {
            SmeltingLane.Settings settings = new SmeltingLane.Settings(
                    ArcforgeConfig.INDUCTION_ARRAY_TIME_MULTIPLIER.getAsDouble() / speedMultiplier(), energyPerTick());
            boolean full = false;
            boolean noPower = false;
            for (SmeltingLane lane : lanes) {
                switch (lane.tick(level, items, energy, experience, settings)) {
                    case WORKING -> {
                        working = true;
                        usage += settings.energyPerTick();
                    }
                    case OUTPUT_FULL -> full = true;
                    case NO_POWER -> noPower = true;
                    case IDLE -> { }
                }
            }
            status = working ? MachineStatus.SMELTING : full ? MachineStatus.OUTPUT_FULL : noPower ? MachineStatus.NO_POWER : MachineStatus.IDLE;
            if (working) {
                setChanged();
            }
        }
        setLit(working);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            pushOutputs(level);
        }
    }

    // Pays the stored smelting XP out to a player who took an output.
    public void awardExperience(Player player) {
        if (level instanceof ServerLevel serverLevel) {
            experience.award(serverLevel, player.position());
        }
    }

    public SmeltingLane getLane(int lane) {
        return lanes[lane];
    }

    public StoredExperience getExperience() {
        return experience;
    }

    // The centre broken: the XP drops where it stood.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            experience.award(serverLevel, Vec3.atCenterOf(pos));
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        int lane = 0;
        for (ValueInput entry : input.childrenListOrEmpty("lanes")) {
            if (lane < LANES) {
                lanes[lane++].deserialize(entry);
            }
        }
        experience.deserialize(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.ValueOutputList list = output.childrenList("lanes");
        for (SmeltingLane lane : lanes) {
            lane.serialize(list.addChild());
        }
        experience.serialize(output);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.induction_furnace_array");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new InductionFurnaceArrayMenu(containerId, inventory, worldPosition, items, data, this::awardExperience);
    }
}
