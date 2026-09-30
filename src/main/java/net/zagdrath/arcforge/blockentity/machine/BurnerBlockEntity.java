/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.CombustionFuel;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.BurnerMenu;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.tag.ModItemTags;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// A machine that burns #arcforge:combustion_fuel from one slot, making something (FE or heat) into a
// buffer each tick it burns. While the buffer is full it pauses, keeping what's left of the burning item.
// Each item of #arcforge:leaves_wood_ash (charcoal) that burns out leaves a Wood Ash in the ash slot with
// farming.fertilizers.woodAshChance; the ash comes out through Output faces, and a full ash slot loses it.
public abstract class BurnerBlockEntity extends MachineBlockEntity {
    public static final int SLOT_FUEL = 0;
    public static final int SLOT_ASH = 1;
    public static final int MACHINE_SLOTS = 2;
    // Saves before the ash slot (layout 1) had the upgrade slots straight after the fuel slot.
    private static final int SLOT_LAYOUT = 2;

    private final ResourceHandler<ItemResource> fuelInput;
    private final ResourceHandler<ItemResource> ashOutput;
    private final ResourceHandler<ItemResource> automation;
    private final ContainerData data;

    private int burnTime;
    private int burnTotal;
    private @Nullable Item burning;
    private int outputPerTick;

