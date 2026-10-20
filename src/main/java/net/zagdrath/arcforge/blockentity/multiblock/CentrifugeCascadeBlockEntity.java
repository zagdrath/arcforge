/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.multiblock.CentrifugeCascadeControllerBlock;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.CentrifugeCascadeMenu;
import net.zagdrath.arcforge.multiblock.CentrifugeCascadeStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.TickSpread;
import net.zagdrath.arcforge.nuclear.Enrichment;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.SidedResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Gas Centrifuge Cascade (see CentrifugeCascadeStructure), run by its controller: it splits Uranium Hexafluoride
// into enriched product at the target assay set in its GUI (1% to 20%: civil fuel only, see Enrichment) and depleted
// tails at its tails assay (0.1% to 0.5%, 0.25% by default).
//  - Its capacity is its centrifuges times swuPerCentrifuge, in SWU a tick; each running centrifuge uses
//    energyPerCentrifuge FE/t. With less FE than that it does as much of the work as its FE pays for.
//  - Each mB of product costs Enrichment.swuPerProduct SWU and takes Enrichment.feedPerProduct mB of feed (any UF6
//    poorer than the target and richer than the tails), leaving the rest as tails (Depleted Uranium Hexafluoride). The
//    feed and tails a mB of product takes are seldom whole, so it carries the fractions to the next mB.
//  - Feed goes in through Input ports; product comes out of Output ports and tails out of Tails ports, pushed into what
//    touches them. Energy goes in through Energy ports. Product or tails of another assay left in the tanks (after the
//    target or tails changed) stop it until they're drained.
//  - Redstone control can stop it. The controller is LIT while it enriches; its centrifuges' bands spin then (see
//    CentrifugeCascadeRenderer) and the hall hums (see CentrifugeCascadeSound).
public class CentrifugeCascadeBlockEntity extends MachineBlockEntity implements MultiblockController {
    public static final int MACHINE_SLOTS = 0;
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.TAILS, SideMode.ENERGY);
    private static final Set<SideMode> PUSHED = EnumSet.of(SideMode.OUTPUT, SideMode.TAILS);
    private static final int REDSTONE_CHECK_INTERVAL = 10;
    private static final int STRUCTURE_CHECK_INTERVAL = 40;
    // The GUI's steps: target by 0.1% or 1%, tails by 0.01% or 0.05%.
    public static final int TARGET_DEFAULT = 5_000;
    // Ticks it stays LIT after it last did work (so a slow trickle doesn't flicker it).
    private static final int LIT_HOLD = 20;

    private CentrifugeCascadeStructure.@Nullable Box box;
    private boolean checkRequested = true;
    private boolean powered;
    private int centrifuges;
    private int targetPcm = TARGET_DEFAULT;
    private int tailsPcm = Enrichment.DEFAULT_TAILS_PCM;
    // Separative work done toward the next mB of product, and feed and tails owed by the mB already made (fractions).
    private double work;
    private double feedOwed;
    private double tailsOwed;
    // What it did over the last tick, for the GUI and Jade: SWU and FE.
    private double lastSwu;
    private int lastEnergy;
    private long lastWorked = Long.MIN_VALUE / 2;

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank feed;
    private final FilteredFluidTank product;
    private final FilteredFluidTank tails;
    private final ResourceHandler<FluidResource> feedInput;
    private final ResourceHandler<FluidResource> productOutput;
    private final ResourceHandler<FluidResource> tailsOutput;
    private final ResourceHandler<FluidResource> all;
    private final ContainerData data;
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();

    // Client side: the bands' angle (degrees) and when it was last moved on.
    private float angle;
    private float lastFrame = -1.0F;
    public static Consumer<CentrifugeCascadeBlockEntity> clientHook = cascade -> {};

    public CentrifugeCascadeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CENTRIFUGE_CASCADE.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE), SIDE_MODES);
        int capacity = ArcforgeConfig.CASCADE_ENERGY_CAPACITY.getAsInt();
        this.energy = new ConsumerEnergyHandler(capacity, capacity, this::setChanged);
        this.feed = new FilteredFluidTank(ArcforgeConfig.CASCADE_FEED_CAPACITY.getAsInt(),
                resource -> resource.getFluid().isSame(ModFluids.URANIUM_HEXAFLUORIDE.get()), this::setChanged);
        this.product = new FilteredFluidTank(ArcforgeConfig.CASCADE_PRODUCT_CAPACITY.getAsInt(),
                resource -> resource.getFluid().isSame(ModFluids.URANIUM_HEXAFLUORIDE.get()), this::setChanged);
        this.tails = new FilteredFluidTank(ArcforgeConfig.CASCADE_TAILS_CAPACITY.getAsInt(),
                resource -> resource.getFluid().isSame(ModFluids.DEPLETED_URANIUM_HEXAFLUORIDE.get()), this::setChanged);
        this.feedInput = new SidedResourceHandler<>(feed, true, false, Integer.MAX_VALUE);
        this.productOutput = new SidedResourceHandler<>(product, false, true, Integer.MAX_VALUE);
        this.tailsOutput = new SidedResourceHandler<>(tails, false, true, Integer.MAX_VALUE);
        // Unsided (automation that asks no face): feed goes in, product and tails come out, never the other way.
        this.all = new CombinedResourceHandler<>(feedInput, productOutput, tailsOutput);
        this.data = new WideIntContainerData(CentrifugeCascadeMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case CentrifugeCascadeMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case CentrifugeCascadeMenu.DATA_ENERGY_CAPACITY -> energy.getCapacityAsInt();
                    case CentrifugeCascadeMenu.DATA_CENTRIFUGES -> box != null ? centrifuges : 0;
                    case CentrifugeCascadeMenu.DATA_TARGET -> targetPcm;
                    case CentrifugeCascadeMenu.DATA_TAILS_ASSAY -> tailsPcm;
                    case CentrifugeCascadeMenu.DATA_FEED_ASSAY -> feed.getAmount() > 0 ? Enrichment.assay(feed.getResource(0)) : 0;
                    case CentrifugeCascadeMenu.DATA_FEED -> feed.getAmount();
                    case CentrifugeCascadeMenu.DATA_FEED_CAPACITY -> feed.getCapacity();
                    case CentrifugeCascadeMenu.DATA_PRODUCT -> product.getAmount();
                    case CentrifugeCascadeMenu.DATA_PRODUCT_ASSAY -> product.getAmount() > 0 ? Enrichment.assay(product.getResource(0)) : 0;
                    case CentrifugeCascadeMenu.DATA_PRODUCT_CAPACITY -> product.getCapacity();
                    case CentrifugeCascadeMenu.DATA_TAILS -> tails.getAmount();
                    case CentrifugeCascadeMenu.DATA_TAILS_CAPACITY -> tails.getCapacity();
                    case CentrifugeCascadeMenu.DATA_SWU_MILLI -> (int) Math.round(lastSwu * 1000.0);
                    case CentrifugeCascadeMenu.DATA_CAPACITY_MILLI -> (int) Math.round(swuPerTick() * 1000.0);
                    case CentrifugeCascadeMenu.DATA_ENERGY_USE -> lastEnergy;
                    case CentrifugeCascadeMenu.DATA_FORMED -> box != null ? 1 : 0;
                    case CentrifugeCascadeMenu.DATA_STATUS -> status.ordinal();
                    case CentrifugeCascadeMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case CentrifugeCascadeMenu.DATA_SIDE_CONFIG -> packedSides();
                    default -> 0;
                };
            }
        };
    }

    // A client-side copy of the (no) slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, (slot, resource) -> false, UPGRADES, () -> {});
    }

    // --- The numbers ---

    // SWU a tick at full power (0 while it isn't formed).
    public double swuPerTick() {
        return box != null ? centrifuges * ArcforgeConfig.CASCADE_SWU_PER_CENTRIFUGE.getAsDouble() : 0.0;
    }

    public int energyPerTick() {
        return box != null ? centrifuges * ArcforgeConfig.CASCADE_ENERGY_PER_CENTRIFUGE.getAsInt() : 0;
    }

    public int getCentrifuges() {
        return box != null ? centrifuges : 0;
    }

    public int getTarget() {
        return targetPcm;
    }

    public int getTailsAssay() {
        return tailsPcm;
    }

    public double getLastSwu() {
        return lastSwu;
    }

    // FE it used last tick.
    public int getLastEnergy() {
        return lastEnergy;
    }

    // The highest target the GUI allows (gasCentrifugeCascade.maxTarget, never above 20%).
    public static int maxTarget() {
        return Math.min(Enrichment.MAX_PCM, (int) Math.round(ArcforgeConfig.CASCADE_MAX_TARGET.getAsDouble() * 1000.0));
    }

    public void setTarget(int pcm) {
        int held = Mth.clamp(pcm, Enrichment.MIN_TARGET_PCM, maxTarget());
        if (held != targetPcm) {
            targetPcm = held;
            work = 0.0;
            setChanged();
        }
    }

    public void setTailsAssay(int pcm) {
        int held = Mth.clamp(pcm, Enrichment.MIN_TAILS_PCM, Enrichment.MAX_TAILS_PCM);
        if (held != tailsPcm) {
            tailsPcm = held;
            work = 0.0;
            setChanged();
        }
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getFeed() {
        return feed;
    }

    public FilteredFluidTank getProduct() {
        return product;
    }

    public FilteredFluidTank getTails() {
        return tails;
    }

    // --- Structure ---

    @Override
    public void onLoad() {
        super.onLoad();
        CentrifugeCascadeStructure.register(this);
    }

    @Override
    public void setRemoved() {
        CentrifugeCascadeStructure.unregister(this);
        super.setRemoved();
    }

    @Override
    public boolean isFormed() {
        return box != null;
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

    // Breaking the controller un-forms the rest of the hall.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && box != null) {
            CentrifugeCascadeStructure.unform(serverLevel, box, pos);
            box = null;
        }
    }

    private void sync(Level level) {
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    private void updateFormed(ServerLevel level) {
        if (box != null && !isLoaded(level, box)) {
            return;
        }
        CentrifugeCascadeStructure.Box found = CentrifugeCascadeStructure.find(level, worldPosition, box);
        if (Objects.equals(found, box) && (box == null || CentrifugeCascadeStructure.isFullyFormed(level, box))) {
            if (box != null) {
                centrifuges = CentrifugeCascadeStructure.centrifuges(level, box).size();
            }
            return;
        }
        if (box != null) {
            CentrifugeCascadeStructure.unform(level, box, null);
        }
        box = found;
        centrifuges = 0;
        if (found != null) {
            CentrifugeCascadeStructure.form(level, found, worldPosition);
            centrifuges = CentrifugeCascadeStructure.centrifuges(level, found).size();
            MultiblockEffects.formed(level, found.min(), found.max());
            ArcforgeAdvancements.formed(level, this, null);
        }
        setLit(level, false);
        setChanged();
        sync(level);
    }

    private static boolean isLoaded(Level level, CentrifugeCascadeStructure.Box box) {
        return level.isLoaded(box.min()) && level.isLoaded(box.max())
                && level.isLoaded(new BlockPos(box.min().getX(), box.min().getY(), box.max().getZ()))
                && level.isLoaded(new BlockPos(box.max().getX(), box.min().getY(), box.min().getZ()));
    }

    public CentrifugeCascadeStructure.@Nullable Box getBox() {
        return box;
    }

    @Override
    public Direction getStructureFacing() {
        return getFacing();
    }

    @Override
    public BlockPos getMinCorner() {
        return box != null ? box.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return box != null ? box.max() : worldPosition;
    }

    // The floor (casings and controller): what can hold ports.
    @Override
    public boolean isPart(BlockPos pos) {
        return box != null && box.isFloor(pos);
    }

    // For advancements (arcforge:multiblock_formed's min_length): its centrifuges.
    @Override
    public int length() {
        return getCentrifuges();
    }

    public AABB getRenderBox() {
        return box != null ? box.aabb().inflate(0.5) : new AABB(worldPosition);
    }

    // --- Running ---

    public void serverTick(ServerLevel level) {
        if (checkRequested || TickSpread.due(level, worldPosition, STRUCTURE_CHECK_INTERVAL)) {
            checkNow();
        }
        lastSwu = 0.0;
        lastEnergy = 0;
        if (box == null) {
            status = MachineStatus.NOT_FORMED;
            setLit(level, false);
            return;
        }
        portDefaults.tick(level, this);
        if (TickSpread.due(level, worldPosition, REDSTONE_CHECK_INTERVAL)) {
            powered = isPowered(level);
        }
        status = enrich(level);
        setLit(level, level.getGameTime() - lastWorked < LIT_HOLD);
        MultiblockAutomation.pushFluids(level, this, PUSHED, FluidType.BUCKET_VOLUME);
    }

    // One tick of separation; what it's doing.
    private MachineStatus enrich(ServerLevel level) {
        if (!controlState.isEnabled() || !redstoneMode.canRun(powered)) {
            return stoppedStatus();
        }
        if (feed.getAmount() <= 0) {
            return MachineStatus.NO_FEED;
        }
        FluidResource feedResource = feed.getResource(0);
        int feedPcm = Enrichment.assay(feedResource);
        double feedPerProduct = Enrichment.feedPerProduct(targetPcm, feedPcm, tailsPcm);
        double swuPerProduct = Enrichment.swuPerProduct(targetPcm, feedPcm, tailsPcm);
        if (Double.isInfinite(feedPerProduct) || !(swuPerProduct > 0.0)) {
            return MachineStatus.FEED_TOO_RICH;
        }
        FluidResource productResource = Enrichment.withAssay(ModFluids.URANIUM_HEXAFLUORIDE.get(), targetPcm);
        FluidResource tailsResource = Enrichment.withAssay(ModFluids.DEPLETED_URANIUM_HEXAFLUORIDE.get(), tailsPcm);
        if (product.getAmount() > 0 && !product.getResource(0).equals(productResource)
                || tails.getAmount() > 0 && !tails.getResource(0).equals(tailsResource)) {
            return MachineStatus.ASSAY_MISMATCH;
        }
        // Room for the next mB of product and its tails, and the feed it takes.
        int tailsNext = (int) Math.ceil(tailsOwed + feedPerProduct - 1.0);
        int feedNext = (int) Math.ceil(feedOwed + feedPerProduct);
        if (product.getSpace() < 1 || tails.getSpace() < tailsNext) {
            return MachineStatus.OUTPUT_FULL;
        }
        if (feed.getAmount() < feedNext) {
            return MachineStatus.NO_FEED;
        }
        // Power: as much of the full rate as the FE pays for.
        int wanted = energyPerTick();
        double share = 1.0;
        if (wanted > 0) {
            int paid = Math.min(wanted, energy.getAmountAsInt());
            if (paid <= 0) {
                return MachineStatus.NO_POWER;
            }
            energy.consume(paid);
            lastEnergy = paid;
            share = (double) paid / wanted;
        }
        double swu = swuPerTick() * share;
        lastSwu = swu;
        work += swu;
        lastWorked = level.getGameTime();
        // Each whole mB of product the work has paid for.
        int made = 0;
        while (work >= swuPerProduct && product.getSpace() >= 1) {
            double feedUsed = feedOwed + feedPerProduct;
            double tailsMade = tailsOwed + feedPerProduct - 1.0;
            int feedTaken = (int) Math.floor(feedUsed);
            int tailsGiven = (int) Math.floor(tailsMade);
            if (feed.getAmount() < feedTaken || tails.getSpace() < tailsGiven) {
                break;
            }
            take(feed, feedTaken);
            add(product, productResource, 1);
            add(tails, tailsResource, tailsGiven);
            feedOwed = feedUsed - feedTaken;
            tailsOwed = tailsMade - tailsGiven;
            work -= swuPerProduct;
            made++;
        }
        if (made > 0) {
            ArcforgeAdvancements.produced(this, net.minecraft.world.item.ItemStack.EMPTY, ModFluids.URANIUM_HEXAFLUORIDE.get(), "enrichment");
        }
        return MachineStatus.ENRICHING;
    }

    private static void add(FilteredFluidTank tank, FluidResource resource, int amount) {
        if (amount > 0) {
            tank.set(0, resource, tank.getAmount() + amount);
        }
    }

    private static void take(FilteredFluidTank tank, int amount) {
        if (amount <= 0) {
            return;
        }
        int left = tank.getAmount() - amount;
        tank.set(0, left > 0 ? tank.getResource(0) : FluidResource.EMPTY, Math.max(0, left));
    }

    private void setLit(Level level, boolean lit) {
        BlockState state = getBlockState();
        if (state.hasProperty(CentrifugeCascadeControllerBlock.LIT) && state.getValue(CentrifugeCascadeControllerBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(CentrifugeCascadeControllerBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    private boolean isPowered(ServerLevel level) {
        for (BlockPos pos : box.floor()) {
            if (level.hasNeighborSignal(pos)) {
                return true;
            }
        }
        return false;
    }

    // --- Client side ---

    public void clientTick() {
        clientHook.accept(this);
    }

    public boolean isRunning() {
        BlockState state = getBlockState();
        return state.hasProperty(CentrifugeCascadeControllerBlock.LIT) && state.getValue(CentrifugeCascadeControllerBlock.LIT);
    }

    // The bands' angle (degrees) this frame: a full turn every 8 ticks while it runs (the rotors themselves turn
    // far faster).
    public float advance(float now) {
        if (lastFrame >= 0.0F && isRunning()) {
            angle = (angle + Math.max(0.0F, Math.min(20.0F, now - lastFrame)) * 45.0F) % 360.0F;
        }
        lastFrame = now;
        return angle;
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return null;
    }

    // Input faces take feed in; Output faces give product, Tails faces tails. Unsided: the three together, each its
    // own way (and the radiation they give off, see RadiationField).
    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (box == null) {
            return null;
        }
        if (mode == null) {
            return all;
        }
        return switch (mode) {
            case INPUT -> feedInput;
            case OUTPUT -> productOutput;
            case TAILS -> tailsOutput;
            default -> null;
        };
    }

    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (box == null) {
            return null;
        }
        if (side == null) {
            return energy;
        }
        return faceMode(pos, side) == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : mode == SideMode.OUTPUT || mode == SideMode.TAILS ? ConnectionMode.OUTPUT
                    : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            default -> ConnectionMode.NONE;
        };
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return isFormed() ? conduitConnectionAt(worldPosition, side, type) : ConnectionMode.NONE;
    }

    @Override
    protected void onSideConfigChanged() {
        setChanged();
        if (level != null && box != null) {
            MultiblockAutomation.refresh(level, box.min(), box.max());
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        box = in.getLong("box_min").flatMap(min -> in.getLong("box_max").map(max -> new CentrifugeCascadeStructure.Box(BlockPos.of(min), BlockPos.of(max),
                Direction.from2DDataValue(in.getIntOr("box_front", 0))))).orElse(null);
        centrifuges = in.getIntOr("centrifuges", 0);
        targetPcm = Mth.clamp(in.getIntOr("target", TARGET_DEFAULT), Enrichment.MIN_TARGET_PCM, Enrichment.MAX_PCM);
        tailsPcm = Mth.clamp(in.getIntOr("tails_assay", Enrichment.DEFAULT_TAILS_PCM), Enrichment.MIN_TAILS_PCM, Enrichment.MAX_TAILS_PCM);
        work = in.getDoubleOr("work", 0.0);
        feedOwed = in.getDoubleOr("feed_owed", 0.0);
        tailsOwed = in.getDoubleOr("tails_owed", 0.0);
        energy.deserialize(in.childOrEmpty("energy"));
        feed.deserialize(in.childOrEmpty("feed"));
        product.deserialize(in.childOrEmpty("product"));
        tails.deserialize(in.childOrEmpty("tails"));
        portDefaults.load(in);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (box != null) {
            out.putLong("box_min", box.min().asLong());
            out.putLong("box_max", box.max().asLong());
            out.putInt("box_front", box.front().get2DDataValue());
        }
        out.putInt("centrifuges", centrifuges);
        out.putInt("target", targetPcm);
        out.putInt("tails_assay", tailsPcm);
        out.putDouble("work", work);
        out.putDouble("feed_owed", feedOwed);
        out.putDouble("tails_owed", tailsOwed);
        energy.serialize(out.child("energy"));
        feed.serialize(out.child("feed"));
        product.serialize(out.child("product"));
        tails.serialize(out.child("tails"));
        portDefaults.save(out);
    }

    // Clients get the box when the chunk loads and whenever it changes (the renderer draws from it).
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
        return box != null ? Component.translatable("container.arcforge.centrifuge_cascade.sized", box.sizeText())
                : Component.translatable("container.arcforge.centrifuge_cascade");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CentrifugeCascadeMenu(containerId, inventory, worldPosition, items, data);
    }

    // The fluid in each tank as registry id, for the screen.
    public static int fluidId(FilteredFluidTank tank) {
        return tank.getAmount() > 0 ? BuiltInRegistries.FLUID.getId(tank.getResource(0).getFluid()) : -1;
    }
}
