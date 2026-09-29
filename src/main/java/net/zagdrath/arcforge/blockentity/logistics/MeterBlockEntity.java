/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.logistics;

import java.util.List;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.arcforge.block.logistics.MeterBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.item.tool.SettingsCopyable;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.interaction.Dismantleable;
import net.zagdrath.arcforge.machine.meter.MeterKind;
import net.zagdrath.arcforge.machine.meter.MeterSettings;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.logistics.MeterMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModCapabilities;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.security.Owned;
import net.zagdrath.arcforge.security.Ownership;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.SidedEnergyHandler;
import net.zagdrath.arcforge.transfer.energy.TrackingEnergyHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;

// An Energy, Heat, Fluid or Gas Meter. It passes what its kind carries from its left side (as you face its screen)
// to its right, up to the kind's cap a tick, through a buffer of one tick's flow: it takes what's pushed into its
// left and pulls from the block there, and pushes into the block on its right (conduits there pull too). What leaves
// the buffer each tick is its flow; the rate is the average over smoothingTicks. It gives a signal of 15 on every
// side while the rate is above (or below) its threshold, and comparators read the rate against the cap.
public class MeterBlockEntity extends BlockEntity implements MenuProvider, Owned, ConduitConnectable, SettingsCopyable, Dismantleable {
    // The most samples the rate is averaged over (config smoothingTicks is capped by this).
    private static final int MAX_SAMPLES = 1_200;
    // The rate shown on the front and in Jade is resent at most this often while it changes.
    private static final int DISPLAY_SYNC_TICKS = 10;

    private final MeterKind kind;
    private final Ownership ownership = new Ownership(this::setChanged);
    private MeterSettings settings = MeterSettings.DEFAULT;

    // Energy.
    private final TrackingEnergyHandler energy;
    private final EnergyHandler energyIn;
    private final EnergyHandler energyOut;
    // Heat: the buffer, and how hot what feeds it is.
    private int heat;
    private int heatCelsius = HeatBuffer.AMBIENT_CELSIUS;
    private final HeatHandler heatIn = new HeatIn();
    private final HeatHandler heatOut = new HeatOut();
    // Liquids or gases.
    private final FilteredFluidTank tank;
    private final ResourceHandler<FluidResource> fluidIn;
    private final ResourceHandler<FluidResource> fluidOut;
    private final Predicate<FluidResource> fluidFilter;
    private int lastTankAmount;

    // What left the buffer this tick, and the recent ticks' flow.
    private int passed;
    private final int[] samples = new int[MAX_SAMPLES];
    private int sampleIndex;
    private int rate;
    // The rate the client was last sent, and when.
    private int syncedRate = -1;
    private long lastSync;

    private final ContainerData data;

