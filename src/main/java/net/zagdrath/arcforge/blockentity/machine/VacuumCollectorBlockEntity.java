/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.conduit.filter.FilterSettings;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.experience.LiquidExperience;
import net.zagdrath.arcforge.item.tool.MachineSettings;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.VacuumCollectorMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

import com.mojang.serialization.Codec;

// Pulls dropped items within its range (a cube reaching `range` blocks out from it on every axis) into its
// 18-slot buffer, for energyPerItem FE per item entity, every scanInterval ticks. Items must have been on the
// ground for minItemAge ticks. A Conduit Filter in its filter slot limits what it takes (an unset filter takes
// everything; the filter's direction doesn't matter). Output faces send the buffer on. It also takes experience orbs
// in its range (the same FE per orb) into a Liquid Experience tank (logistics.experience.vacuumXpTank, 20 mB a point), which
// fluid conduits drain from its output faces (and auto-eject pushes out of them).
public class VacuumCollectorBlockEntity extends MachineBlockEntity {
    public static final int SLOT_FILTER = 0;
    public static final int FIRST_BUFFER = 1;
    public static final int BUFFER_SLOTS = 18;
    public static final int MACHINE_SLOTS = FIRST_BUFFER + BUFFER_SLOTS;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.OUTPUT, SideMode.ENERGY);
    // It counts as collecting this long after it last took something.
    private static final int COLLECTING_TICKS = 20;

    // Collectors loaded on this client, for the range outline (see RangeOutlineRenderer). Client side only.
    private static final Set<BlockPos> CLIENT_LOADED = ConcurrentHashMap.newKeySet();

    private final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemOutput;
    private final FilteredFluidTank xpTank;
    private final ResourceHandler<FluidResource> xpOutput;
    private final ContainerData data;

    private int range = ArcforgeConfig.VACUUM_DEFAULT_RANGE.getAsInt();
    private int scanTimer;
    private long lastCollected = Long.MIN_VALUE / 2;
    private int collected;

    public VacuumCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.VACUUM_COLLECTOR.get(), pos, state, MACHINE_SLOTS, VacuumCollectorBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        items.setSlotLimit(SLOT_FILTER, 1);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.VACUUM_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.VACUUM_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot >= FIRST_BUFFER && slot < MACHINE_SLOTS);
        this.xpTank = new FilteredFluidTank(ArcforgeConfig.VACUUM_XP_TANK.getAsInt(), LiquidExperience::is, this::setChanged);
        this.xpOutput = new SidedResourceHandler<>(xpTank, false, true, ArcforgeConfig.VACUUM_XP_OUTPUT_RATE.getAsInt());
        this.data = new WideIntContainerData(VacuumCollectorMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case VacuumCollectorMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case VacuumCollectorMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case VacuumCollectorMenu.DATA_RANGE -> range;
                    case VacuumCollectorMenu.DATA_MAX_RANGE -> maxRange();
                    case VacuumCollectorMenu.DATA_STATUS -> status.ordinal();
                    case VacuumCollectorMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case VacuumCollectorMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    case VacuumCollectorMenu.DATA_XP -> xpTank.getAmount();
                    case VacuumCollectorMenu.DATA_XP_CAPACITY -> xpTank.getCapacity();
                    default -> 0;
                };
            }
        };
    }

    // Only the filter slot takes anything by hand, and only a Conduit Filter.
    public static boolean isItemValid(int slot, ItemResource resource) {
        return slot == SLOT_FILTER && resource.getItem() == ModItems.CONDUIT_FILTER.get();
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        MachineItemHandler items = new MachineItemHandler(MACHINE_SLOTS, VacuumCollectorBlockEntity::isItemValid, UPGRADES, () -> {});
        items.setSlotLimit(SLOT_FILTER, 1);
        return items;
    }

    public static int maxRange() {
        return ArcforgeConfig.VACUUM_MAX_RANGE.getAsInt();
    }

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        int clamped = Math.max(1, Math.min(maxRange(), range));
        if (clamped != this.range) {
            this.range = clamped;
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    // The volume it collects from: a cube reaching `range` blocks out from the block on every axis.
    public static AABB area(BlockPos pos, int range) {
        return new AABB(pos).inflate(range);
    }

    // FE per item entity taken: Energy upgrades cut it.
    public int energyPerItem() {
        return (int) Math.ceil(ArcforgeConfig.VACUUM_ENERGY_PER_ITEM.getAsInt() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // Whether its filter lets this through: no filter, or an unset one, takes everything.
    public boolean accepts(ItemStack stack) {
        ItemStack filter = items.getStack(SLOT_FILTER);
        if (filter.isEmpty() || FilterSettings.mode(filter) == FilterSettings.Mode.UNSET) {
            return true;
        }
        return FilterSettings.of(filter).matches(stack);
    }

    public boolean isFiltered() {
        ItemStack filter = items.getStack(SLOT_FILTER);
        return !filter.isEmpty() && FilterSettings.mode(filter) != FilterSettings.Mode.UNSET;
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!redstoneAllows(level)) {
            status = MachineStatus.DISABLED;
        } else if (--scanTimer <= 0) {
            scanTimer = UpgradeType.time(ArcforgeConfig.VACUUM_SCAN_INTERVAL.getAsInt(), upgrades(UpgradeType.SPEED));
            status = scan(level, pos);
        }
        setLit(status == MachineStatus.COLLECTING);
        autoEject(level, itemOutput);
        if (isAutoEject() && xpTank.getAmount() > 0) {
            outputs.pushFluid(level, pos, getFacing(), sideConfig, xpOutput, ArcforgeConfig.VACUUM_XP_OUTPUT_RATE.getAsInt(), null);
        }
    }

    private MachineStatus scan(ServerLevel level, BlockPos pos) {
        int minAge = ArcforgeConfig.VACUUM_MIN_ITEM_AGE.getAsInt();
        List<ItemEntity> found = level.getEntitiesOfClass(ItemEntity.class, area(pos, range), entity -> entity.isAlive() && entity.getAge() >= minAge);
        boolean full = false;
        for (ItemEntity entity : found) {
            ItemStack stack = entity.getItem();
            if (!accepts(stack)) {
                continue;
            }
            int cost = energyPerItem();
            if (energy.getAmountAsInt() < cost) {
                return MachineStatus.NO_POWER;
            }
            int taken = insert(stack);
            if (taken <= 0) {
                full = true;
                continue;
            }
            energy.consume(cost);
            level.sendParticles(ParticleTypes.PORTAL, entity.getX(), entity.getY() + 0.2, entity.getZ(), 6, 0.1, 0.1, 0.1, 0.2);
            if (taken >= stack.getCount()) {
                entity.discard();
            } else {
                entity.setItem(stack.copyWithCount(stack.getCount() - taken));
            }
            collected++;
            lastCollected = level.getGameTime();
            setChanged();
        }
        if (xpTank.getCapacity() > 0) {
            for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, area(pos, range), Entity::isAlive)) {
                int cost = energyPerItem();
                if (energy.getAmountAsInt() < cost) {
                    return MachineStatus.NO_POWER;
                }
                int amount = LiquidExperience.points(level, orb) * LiquidExperience.MB_PER_POINT;
                if (amount > xpTank.getSpace()) {
                    full = true;
                    continue;
                }
                try (Transaction transaction = Transaction.openRoot()) {
                    if (xpTank.insert(0, LiquidExperience.resource(), amount, transaction) != amount) {
                        full = true;
                        continue;
                    }
                    transaction.commit();
                }
                energy.consume(cost);
                level.sendParticles(ParticleTypes.PORTAL, orb.getX(), orb.getY() + 0.1, orb.getZ(), 4, 0.1, 0.1, 0.1, 0.2);
                orb.discard();
                lastCollected = level.getGameTime();
                setChanged();
            }
        }
        if (level.getGameTime() - lastCollected < COLLECTING_TICKS) {
            return MachineStatus.COLLECTING;
        }
        return full ? MachineStatus.OUTPUT_FULL : MachineStatus.IDLE;
    }

    public FilteredFluidTank getXpTank() {
        return xpTank;
    }

    // Puts as much of the stack as fits into the buffer; returns how many.
    private int insert(ItemStack stack) {
        int left = stack.getCount();
        for (int slot = FIRST_BUFFER; slot < MACHINE_SLOTS && left > 0; slot++) {
            ItemStack held = items.getStack(slot);
            if (!held.isEmpty() && ItemStack.isSameItemSameComponents(held, stack)) {
                int moved = Math.min(left, held.getMaxStackSize() - held.getCount());
                if (moved > 0) {
                    items.setStack(slot, held.copyWithCount(held.getCount() + moved));
                    left -= moved;
                }
            }
        }
        for (int slot = FIRST_BUFFER; slot < MACHINE_SLOTS && left > 0; slot++) {
            if (items.getStack(slot).isEmpty()) {
                int moved = Math.min(left, stack.getMaxStackSize());
                items.setStack(slot, stack.copyWithCount(moved));
                left -= moved;
            }
        }
        return stack.getCount() - left;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    // Item entities taken since it was loaded, for tests.
    public int getCollected() {
        return collected;
    }

    // --- The client's list of loaded collectors, for the range outline ---

    public static Set<BlockPos> clientLoaded() {
        return CLIENT_LOADED;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide()) {
            CLIENT_LOADED.add(worldPosition.immutable());
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide()) {
            CLIENT_LOADED.remove(worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && level.isClientSide()) {
            CLIENT_LOADED.remove(worldPosition);
        }
        super.onChunkUnloaded();
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.OUTPUT ? itemOutput : null;
    }

    // Its Liquid Experience, drained from output faces.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.OUTPUT ? xpOutput : null;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM, FLUID -> mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    // The Settings Card also copies its range, and its filter's settings onto a filter already in the target's slot.
    @Override
    public void writeSettings(ValueOutput output) {
        super.writeSettings(output);
        output.putInt("range", range);
        ItemStack filter = items.getStack(SLOT_FILTER);
        if (!filter.isEmpty()) {
            output.store("filter", FilterSettings.CODEC, FilterSettings.of(filter));
        }
    }

    @Override
    public int readSettings(ValueInput input) {
        input.getInt("range").ifPresent(this::setRange);
        int skipped = 0;
        var filter = input.read("filter", FilterSettings.CODEC);
        if (filter.isPresent()) {
            ItemStack installed = items.getStack(SLOT_FILTER);
            if (installed.isEmpty()) {
                skipped++;
            } else {
                ItemStack copy = installed.copy();
                copy.set(net.zagdrath.arcforge.registry.ModDataComponents.CONDUIT_FILTER.get(), filter.get());
                items.setStack(SLOT_FILTER, copy);
            }
        }
        return skipped + super.readSettings(input);
    }

    @Override
    public List<Component> describe(ValueInput input) {
        List<Component> lines = new java.util.ArrayList<>(super.describe(input));
        input.getInt("range").ifPresent(value -> lines.add(Component.translatable("gui.arcforge.vacuum.range", value)));
        if (input.read("filter", FilterSettings.CODEC).isPresent()) {
            lines.add(Component.translatable("settings.arcforge.filters", 1));
        }
        return lines;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        xpTank.deserialize(input.childOrEmpty("xp_tank"));
        range = Math.max(1, Math.min(maxRange(), input.getIntOr("range", ArcforgeConfig.VACUUM_DEFAULT_RANGE.getAsInt())));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        xpTank.serialize(output.child("xp_tank"));
        output.putInt("range", range);
    }

    // Clients need the range for the outline.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.vacuum_collector");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new VacuumCollectorMenu(containerId, inventory, worldPosition, items, data);
    }
}
