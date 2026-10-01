/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.ThermalEvaporatorMenu;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.ThermalEvaporatorStructure;
import net.zagdrath.arcforge.recipe.EvaporatingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Thermal Evaporator Array (see ThermalEvaporatorStructure), run by its controller. It boils its input down with heat
// (arcforge:evaporating recipes): Seawater into Brine, Brine into Salt, each returning most of the steam as Water.
//  - It works only at thermalEvaporator.minTemperature (100°C) or hotter, and faster the hotter it is: minSpeed of its
//    throughput at minTemperature, rising in a straight line to the full throughput at fullSpeedTemperature.
//  - Each mB evaporated takes the recipe's heat per mB from its heat buffer; with less heat than that, it does what the
//    heat it has allows.
//  - Every recipe input.amount mB evaporated gives the recipe's fluid result (into the output tank) and item result
//    (into the Salt slot), and its water (into the Water tank; what doesn't fit is lost as steam). It stops while the
//    output tank or Salt slot can't take the next result.
// It does IO only through its ports: Seawater or Brine in (Input), heat in (Heat), the fluid result out (Brine), Salt out
// (Salt) and Water out (Water); the outputs are pushed with auto-eject. Clients get the input tank and whether it's
// making Salt, for the renderer's fluid column and salt bed.
public class ThermalEvaporatorBlockEntity extends MachineBlockEntity implements MultiblockController {
    public static final int SLOT_SALT = 0;
    public static final int MACHINE_SLOTS = 1;
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.HEAT, SideMode.BRINE, SideMode.SALT,
            SideMode.WATER);
    private static final int REDSTONE_CHECK_INTERVAL = 10;
    // Clients hear about the input tank when it moves by a step of this many (of its capacity).
    private static final int LEVEL_STEPS = 64;

    private ThermalEvaporatorStructure.@Nullable Tower tower;
    // Client: the renderer's window tiles (see windowQuads), the tower they were found for, and when.
    private static final int WINDOW_REFRESH_INTERVAL = 20;
    private @Nullable Set<Long> windowQuads;
    private ThermalEvaporatorStructure.@Nullable Tower windowQuadsTower;
    private long windowQuadsTime;
    private boolean checkRequested = true;
    private boolean powered;

    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final FilteredFluidTank input;
    private final FilteredFluidTank output;
    private final FilteredFluidTank water;
    private final ResourceHandler<FluidResource> inputHandler;
    private final ResourceHandler<FluidResource> outputHandler;
    private final ResourceHandler<FluidResource> waterHandler;
    private final ResourceHandler<FluidResource> fluidAutomation;
    private final ResourceHandler<ItemResource> saltOutput;
    private final ContainerData data;

    // mB evaporated but not yet drained from the tank (the rate isn't whole), and mB towards the next result.
    private double pending;
    private double progress;
    // mB/t evaporating now, and HU/t used.
    private double rate;
    private int heatUsage;
    private boolean running;
    // Client side (synced): whether its current recipe makes an item (the salt bed shows while it does and runs).
    private boolean makingSalt;
    private int lastSyncKey = Integer.MIN_VALUE;
    // Client only: the fill the renderer draws, easing toward the synced one (-1 until first drawn).
    private float shownLevel = -1.0F;
    // Gives the structure its first ports (none for a new one; see MultiblockPorts.Defaults).
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();

    public ThermalEvaporatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.THERMAL_EVAPORATOR.get(), pos, state, MACHINE_SLOTS, ThermalEvaporatorBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE),
                SIDE_MODES);
        this.heat = new HeatBuffer(ArcforgeConfig.EVAPORATOR_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.EVAPORATOR_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        this.input = new FilteredFluidTank(ArcforgeConfig.EVAPORATOR_INPUT_CAPACITY.getAsInt(),
                resource -> MachineRecipes.isEvaporatingInput(level, resource), this::setChanged);
        this.output = new FilteredFluidTank(ArcforgeConfig.EVAPORATOR_OUTPUT_CAPACITY.getAsInt(), resource -> !Gases.isGas(resource),
                this::setChanged);
        this.water = new FilteredFluidTank(ArcforgeConfig.EVAPORATOR_WATER_CAPACITY.getAsInt(),
                resource -> resource.getFluid() == Fluids.WATER, this::setChanged);
        this.inputHandler = new AutomationResourceHandler<>(input, index -> true, index -> false);
        this.outputHandler = new AutomationResourceHandler<>(output, index -> false, index -> true);
        this.waterHandler = new AutomationResourceHandler<>(water, index -> false, index -> true);
        this.fluidAutomation = new CombinedResourceHandler<>(inputHandler, outputHandler, waterHandler);
        this.saltOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_SALT);
        this.data = new WideIntContainerData(ThermalEvaporatorMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case ThermalEvaporatorMenu.DATA_INPUT_FLUID -> fluidId(input);
                    case ThermalEvaporatorMenu.DATA_INPUT -> input.getAmount();
                    case ThermalEvaporatorMenu.DATA_INPUT_CAPACITY -> input.getCapacity();
                    case ThermalEvaporatorMenu.DATA_OUTPUT_FLUID -> fluidId(output);
                    case ThermalEvaporatorMenu.DATA_OUTPUT -> output.getAmount();
                    case ThermalEvaporatorMenu.DATA_OUTPUT_CAPACITY -> output.getCapacity();
                    case ThermalEvaporatorMenu.DATA_WATER -> water.getAmount();
                    case ThermalEvaporatorMenu.DATA_WATER_CAPACITY -> water.getCapacity();
                    case ThermalEvaporatorMenu.DATA_HEAT -> heat.getStored();
                    case ThermalEvaporatorMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case ThermalEvaporatorMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case ThermalEvaporatorMenu.DATA_MIN_TEMPERATURE -> minTemperature();
                    case ThermalEvaporatorMenu.DATA_HEAT_USAGE -> heatUsage;
                    case ThermalEvaporatorMenu.DATA_RATE -> (int) Math.round(rate * 100);
                    case ThermalEvaporatorMenu.DATA_PROGRESS -> (int) progress;
                    case ThermalEvaporatorMenu.DATA_TOTAL -> currentInputAmount();
                    case ThermalEvaporatorMenu.DATA_STATUS -> status.ordinal();
                    case ThermalEvaporatorMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case ThermalEvaporatorMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // The Salt slot takes nothing in: the tower fills it.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return false;
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    public static int minTemperature() {
        return ArcforgeConfig.EVAPORATOR_MIN_TEMPERATURE.getAsInt();
    }

    // Share of the throughput at this temperature: 0 below minTemperature, minSpeed at it, 1 at fullSpeedTemperature.
    public static double speedAt(int celsius) {
        int min = minTemperature();
        if (celsius < min) {
            return 0.0;
        }
        int full = Math.max(min + 1, ArcforgeConfig.EVAPORATOR_FULL_SPEED_TEMPERATURE.getAsInt());
        double minSpeed = ArcforgeConfig.EVAPORATOR_MIN_SPEED.getAsDouble();
        return minSpeed + (1.0 - minSpeed) * Mth.clamp((celsius - min) / (double) (full - min), 0.0, 1.0);
    }

    private static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    private int currentInputAmount() {
        return level != null && input.getAmount() > 0
                ? MachineRecipes.evaporating(level, input.getResource(0)).map(holder -> holder.value().input().amount()).orElse(0) : 0;
    }

    // --- Structure ---

    @Override
    public boolean isFormed() {
        return tower != null;
    }

    public void requestCheck() {
        checkRequested = true;
    }

    public void checkNow() {
        if (level instanceof ServerLevel serverLevel) {
            checkRequested = false;
            updateFormed(serverLevel);
        }
    }

    // Breaking the controller un-forms the rest of the tower, so a new controller can claim it.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && tower != null) {
            ThermalEvaporatorStructure.unform(serverLevel, tower, pos);
            tower = null;
        }
    }

    private void updateFormed(ServerLevel level) {
        ThermalEvaporatorStructure.Tower found = ThermalEvaporatorStructure.find(level, worldPosition, tower);
        if (Objects.equals(found, tower)) {
            return;
        }
        if (tower != null) {
            ThermalEvaporatorStructure.unform(level, tower);
        }
        tower = found;
        if (found != null) {
            ThermalEvaporatorStructure.form(level, found, worldPosition);
            MultiblockEffects.formed(level, found.min(), found.max());
            ArcforgeAdvancements.formed(level, this, null);
        }
        running = false;
        setChanged();
        sync(level);
    }

    public ThermalEvaporatorStructure.@Nullable Tower getTower() {
        return tower;
    }

    @Override
    public Direction getStructureFacing() {
        return getFacing();
    }

    @Override
    public BlockPos getMinCorner() {
        return tower != null ? tower.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return tower != null ? tower.max() : worldPosition;
    }

    // The tower's blocks, not its hollow core.
    @Override
    public boolean isPart(BlockPos pos) {
        return tower != null && tower.contains(pos) && !tower.isCore(pos);
    }

    @Override
    public int length() {
        return ThermalEvaporatorStructure.HEIGHT;
    }

    // --- Running ---

    public void serverTick(ServerLevel level) {
        if (checkRequested) {
            checkNow();
        }
        rate = 0;
        heatUsage = 0;
        if (tower == null) {
            status = MachineStatus.NOT_FORMED;
            setRunning(false);
            syncIfChanged(level);
            return;
        }
        portDefaults.tick(level, this);
        if (level.getGameTime() % REDSTONE_CHECK_INTERVAL == 0) {
            powered = isPowered(level);
        }
        status = redstoneMode.canRun(powered) ? evaporate(level) : MachineStatus.DISABLED;
        setRunning(status == MachineStatus.EVAPORATING);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this);
        }
        syncIfChanged(level);
    }

    private MachineStatus evaporate(ServerLevel level) {
        Optional<RecipeHolder<EvaporatingRecipe>> holder = input.getAmount() > 0 ? MachineRecipes.evaporating(level, input.getResource(0))
                : Optional.empty();
        makingSalt = holder.isPresent() && holder.get().value().itemResult().isPresent();
        if (holder.isEmpty()) {
            return MachineStatus.NO_FLUID;
        }
        EvaporatingRecipe recipe = holder.get().value();
        if (!hasRoomFor(recipe)) {
            return MachineStatus.OUTPUT_FULL;
        }
        double speed = speedAt(heat.getTemperature());
        if (speed <= 0) {
            return MachineStatus.TOO_COLD;
        }
        double want = Math.min(ArcforgeConfig.EVAPORATOR_THROUGHPUT.getAsInt() * speed, input.getAmount() - pending);
        // What the heat it holds allows.
        double perMb = recipe.heatPerMb();
        double affordable = perMb > 0 ? heat.getStored() / perMb : want;
        double mb = Math.min(want, affordable);
        if (mb <= 1.0E-6) {
            return MachineStatus.NO_HEAT;
        }
        int hu = (int) Math.ceil(mb * perMb - 1.0E-9);
        heatUsage = heat.remove(Math.min(hu, heat.getStored()));
        rate = mb;
        pending += mb;
        int drain = Math.min((int) pending, input.getAmount());
        if (drain > 0) {
            pending -= drain;
            try (Transaction tx = Transaction.openRoot()) {
                input.extract(0, input.getResource(0), drain, tx);
                tx.commit();
            }
            if (input.getAmount() == 0) {
                pending = 0;
            }
        }
        progress += mb;
        int per = recipe.input().amount();
        while (progress >= per && hasRoomFor(recipe)) {
            progress -= per;
            finish(recipe);
        }
        setChanged();
        return MachineStatus.EVAPORATING;
    }

    // Whether the next result fits: the fluid result in the output tank (one fluid at a time) and the item in the slot.
    private boolean hasRoomFor(EvaporatingRecipe recipe) {
        if (recipe.fluidResult().isPresent()) {
            FluidStack made = recipe.fluidResult().get().create();
            if (output.getAmount() > 0 && !output.getResource(0).equals(FluidResource.of(made)) || output.getSpace() < made.getAmount()) {
                return false;
            }
        }
        if (recipe.itemResult().isPresent()) {
            ItemStack made = recipe.itemResult().get().create();
            ItemStack slot = items.getStack(SLOT_SALT);
            return slot.isEmpty() || ItemStack.isSameItemSameComponents(slot, made) && slot.getCount() + made.getCount() <= slot.getMaxStackSize();
        }
        return true;
    }

    private void finish(EvaporatingRecipe recipe) {
        recipe.fluidResult().ifPresent(template -> {
            FluidStack made = template.create();
            try (Transaction tx = Transaction.openRoot()) {
                output.insert(0, FluidResource.of(made), made.getAmount(), tx);
                tx.commit();
            }
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, made.getFluid(), "evaporating");
        });
        recipe.itemResult().ifPresent(template -> {
            ItemStack made = template.create();
            ItemStack slot = items.getStack(SLOT_SALT);
            items.setStack(SLOT_SALT, slot.isEmpty() ? made : slot.copyWithCount(slot.getCount() + made.getCount()));
            ArcforgeAdvancements.produced(this, made, null, "evaporating");
        });
        if (recipe.water() > 0) {
            // Water that doesn't fit is lost as steam.
            try (Transaction tx = Transaction.openRoot()) {
                water.insert(0, FluidResource.of(Fluids.WATER), Math.min(recipe.water(), water.getSpace()), tx);
                tx.commit();
            }
        }
    }

    private boolean isPowered(ServerLevel level) {
        for (BlockPos pos : tower.positions()) {
            if (!tower.isCore(pos) && level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    private void setRunning(boolean running) {
        if (this.running != running) {
            this.running = running;
            setLit(running);
        }
    }

    @Override
    protected void setLit(boolean lit) {
        if (tower != null || !lit) {
            super.setLit(lit);
        }
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getInput() {
        return input;
    }

    public FilteredFluidTank getOutput() {
        return output;
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    public ItemStack getSalt() {
        return items.getStack(SLOT_SALT);
    }

    // mB/t evaporating now.
    public double getRate() {
        return rate;
    }

    public int getHeatUsage() {
        return heatUsage;
    }

    public boolean isRunning() {
        return running;
    }

    // --- The renderer: the liquid inside the tower, the salt bed and the windows ---

    // The input's fluid, how full its tank is (0-1), and whether the salt bed shows.
    public Fluid getColumnFluid() {
        return input.getAmount() > 0 ? input.getResource(0).getFluid() : Fluids.EMPTY;
    }

    public float columnLevel() {
        return input.getCapacity() > 0 ? Math.min(1.0F, input.getAmount() / (float) input.getCapacity()) : 0.0F;
    }

    public boolean showsSaltBed() {
        return makingSalt && running;
    }

    // Moves the drawn level a share of the way to the synced one each frame, starting there.
    public float easeLevel(float target, float share) {
        shownLevel = shownLevel < 0 ? target : shownLevel + (target - shownLevel) * share;
        return shownLevel;
    }

    // The renderer's window tiles for this tower (the lining left out behind its windows), worked out by find when the
    // tower changes, and again now and then, as the panes' own block updates can reach the client after this one's.
    public Set<Long> windowQuads(Function<ThermalEvaporatorStructure.Tower, Set<Long>> find) {
        if (tower == null) {
            return Set.of();
        }
        long time = level != null ? level.getGameTime() : 0L;
        if (windowQuads == null || !tower.equals(windowQuadsTower) || Math.abs(time - windowQuadsTime) >= WINDOW_REFRESH_INTERVAL) {
            windowQuads = find.apply(tower);
            windowQuadsTower = tower;
            windowQuadsTime = time;
        }
        return windowQuads;
    }

    public AABB getRenderBox() {
        return tower != null ? AABB.encapsulatingFullBlocks(tower.min(), tower.max()) : new AABB(worldPosition);
    }

    private int syncKey() {
        int capacity = Math.max(1, input.getCapacity());
        int step = input.getAmount() == 0 ? 0 : 1 + input.getAmount() * LEVEL_STEPS / capacity;
        return Objects.hash(tower, step, fluidId(input), showsSaltBed());
    }

    private void syncIfChanged(ServerLevel level) {
        if (syncKey() != lastSyncKey) {
            sync(level);
        }
    }

    private void sync(Level level) {
        lastSyncKey = syncKey();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return saltOutput;
        }
        return mode == SideMode.SALT ? saltOutput : null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return fluidAutomation;
        }
        return switch (mode) {
            case INPUT -> inputHandler;
            case BRINE -> outputHandler;
            case WATER -> waterHandler;
            default -> null;
        };
    }

    public @Nullable HeatHandler heatHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return heatInput;
        }
        return faceMode(pos, side) == SideMode.HEAT ? heatInput : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case ITEM -> mode == SideMode.SALT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT
                    : mode == SideMode.BRINE || mode == SideMode.WATER ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS, ENERGY -> ConnectionMode.NONE;
        };
    }

    // Conduits next to the controller ask it about the tower face it lies on.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    @Override
    protected void onSideConfigChanged() {
        setChanged();
        if (level != null && tower != null) {
            MultiblockAutomation.refresh(level, tower.min(), tower.max());
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        tower = in.getLong("tower_min").map(min -> new ThermalEvaporatorStructure.Tower(BlockPos.of(min),
                Direction.from2DDataValue(in.getIntOr("tower_front", 0)))).orElse(null);
        heat.deserialize(in);
        input.deserialize(in.childOrEmpty("input"));
        output.deserialize(in.childOrEmpty("output"));
        water.deserialize(in.childOrEmpty("water"));
        pending = in.getDoubleOr("pending", 0.0);
        progress = in.getDoubleOr("progress", 0.0);
        running = in.getBooleanOr("running", false);
        makingSalt = in.getBooleanOr("making_salt", false);
        portDefaults.load(in);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (tower != null) {
            out.putLong("tower_min", tower.min().asLong());
            out.putInt("tower_front", tower.front().get2DDataValue());
        }
        heat.serialize(out);
        input.serialize(out.child("input"));
        output.serialize(out.child("output"));
        water.serialize(out.child("water"));
        out.putDouble("pending", pending);
        out.putDouble("progress", progress);
        out.putBoolean("running", running);
        out.putBoolean("making_salt", makingSalt);
        portDefaults.save(out);
    }

    // Clients get the saved state when the chunk loads and whenever the tower, its fill or its salt bed changes.
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
        return Component.translatable("container.arcforge.thermal_evaporator");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ThermalEvaporatorMenu(containerId, inventory, worldPosition, items, data);
    }
}
