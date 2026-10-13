/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.BucketSlots;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.PumpjackMenu;
import net.zagdrath.arcforge.multiblock.ModelFrame;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.multiblock.MultiblockEffects;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.multiblock.OilfieldController;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.oil.OilDeposits;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Pumpjack (see OilfieldStructures.Unit), run by its controller. Built on a Well Head, it pumps Crude Oil out of
// the deposit under it (see OilDeposits) with pumpjack.energyPerTick FE/t: pumpPerTick mB/t from a full deposit,
// slowing in a straight line to minShare of that as it drains; a drained deposit still gives tricklePerSecond mB/s, for
// good. Several pumpjacks on one deposit share it. Its Crude Oil leaves through Output ports (pushed every few ticks)
// or into buckets in its bucket slot; FE comes in through Energy ports. The beam rocks as fast as it pumps (see
// PumpjackRenderer).
public class PumpjackBlockEntity extends MachineBlockEntity implements OilfieldController {
    public static final int SLOT_BUCKET_IN = 0, SLOT_BUCKET_OUT = 1;
    public static final int MACHINE_SLOTS = 2;
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.OUTPUT, SideMode.ENERGY);
    private static final int REDSTONE_CHECK_INTERVAL = 10;
    // The beam's strokes are synced to clients when the pumping speed moves by this much.
    private static final float SPEED_STEP = 0.05F;
    // The crank's turn a tick (degrees) at full speed, and the least it turns while pumping a trickle.
    public static final float CRANK_SPEED = 9.0F, MIN_CRANK_SHARE = 0.2F;

    private OilfieldStructures.@Nullable Unit unit;
    private boolean checkRequested = true;
    private boolean powered;

    private final ConsumerEnergyHandler energy;
    private final FilteredFluidTank crude;
    private final ResourceHandler<FluidResource> crudeOutput;
    private final ResourceHandler<ItemResource> bucketAutomation;
    private final ContainerData data;

    // Crude Oil owed below a whole mB, carried to the next tick; the rate (mB/t) and its share of the full rate; the
    // last share the clients saw.
    private double pending;
    private double rate;
    private float share;
    private float syncedShare = -1.0F;
    private boolean running;
    private int usage;
    private final MultiblockPorts.Defaults portDefaults = new MultiblockPorts.Defaults();
    // Client side: the crank's angle (degrees) and when it was last moved on.
    private float crankAngle;
    private float lastFrame = -1.0F;

    public PumpjackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.PUMPJACK.get(), pos, state, MACHINE_SLOTS, (slot, resource) -> isItemValid(slot, resource), UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE, SideMode.NONE), SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(ArcforgeConfig.PUMPJACK_ENERGY_CAPACITY.getAsInt(), ArcforgeConfig.PUMPJACK_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.crude = new FilteredFluidTank(ArcforgeConfig.PUMPJACK_TANK_CAPACITY.getAsInt(), resource -> resource.is(ModFluids.CRUDE_OIL.get()),
                this::setChanged);
        this.crudeOutput = new AutomationResourceHandler<>(crude, index -> false, index -> true);
        this.bucketAutomation = new AutomationResourceHandler<>(items, slot -> slot == SLOT_BUCKET_IN, slot -> slot == SLOT_BUCKET_OUT);
        this.data = new WideIntContainerData(PumpjackMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case PumpjackMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case PumpjackMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case PumpjackMenu.DATA_USAGE -> usage;
                    case PumpjackMenu.DATA_CRUDE -> crude.getAmount();
                    case PumpjackMenu.DATA_CRUDE_CAPACITY -> crude.getCapacity();
                    case PumpjackMenu.DATA_RATE -> (int) Math.round(rate * 100);
                    case PumpjackMenu.DATA_KIND -> depositInfo()[0];
                    case PumpjackMenu.DATA_LEFT -> depositInfo()[1];
                    case PumpjackMenu.DATA_AMOUNT -> depositInfo()[2];
                    case PumpjackMenu.DATA_STATUS -> status.ordinal();
                    case PumpjackMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case PumpjackMenu.DATA_SIDE_CONFIG -> packedSides();
                    default -> 0;
                };
            }
        };
    }

    public static boolean isItemValid(int slot, ItemResource resource) {
        return slot == SLOT_BUCKET_IN && resource.is(Items.BUCKET);
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(MACHINE_SLOTS, PumpjackBlockEntity::isItemValid, UPGRADES, () -> {});
    }

    // The deposit under it, for the GUI: {kind + 1 (0 for none), buckets left (rounded up, so 0 means drained), buckets
    // it held}.
    private int[] depositInfo() {
        if (!(level instanceof ServerLevel serverLevel) || unit == null) {
            return new int[3];
        }
        OilDeposits.Deposit deposit = OilDeposits.at(serverLevel, unit.well());
        if (deposit == null) {
            return new int[3];
        }
        long left = OilDeposits.get(serverLevel.getServer()).remaining(deposit);
        return new int[] { deposit.kind().ordinal() + 1, (int) Math.min(Integer.MAX_VALUE, (left + 999) / 1_000), (int) Math.min(Integer.MAX_VALUE, deposit.amount() / 1_000) };
    }

    // The frame the pumpjack's model and shapes are built in: u from the well toward the far end.
    public static ModelFrame frame(OilfieldStructures.Unit unit) {
        return ModelFrame.toward(unit.back(), OilfieldStructures.UNIT_WIDTH, OilfieldStructures.UNIT_HEIGHT, OilfieldStructures.UNIT_LENGTH);
    }

    // --- Structure ---

    @Override
    public boolean isFormed() {
        return unit != null;
    }

    @Override
    public void requestCheck() {
        checkRequested = true;
    }

    @Override
    public void checkNow() {
        if (level instanceof ServerLevel serverLevel) {
            checkRequested = false;
            updateFormed(serverLevel);
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && unit != null) {
            OilfieldStructures.unform(serverLevel, unit.parts(), pos, unit.min(), unit.max());
            unit = null;
        }
    }

    private void updateFormed(ServerLevel level) {
        OilfieldStructures.Unit found = OilfieldStructures.findUnit(level, worldPosition, unit);
        if (Objects.equals(found, unit)) {
            return;
        }
        if (unit != null) {
            OilfieldStructures.unform(level, unit.parts(), null, unit.min(), unit.max());
        }
        unit = found;
        if (found != null) {
            OilfieldStructures.form(level, found.parts(), worldPosition, found.back(), found.min(), found.max());
            MultiblockEffects.formed(level, found.min(), found.max());
            ArcforgeAdvancements.formed(level, this, null);
        }
        running = false;
        setChanged();
        sync();
    }

    public OilfieldStructures.@Nullable Unit getUnit() {
        return unit;
    }

    @Override
    public Direction getStructureFacing() {
        return getFacing();
    }

    @Override
    public BlockPos getMinCorner() {
        return unit != null ? unit.min() : worldPosition;
    }

    @Override
    public BlockPos getMaxCorner() {
        return unit != null ? unit.max() : worldPosition;
    }

    @Override
    public boolean isPart(BlockPos pos) {
        return unit != null && unit.contains(pos);
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
        usage = 0;
        rate = 0;
        if (unit == null) {
            status = MachineStatus.NOT_FORMED;
            setRunning(false);
            return;
        }
        portDefaults.tick(level, this);
        if (level.getGameTime() % REDSTONE_CHECK_INTERVAL == 0) {
            powered = isPowered(level);
        }
        status = controlState.isEnabled() && redstoneMode.canRun(powered) ? pump(level) : stoppedStatus();
        setRunning(status == MachineStatus.PUMPING);
        BucketSlots.fill(items, SLOT_BUCKET_IN, SLOT_BUCKET_OUT, crude);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this, true);
        }
        float shown = running ? share : 0.0F;
        if (Math.abs(shown - syncedShare) >= SPEED_STEP || shown == 0.0F && syncedShare != 0.0F) {
            syncedShare = shown;
            sync();
        }
    }

    private MachineStatus pump(ServerLevel level) {
        OilDeposits.Deposit deposit = OilDeposits.at(level, unit.well());
        if (deposit == null) {
            return MachineStatus.NO_OIL;
        }
        if (crude.getSpace() <= 0) {
            return MachineStatus.TANK_FULL;
        }
        int perTick = ArcforgeConfig.PUMPJACK_ENERGY_PER_TICK.getAsInt();
        if (!energy.consume(perTick)) {
            return MachineStatus.NO_POWER;
        }
        usage = perTick;
        OilDeposits deposits = OilDeposits.get(level.getServer());
        long left = deposits.remaining(deposit);
        double full = ArcforgeConfig.PUMPJACK_RATE.getAsInt();
        double trickle = ArcforgeConfig.PUMPJACK_TRICKLE.getAsInt() / 20.0;
        if (left > 0) {
            double minShare = ArcforgeConfig.PUMPJACK_MIN_SHARE.getAsDouble();
            share = (float) (minShare + (1.0 - minShare) * left / (double) deposit.amount());
            rate = Math.max(trickle, full * share);
        } else {
            rate = trickle;
            share = (float) Math.min(1.0, trickle / full);
        }
        // Oil it couldn't put in a full tank isn't banked: at most a tick's worth (and the fraction) waits.
        pending = Math.min(pending + rate, rate + 1.0);
        int whole = (int) Math.min(crude.getSpace(), Math.floor(pending));
        if (whole > 0) {
            pending -= whole;
            if (left > 0) {
                // What a nearly dry deposit can't give, its trickle makes up.
                long taken = deposits.take(deposit, whole);
                whole = (int) Math.max(taken, Math.min(whole, Math.round(trickle)));
            }
            try (Transaction tx = Transaction.openRoot()) {
                whole = crude.insert(0, FluidResource.of(ModFluids.CRUDE_OIL.get()), whole, tx);
                tx.commit();
            }
            controlState.completed(List.of(), List.of(new FluidStack(ModFluids.CRUDE_OIL.get(), whole)), 0, 0);
            ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.CRUDE_OIL.get(), null);
        }
        setChanged();
        return MachineStatus.PUMPING;
    }

    private boolean isPowered(ServerLevel level) {
        for (BlockPos pos : unit.parts()) {
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
        if (unit != null || !lit) {
            super.setLit(lit);
        }
    }

    // Client side: the crank's angle (degrees) this frame, turning as fast as the pumpjack pumps (but not slower than a
    // fifth of full speed while it runs).
    public float advanceCrank(float now) {
        if (lastFrame >= 0.0F && syncedShare > 0.0F) {
            float speed = CRANK_SPEED * Math.max(MIN_CRANK_SHARE, syncedShare);
            crankAngle = (crankAngle + Math.max(0.0F, Math.min(20.0F, now - lastFrame)) * speed) % 360.0F;
        }
        lastFrame = now;
        return crankAngle;
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public FilteredFluidTank getCrude() {
        return crude;
    }

    // FE it used last tick.
    public int getUsage() {
        return usage;
    }

    // mB/t it pumped last tick.
    public double getRate() {
        return rate;
    }

    public boolean isRunning() {
        return running;
    }

    // The pumpjack and the beam's swing over it, so the renderer isn't culled when the controller is off screen.
    public AABB getRenderBox() {
        return unit != null ? AABB.encapsulatingFullBlocks(unit.min(), unit.max()).inflate(1.0, 2.0, 1.0) : new AABB(worldPosition);
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return mode == null ? bucketAutomation : null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        return mode == null || mode == SideMode.OUTPUT ? crudeOutput : null;
    }

    public @Nullable EnergyHandler energyHandlerAt(BlockPos pos, @Nullable Direction side) {
        if (side == null) {
            return energy;
        }
        return faceMode(pos, side) == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case FLUID -> mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
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
        if (level != null && unit != null) {
            MultiblockAutomation.refresh(level, unit.min(), unit.max());
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        unit = input.getLong("well").map(well -> new OilfieldStructures.Unit(BlockPos.of(well),
                Direction.from2DDataValue(input.getIntOr("back", 0)))).orElse(null);
        energy.deserialize(input.childOrEmpty("energy"));
        crude.deserialize(input.childOrEmpty("crude"));
        pending = input.getDoubleOr("pending", 0.0);
        running = input.getBooleanOr("running", false);
        syncedShare = input.getFloatOr("speed", 0.0F);
        portDefaults.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (unit != null) {
            output.putLong("well", unit.well().asLong());
            output.putInt("back", unit.back().get2DDataValue());
        }
        energy.serialize(output.child("energy"));
        crude.serialize(output.child("crude"));
        output.putDouble("pending", pending);
        output.putBoolean("running", running);
        output.putFloat("speed", Math.max(0.0F, syncedShare));
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
        return Component.translatable("container.arcforge.pumpjack");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new PumpjackMenu(containerId, inventory, worldPosition, items, data);
    }
}
