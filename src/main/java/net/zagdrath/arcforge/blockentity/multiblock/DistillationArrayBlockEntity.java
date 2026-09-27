/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
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
import net.zagdrath.arcforge.menu.multiblock.DistillationArrayMenu;
import net.zagdrath.arcforge.multiblock.DistillationStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.recipe.DistillingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModRecipes;
import net.zagdrath.arcforge.steam.SteamTank;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Distillation Array (see DistillationStructure), run by its controller. It distils its feed
// (creosote) a batch at a time (1,000 mB, see DistillingRecipe) with heat: a batch starts when the feed
// tank holds a whole one and every product has room, and is fed through the column at 10 mB/t per 4
// blocks of height (so 100, 67 or 50 ticks), using the recipe's heat as it goes (4,000 HU a batch: 40 /
// 60 / 80 HU/t) and only while the column is at 350°C or hotter. The taller the column, the more
// fractions it makes (4 high: Naphtha and Pitch; 6: + Heavy Oil; 8: + Light Oil). Steam in its steam tank
// is used for steam stripping: 100 mB a batch, for more Naphtha by the grade. Its heat buffer holds
// 40,000 HU per 4 blocks of height, up to 1,400°C.
public class DistillationArrayBlockEntity extends MachineBlockEntity implements MultiblockController {
    public static final int SLOT_PITCH = 0;
    public static final int MACHINE_SLOTS = 1;
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.STEAM, SideMode.HEAT,
            SideMode.NAPHTHA, SideMode.LIGHT_OIL, SideMode.HEAVY_OIL, SideMode.PITCH);
    private static final int REDSTONE_CHECK_INTERVAL = 10;
    private static final int BASE_HEIGHT = 4;

    private DistillationStructure.@Nullable Column column;
    private boolean checkRequested = true;
    private boolean powered;

    private HeatBuffer heat;
    private HeatHandler heatInput;
    private final FilteredFluidTank feed;
    private final SteamTank steam;
    private final FilteredFluidTank naphtha;
    private final FilteredFluidTank lightOil;
    private final FilteredFluidTank heavyOil;
    private final ResourceHandler<FluidResource> feedInput;
    private final ResourceHandler<FluidResource> steamInput;
    private final ResourceHandler<FluidResource> naphthaOutput;
    private final ResourceHandler<FluidResource> lightOilOutput;
    private final ResourceHandler<FluidResource> heavyOilOutput;
    private final ResourceHandler<FluidResource> fluidAutomation;
    private final ResourceHandler<ItemResource> pitchOutput;
    private final ContainerData data;

    // The batch being distilled: the feed it came from (null when there is none), the steam stripping
    // bonus it got, and how much of it has gone through (mB).
    private @Nullable Fluid batchFeed;
    private float batchBonus;
    private double progress;
    private double pendingHeat;
    private int heatPerTick;
    private boolean running;
    // What the clients last saw of the products and the column's state (see syncKey), so they're sent
    // again only when a tank crosses a 1% step or the state changes.
    private int lastSyncKey = -1;
    // Gives the structure its first ports (see MultiblockPorts.Defaults).
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();
    // Client side: the pools' fill (naphtha, light oil, heavy oil) as last drawn, easing toward the synced.
    private final float[] shownFill = { -1.0F, -1.0F, -1.0F };

    public DistillationArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.DISTILLATION_ARRAY.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.NAPHTHA, SideMode.HEAT, SideMode.INPUT, SideMode.HEAVY_OIL, SideMode.PITCH, SideMode.NONE),
                SIDE_MODES);
        this.feed = new FilteredFluidTank(ArcforgeConfig.DISTILLATION_FEED_CAPACITY.getAsInt(),
                resource -> MachineRecipes.isDistillingFeed(level, resource), this::setChanged);
        this.steam = new SteamTank(ArcforgeConfig.DISTILLATION_STEAM_CAPACITY.getAsInt(), this::setChanged);
        int output = ArcforgeConfig.DISTILLATION_OUTPUT_CAPACITY.getAsInt();
        this.naphtha = new FilteredFluidTank(output, resource -> resource.is(ModFluids.NAPHTHA.get()), this::setChanged);
        this.lightOil = new FilteredFluidTank(output, resource -> resource.is(ModFluids.LIGHT_OIL.get()), this::setChanged);
        this.heavyOil = new FilteredFluidTank(output, resource -> resource.is(ModFluids.HEAVY_OIL.get()), this::setChanged);
        this.feedInput = new AutomationResourceHandler<>(feed, index -> true, index -> false);
        this.steamInput = new AutomationResourceHandler<>(steam, index -> true, index -> false);
        this.naphthaOutput = new AutomationResourceHandler<>(naphtha, index -> false, index -> true);
        this.lightOilOutput = new AutomationResourceHandler<>(lightOil, index -> false, index -> true);
        this.heavyOilOutput = new AutomationResourceHandler<>(heavyOil, index -> false, index -> true);
        this.fluidAutomation = new CombinedResourceHandler<>(feedInput, steamInput, naphthaOutput, lightOilOutput, heavyOilOutput);
        this.pitchOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_PITCH);
        resize(BASE_HEIGHT);
        this.data = new WideIntContainerData(DistillationArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case DistillationArrayMenu.DATA_HEAT -> heat.getStored();
                    case DistillationArrayMenu.DATA_HEAT_CAPACITY -> heat.getCapacity();
                    case DistillationArrayMenu.DATA_TEMPERATURE -> heat.getTemperature();
                    case DistillationArrayMenu.DATA_FEED -> fluidId(feed);
                    case DistillationArrayMenu.DATA_FEED_AMOUNT -> feed.getAmount();
                    case DistillationArrayMenu.DATA_FEED_CAPACITY -> feed.getCapacity();
                    case DistillationArrayMenu.DATA_STEAM -> fluidId(steam);
                    case DistillationArrayMenu.DATA_STEAM_AMOUNT -> steam.getAmount();
                    case DistillationArrayMenu.DATA_STEAM_CAPACITY -> steam.getCapacity();
                    case DistillationArrayMenu.DATA_NAPHTHA -> naphtha.getAmount();
                    case DistillationArrayMenu.DATA_LIGHT_OIL -> lightOil.getAmount();
                    case DistillationArrayMenu.DATA_HEAVY_OIL -> heavyOil.getAmount();
                    case DistillationArrayMenu.DATA_OUTPUT_CAPACITY -> naphtha.getCapacity();
                    case DistillationArrayMenu.DATA_HEIGHT -> getHeight();
                    case DistillationArrayMenu.DATA_BONUS -> Math.round(steamBonus() * 100);
                    case DistillationArrayMenu.DATA_HEAT_PER_TICK -> heatPerTick;
                    case DistillationArrayMenu.DATA_STATUS -> status.ordinal();
                    case DistillationArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case DistillationArrayMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    private static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    // The heat buffer scales with the height; stored heat carries over (clamped).
    private void resize(int height) {
        int stored = heat != null ? heat.getStored() : 0;
        heat = new HeatBuffer(
                ArcforgeConfig.DISTILLATION_HEAT_CAPACITY.getAsInt() * height / BASE_HEIGHT,
                ArcforgeConfig.DISTILLATION_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        heat.add(stored);
        heatInput = heat.input(Integer.MAX_VALUE);
    }

    public int getHeight() {
        return column != null ? column.height() : BASE_HEIGHT;
    }

    public DistillationStructure.@Nullable Column getColumn() {
        return column;
    }

    // Feed through the column per tick, in mB.
    public int feedRate() {
        return ArcforgeConfig.DISTILLATION_FEED_RATE.getAsInt() * getHeight() / BASE_HEIGHT;
    }

    // --- Structure ---

    @Override
    public boolean isFormed() {
        return column != null;
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

    private void updateFormed(ServerLevel level) {
        DistillationStructure.Column found = DistillationStructure.find(level, worldPosition, column);
        if (Objects.equals(found, column)) {
            return;
        }
        if (column != null) {
            DistillationStructure.unform(level, column);
        }
        column = found;
        if (found != null) {
            resize(found.height());
            DistillationStructure.form(level, found);
            MultiblockEffects.formed(level, found.min(), found.max());
        }
        running = false;
        setChanged();
        sync();
    }

    // The controller was turned (see DistillationArrayControllerBlock): every face of the column changed.
    public void onStructureTurned() {
        if (level != null && column != null) {
            MultiblockAutomation.refresh(level, column.min(), column.max());
        }
    }

    @Override
    public Direction getStructureFacing() {
        return getFacing();
    }

    @Override
    public BlockPos getMinCorner() {
        return column != null ? column.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return column != null ? column.max() : worldPosition;
    }

    @Override
    public boolean isPart(BlockPos pos) {
        return column != null && column.contains(pos);
    }

    private void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // --- Running ---

    public void serverTick(ServerLevel level) {
        if (checkRequested) {
            checkNow();
        }
        if (column == null) {
            status = MachineStatus.NOT_FORMED;
            heatPerTick = 0;
            setRunning(level, false);
            return;
        }
        portDefaults.tick(level, this);
        if (level.getGameTime() % REDSTONE_CHECK_INTERVAL == 0) {
            powered = isPowered(level);
        }
        heatPerTick = 0;
        if (!redstoneMode.canRun(powered)) {
            status = MachineStatus.DISABLED;
        } else {
            status = distil(level);
        }
        setRunning(level, status == MachineStatus.RUNNING);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this);
        }
        int key = syncKey();
        if (key != lastSyncKey) {
            lastSyncKey = key;
            sync();
        }
    }

    // The renderer shows each product's pool (to the nearest percent), and vapour while running, tinted
    // by the steam when the batch is being stripped.
    private int syncKey() {
        int key = 0;
        for (FilteredFluidTank tank : List.of(naphtha, lightOil, heavyOil)) {
            key = key * 101 + tank.getAmount() * 100 / Math.max(1, tank.getCapacity());
        }
        return key * 4 + (running ? 2 : 0) + (batchBonus > 0 ? 1 : 0);
    }

    // Client side: a product's pool fill (0..1; index 0 naphtha, 1 light oil, 2 heavy oil) to draw this
    // frame, closing share of the gap to the synced amount.
    public float easeFill(int index, float share) {
        FilteredFluidTank tank = List.of(naphtha, lightOil, heavyOil).get(index);
        float target = Math.min(1.0F, tank.getAmount() / (float) Math.max(1, tank.getCapacity()));
        float shown = shownFill[index];
        shownFill[index] = shown < 0 ? target : shown + (target - shown) * share;
        return shownFill[index];
    }

    // Client side: whether the batch now running gets steam stripping.
    public boolean isStripping() {
        return batchBonus > 0;
    }

    // A column's sides are two blocks wide: its default ports go on the left one (seen from outside) of the
    // second layer up. Top and bottom ones go in the middle as usual.
    @Override
    public @Nullable BlockPos defaultPortPos(Level level, Direction side) {
        if (column == null || side.getAxis().isVertical()) {
            return MultiblockController.super.defaultPortPos(level, side);
        }
        Vec3 layer = Vec3.atLowerCornerOf(column.min()).add(1.0, 1.5, 1.0);
        Vec3 target = layer.add(side.getStepX() * 0.5, 0.0, side.getStepZ() * 0.5)
                .add(side.getClockWise().getStepX() * 0.5, 0.0, side.getClockWise().getStepZ() * 0.5);
        return MultiblockPorts.nearest(level, this, side, target);
    }

    // The whole column, so the renderer isn't culled when the controller is off screen.
    public AABB getRenderBox() {
        return column != null ? AABB.encapsulatingFullBlocks(column.min(), column.max()) : new AABB(worldPosition);
    }

    private boolean isPowered(ServerLevel level) {
        for (BlockPos pos : column.positions()) {
            if (level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    private void setRunning(ServerLevel level, boolean running) {
        if (this.running != running) {
            this.running = running;
            setLit(running);
            if (column != null) {
                DistillationStructure.setLit(level, column, running);
            }
        }
    }

    @Override
    protected void setLit(boolean lit) {
        if (column != null) {
            super.setLit(lit);
        }
    }

    private MachineStatus distil(ServerLevel level) {
        if (batchFeed == null && !startBatch(level)) {
            return status;
        }
        DistillingRecipe recipe = recipeFor(level, batchFeed);
        if (recipe == null) {
            // The recipe went away (a data pack reload): the batch is lost.
            batchFeed = null;
            progress = 0;
            return MachineStatus.NO_FEED;
        }
        if (heat.getTemperature() < recipe.minTemp()) {
            return MachineStatus.HEATING;
        }
        double fed = Math.min(feedRate(), recipe.amount() - progress);
        double heatNeeded = fed * recipe.heat() / recipe.amount();
        if (heat.getStored() < pendingHeat + heatNeeded) {
            return MachineStatus.HEATING;
        }
        pendingHeat += heatNeeded;
        int used = (int) Math.floor(pendingHeat);
        pendingHeat -= used;
        heat.remove(used);
        heatPerTick = (int) Math.round(heatNeeded);
        progress += fed;
        if (progress >= recipe.amount()) {
            finishBatch(recipe);
        }
        setChanged();
        return MachineStatus.RUNNING;
    }

    private static @Nullable DistillingRecipe recipeFor(@Nullable Level level, @Nullable Fluid feed) {
        return feed == null ? null : MachineRecipes.distilling(level, FluidResource.of(feed)).map(RecipeHolder::value).orElse(null);
    }

    // Takes a batch of feed (and its steam) if there is a whole one and every product has room. Sets the
    // status to why not if it can't.
    private boolean startBatch(ServerLevel level) {
        FluidResource resource = feed.getResource(0);
        DistillingRecipe recipe = resource.isEmpty() ? null : recipeFor(level, resource.getFluid());
        if (recipe == null || feed.getAmount() < recipe.amount() || recipe.fractions(getHeight()) == null) {
            status = MachineStatus.NO_FEED;
            return false;
        }
        boolean stripping = recipe.steamPerBatch() > 0 && steam.getAmount() >= recipe.steamPerBatch();
        float bonus = stripping ? recipe.bonusFor(steam.getResource(0)) : 0.0F;
        if (!hasRoom(recipe.outputs(getHeight(), bonus), recipe.itemOutput().getDefaultInstance(), recipe.items(getHeight()))) {
            status = MachineStatus.FULL;
            return false;
        }
        try (Transaction tx = Transaction.openRoot()) {
            feed.extract(0, resource, recipe.amount(), tx);
            if (stripping) {
                steam.extract(0, steam.getResource(0), recipe.steamPerBatch(), tx);
            }
            tx.commit();
        }
        batchFeed = resource.getFluid();
        batchBonus = bonus;
        progress = 0;
        return true;
    }

    private boolean hasRoom(Map<Fluid, Integer> fluids, ItemStack item, int items) {
        for (Map.Entry<Fluid, Integer> entry : fluids.entrySet()) {
            FilteredFluidTank tank = tankFor(entry.getKey());
            if (tank == null || tank.getAmount() > 0 && !tank.contains(entry.getKey()) || tank.getSpace() < entry.getValue()) {
                return false;
            }
        }
        if (items <= 0) {
            return true;
        }
        ItemStack slot = items(SLOT_PITCH);
        return slot.isEmpty() || ItemStack.isSameItemSameComponents(slot, item) && slot.getCount() + items <= slot.getMaxStackSize();
    }

    private void finishBatch(DistillingRecipe recipe) {
        int height = getHeight();
        try (Transaction tx = Transaction.openRoot()) {
            for (Map.Entry<Fluid, Integer> entry : recipe.outputs(height, batchBonus).entrySet()) {
                FilteredFluidTank tank = tankFor(entry.getKey());
                if (tank != null) {
                    tank.insert(0, FluidResource.of(entry.getKey()), entry.getValue(), tx);
                }
            }
            tx.commit();
        }
        int count = recipe.items(height);
        if (count > 0) {
            ItemStack slot = items(SLOT_PITCH);
            ItemStack made = new ItemStack(recipe.itemOutput(), count);
            if (slot.isEmpty()) {
                items.setStack(SLOT_PITCH, made);
            } else if (ItemStack.isSameItemSameComponents(slot, made)) {
                items.setStack(SLOT_PITCH, slot.copyWithCount(Math.min(slot.getMaxStackSize(), slot.getCount() + count)));
            }
        }
        batchFeed = null;
        batchBonus = 0;
        progress = 0;
    }

    private ItemStack items(int slot) {
        return items.getStack(slot);
    }

    private @Nullable FilteredFluidTank tankFor(Fluid fluid) {
        if (fluid.isSame(ModFluids.NAPHTHA.get())) {
            return naphtha;
        }
        if (fluid.isSame(ModFluids.LIGHT_OIL.get())) {
            return lightOil;
        }
        return fluid.isSame(ModFluids.HEAVY_OIL.get()) ? heavyOil : null;
    }

    // The steam stripping bonus the steam in the tank would give the next batch (0 without enough steam).
    public float steamBonus() {
        if (level == null) {
            return 0.0F;
        }
        FluidResource resource = feed.getResource(0);
        DistillingRecipe recipe = resource.isEmpty() ? firstRecipe() : recipeFor(level, resource.getFluid());
        if (recipe == null || recipe.steamPerBatch() <= 0 || steam.getAmount() < recipe.steamPerBatch()) {
            return 0.0F;
        }
        return recipe.bonusFor(steam.getResource(0));
    }

    private @Nullable DistillingRecipe firstRecipe() {
        return level instanceof ServerLevel serverLevel
                ? serverLevel.recipeAccess().recipeMap().byType(ModRecipes.DISTILLING.get()).stream()
                        .map(RecipeHolder::value).findFirst().orElse(null)
                : null;
    }

    public HeatBuffer getHeat() {
        return heat;
    }

    public FilteredFluidTank getFeed() {
        return feed;
    }

    public SteamTank getSteam() {
        return steam;
    }

    public FilteredFluidTank getNaphtha() {
        return naphtha;
    }

    public FilteredFluidTank getLightOil() {
        return lightOil;
    }

    public FilteredFluidTank getHeavyOil() {
        return heavyOil;
    }

    public boolean isRunning() {
        return running;
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return mode == null || mode == SideMode.PITCH ? pitchOutput : null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return fluidAutomation;
        }
        return switch (mode) {
            case INPUT -> feedInput;
            case STEAM -> steamInput;
            case NAPHTHA -> naphthaOutput;
            case LIGHT_OIL -> lightOilOutput;
            case HEAVY_OIL -> heavyOilOutput;
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
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT
                    : mode == SideMode.NAPHTHA || mode == SideMode.LIGHT_OIL || mode == SideMode.HEAVY_OIL ? ConnectionMode.OUTPUT
                    : ConnectionMode.NONE;
            case GAS -> mode == SideMode.STEAM ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ITEM -> mode == SideMode.PITCH ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case THERMAL -> mode == SideMode.HEAT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> ConnectionMode.NONE;
        };
    }

    // Conduits next to the controller ask it about the column face it lies on.
    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    @Override
    protected void onSideConfigChanged() {
        setChanged();
        onStructureTurned();
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        column = input.getInt("column_height").map(height -> new DistillationStructure.Column(
                BlockPos.of(input.getLongOr("column_min", 0L)), height)).orElse(null);
        resize(getHeight());
        heat.deserialize(input);
        feed.deserialize(input.childOrEmpty("feed"));
        steam.deserialize(input.childOrEmpty("steam"));
        naphtha.deserialize(input.childOrEmpty("naphtha"));
        lightOil.deserialize(input.childOrEmpty("light_oil"));
        heavyOil.deserialize(input.childOrEmpty("heavy_oil"));
        batchFeed = input.getString("batch_feed").map(id -> BuiltInRegistries.FLUID.getValue(Identifier.parse(id))).orElse(null);
        batchBonus = input.getFloatOr("batch_bonus", 0.0F);
        progress = input.getDoubleOr("progress", 0.0);
        pendingHeat = input.getDoubleOr("pending_heat", 0.0);
        running = input.getBooleanOr("running", false);
        portDefaults.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (column != null) {
            output.putLong("column_min", column.min().asLong());
            output.putInt("column_height", column.height());
        }
        heat.serialize(output);
        feed.serialize(output.child("feed"));
        steam.serialize(output.child("steam"));
        naphtha.serialize(output.child("naphtha"));
        lightOil.serialize(output.child("light_oil"));
        heavyOil.serialize(output.child("heavy_oil"));
        if (batchFeed != null) {
            output.putString("batch_feed", BuiltInRegistries.FLUID.getKey(batchFeed).toString());
        }
        output.putFloat("batch_bonus", batchBonus);
        output.putDouble("progress", progress);
        output.putDouble("pending_heat", pendingHeat);
        output.putBoolean("running", running);
        portDefaults.save(output);
    }

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
        return Component.translatable("container.arcforge.distillation_array.sized", getHeight());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new DistillationArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