    public MeterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.METER.get(), pos, state);
        this.kind = state.getBlock() instanceof MeterBlock meter ? meter.kind() : MeterKind.ENERGY;
        int cap = kind.cap();
        this.energy = new TrackingEnergyHandler(kind == MeterKind.ENERGY ? cap : 1, kind == MeterKind.ENERGY ? cap : 0, this::setChanged);
        this.energyIn = new SidedEnergyHandler(energy, true, false);
        this.energyOut = new SidedEnergyHandler(energy, false, true);
        this.fluidFilter = kind == MeterKind.GAS ? Gases::isGas : resource -> !Gases.isGas(resource);
        this.tank = new FilteredFluidTank(kind == MeterKind.FLUID || kind == MeterKind.GAS ? cap : 1, fluidFilter, this::onTankChanged);
        this.fluidIn = new AutomationResourceHandler<>(tank, index -> true, index -> false);
        this.fluidOut = new AutomationResourceHandler<>(tank, index -> false, index -> true);
        this.data = new WideIntContainerData(MeterMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case MeterMenu.DATA_RATE -> rate;
                    case MeterMenu.DATA_THRESHOLD -> settings.threshold();
                    case MeterMenu.DATA_MODE -> settings.mode().ordinal();
                    case MeterMenu.DATA_POWERED -> isPowered() ? 1 : 0;
                    default -> 0;
                };
            }
        };
    }

    public MeterKind getKind() {
        return kind;
    }

    public MeterSettings getSettings() {
        return settings;
    }

    public void setSettings(MeterSettings settings) {
        MeterSettings clamped = new MeterSettings(Mth.clamp(settings.threshold(), 0, kind.cap()), settings.mode());
        if (!clamped.equals(this.settings)) {
            this.settings = clamped;
            setChanged();
        }
    }

    // The smoothed flow per tick.
    public int getRate() {
        return rate;
    }

    public boolean isPowered() {
        return getBlockState().hasProperty(MeterBlock.POWERED) && getBlockState().getValue(MeterBlock.POWERED);
    }

    // Comparators: 0 with no flow, else 1 to 15 by the rate against the cap.
    public int getComparatorSignal() {
        return comparatorSignal(rate, kind.cap());
    }

    public static int comparatorSignal(int rate, int cap) {
        return rate <= 0 ? 0 : Mth.clamp(Mth.ceil(15.0 * rate / Math.max(1, cap)), 1, 15);
    }

    private Direction left() {
        return RelativeSide.LEFT.toDirection(getBlockState().getValue(MeterBlock.FACING));
    }

    private Direction right() {
        return RelativeSide.RIGHT.toDirection(getBlockState().getValue(MeterBlock.FACING));
    }

    // --- Ticking ---

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, MeterBlockEntity meter) {
        meter.tick(level, pos, state);
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        int cap = kind.cap();
        Direction left = left();
        Direction right = right();
        BlockPos leftPos = pos.relative(left);
        BlockPos rightPos = pos.relative(right);
        switch (kind) {
            case ENERGY -> {
                EnergyHandler target = level.getCapability(Capabilities.Energy.BLOCK, rightPos, right.getOpposite());
                if (target != null) {
                    EnergyHandlerUtil.move(energy, target, cap, null);
                }
                EnergyHandler source = level.getCapability(Capabilities.Energy.BLOCK, leftPos, left.getOpposite());
                if (source != null) {
                    EnergyHandlerUtil.move(source, energy, cap, null);
                }
                passed += energy.getExtracted();
                energy.resetTotals();
            }
            case HEAT -> {
                HeatHandler source = level.getCapability(ModCapabilities.HEAT, leftPos, left.getOpposite());
                heatCelsius = source != null ? source.getTemperature() : heat > 0 ? heatCelsius : HeatBuffer.AMBIENT_CELSIUS;
                HeatHandler target = level.getCapability(ModCapabilities.HEAT, rightPos, right.getOpposite());
                if (target != null && heat > 0 && heatCelsius > target.getTemperature()) {
                    int moved = target.receiveHeat(Math.min(heat, cap), false);
                    heat -= moved;
                    passed += moved;
                }
                if (source != null && heat < cap) {
                    heat += source.extractHeat(cap - heat, false);
                }
            }
            case FLUID, GAS -> {
                ResourceHandler<FluidResource> target = level.getCapability(Capabilities.Fluid.BLOCK, rightPos, right.getOpposite());
                if (target != null) {
                    ResourceHandlerUtil.move(tank, target, fluidFilter, cap, null);
                }
                ResourceHandler<FluidResource> source = level.getCapability(Capabilities.Fluid.BLOCK, leftPos, left.getOpposite());
                if (source != null) {
                    ResourceHandlerUtil.move(source, tank, fluidFilter, cap, null);
                }
            }
        }

        // The rate: the average flow over the last smoothingTicks ticks.
        int window = Math.min(MAX_SAMPLES, ArcforgeConfig.METER_SMOOTHING_TICKS.getAsInt());
        samples[sampleIndex % window] = passed;
        sampleIndex = (sampleIndex + 1) % window;
        passed = 0;
        long sum = 0;
        for (int i = 0; i < window; i++) {
            sum += samples[i];
        }
        int newRate = (int) (sum / window);
        if (newRate != rate) {
            int signalBefore = getComparatorSignal();
            rate = newRate;
            if (getComparatorSignal() != signalBefore) {
                level.updateNeighbourForOutputSignal(pos, state.getBlock());
            }
        }

        boolean powered = settings.signals(rate);
        if (powered != state.getValue(MeterBlock.POWERED)) {
            level.setBlock(pos, state.setValue(MeterBlock.POWERED, powered), Block.UPDATE_ALL);
        }
        if (rate != syncedRate && level.getGameTime() - lastSync >= DISPLAY_SYNC_TICKS) {
            syncedRate = rate;
            lastSync = level.getGameTime();
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    // Fluid that left the tank (pushed out, or pulled by a conduit) is flow.
    private void onTankChanged() {
        int now = tank.getAmount();
        if (now < lastTankAmount) {
            passed += lastTankAmount - now;
        }
        lastTankAmount = now;
        setChanged();
    }

    // --- Capabilities: in on the left, out on the right, nothing elsewhere ---

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        if (kind != MeterKind.ENERGY) {
            return null;
        }
        return side == left() ? energyIn : side == right() ? energyOut : null;
    }

    public @Nullable HeatHandler getHeatHandler(@Nullable Direction side) {
        if (kind != MeterKind.HEAT) {
            return null;
        }
        return side == left() ? heatIn : side == right() ? heatOut : null;
    }

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        if (kind != MeterKind.FLUID && kind != MeterKind.GAS) {
            return null;
        }
        return side == left() ? fluidIn : side == right() ? fluidOut : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        if (type != kind.conduitType()) {
            return ConnectionMode.NONE;
        }
        return side == left() ? ConnectionMode.INPUT : side == right() ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
    }

    // The left side takes heat from anything hotter than ambient; the right side gives it at the source's heat.
    private final class HeatIn implements HeatHandler {
        @Override
        public int getHeat() {
            return heat;
        }

        @Override
        public int getMaxHeat() {
            return kind.cap();
        }

        @Override
        public int getTemperature() {
            return HeatBuffer.AMBIENT_CELSIUS;
        }

        @Override
        public int receiveHeat(int amount, boolean simulate) {
            int accepted = Math.max(0, Math.min(amount, kind.cap() - heat));
            if (!simulate && accepted > 0) {
                heat += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public int extractHeat(int amount, boolean simulate) {
            return 0;
        }
    }

    private final class HeatOut implements HeatHandler {
        @Override
        public int getHeat() {
            return heat;
        }

        @Override
        public int getMaxHeat() {
            return kind.cap();
        }

        @Override
        public int getTemperature() {
            return heat > 0 ? heatCelsius : HeatBuffer.AMBIENT_CELSIUS;
        }

        @Override
        public int receiveHeat(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int extractHeat(int amount, boolean simulate) {
            int taken = Math.max(0, Math.min(amount, heat));
            if (!simulate && taken > 0) {
                heat -= taken;
                passed += taken;
                setChanged();
            }
            return taken;
        }
    }

    // --- Security, menu, settings ---

    @Override
    public Ownership ownership() {
        return ownership;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge." + kind.getSerializedName() + "_meter");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new MeterMenu(containerId, inventory, worldPosition, data);
    }

    @Override
    public Identifier settingsKind() {
        return BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock());
    }

    @Override
    public void writeSettings(ValueOutput output) {
        output.store("meter", MeterSettings.CODEC, settings);
    }

    @Override
    public int readSettings(ValueInput input) {
        input.read("meter", MeterSettings.CODEC).ifPresent(this::setSettings);
        return 0;
    }

    @Override
    public List<Component> describe(ValueInput input) {
        return input.read("meter", MeterSettings.CODEC)
                .map(read -> List.<Component>of(Component.translatable("gui.arcforge.meter.threshold_value", read.mode().displayName(),
                        String.format("%,d", read.threshold()), kind.unit())))
                .orElse(List.of());
    }

    // --- Item components: the settings go with the item ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        MeterSettings stored = components.get(ModDataComponents.METER_SETTINGS.get());
        if (stored != null) {
            setSettings(stored);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!settings.equals(MeterSettings.DEFAULT)) {
            components.set(ModDataComponents.METER_SETTINGS.get(), settings);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("meter");
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ownership.load(input);
        settings = input.read("meter", MeterSettings.CODEC).orElse(MeterSettings.DEFAULT);
        energy.load(input.getIntOr("energy", 0));
        heat = Math.max(0, input.getIntOr("heat", 0));
        heatCelsius = input.getIntOr("heat_celsius", HeatBuffer.AMBIENT_CELSIUS);
        tank.deserialize(input.childOrEmpty("tank"));
        lastTankAmount = tank.getAmount();
        rate = input.getIntOr("rate", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ownership.save(output);
        output.store("meter", MeterSettings.CODEC, settings);
        output.putInt("energy", energy.getAmountAsInt());
        output.putInt("heat", heat);
        output.putInt("heat_celsius", heatCelsius);
        tank.serialize(output.child("tank"));
        output.putInt("rate", rate);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
