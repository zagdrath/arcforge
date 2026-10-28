/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

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
import net.zagdrath.arcforge.menu.multiblock.DistillationArrayMenu;
import net.zagdrath.arcforge.multiblock.DistillationStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.TickSpread;
import net.zagdrath.arcforge.recipe.DistillingRecipe;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModRecipes;
import net.zagdrath.arcforge.steam.SteamTank;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Distillation Array (see DistillationStructure), run by its controller: a refinery column 2x2 or 3x3 across and 4
// to 16 tall. It distils its feed (creosote or Crude Oil) a batch at a time (1,000 mB, see DistillingRecipe) with heat:
// a batch starts when the feed tank holds a whole one and every product has room, and is fed through the column at 10
// mB/t for a 2x2 four blocks tall, scaled by its cross-section (a 3x3 x2.25) and height (per 4 blocks): 20 mB/t for a
// 2x2x8, 90 for a 3x3x16. It uses the recipe's heat as it goes (so the heat per tick scales the same way) and only
// while the column is at the recipe's min_temp or hotter. What it makes follows its height (the recipe's by_height):
// creosote gives Naphtha and Pitch at 4, Heavy Oil from 6 and Light Oil from 8; Crude Oil needs 6 (Refinery Gas,
// Naphtha, Diesel, Bitumen), 10 adds Kerosene and Heavy Oil, and from 14 the light fractions come in larger shares.
// Steam in its steam tank is used for steam stripping: a batch's worth, for more of the boosted fraction by the grade.
// Its heat buffer holds 40,000 HU per 4 blocks of a 2x2 (scaled like the feed), up to 1,400°C; its tanks are 16,000 mB
// of feed, 8,000 of steam and 8,000 of each product for a 2x2x8, scaled by its volume. Speed Upgrades feed it 2^(n/2)
// times as fast (heat as fast too, so the heat per batch doesn't change), like every machine; Heat Upgrades cut the
// heat per batch to 0.8^n, like the other heat machines.
public class DistillationArrayBlockEntity extends MachineBlockEntity implements MultiblockController {
    public static final int SLOT_PITCH = 0;
    public static final int MACHINE_SLOTS = 1;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.HEAT);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.STEAM, SideMode.HEAT,
            SideMode.REFINERY_GAS, SideMode.NAPHTHA, SideMode.KEROSENE, SideMode.LIGHT_OIL, SideMode.DIESEL, SideMode.HEAVY_OIL,
            SideMode.BITUMEN, SideMode.PITCH);
    private static final int REDSTONE_CHECK_INTERVAL = 10;
    // The column the config's values are for: a 2x2, 4 tall (rates and heat) or 8 tall (tanks).
    private static final int BASE_WIDTH = 2, BASE_HEIGHT = 4, BASE_TANK_HEIGHT = 8;

    // What the column makes, lightest first, each with its own tank and port mode.
    public enum Product {
        REFINERY_GAS("refinery_gas", SideMode.REFINERY_GAS, ModFluids.REFINERY_GAS),
        NAPHTHA("naphtha", SideMode.NAPHTHA, ModFluids.NAPHTHA),
        KEROSENE("kerosene", SideMode.KEROSENE, ModFluids.KEROSENE, ModFluids.SOUR_KEROSENE),
        LIGHT_OIL("light_oil", SideMode.LIGHT_OIL, ModFluids.LIGHT_OIL),
        DIESEL("diesel", SideMode.DIESEL, ModFluids.DIESEL, ModFluids.SOUR_DIESEL),
        HEAVY_OIL("heavy_oil", SideMode.HEAVY_OIL, ModFluids.HEAVY_OIL),
        BITUMEN("bitumen", SideMode.BITUMEN, ModFluids.BITUMEN);

        public final String key;
        public final SideMode mode;
        private final Supplier<? extends Fluid> fluid;
        // Kerosene and Diesel come out sour from Sour Crude Oil, into the same tank (one at a time).
        private final @Nullable Supplier<? extends Fluid> sour;

        Product(String key, SideMode mode, Supplier<? extends Fluid> fluid) {
            this(key, mode, fluid, null);
        }

        Product(String key, SideMode mode, Supplier<? extends Fluid> fluid, @Nullable Supplier<? extends Fluid> sour) {
            this.key = key;
            this.mode = mode;
            this.fluid = fluid;
            this.sour = sour;
        }

        public Fluid fluid() {
            return fluid.get();
        }

        // The sour form when sour and it has one, else its own.
        public Fluid fluid(boolean sour) {
            return sour && this.sour != null ? this.sour.get() : fluid.get();
        }

        // Whether its tank takes this fluid: its own, or its sour form.
        public boolean accepts(Fluid other) {
            return other.isSame(fluid()) || sour != null && other.isSame(sour.get());
        }

        // A gas: it leaves the top of the column for the condensers, not a tray.
        public boolean isGas() {
            return this == REFINERY_GAS;
        }

        public static @Nullable Product of(Fluid fluid) {
            for (Product product : values()) {
                if (product.accepts(fluid)) {
                    return product;
                }
            }
            return null;
        }

        public static @Nullable Product of(SideMode mode) {
            for (Product product : values()) {
                if (product.mode == mode) {
                    return product;
                }
            }
            return null;
        }
    }

    private DistillationStructure.@Nullable Column column;
    private boolean checkRequested = true;
    private boolean powered;

    private HeatBuffer heat;
    private HeatHandler heatInput;
    private final FilteredFluidTank feed;
    private final SteamTank steam;
    private final Map<Product, FilteredFluidTank> products = new EnumMap<>(Product.class);
    private final Map<Product, ResourceHandler<FluidResource>> productOutputs = new EnumMap<>(Product.class);
    private final ResourceHandler<FluidResource> feedInput;
    private final ResourceHandler<FluidResource> steamInput;
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
    // The feed last distilled, so the column keeps showing its draw-offs while the feed tank is empty.
    private @Nullable Fluid lastFeed;
    // What the clients last saw of the products and the column's state (see syncKey), so they're sent again only when a
    // tank crosses a 1% step or the state changes.
    private int[] lastSyncKey = new int[0];
    // Gives the structure its first ports (see MultiblockPorts.Defaults).
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();
    // Client side: the trays' pools (by Product) as last drawn, easing toward the synced fill; the products the
    // column's recipe makes at its height (a bit per Product), and how hot it is.
    private final float[] shownFill = new float[Product.values().length];
    private int clientMakes;
    private int clientTemperature;

    public DistillationArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.DISTILLATION_ARRAY.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.NAPHTHA, SideMode.HEAT, SideMode.INPUT, SideMode.HEAVY_OIL, SideMode.PITCH, SideMode.NONE),
                SIDE_MODES);
        Arrays.fill(shownFill, -1.0F);
        this.feed = new FilteredFluidTank(ArcforgeConfig.DISTILLATION_FEED_CAPACITY.getAsInt(),
                resource -> MachineRecipes.isDistillingFeed(level, resource), this::setChanged);
        this.steam = new SteamTank(ArcforgeConfig.DISTILLATION_STEAM_CAPACITY.getAsInt(), this::setChanged);
        int output = ArcforgeConfig.DISTILLATION_OUTPUT_CAPACITY.getAsInt();
        List<ResourceHandler<FluidResource>> all = new ArrayList<>();
        this.feedInput = new AutomationResourceHandler<>(feed, index -> true, index -> false);
        this.steamInput = new AutomationResourceHandler<>(steam, index -> true, index -> false);
        all.add(feedInput);
        all.add(steamInput);
        for (Product product : Product.values()) {
            FilteredFluidTank tank = new FilteredFluidTank(output, resource -> !resource.isEmpty() && product.accepts(resource.getFluid()), this::setChanged);
            products.put(product, tank);
            if (product.isGas()) gasVents.add(tank, product.fluid());
            ResourceHandler<FluidResource> out = new AutomationResourceHandler<>(tank, index -> false, index -> true);
            productOutputs.put(product, out);
            all.add(out);
        }
        @SuppressWarnings("unchecked")
        ResourceHandler<FluidResource>[] handlers = all.toArray(new ResourceHandler[0]);
        this.fluidAutomation = new CombinedResourceHandler<>(handlers);
        this.pitchOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_PITCH);
        resize(BASE_WIDTH, BASE_HEIGHT);
        this.data = new WideIntContainerData(DistillationArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                if (index >= DistillationArrayMenu.DATA_PRODUCTS && index < DistillationArrayMenu.DATA_PRODUCTS + Product.values().length) {
                    return products.get(Product.values()[index - DistillationArrayMenu.DATA_PRODUCTS]).getAmount();
                }
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
                    case DistillationArrayMenu.DATA_OUTPUT_CAPACITY -> products.get(Product.NAPHTHA).getCapacity();
                    case DistillationArrayMenu.DATA_HEIGHT -> getHeight();
                    case DistillationArrayMenu.DATA_WIDTH -> getWidth();
                    case DistillationArrayMenu.DATA_BONUS -> Math.round(steamBonus() * 100);
                    case DistillationArrayMenu.DATA_HEAT_PER_TICK -> heatPerTick;
                    case DistillationArrayMenu.DATA_FEED_RATE -> feedRate();
                    case DistillationArrayMenu.DATA_MAKES -> makes();
                    case DistillationArrayMenu.DATA_STATUS -> status.ordinal();
                    case DistillationArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case DistillationArrayMenu.DATA_SIDE_CONFIG -> packedSides();
                    case DistillationArrayMenu.DATA_SOUR -> sourBits();
                    default -> 0;
                };
            }
        };
    }

    private static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }

    // How much bigger than the config's column this one is: by cross-section and height (rates and heat), or by volume
    // against a 2x2x8 (tanks).
    private static double rateScale(int width, int height) {
        return (double) (width * width) / (BASE_WIDTH * BASE_WIDTH) * height / BASE_HEIGHT;
    }

    private static double tankScale(int width, int height) {
        return (double) (width * width) / (BASE_WIDTH * BASE_WIDTH) * height / BASE_TANK_HEIGHT;
    }

    // The heat buffer and tanks scale with the column; stored heat carries over (clamped), and so do the tanks'
    // contents.
    private void resize(int width, int height) {
        int stored = heat != null ? heat.getStored() : 0;
        heat = new HeatBuffer(
                (int) Math.round(ArcforgeConfig.DISTILLATION_HEAT_CAPACITY.getAsInt() * rateScale(width, height)),
                ArcforgeConfig.DISTILLATION_MAX_TEMPERATURE.getAsInt(),
                this::setChanged);
        heat.add(stored);
        heatInput = heat.input(Integer.MAX_VALUE);
        double tanks = tankScale(width, height);
        feed.setCapacity(scaled(ArcforgeConfig.DISTILLATION_FEED_CAPACITY.getAsInt(), tanks));
        steam.setCapacity(scaled(ArcforgeConfig.DISTILLATION_STEAM_CAPACITY.getAsInt(), tanks));
        int output = scaled(ArcforgeConfig.DISTILLATION_OUTPUT_CAPACITY.getAsInt(), tanks);
        for (FilteredFluidTank tank : products.values()) {
            tank.setCapacity(output);
        }
    }

    // At least a bucket, so a small column still takes a whole batch.
    private static int scaled(int capacity, double scale) {
        return (int) Math.max(1_000, Math.min(Integer.MAX_VALUE, Math.round(capacity * scale)));
    }

    public int getHeight() {
        return column != null ? column.height() : BASE_HEIGHT;
    }

    public int getWidth() {
        return column != null ? column.width() : BASE_WIDTH;
    }

    public DistillationStructure.@Nullable Column getColumn() {
        return column;
    }

    // Feed through the column per tick, in mB: by its size, faster with Speed upgrades.
    public int feedRate() {
        return (int) Math.round(ArcforgeConfig.DISTILLATION_FEED_RATE.getAsInt() * rateScale(getWidth(), getHeight()) * speedMultiplier());
    }

    // The column's height, for advancements (arcforge:multiblock_formed's min_length).
    @Override
    public int length() {
        return getHeight();
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

    // Breaking the controller un-forms the rest of the column: the parts only ever tell a controller they
    // changed, so with this one gone nothing else would, and a new controller couldn't claim them.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && column != null) {
            DistillationStructure.unform(serverLevel, column, pos);
            column = null;
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
            resize(found.width(), found.height());
            DistillationStructure.form(level, found);
            MultiblockEffects.formed(level, found.min(), found.max());
            ArcforgeAdvancements.formed(level, this, null);
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
        sync();
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
        if (TickSpread.due(level, worldPosition, REDSTONE_CHECK_INTERVAL)) {
            powered = isPowered(level);
        }
        heatPerTick = 0;
        if (!controlState.isEnabled() || !redstoneMode.canRun(powered)) {
            status = stoppedStatus();
        } else {
            status = distil(level);
        }
        setRunning(level, status == MachineStatus.RUNNING);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this);
        }
        int[] key = syncKey();
        if (!Arrays.equals(key, lastSyncKey)) {
            lastSyncKey = key;
            sync();
        }
    }

    // The renderer shows each product's pool (to the nearest percent), vapour while running (tinted by the steam when
    // the batch is being stripped), the draw-offs of what the column makes, and the reboiler's glow (to the nearest
    // 25°C).
    private int[] syncKey() {
        Product[] all = Product.values();
        int[] key = new int[all.length + 3];
        for (int i = 0; i < all.length; i++) {
            FilteredFluidTank tank = products.get(all[i]);
            key[i] = tank.getAmount() * 100 / Math.max(1, tank.getCapacity());
        }
        key[all.length] = (running ? 2 : 0) + (batchBonus > 0 ? 1 : 0);
        key[all.length + 1] = makes();
        key[all.length + 2] = heat.getTemperature() / 25;
        return key;
    }

    // A bit per product whose tank holds its sour form (see Product), for the GUI.
    private int sourBits() {
        int bits = 0;
        for (Product product : Product.values()) {
            FilteredFluidTank tank = products.get(product);
            if (tank.getAmount() > 0 && !tank.getResource(0).getFluid().isSame(product.fluid())) {
                bits |= 1 << product.ordinal();
            }
        }
        return bits;
    }

    // What a product's tank shows: what's in it, or the product's own fluid while it's empty (the client knows the
    // tanks from the update tag).
    public Fluid shownFluid(Product product) {
        FilteredFluidTank tank = products.get(product);
        return tank.getAmount() > 0 ? tank.getResource(0).getFluid() : product.fluid();
    }

    // Client side: a product's pool fill (0..1) to draw this frame, closing share of the gap to the synced amount.
    public float easeFill(Product product, float share) {
        FilteredFluidTank tank = products.get(product);
        float target = Math.min(1.0F, tank.getAmount() / (float) Math.max(1, tank.getCapacity()));
        int index = product.ordinal();
        float shown = shownFill[index];
        shownFill[index] = shown < 0 ? target : shown + (target - shown) * share;
        return shownFill[index];
    }

    // Client side: whether the batch now running gets steam stripping.
    public boolean isStripping() {
        return batchBonus > 0;
    }

    // The products the column's recipe (the batch's, else the feed tank's, else the last one's) makes at its height, a
    // bit per Product. On the client, as last synced.
    public int makes() {
        if (level != null && level.isClientSide()) {
            return clientMakes;
        }
        Fluid fluid = batchFeed != null ? batchFeed : !feed.getResource(0).isEmpty() ? feed.getResource(0).getFluid() : lastFeed;
        DistillingRecipe recipe = level != null ? recipeFor(level, fluid) : null;
        if (recipe == null) {
            return 0;
        }
        int bits = 0;
        for (Fluid made : recipe.outputs(getHeight(), 0.0F).keySet()) {
            Product product = Product.of(made);
            if (product != null) {
                bits |= 1 << product.ordinal();
            }
        }
        return bits;
    }

    // The column's temperature (°C); on the client, as last synced.
    public int temperature() {
        return level != null && level.isClientSide() ? clientTemperature : heat.getTemperature();
    }

    // A column's sides are two or three blocks wide: its default ports go on the left one (seen from outside) of the
    // second layer up. Top and bottom ones go in the middle as usual.
    @Override
    public @Nullable BlockPos defaultPortPos(Level level, Direction side) {
        if (column == null || side.getAxis().isVertical()) {
            return MultiblockController.super.defaultPortPos(level, side);
        }
        double half = column.width() / 2.0;
        Vec3 layer = Vec3.atLowerCornerOf(column.min()).add(half, 1.5, half);
        Vec3 target = layer.add(side.getStepX() * half, 0.0, side.getStepZ() * half)
                .add(side.getClockWise().getStepX() * 0.5, 0.0, side.getClockWise().getStepZ() * 0.5);
        return MultiblockPorts.nearest(level, this, side, target);
    }

    // The whole column and a block round it, so the renderer isn't culled when the controller is off screen.
    public AABB getRenderBox() {
        return column != null ? AABB.encapsulatingFullBlocks(column.min(), column.max()).inflate(1.0) : new AABB(worldPosition);
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
        double heatNeeded = fed * recipe.heat() / recipe.amount() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.HEAT));
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

    // Takes a batch of feed (and its steam) if there is a whole one, the column is tall enough for it and every product
    // has room. Sets the status to why not if it can't.
    private boolean startBatch(ServerLevel level) {
        FluidResource resource = feed.getResource(0);
        DistillingRecipe recipe = resource.isEmpty() ? null : recipeFor(level, resource.getFluid());
        if (recipe == null || feed.getAmount() < recipe.amount()) {
            status = MachineStatus.NO_FEED;
            return false;
        }
        if (recipe.fractions(getHeight()) == null) {
            status = MachineStatus.TOO_SHORT;
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
        // The steam counts as used now (the batch keeps only its bonus); the feed when the batch is done.
        if (stripping) {
            controlState.consumedFluid(recipe.steamPerBatch());
        }
        batchFeed = resource.getFluid();
        lastFeed = batchFeed;
        batchBonus = bonus;
        progress = 0;
        return true;
    }

    private boolean hasRoom(Map<Fluid, Integer> fluids, ItemStack item, int items) {
        for (Map.Entry<Fluid, Integer> entry : fluids.entrySet()) {
            FilteredFluidTank tank = tankFor(entry.getKey());
            if (tank == null || !tank.canVent(FluidResource.of(entry.getKey())) && (tank.getAmount() > 0 && !tank.contains(entry.getKey()) || tank.getSpace() < entry.getValue())) {
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
        List<FluidStack> madeFluids = new ArrayList<>();
        try (Transaction tx = Transaction.openRoot()) {
            for (Map.Entry<Fluid, Integer> entry : recipe.outputs(height, batchBonus).entrySet()) {
                FilteredFluidTank tank = tankFor(entry.getKey());
                if (tank != null) {
                    int inserted = tank.insert(0, FluidResource.of(entry.getKey()), entry.getValue(), tx);
                    madeFluids.add(new FluidStack(entry.getKey(), inserted));
                }
            }
            tx.commit();
        }
        for (Fluid made : recipe.outputs(height, batchBonus).keySet()) {
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, made, null);
        }
        int count = recipe.items(height);
        List<ItemStack> madeItems = new ArrayList<>();
        if (count > 0) {
            ItemStack slot = items(SLOT_PITCH);
            ItemStack made = new ItemStack(recipe.itemOutput(), count);
            if (slot.isEmpty()) {
                items.setStack(SLOT_PITCH, made);
                madeItems.add(made.copy());
            } else if (ItemStack.isSameItemSameComponents(slot, made)) {
                int total = Math.min(slot.getMaxStackSize(), slot.getCount() + count);
                items.setStack(SLOT_PITCH, slot.copyWithCount(total));
                madeItems.add(made.copyWithCount(total - slot.getCount()));
            }
        }
        controlState.completed(madeItems, madeFluids, 0, recipe.amount());
        batchFeed = null;
        batchBonus = 0;
        progress = 0;
    }

    private ItemStack items(int slot) {
        return items.getStack(slot);
    }

    private @Nullable FilteredFluidTank tankFor(Fluid fluid) {
        Product product = Product.of(fluid);
        return product != null ? products.get(product) : null;
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

    public FilteredFluidTank getProduct(Product product) {
        return products.get(product);
    }

    public FilteredFluidTank getNaphtha() {
        return products.get(Product.NAPHTHA);
    }

    public FilteredFluidTank getLightOil() {
        return products.get(Product.LIGHT_OIL);
    }

    public FilteredFluidTank getHeavyOil() {
        return products.get(Product.HEAVY_OIL);
    }

    public boolean isRunning() {
        return running;
    }

    public int getHeatPerTick() {
        return heatPerTick;
    }

    // mB of the batch gone through the column.
    public double getProgress() {
        return progress;
    }

    // The batch's recipe (null with no batch) and the steam stripping bonus it got.
    public @Nullable DistillingRecipe getBatchRecipe() {
        return recipeFor(level, batchFeed);
    }

    public float getBatchBonus() {
        return batchBonus;
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
        if (mode == SideMode.INPUT) {
            return feedInput;
        }
        if (mode == SideMode.STEAM) {
            return steamInput;
        }
        Product product = Product.of(mode);
        return product != null ? productOutputs.get(product) : null;
    }

    public @Nullable HeatHandler heatHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return heatInput;
        }
        return faceMode(pos, side) == SideMode.HEAT ? heatInput : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        Product product = Product.of(mode);
        return switch (type) {
            case FLUID -> mode == SideMode.INPUT ? ConnectionMode.INPUT
                    : product != null && !product.isGas() ? ConnectionMode.OUTPUT
                    : ConnectionMode.NONE;
            case GAS -> mode == SideMode.STEAM ? ConnectionMode.INPUT : product != null && product.isGas() ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
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
                BlockPos.of(input.getLongOr("column_min", 0L)), input.getIntOr("column_width", BASE_WIDTH), height)).orElse(null);
        resize(getWidth(), getHeight());
        heat.deserialize(input);
        feed.deserialize(input.childOrEmpty("feed"));
        steam.deserialize(input.childOrEmpty("steam"));
        for (Product product : Product.values()) {
            products.get(product).deserialize(input.childOrEmpty(product.key));
        }
        batchFeed = input.getString("batch_feed").map(id -> BuiltInRegistries.FLUID.getValue(Identifier.parse(id))).orElse(null);
        lastFeed = input.getString("last_feed").map(id -> BuiltInRegistries.FLUID.getValue(Identifier.parse(id))).orElse(null);
        batchBonus = input.getFloatOr("batch_bonus", 0.0F);
        progress = input.getDoubleOr("progress", 0.0);
        pendingHeat = input.getDoubleOr("pending_heat", 0.0);
        running = input.getBooleanOr("running", false);
        clientMakes = input.getIntOr("makes", 0);
        clientTemperature = input.getIntOr("temperature", 0);
        portDefaults.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (column != null) {
            output.putLong("column_min", column.min().asLong());
            output.putInt("column_width", column.width());
            output.putInt("column_height", column.height());
        }
        heat.serialize(output);
        feed.serialize(output.child("feed"));
        steam.serialize(output.child("steam"));
        for (Product product : Product.values()) {
            products.get(product).serialize(output.child(product.key));
        }
        if (batchFeed != null) {
            output.putString("batch_feed", BuiltInRegistries.FLUID.getKey(batchFeed).toString());
        }
        if (lastFeed != null) {
            output.putString("last_feed", BuiltInRegistries.FLUID.getKey(lastFeed).toString());
        }
        output.putFloat("batch_bonus", batchBonus);
        output.putDouble("progress", progress);
        output.putDouble("pending_heat", pendingHeat);
        output.putBoolean("running", running);
        portDefaults.save(output);
    }

    // Clients get the saved state, with what the column makes and how hot it is for the renderer.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        tag.putInt("makes", makes());
        tag.putInt("temperature", heat.getTemperature());
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.distillation_array.sized", getWidth(), getHeight());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new DistillationArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
