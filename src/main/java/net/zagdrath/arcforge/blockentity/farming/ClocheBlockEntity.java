/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.farming.CropRotation;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.farming.ClochePlants;
import net.zagdrath.arcforge.farming.ClocheSoil;
import net.zagdrath.arcforge.item.farming.FertilizerItem;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.ClocheMenu;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The shared workings of the Glass Cloche, Grow Chamber and Hydroponic Cell: a seed (and, but in the Hydroponic Cell, a
// soil) sits in the farm and grows over the plant's time (ClochePlants: its arcforge:cloche recipe, or a vanilla-style
// crop's fallback), at the farm's speed x the soil's growth x the fertilizer bonus (x any bonus of the farm's own). Each
// harvest first takes its water (or Nutrient Solution) from the tank, and one fertilizer point if there is any; when
// grown, the harvest goes into the four output slots (it waits, full grown, while they can't take it all), and the seed
// grows again. The seed and soil are never used up.
//
// Clients get the seed, soil and growth (on every 1/16 of growth, and when the slots change) to draw the plant inside.
public abstract class ClocheBlockEntity extends MachineBlockEntity implements FluidInteractable {
    public static final int SLOT_SEED = 0;
    public static final int SLOT_SOIL = 1;
    public static final int SLOT_FERTILIZER = 2;
    public static final int SLOT_OUTPUT_FIRST = 3;
    public static final int OUTPUT_SLOTS = 4;
    public static final int MACHINE_SLOTS = SLOT_OUTPUT_FIRST + OUTPUT_SLOTS;
    // Growth steps clients are told about: the plant's drawn stage changes on each.
    private static final int SYNC_STEPS = 16;

    public enum Kind {
        GLASS_CLOCHE("glass_cloche", false, false),
        GROW_CHAMBER("grow_chamber", false, true),
        HYDROPONIC_CELL("hydroponic_cell", true, true);

        private final String id;
        private final boolean hydroponic;
        private final boolean powered;

        Kind(String id, boolean hydroponic, boolean powered) {
            this.id = id;
            this.hydroponic = hydroponic;
            this.powered = powered;
        }

        public String id() {
            return id;
        }

        // No soil: grows in Nutrient Solution, and grows the hydroponic-only recipes too.
        public boolean hydroponic() {
            return hydroponic;
        }

        public boolean powered() {
            return powered;
        }
    }

    protected final Kind kind;
    protected final @Nullable ConsumerEnergyHandler energy;
    // Water, or Nutrient Solution in the Hydroponic Cell.
    protected final FilteredFluidTank tank;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;
    private @Nullable ResourceHandler<FluidResource> fluidInput;

    // Growth so far, in ticks at speed 1, towards the plant's time.
    private double progress;
    private int total;
    // The plant growing (progress starts over when it changes), and whether this harvest's water/nutrients are paid.
    private @Nullable Identifier plantKey;
    private boolean cycling;
    // Fertilizer points left (each harvest uses one), whether they're from an enriching fertilizer, and this
    // harvest's bonus from them.
    private int fertilizer;
    private boolean enriched;
    private float cycleBonus = 1.0F;
    // This tick's growth per tick (x100 for the GUI) and FE/t.
    private double rate;
    protected int usage;

    // Looked up again when the seed or soil changes, and every second.
    private ClochePlants.@Nullable Plant plant;
    private @Nullable Item plantSeed;
    private @Nullable Item plantSoil;
    private int plantAge;
    // What clients were last told.
    private int syncedStep = -1;
    private @Nullable Item syncedSeed;
    private @Nullable Item syncedSoil;

    protected ClocheBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, Kind kind, Set<UpgradeType> upgrades, SideConfig sides,
            List<SideMode> allowedSideModes, int energyCapacity, int energyInput, int tankCapacity, Predicate<FluidResource> tankFilter) {
        super(type, pos, state, MACHINE_SLOTS, (level, slot, resource) -> isItemValid(kind, level, slot, resource), upgrades, sides, allowedSideModes);
        this.kind = kind;
        // energyCapacity: 0 for an unpowered farm.
        this.energy = energyCapacity > 0 ? new ConsumerEnergyHandler(energyCapacity, energyInput, this::setChanged) : null;
        this.tank = new FilteredFluidTank(tankCapacity, tankFilter, this::setChanged);
        items.setSlotLimit(SLOT_SEED, 1);
        items.setSlotLimit(SLOT_SOIL, 1);
        this.itemInput = new AutomationResourceHandler<>(items, ClocheBlockEntity::isInputSlot, slot -> false);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, ClocheBlockEntity::isOutputSlot);
        this.itemAutomation = new AutomationResourceHandler<>(items, ClocheBlockEntity::isInputSlot, ClocheBlockEntity::isOutputSlot);
        this.data = new WideIntContainerData(ClocheMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case ClocheMenu.DATA_ENERGY -> ClocheBlockEntity.this.energy != null ? ClocheBlockEntity.this.energy.getAmountAsInt() : 0;
                    case ClocheMenu.DATA_ENERGY_CAPACITY -> ClocheBlockEntity.this.energy != null ? ClocheBlockEntity.this.energy.getCapacityAsInt() : 0;
                    case ClocheMenu.DATA_FLUID -> tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
                    case ClocheMenu.DATA_FLUID_AMOUNT -> tank.getAmount();
                    case ClocheMenu.DATA_FLUID_CAPACITY -> tank.getCapacity();
                    case ClocheMenu.DATA_GAS_AMOUNT -> gasAmount();
                    case ClocheMenu.DATA_GAS_CAPACITY -> gasCapacity();
                    case ClocheMenu.DATA_PROGRESS -> (int) progress;
                    case ClocheMenu.DATA_TOTAL -> total;
                    case ClocheMenu.DATA_USAGE -> usage;
                    case ClocheMenu.DATA_FERTILIZER -> fertilizer;
                    case ClocheMenu.DATA_RATE -> (int) Math.round(rate * 100);
                    case ClocheMenu.DATA_STATUS -> status.ordinal();
                    case ClocheMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ClocheMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    private static boolean isInputSlot(int slot) {
        return slot == SLOT_SEED || slot == SLOT_SOIL || slot == SLOT_FERTILIZER;
    }

    private static boolean isOutputSlot(int slot) {
        return slot >= SLOT_OUTPUT_FIRST && slot < MACHINE_SLOTS;
    }

    // The seed slot takes anything that grows in some farm, the soil slot the arcforge:cloche_soils (not in the
    // Hydroponic Cell), the fertilizer slot fertilizers and bone meal. The client passes a null level and checks
    // against the recipes the server synced.
    public static boolean isItemValid(Kind kind, @Nullable Level level, int slot, ItemResource resource) {
        ItemStack stack = resource.toStack(1);
        return switch (slot) {
            case SLOT_SEED -> ClochePlants.isSeed(level, stack);
            case SLOT_SOIL -> !kind.hydroponic() && ClocheSoil.of(stack) != null;
            case SLOT_FERTILIZER -> fertilizerPoints(stack) > 0;
            default -> false;
        };
    }

    public static MachineItemHandler clientItems(Kind kind, Set<UpgradeType> upgrades) {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(kind, null, slot, resource), upgrades, () -> {});
    }

    // Nutrient points one of this item gives: a fertilizer's own, 1 for bone meal.
    public static int fertilizerPoints(ItemStack stack) {
        if (stack.getItem() instanceof FertilizerItem fertilizerItem) {
            return fertilizerItem.nutrients();
        }
        return stack.is(Items.BONE_MEAL) ? 1 : 0;
    }

    // --- What each farm sets ---

    // Growth speed, before the soil and fertilizer (1 = recipe times).
    protected abstract double speed();

    // mB of the tank's fluid each harvest takes.
    protected abstract int fluidPerHarvest();

    // FE/t while growing (0 for an unpowered farm).
    public int energyPerTick() {
        return 0;
    }

    // A further bonus this tick (the Hydroponic Cell's Carbon Dioxide), taking what it needs. 1 for none.
    protected double tickBonus(ServerLevel level) {
        return 1.0;
    }

    // The status when the tank runs short.
    protected MachineStatus noFluidStatus() {
        return MachineStatus.NO_WATER;
    }

    // Called at the end of every server tick: where the harvest goes.
    protected abstract void moveOutputs(ServerLevel level);

    protected int gasAmount() {
        return 0;
    }

    protected int gasCapacity() {
        return 0;
    }

    // --- Ticking ---

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        rate = 0;
        if (!redstoneAllows(level)) {
            status = redstoneMode == net.zagdrath.arcforge.machine.config.RedstoneMode.PULSE ? MachineStatus.WAITING_PULSE : MachineStatus.DISABLED;
        } else {
            status = work(level);
        }
        setLit(status == MachineStatus.GROWING);
        moveOutputs(level);
        syncIfChanged(level);
    }

    private MachineStatus work(ServerLevel level) {
        ItemStack seed = items.getStack(SLOT_SEED);
        if (seed.isEmpty()) {
            reset();
            return MachineStatus.IDLE;
        }
        if (!kind.hydroponic() && items.getStack(SLOT_SOIL).isEmpty()) {
            reset();
            return MachineStatus.NO_SOIL;
        }
        ClochePlants.Plant current = plant();
        if (current == null) {
            reset();
            return MachineStatus.CANT_GROW;
        }
        if (!current.key().equals(plantKey)) {
            reset();
            plantKey = current.key();
        }
        total = current.time();
        // Legumes fix their own nitrogen: no fertilizer, and in the Hydroponic Cell no Nutrient Solution (water they
        // still need), yet they grow at the fertilized rate (CropRotation).
        boolean legume = CropRotation.isLegumeSeed(seed);
        if (!cycling) {
            int needed = legume && kind.hydroponic() ? 0 : fluidPerHarvest();
            if (tank.getAmount() < needed) {
                return noFluidStatus();
            }
            if (needed > 0) {
                try (Transaction tx = Transaction.openRoot()) {
                    tank.extract(0, tank.getResource(0), needed, tx);
                    tx.commit();
                }
            }
            cycleBonus = legume ? (float) ArcforgeConfig.CLOCHE_FERTILIZER_BONUS.getAsDouble() : takeFertilizer();
            cycling = true;
            setChanged();
        }
        if (progress < total) {
            int fe = energyPerTick();
            if (fe > 0) {
                if (energy == null || !energy.consume(fe)) {
                    return MachineStatus.NO_POWER;
                }
                usage = fe;
            }
            rate = speed() * soilGrowth() * cycleBonus * tickBonus(level);
            progress = Math.min(total, progress + rate);
            setChanged();
        }
        if (progress >= total) {
            if (!harvest(level, current)) {
                return MachineStatus.OUTPUT_FULL;
            }
            progress = 0;
            cycling = false;
            // A redstone pulse allows one harvest.
            consumePulse();
            setChanged();
        }
        return MachineStatus.GROWING;
    }

    // One fertilizer point for this harvest, opening a new fertilizer item when out: its bonus, or 1 without.
    private float takeFertilizer() {
        if (fertilizer <= 0) {
            ItemStack stack = items.getStack(SLOT_FERTILIZER);
            int points = fertilizerPoints(stack);
            if (points <= 0) {
                return 1.0F;
            }
            fertilizer = points;
            enriched = stack.getItem() instanceof FertilizerItem fertilizerItem && fertilizerItem.enriches();
            items.setStack(SLOT_FERTILIZER, stack.copyWithCount(stack.getCount() - 1));
        }
        fertilizer--;
        return (float) (enriched ? ArcforgeConfig.CLOCHE_ENRICHED_BONUS.getAsDouble() : ArcforgeConfig.CLOCHE_FERTILIZER_BONUS.getAsDouble());
    }

    private double soilGrowth() {
        if (kind.hydroponic()) {
            return 1.0;
        }
        ClocheSoil soil = ClocheSoil.of(items.getStack(SLOT_SOIL));
        return soil != null ? soil.growth() : 1.0;
    }

    // Puts the harvest in the output slots if it all fits.
    private boolean harvest(ServerLevel level, ClochePlants.Plant current) {
        List<ItemStack> harvest = current.harvest(level, worldPosition, level.getRandom());
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            slots.add(items.getStack(SLOT_OUTPUT_FIRST + i).copy());
        }
        for (ItemStack stack : harvest) {
            ItemStack left = stack.copy();
            for (int i = 0; i < OUTPUT_SLOTS && !left.isEmpty(); i++) {
                ItemStack slot = slots.get(i);
                if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, left)) {
                    int moved = Math.min(left.getCount(), slot.getMaxStackSize() - slot.getCount());
                    slot.grow(moved);
                    left.shrink(moved);
                }
            }
            for (int i = 0; i < OUTPUT_SLOTS && !left.isEmpty(); i++) {
                if (slots.get(i).isEmpty()) {
                    int moved = Math.min(left.getCount(), left.getMaxStackSize());
                    slots.set(i, left.copyWithCount(moved));
                    left.shrink(moved);
                }
            }
            if (!left.isEmpty()) {
                return false;
            }
        }
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            items.setStack(SLOT_OUTPUT_FIRST + i, slots.get(i));
        }
        return true;
    }

    private void reset() {
        if (progress != 0 || plantKey != null || total != 0) {
            setChanged();
        }
        progress = 0;
        total = 0;
        plantKey = null;
        // Water and fertilizer already paid for a harvest carry over to the next plant.
    }

    // --- The plant, on both sides ---

    // What grows from the seed and soil in the slots, or null.
    public ClochePlants.@Nullable Plant plant() {
        ItemStack seed = items.getStack(SLOT_SEED);
        ItemStack soil = kind.hydroponic() ? ItemStack.EMPTY : items.getStack(SLOT_SOIL);
        Item seedItem = seed.isEmpty() ? null : seed.getItem();
        Item soilItem = soil.isEmpty() ? null : soil.getItem();
        // Looked up again every second too: after /reload, and on clients whose recipes arrived after the farm did.
        boolean stale = level != null && level.getGameTime() - plantAge >= 20;
        if (seedItem != plantSeed || soilItem != plantSoil || stale) {
            plantSeed = seedItem;
            plantSoil = soilItem;
            plantAge = level != null ? (int) level.getGameTime() : 0;
            plant = seed.isEmpty() ? null : ClochePlants.find(level, seed, soil, kind.hydroponic());
        }
        return plant;
    }

    // How grown the plant is, 0..1 (clients see it in 1/16 steps).
    public float growth() {
        return total <= 0 ? 0.0F : (float) Math.min(1.0, progress / total);
    }

    public ItemStack getSoil() {
        return items.getStack(SLOT_SOIL);
    }

    public Kind kind() {
        return kind;
    }

    public int getFertilizer() {
        return fertilizer;
    }

    public boolean isEnriched() {
        return enriched && fertilizer > 0;
    }

    // Growth per tick last tick (0 when not growing).
    public double getRate() {
        return rate;
    }

    public FilteredFluidTank getTank() {
        return tank;
    }

    public @Nullable ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    private void syncIfChanged(ServerLevel level) {
        int step = (int) (growth() * SYNC_STEPS);
        Item seed = items.getStack(SLOT_SEED).isEmpty() ? null : items.getStack(SLOT_SEED).getItem();
        Item soil = items.getStack(SLOT_SOIL).isEmpty() ? null : items.getStack(SLOT_SOIL).getItem();
        if (step != syncedStep || seed != syncedSeed || soil != syncedSoil) {
            syncedStep = step;
            syncedSeed = seed;
            syncedSoil = soil;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    // Input faces take seeds, soils and fertilizer; output faces give the harvest.
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

    // What output faces (and auto-eject) take from: the output slots.
    protected ResourceHandler<ItemResource> outputHandler() {
        return itemOutput;
    }

    // Input faces fill the tank(s); nothing drains them.
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? fluidInput() : null;
    }

    // Built on first use, after the subclass's own tanks exist.
    private ResourceHandler<FluidResource> fluidInput() {
        if (fluidInput == null) {
            fluidInput = new AutomationResourceHandler<>(inputTanks(), index -> true, index -> false);
        }
        return fluidInput;
    }

    // The tanks input faces and held buckets fill.
    protected ResourceHandler<FluidResource> inputTanks() {
        return tank;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        if (energy == null) {
            return null;
        }
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    // Held buckets fill the tank from any face.
    @Override
    public ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidInput();
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS -> mode == SideMode.INPUT && gasCapacity() > 0 ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY && energy != null ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case THERMAL -> ConnectionMode.NONE;
        };
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (energy != null) {
            energy.deserialize(input.childOrEmpty("energy"));
        }
        tank.deserialize(input.childOrEmpty("tank"));
        progress = input.getDoubleOr("progress", 0.0);
        total = input.getIntOr("total", 0);
        plantKey = input.getString("plant").map(Identifier::tryParse).orElse(null);
        cycling = input.getBooleanOr("cycling", false);
        fertilizer = input.getIntOr("fertilizer", 0);
        enriched = input.getBooleanOr("enriched", false);
        cycleBonus = input.getFloatOr("cycle_bonus", 1.0F);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (energy != null) {
            energy.serialize(output.child("energy"));
        }
        tank.serialize(output.child("tank"));
        output.putDouble("progress", progress);
        output.putInt("total", total);
        if (plantKey != null) {
            output.putString("plant", plantKey.toString());
        }
        output.putBoolean("cycling", cycling);
        output.putInt("fertilizer", fertilizer);
        output.putBoolean("enriched", enriched);
        output.putFloat("cycle_bonus", cycleBonus);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge." + kind.id());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ClocheMenu(kind, containerId, inventory, worldPosition, items, data);
    }
}