    protected BurnerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Set<UpgradeType> upgrades,
            SideConfig sideConfig, List<SideMode> allowedSideModes) {
        super(type, pos, state, MACHINE_SLOTS, BurnerBlockEntity::isItemValid, upgrades, sideConfig, allowedSideModes);
        this.fuelInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_FUEL, slot -> false);
        this.ashOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_ASH);
        this.automation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_FUEL, slot -> slot == SLOT_ASH);
        this.data = new WideIntContainerData(BurnerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case BurnerMenu.DATA_STORED -> getStored();
                    case BurnerMenu.DATA_CAPACITY -> getCapacity();
                    case BurnerMenu.DATA_TEMPERATURE -> getTemperature();
                    case BurnerMenu.DATA_BURN_TIME -> burnTime;
                    case BurnerMenu.DATA_BURN_TOTAL -> burnTotal;
                    case BurnerMenu.DATA_OUTPUT_PER_TICK -> outputPerTick;
                    case BurnerMenu.DATA_BURNING_ITEM -> burning == null ? -1 : BuiltInRegistries.ITEM.getId(burning);
                    case BurnerMenu.DATA_STATUS -> status.ordinal();
                    case BurnerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case BurnerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    case BurnerMenu.DATA_OXYGEN -> oxygenAmount();
                    case BurnerMenu.DATA_OXYGEN_CAPACITY -> oxygenCapacity();
                    case BurnerMenu.DATA_OXY_ACTIVE -> isOxyActive() ? 1 : 0;
                    default -> 0;
                };
            }
        };
    }

    // Only fuel can be put in; the ash slot is filled by the burner alone.
    public static boolean isItemValid(int slot, ItemResource resource) {
        return slot == SLOT_FUEL && CombustionFuel.isFuel(resource.toStack(1));
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems(Set<UpgradeType> upgrades) {
        return new MachineItemHandler(MACHINE_SLOTS, BurnerBlockEntity::isItemValid, upgrades, () -> {});
    }

    // --- What the burner makes ---

    // How much faster than a vanilla furnace fuel burns.
    protected abstract double burnSpeed();

    // Makes one tick's worth of output into the buffer; returns how much fit.
    protected abstract int produce();

    protected abstract boolean isBufferFull();

    // Hands the buffer's contents to touching blocks on the output faces.
    protected abstract void pushOutput(ServerLevel level, BlockPos pos, Direction facing);

    public abstract int getStored();

    public abstract int getCapacity();

    // °C for heat, 0 for FE.
    // What it made this tick (FE or HU).
    protected int producedThisTick() {
        return outputPerTick;
    }

    public int getOutputPerTick() {
        return outputPerTick;
    }

    protected int getTemperature() {
        return 0;
    }

    // Oxy-fuel (the Firebox): the oxygen held, and whether it's burning with it. None here.
    protected int oxygenAmount() {
        return 0;
    }

    protected int oxygenCapacity() {
        return 0;
    }

    public boolean isOxyActive() {
        return false;
    }

    // The status while it burns.
    protected MachineStatus runningStatus() {
        return MachineStatus.RUNNING;
    }

    protected ContainerData getData() {
        return data;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        // A redstone-disabled burner holds its fire where it is.
        boolean enabled = redstoneMode.canRun(level.hasNeighborSignal(pos));
        int produced = 0;
        if (enabled) {
            if (burnTime <= 0 && !isBufferFull()) {
                startBurn(level);
            }
            if (burnTime > 0) {
                produced = produce();
                if (produced > 0) {
                    burnTime--;
                    if (burnTime == 0) {
                        leaveAsh(level, burning);
                        burning = null;
                    }
                }
            }
        }
        outputPerTick = produced;
        pushOutput(level, pos, getFacing());
        autoEject(level, ashOutput);

        if (!enabled) {
            status = MachineStatus.DISABLED;
        } else if (produced > 0) {
            status = runningStatus();
        } else if (isBufferFull()) {
            status = MachineStatus.FULL;
        } else {
            status = MachineStatus.NO_FUEL;
        }
        setLit(enabled && burnTime > 0);
        if (produced > 0) {
            setChanged();
        }
    }

    private void startBurn(ServerLevel level) {
        ItemStack fuel = items.getStack(SLOT_FUEL);
        if (!CombustionFuel.isFuel(fuel)) {
            return;
        }
        int ticks = CombustionFuel.burnTicks(level, this, fuel, burnSpeed());
        if (ticks <= 0) {
            return;
        }
        burning = fuel.getItem();
        items.setStack(SLOT_FUEL, fuel.copyWithCount(fuel.getCount() - 1));
        burnTime = ticks;
        burnTotal = ticks;
    }

    public int getBurnTime() {
        return burnTime;
    }

    // What a burnt-out item leaves: Wood Ash, now and then, from charcoal.
    private void leaveAsh(ServerLevel level, @Nullable Item burnt) {
        if (burnt != null && leavesAsh(burnt.getDefaultInstance()) && level.getRandom().nextDouble() < ArcforgeConfig.WOOD_ASH_CHANCE.getAsDouble()) {
            addAsh(1);
        }
    }

    public static boolean leavesAsh(ItemStack stack) {
        return stack.is(ModItemTags.LEAVES_WOOD_ASH);
    }

    // Puts Wood Ash in the ash slot, up to a stack; returns how many fit (the rest is lost).
    public int addAsh(int count) {
        ItemStack ash = items.getStack(SLOT_ASH);
        if (!ash.isEmpty() && !ash.is(ModItems.WOOD_ASH.get())) {
            return 0;
        }
        int fit = Math.min(count, new ItemStack(ModItems.WOOD_ASH.get()).getMaxStackSize() - ash.getCount());
        if (fit > 0) {
            items.setStack(SLOT_ASH, new ItemStack(ModItems.WOOD_ASH.get(), ash.getCount() + fit));
        }
        return fit;
    }

    // --- Capabilities ---

    // Fuel goes in through input faces, ash comes out of output faces, and unsided queries get both.
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        if (mode == null) {
            return automation;
        }
        return switch (mode) {
            case INPUT -> fuelInput;
            case OUTPUT -> ashOutput;
            default -> null;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (input.getIntOr("slot_layout", 1) < SLOT_LAYOUT) {
            // Layout 1 was fuel, then the upgrade slots: move the upgrades up past the new ash slot.
            items.ensureSize(MACHINE_SLOTS + UPGRADE_SLOTS);
            for (int slot = MACHINE_SLOTS + UPGRADE_SLOTS - 1; slot > SLOT_ASH; slot--) {
                items.setStack(slot, items.getStack(slot - 1));
            }
            items.setStack(SLOT_ASH, ItemStack.EMPTY);
        }
        burnTime = input.getIntOr("burn_time", 0);
        burnTotal = input.getIntOr("burn_total", 0);
        burning = input.getString("burning")
                .map(Identifier::tryParse)
                .flatMap(id -> id == null ? java.util.Optional.empty() : BuiltInRegistries.ITEM.getOptional(id))
                .orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("slot_layout", SLOT_LAYOUT);
        output.putInt("burn_time", burnTime);
        output.putInt("burn_total", burnTotal);
        if (burning != null) {
            output.putString("burning", BuiltInRegistries.ITEM.getKey(burning).toString());
        }
    }
}
