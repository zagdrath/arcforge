/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
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
import net.zagdrath.arcforge.menu.multiblock.BiogasDigesterMenu;
import net.zagdrath.arcforge.multiblock.BiogasDigesterStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.recipe.DigestingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.registry.ModRecipes;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Biogas Digester (see BiogasDigesterStructure), run by its controller. It digests plant matter (crops, seeds,
// leaves, Press Cake, Compost: arcforge:digesting recipes) in water into Biogas, and sometimes Digestate, a few items at
// a time (biogasDigester.lanes, 4): each lane takes one item and its water when it starts, and gives its gas (and rolls
// its Digestate) when it's done. A lane only starts when everything the busy lanes will make still fits. Anaerobic
// digestion needs warmth: it only works at biogasDigester.minTemperature (35°C) or hotter, using heatPerTick HU/t while
// any lane is busy, and pauses (keeping each lane's progress) when it cools. It does IO only through its ports: plant
// matter and water in (Input), Biogas out (Gas Output, pushed every tick), Digestate out (By-product, with auto-eject)
// and heat in (Heat). It scrubs its Biogas as it makes it: biogasDigester.sulfurPerThousandMb Sulfur Dust for every 1,000
// mB, collected in its sulfur slot (a stack at most; more is lost) and given out of Sulfur ports (with auto-eject).
public class BiogasDigesterBlockEntity extends MachineBlockEntity implements MultiblockController {
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_DIGESTATE = 1;
    public static final int SLOT_SULFUR = 2;
    public static final int MACHINE_SLOTS = 3;
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.GAS_OUTPUT, SideMode.BYPRODUCT, SideMode.HEAT,
            SideMode.SULFUR);
    private static final int REDSTONE_CHECK_INTERVAL = 10;

    private BiogasDigesterStructure.@Nullable Tank tank;
    private boolean checkRequested = true;
    private boolean powered;

    private final HeatBuffer heat;
    private final HeatHandler heatInput;
    private final FilteredFluidTank water;
    private final FilteredFluidTank biogas;
    private final ResourceHandler<FluidResource> waterInput;
    private final ResourceHandler<FluidResource> gasOutput;
    private final ResourceHandler<FluidResource> fluidAutomation;
    private final ResourceHandler<ItemResource> itemInput;
    private final ResourceHandler<ItemResource> digestateOutput;
    private final ResourceHandler<ItemResource> sulfurOutput;
    private final ResourceHandler<ItemResource> itemAutomation;
    private final ContainerData data;

    // Each lane's recipe (null when idle), and its progress and length in ticks.
    private @Nullable Identifier[] laneRecipe;
    private int[] laneProgress;
    private int[] laneTotal;
    private int heatUsage;
    private boolean running;
    // Sulfur Dust owed below a whole one, carried to the next batch of Biogas.
    private double sulfurOwed;
    // Gives the structure its first ports (see MultiblockPorts.Defaults).
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();

    public BiogasDigesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.BIOGAS_DIGESTER.get(), pos, state, MACHINE_SLOTS, BiogasDigesterBlockEntity::isItemValid, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE),
                SIDE_MODES);
        this.heat = new HeatBuffer(ArcforgeConfig.DIGESTER_HEAT_CAPACITY.getAsInt(), ArcforgeConfig.DIGESTER_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        this.heatInput = heat.input(Integer.MAX_VALUE);
        this.water = new FilteredFluidTank(ArcforgeConfig.DIGESTER_WATER_CAPACITY.getAsInt(),
                resource -> resource.getFluid().defaultFluidState().is(FluidTags.WATER), this::setChanged);
        this.biogas = new FilteredFluidTank(ArcforgeConfig.DIGESTER_GAS_CAPACITY.getAsInt(), Gases::isGas, this::setChanged);
        this.waterInput = new AutomationResourceHandler<>(water, index -> true, index -> false);
        this.gasOutput = new AutomationResourceHandler<>(biogas, index -> false, index -> true);
        this.fluidAutomation = new CombinedResourceHandler<>(waterInput, gasOutput);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.digestateOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_DIGESTATE);
        this.sulfurOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_SULFUR);
        this.itemAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> slot == SLOT_DIGESTATE || slot == SLOT_SULFUR);
        resizeLanes(ArcforgeConfig.DIGESTER_LANES.getAsInt());
        this.data = new WideIntContainerData(BiogasDigesterMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case BiogasDigesterMenu.DATA_WATER -> water.getAmount();
                    case BiogasDigesterMenu.DATA_WATER_CAPACITY -> water.getCapacity();
                    case BiogasDigesterMenu.DATA_GAS_FLUID -> biogas.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(biogas.getResource(0).getFluid()) : -1;
                    case BiogasDigesterMenu.DATA_GAS -> biogas.getAmount();
                    case BiogasDigesterMenu.DATA_GAS_CAPACITY -> biogas.getCapacity();
                    case BiogasDigesterMenu.DATA_HEAT -> heat.getStored();
                    case BiogasDigesterMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case BiogasDigesterMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case BiogasDigesterMenu.DATA_MIN_TEMPERATURE -> minTemperature();
                    case BiogasDigesterMenu.DATA_HEAT_USAGE -> heatUsage;
                    case BiogasDigesterMenu.DATA_PROGRESS -> leadLane() >= 0 ? laneProgress[leadLane()] : 0;
                    case BiogasDigesterMenu.DATA_TOTAL -> leadLane() >= 0 ? laneTotal[leadLane()] : 0;
                    case BiogasDigesterMenu.DATA_BUSY -> busyLanes();
                    case BiogasDigesterMenu.DATA_LANES -> laneRecipe.length;
                    case BiogasDigesterMenu.DATA_STATUS -> status.ordinal();
                    case BiogasDigesterMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case BiogasDigesterMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // The input slot takes anything some recipe digests. The client passes a null level and checks against the recipes
    // the server synced.
    public static boolean isItemValid(@Nullable Level level, int slot, ItemResource resource) {
        return slot == SLOT_INPUT && MachineRecipes.isDigesterInput(level, resource.toStack(1));
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> isItemValid(null, slot, resource), UPGRADES, () -> {});
    }

    public static int minTemperature() {
        return ArcforgeConfig.DIGESTER_MIN_TEMPERATURE.getAsInt();
    }

    // Lanes carried over, cut off or added (a config change): lanes past the new count are lost.
    private void resizeLanes(int lanes) {
        int count = Math.max(1, lanes);
        laneRecipe = laneRecipe == null ? new Identifier[count] : Arrays.copyOf(laneRecipe, count);
        laneProgress = laneProgress == null ? new int[count] : Arrays.copyOf(laneProgress, count);
        laneTotal = laneTotal == null ? new int[count] : Arrays.copyOf(laneTotal, count);
    }

    public int busyLanes() {
        int busy = 0;
        for (Identifier recipe : laneRecipe) {
            if (recipe != null) {
                busy++;
            }
        }
        return busy;
    }

    // The busy lane nearest done (for the GUI's arrow), or -1.
    private int leadLane() {
        int lead = -1;
        for (int lane = 0; lane < laneRecipe.length; lane++) {
            if (laneRecipe[lane] != null && (lead < 0
                    || (long) laneProgress[lane] * laneTotal[lead] > (long) laneProgress[lead] * laneTotal[lane])) {
                lead = lane;
            }
        }
        return lead;
    }

    // --- Structure ---

    @Override
    public boolean isFormed() {
        return tank != null;
    }

    // Recheck on the next tick (a nearby part was placed or removed).
    public void requestCheck() {
        checkRequested = true;
    }

    public void checkNow() {
        if (level instanceof ServerLevel serverLevel) {
            checkRequested = false;
            updateFormed(serverLevel);
        }
    }

    // Breaking the controller un-forms the rest of the tank: the casings only ever tell a controller they changed, so
    // with this one gone nothing else would, and a new controller couldn't claim them.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && tank != null) {
            BiogasDigesterStructure.unform(serverLevel, tank, pos);
            tank = null;
        }
    }

    private void updateFormed(ServerLevel level) {
        BiogasDigesterStructure.Tank found = BiogasDigesterStructure.find(level, worldPosition, tank);
        if (Objects.equals(found, tank)) {
            return;
        }
        if (tank != null) {
            BiogasDigesterStructure.unform(level, tank);
        }
        tank = found;
        if (found != null) {
            BiogasDigesterStructure.form(level, found, worldPosition);
            MultiblockEffects.formed(level, found.min(), found.max());
            ArcforgeAdvancements.formed(level, this, null);
        }
        running = false;
        setChanged();
        // Clients learn the shape too, so Jade can name the machine from any of its blocks.
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public BiogasDigesterStructure.@Nullable Tank getTank() {
        return tank;
    }

    @Override
    public Direction getStructureFacing() {
        return getFacing();
    }

    @Override
    public BlockPos getMinCorner() {
        return tank != null ? tank.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return tank != null ? tank.max() : worldPosition;
    }

    @Override
    public boolean isPart(BlockPos pos) {
        return tank != null && tank.contains(pos);
    }

    // --- Running ---

    public void serverTick(ServerLevel level) {
        if (checkRequested) {
            checkNow();
        }
        heatUsage = 0;
        if (tank == null) {
            status = MachineStatus.NOT_FORMED;
            setRunning(false);
            return;
        }
        portDefaults.tick(level, this);
        if (level.getGameTime() % REDSTONE_CHECK_INTERVAL == 0) {
            powered = isPowered(level);
        }
        if (!controlState.isEnabled() || !redstoneMode.canRun(powered)) {
            status = stoppedStatus();
        } else {
            status = digest(level);
        }
        setRunning(status == MachineStatus.DIGESTING);
        pushBiogas(level);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this);
        }
    }

    private MachineStatus digest(ServerLevel level) {
        int heatPerTick = ArcforgeConfig.DIGESTER_HEAT_PER_TICK.getAsInt();
        if (heat.getTemperature() < minTemperature() || heat.getStored() < heatPerTick) {
            // Too cool to digest: busy lanes keep their progress. Idle with nothing to do, it's just idle.
            return busyLanes() > 0 || !items.getStack(SLOT_INPUT).isEmpty() ? MachineStatus.TOO_COLD : MachineStatus.IDLE;
        }
        MachineStatus waiting = startLanes(level);
        if (busyLanes() == 0) {
            return waiting;
        }
        heatUsage = heat.remove(heatPerTick);
        for (int lane = 0; lane < laneRecipe.length; lane++) {
            if (laneRecipe[lane] != null && ++laneProgress[lane] >= laneTotal[lane]) {
                finishLane(level, lane);
            }
        }
        setChanged();
        return MachineStatus.DIGESTING;
    }

    // Starts idle lanes on the input while it can, and says why it couldn't (for when every lane is idle).
    private MachineStatus startLanes(ServerLevel level) {
        MachineStatus waiting = MachineStatus.IDLE;
        for (int lane = 0; lane < laneRecipe.length; lane++) {
            if (laneRecipe[lane] != null) {
                continue;
            }
            ItemStack input = items.getStack(SLOT_INPUT);
            RecipeHolder<DigestingRecipe> holder = input.isEmpty() ? null : MachineRecipes.digesting(level, input).orElse(null);
            if (holder == null) {
                return waiting;
            }
            DigestingRecipe recipe = holder.value();
            if (water.getAmount() < recipe.water()) {
                return MachineStatus.NO_WATER;
            }
            if (!hasRoomFor(level, recipe)) {
                return MachineStatus.FULL;
            }
            if (recipe.water() > 0) {
                try (Transaction tx = Transaction.openRoot()) {
                    water.extract(0, water.getResource(0), recipe.water(), tx);
                    tx.commit();
                }
            }
            items.setStack(SLOT_INPUT, input.copyWithCount(input.getCount() - 1));
            laneRecipe[lane] = holder.id().identifier();
            laneProgress[lane] = 0;
            laneTotal[lane] = recipe.time();
        }
        return waiting;
    }

    // Whether what the busy lanes will make, and this recipe's too, fits: the gas in the gas tank (one gas at a time)
    // and, if the by-product could come, a full stack of it in the Digestate slot.
    private boolean hasRoomFor(ServerLevel level, DigestingRecipe next) {
        FluidStack gas = next.result().create();
        int pending = gas.getAmount();
        int byproducts = next.byproduct().isPresent() ? 1 : 0;
        for (Identifier id : laneRecipe) {
            DigestingRecipe busy = id != null ? recipe(level, id) : null;
            if (busy == null) {
                continue;
            }
            FluidStack made = busy.result().create();
            if (!FluidStack.isSameFluidSameComponents(made, gas)) {
                return false;
            }
            pending += made.getAmount();
            byproducts += busy.byproduct().isPresent() ? 1 : 0;
        }
        if (biogas.getAmount() > 0 && !biogas.getResource(0).equals(FluidResource.of(gas)) || biogas.getAmount() + pending > biogas.getCapacity()) {
            return false;
        }
        if (next.byproduct().isPresent()) {
            ItemStack slot = items.getStack(SLOT_DIGESTATE);
            ItemStack made = next.byproduct().get().create();
            return slot.isEmpty() || ItemStack.isSameItemSameComponents(slot, made) && slot.getCount() + byproducts * made.getCount() <= slot.getMaxStackSize();
        }
        return true;
    }

    private static @Nullable DigestingRecipe recipe(ServerLevel level, Identifier id) {
        return level.recipeAccess().recipeMap().byType(ModRecipes.DIGESTING.get()).stream()
                .filter(holder -> holder.id().identifier().equals(id))
                .map(RecipeHolder::value)
                .findFirst().orElse(null);
    }

    private void finishLane(ServerLevel level, int lane) {
        DigestingRecipe recipe = recipe(level, laneRecipe[lane]);
        laneRecipe[lane] = null;
        laneProgress[lane] = 0;
        laneTotal[lane] = 0;
        if (recipe == null) {
            // The recipe went away (a data pack reload): the item is lost.
            return;
        }
        FluidStack gas = recipe.result().create();
        try (Transaction tx = Transaction.openRoot()) {
            biogas.insert(0, FluidResource.of(gas), gas.getAmount(), tx);
            tx.commit();
        }
        List<ItemStack> produced = new ArrayList<>();
        if (recipe.byproduct().isPresent() && level.getRandom().nextFloat() < recipe.byproductChance()) {
            ItemStack made = recipe.byproduct().get().create();
            ItemStack slot = items.getStack(SLOT_DIGESTATE);
            if (slot.isEmpty()) {
                items.setStack(SLOT_DIGESTATE, made);
                produced.add(made.copy());
            } else if (ItemStack.isSameItemSameComponents(slot, made)) {
                int count = Math.min(slot.getMaxStackSize(), slot.getCount() + made.getCount());
                items.setStack(SLOT_DIGESTATE, slot.copyWithCount(count));
                produced.add(made.copyWithCount(count - slot.getCount()));
            }
        }
        produced.add(scrub(gas.getAmount()));
        // One item and the water it took when the lane started.
        controlState.completed(produced, List.of(gas.copy()), 1, recipe.water());
        ArcforgeAdvancements.produced(this, ItemStack.EMPTY, gas.getFluid(), null);
    }

    // The sulfur scrubbed out of this much Biogas, into the sulfur slot (what doesn't fit is lost). Returns the Sulfur
    // Dust added (maybe none). Public for the GameTests.
    public ItemStack scrub(int biogasMb) {
        sulfurOwed += biogasMb / 1_000.0 * ArcforgeConfig.DIGESTER_SULFUR_PER_THOUSAND_MB.getAsDouble();
        int whole = (int) sulfurOwed;
        if (whole <= 0) {
            return ItemStack.EMPTY;
        }
        sulfurOwed -= whole;
        ItemStack slot = items.getStack(SLOT_SULFUR);
        ItemStack sulfur = new ItemStack(ModItems.SULFUR_DUST.get());
        int added;
        if (slot.isEmpty()) {
            added = Math.min(whole, sulfur.getMaxStackSize());
            items.setStack(SLOT_SULFUR, sulfur.copyWithCount(added));
        } else if (ItemStack.isSameItemSameComponents(slot, sulfur) && slot.getCount() < slot.getMaxStackSize()) {
            int count = Math.min(slot.getMaxStackSize(), slot.getCount() + whole);
            added = count - slot.getCount();
            items.setStack(SLOT_SULFUR, slot.copyWithCount(count));
        } else {
            return ItemStack.EMPTY;
        }
        ArcforgeAdvancements.produced(this, sulfur, null, "biogas_sulfur");
        return sulfur.copyWithCount(added);
    }

    public ItemStack getSulfur() {
        return items.getStack(SLOT_SULFUR);
    }

    // Biogas has no other way out: it's pushed out of every Gas Output port each tick, up to outputRate mB in all.
    private void pushBiogas(ServerLevel level) {
        int budget = ArcforgeConfig.DIGESTER_OUTPUT_RATE.getAsInt();
        for (BlockPos pos : BlockPos.betweenClosed(getMinCorner(), getMaxCorner())) {
            for (Direction side : Direction.values()) {
                if (budget <= 0 || biogas.getAmount() <= 0) {
                    return;
                }
                if (faceMode(pos, side) != SideMode.GAS_OUTPUT) {
                    continue;
                }
                BlockPos target = pos.relative(side);
                ResourceHandler<FluidResource> into = level.isLoaded(target)
                        ? level.getCapability(Capabilities.Fluid.BLOCK, target, side.getOpposite()) : null;
                if (into != null) {
                    budget -= ResourceHandlerUtil.move(gasOutput, into, resource -> true, budget, null);
                }
            }
        }
    }

    private boolean isPowered(ServerLevel level) {
        for (BlockPos pos : tank.positions()) {
            if (level.hasNeighborSignal(pos)) {
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
        if (tank != null || !lit) {
            super.setLit(lit);
        }
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getWater() {
        return water;
    }

    public FilteredFluidTank getBiogas() {
        return biogas;
    }

    public int getLanes() {
        return laneRecipe.length;
    }

    public int getHeatUsage() {
        return heatUsage;
    }

    // The lead lane's progress and length (0 with every lane idle).
    public int getProgress() {
        return leadLane() >= 0 ? laneProgress[leadLane()] : 0;
    }

    public int getTotal() {
        return leadLane() >= 0 ? laneTotal[leadLane()] : 0;
    }

    // The lead lane's recipe, on the server (null with every lane idle).
    public @Nullable DigestingRecipe getLeadRecipe() {
        int lead = leadLane();
        return lead >= 0 && level instanceof ServerLevel serverLevel ? recipe(serverLevel, laneRecipe[lead]) : null;
    }

    public boolean isRunning() {
        return running;
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return itemAutomation;
        }
        return switch (mode) {
            case INPUT -> itemInput;
            case BYPRODUCT -> digestateOutput;
            case SULFUR -> sulfurOutput;
            default -> null;
        };
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return fluidAutomation;
        }
        return switch (mode) {
            case INPUT -> waterInput;
            case GAS_OUTPUT -> gasOutput;
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
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT
                    : mode == SideMode.BYPRODUCT || mode == SideMode.SULFUR ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case GAS -> mode == SideMode.GAS_OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> ConnectionMode.NONE;
        };
    }

    // Conduits next to the controller ask it about the tank face it lies on.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    @Override
    protected void onSideConfigChanged() {
        setChanged();
        if (level != null && tank != null) {
            MultiblockAutomation.refresh(level, tank.min(), tank.max());
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank = input.getLong("tank_min").map(min -> new BiogasDigesterStructure.Tank(BlockPos.of(min),
                Direction.from2DDataValue(input.getIntOr("tank_front", 0)))).orElse(null);
        heat.deserialize(input);
        water.deserialize(input.childOrEmpty("water"));
        biogas.deserialize(input.childOrEmpty("biogas"));
        int lanes = ArcforgeConfig.DIGESTER_LANES.getAsInt();
        laneRecipe = new Identifier[Math.max(1, lanes)];
        laneProgress = new int[laneRecipe.length];
        laneTotal = new int[laneRecipe.length];
        for (int lane = 0; lane < laneRecipe.length; lane++) {
            ValueInput saved = input.childOrEmpty("lane_" + lane);
            laneRecipe[lane] = saved.getString("recipe").map(Identifier::tryParse).orElse(null);
            laneProgress[lane] = saved.getIntOr("progress", 0);
            laneTotal[lane] = saved.getIntOr("total", 0);
        }
        running = input.getBooleanOr("running", false);
        sulfurOwed = input.getDoubleOr("sulfur_owed", 0.0);
        // Saves from before the sulfur slot had one slot fewer (and no upgrades, so nothing moves).
        items.ensureSize(MACHINE_SLOTS + UPGRADE_SLOTS);
        portDefaults.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (tank != null) {
            output.putLong("tank_min", tank.min().asLong());
            output.putInt("tank_front", tank.front().get2DDataValue());
        }
        heat.serialize(output);
        water.serialize(output.child("water"));
        biogas.serialize(output.child("biogas"));
        for (int lane = 0; lane < laneRecipe.length; lane++) {
            if (laneRecipe[lane] != null) {
                ValueOutput saved = output.child("lane_" + lane);
                saved.putString("recipe", laneRecipe[lane].toString());
                saved.putInt("progress", laneProgress[lane]);
                saved.putInt("total", laneTotal[lane]);
            }
        }
        output.putBoolean("running", running);
        output.putDouble("sulfur_owed", sulfurOwed);
        portDefaults.save(output);
    }

    // Clients get the saved state when the chunk loads and whenever the structure forms or breaks up.
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
        return Component.translatable("container.arcforge.biogas_digester");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BiogasDigesterMenu(containerId, inventory, worldPosition, items, data);
    }
}
