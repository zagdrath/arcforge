/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.arcforge.api.gas.GasHandler;
import net.zagdrath.arcforge.api.machine.resource.EnergyRole;
import net.zagdrath.arcforge.api.machine.resource.HeatRole;
import net.zagdrath.arcforge.api.machine.settings.InstalledUpgrade;
import net.zagdrath.arcforge.api.machine.MachineControl;
import net.zagdrath.arcforge.api.machine.resource.MachineEnergy;
import net.zagdrath.arcforge.api.machine.resource.MachineFluids;
import net.zagdrath.arcforge.api.machine.resource.MachineHeat;
import net.zagdrath.arcforge.api.machine.resource.MachineItems;
import net.zagdrath.arcforge.api.machine.status.MachineListener;
import net.zagdrath.arcforge.api.machine.settings.MachineOption;
import net.zagdrath.arcforge.api.machine.MachineOwner;
import net.zagdrath.arcforge.api.machine.settings.MachinePort;
import net.zagdrath.arcforge.api.machine.settings.MachineSettings;
import net.zagdrath.arcforge.api.machine.settings.MachineSide;
import net.zagdrath.arcforge.api.machine.status.MachineStatistics;
import net.zagdrath.arcforge.api.machine.status.MachineStatus;
import net.zagdrath.arcforge.api.machine.settings.OptionType;
import net.zagdrath.arcforge.api.machine.settings.RedstoneMode;
import net.zagdrath.arcforge.api.machine.settings.SettingResult;
import net.zagdrath.arcforge.api.machine.settings.SideModes;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;
import net.zagdrath.arcforge.api.machine.StructureSize;
import net.zagdrath.arcforge.blockentity.machine.MachineBlockEntity;
import net.zagdrath.arcforge.gas.FluidGasHandler;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.item.tool.MachineSettings.Frame;
import net.zagdrath.arcforge.machine.config.ConfigurableMachine;
import net.zagdrath.arcforge.machine.config.RelativeSide;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPorts;
import net.zagdrath.arcforge.security.Owned;
import net.zagdrath.arcforge.transfer.Transactions;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The machine control API for one block entity, read through its MachineControlSpec. Every write goes through the
// machine's own handlers and setters, after the same checks its GUI, the Wrench and pipes make.
final class SpecMachineControl<T extends BlockEntity> implements MachineControl {
    private final MachineControlSpec<T> spec;
    private final T machine;
    private final MachineControlState state;

    private final @Nullable MachineEnergy energy;
    private final @Nullable MachineItems items;
    private final @Nullable MachineFluids fluids;
    private final @Nullable MachineHeat heat;
    private final MachineSettings settings = new Settings();
    private final MachineStatistics statistics = new Statistics();
    private @Nullable GasHandler gases;
    private boolean gasesResolved;

    SpecMachineControl(MachineControlSpec<T> spec, T machine, MachineControlState state) {
        this.spec = spec;
        this.machine = machine;
        this.state = state;
        this.energy = spec.energy == null ? null : new Energy(spec.energy);
        this.items = spec.items == null ? null : new Items(spec.items);
        this.fluids = spec.tanks.isEmpty() ? null : new Fluids();
        this.heat = spec.heat == null ? null : new Heat(spec.heat);
    }

    T blockEntity() {
        return machine;
    }

    // Once a tick while loaded (MachineControlTracker).
    void tick() {
        state.tick(this, status());
    }

    private void changed() {
        machine.setChanged();
    }

    // --- Identity ---

    @Override
    public Identifier machineType() {
        if (machine instanceof MultiblockController controller) {
            return controller.multiblockId();
        }
        Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(machine.getType());
        return id != null ? id : BuiltInRegistries.BLOCK.getKey(machine.getBlockState().getBlock());
    }

    @Override
    public Component displayName() {
        return machine instanceof MenuProvider menu ? menu.getDisplayName() : machine.getBlockState().getBlock().getName();
    }

    @Override
    public OptionalInt tier() {
        return OptionalInt.empty();
    }

    @Override
    public boolean isMultiblock() {
        return machine instanceof MultiblockController;
    }

    @Override
    public boolean isFormed() {
        return !(machine instanceof MultiblockController controller) || controller.isFormed();
    }

    @Override
    public Optional<StructureSize> structureSize() {
        if (machine instanceof MultiblockController controller && controller.isFormed()) {
            var size = Frame.of(controller).size();
            return Optional.of(new StructureSize(size.width(), size.height(), size.depth()));
        }
        return Optional.empty();
    }

    @Override
    public BlockPos position() {
        return machine.getBlockPos();
    }

    @Override
    public Optional<MachineOwner> owner() {
        if (machine instanceof Owned owned && owned.owner() != null) {
            String name = owned.ownerName();
            return Optional.of(new MachineOwner(owned.owner(), name == null ? "" : name));
        }
        return Optional.empty();
    }

    @Override
    public boolean isValid() {
        Level level = machine.getLevel();
        return !machine.isRemoved() && level != null && level.getBlockEntity(machine.getBlockPos()) == machine && spec.primary.test(machine);
    }

    // --- Status ---

    @Override
    public MachineStatus status() {
        if (!isFormed()) {
            return MachineStatus.NOT_FORMED;
        }
        if (!state.isEnabled()) {
            return MachineStatus.DISABLED;
        }
        if (spec.report != null) {
            return spec.report.apply(machine).status();
        }
        var own = ownStatus();
        return own != null ? StatusMapping.toApi(own) : MachineStatus.IDLE;
    }

    @Override
    public Component statusReason() {
        if (!isFormed()) {
            return net.zagdrath.arcforge.machine.MachineStatus.NOT_FORMED.getDescription();
        }
        if (!state.isEnabled()) {
            return net.zagdrath.arcforge.machine.MachineStatus.SWITCHED_OFF.getDescription();
        }
        if (spec.report != null) {
            return spec.report.apply(machine).reason();
        }
        var own = ownStatus();
        return own != null ? own.getDescription() : net.zagdrath.arcforge.machine.MachineStatus.IDLE.getDescription();
    }

    private net.zagdrath.arcforge.machine.@Nullable MachineStatus ownStatus() {
        if (spec.status != null) {
            return spec.status.apply(machine);
        }
        return machine instanceof MachineBlockEntity machineEntity ? machineEntity.getStatus() : null;
    }

    @Override
    public OptionalDouble progress() {
        return spec.progress == null || !isFormed() ? OptionalDouble.empty() : spec.progress.apply(machine);
    }

    @Override
    public OptionalInt ticksRemaining() {
        return spec.remaining == null || !isFormed() ? OptionalInt.empty() : spec.remaining.apply(machine);
    }

    @Override
    public List<ItemStack> currentOutputs() {
        return spec.outputs.apply(machine).stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList();
    }

    @Override
    public List<FluidStack> currentFluidOutputs() {
        return spec.fluidOutputs.apply(machine).stream().filter(stack -> !stack.isEmpty()).map(FluidStack::copy).toList();
    }

    // --- Resources ---

    @Override
    public Optional<MachineEnergy> energy() {
        return Optional.ofNullable(energy);
    }

    @Override
    public Optional<MachineItems> items() {
        return Optional.ofNullable(items);
    }

    @Override
    public Optional<MachineFluids> fluids() {
        return Optional.ofNullable(fluids);
    }

    // The gas API's view (GasCapabilities.BLOCK): the tanks whose own filter takes some gas (bar liquid tanks), with their roles, through
    // the same role-checked tanks as fluids(). Worked out on first use; null if the machine has no gas tank.
    @Nullable GasHandler gases() {
        if (!gasesResolved) {
            gasesResolved = true;
            gases = fluids == null ? null : FluidGasHandler.of(fluids.handler(),
                    tank -> !spec.tanks.get(tank).liquid()
                            && FluidGasHandler.holdsGas(spec.tanks.get(tank).handler().apply(machine), spec.tanks.get(tank).index()),
                    tank -> spec.tanks.get(tank).role().insertable(),
                    tank -> spec.tanks.get(tank).role().extractable());
        }
        return gases;
    }

    @Override
    public Optional<MachineHeat> heat() {
        return Optional.ofNullable(heat);
    }

    @Override
    public MachineSettings settings() {
        return settings;
    }

    @Override
    public MachineStatistics statistics() {
        return statistics;
    }

    @Override
    public void addListener(MachineListener listener) {
        state.addListener(listener);
    }

    @Override
    public void removeListener(MachineListener listener) {
        state.removeListener(listener);
    }

    // --- Views ---

    private final class Energy implements MachineEnergy {
        private final MachineControlSpec.Energy<T> energy;

        Energy(MachineControlSpec.Energy<T> energy) {
            this.energy = energy;
        }

        @Override
        public EnergyRole role() {
            return energy.role();
        }

        @Override
        public long stored() {
            return energy.stored().applyAsLong(machine);
        }

        @Override
        public long capacity() {
            return energy.capacity().applyAsLong(machine);
        }

        @Override
        public long perTick() {
            return energy.perTick().applyAsLong(machine);
        }
    }

    private final class Heat implements MachineHeat {
        private final MachineControlSpec.Heat<T> heat;

        Heat(MachineControlSpec.Heat<T> heat) {
            this.heat = heat;
        }

        @Override
        public HeatRole role() {
            return heat.role();
        }

        @Override
        public long stored() {
            return heat.stored().applyAsLong(machine);
        }

        @Override
        public long capacity() {
            return heat.capacity().applyAsLong(machine);
        }

        @Override
        public int temperature() {
            return heat.temperature().applyAsInt(machine);
        }

        @Override
        public int maxTemperature() {
            return heat.maxTemperature().applyAsInt(machine);
        }

        @Override
        public long perTick() {
            return heat.perTick().applyAsLong(machine);
        }

        private @Nullable HeatHandler io() {
            return heat.io() == null ? null : heat.io().apply(machine);
        }

        @Override
        public int insert(int amount, boolean simulate) {
            HeatHandler io = io();
            return amount > 0 && heat.role() == HeatRole.CONSUMER && io != null ? io.receiveHeat(amount, simulate) : 0;
        }

        @Override
        public int extract(int amount, boolean simulate) {
            HeatHandler io = io();
            return amount > 0 && heat.role() == HeatRole.PRODUCER && io != null ? io.extractHeat(amount, simulate) : 0;
        }
    }

    private final class Items implements MachineItems {
        private final MachineControlSpec.Items<T> items;

        Items(MachineControlSpec.Items<T> items) {
            this.items = items;
        }

        private ResourceHandler<ItemResource> contents() {
            return items.contents().apply(machine);
        }

        @Override
        public ResourceHandler<ItemResource> handler() {
            ResourceHandler<ItemResource> contents = contents();
            ResourceHandler<ItemResource> automation = items.automation() == null ? contents : items.automation().apply(machine);
            return new RoleGatedHandler<>(contents, automation, this::role, items.automation() != null);
        }

        @Override
        public int slotCount() {
            return contents().size();
        }

        @Override
        public SlotRole role(int slot) {
            ResourceHandler<ItemResource> contents = contents();
            if (slot < 0 || slot >= contents.size()) {
                return SlotRole.OTHER;
            }
            if (contents instanceof MachineItemHandler machineItems && machineItems.isUpgradeSlot(slot)) {
                return SlotRole.UPGRADE;
            }
            return items.roles().getOrDefault(slot, items.otherwise());
        }

        @Override
        public ItemStack stack(int slot) {
            ResourceHandler<ItemResource> contents = contents();
            if (slot < 0 || slot >= contents.size()) {
                return ItemStack.EMPTY;
            }
            ItemResource resource = contents.getResource(slot);
            return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(contents.getAmountAsInt(slot));
        }

        @Override
        public int capacity(int slot) {
            ResourceHandler<ItemResource> contents = contents();
            if (slot < 0 || slot >= contents.size()) {
                return 0;
            }
            ItemResource resource = contents.getResource(slot);
            // Empty: what it holds of a stack of 64. Asked with no item, as a stand-in item may not pass the slot's filter.
            return resource.isEmpty() ? Math.min(net.minecraft.world.item.Item.DEFAULT_MAX_STACK_SIZE, contents.getCapacityAsInt(slot, ItemResource.EMPTY))
                    : contents.getCapacityAsInt(slot, resource);
        }

        @Override
        public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ResourceHandler<ItemResource> handler = handler();
            ItemResource resource = ItemResource.of(stack);
            int inserted = Transactions.transact(simulate, tx -> handler.insert(slot, resource, stack.getCount(), tx));
            return stack.copyWithCount(stack.getCount() - inserted);
        }

        @Override
        public ItemStack insert(ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ResourceHandler<ItemResource> handler = handler();
            ItemResource resource = ItemResource.of(stack);
            int inserted = Transactions.transact(simulate, tx -> handler.insert(resource, stack.getCount(), tx));
            return stack.copyWithCount(stack.getCount() - inserted);
        }

        @Override
        public ItemStack extract(int slot, int amount, boolean simulate) {
            ResourceHandler<ItemResource> handler = handler();
            if (amount <= 0 || slot < 0 || slot >= handler.size()) {
                return ItemStack.EMPTY;
            }
            ItemResource resource = handler.getResource(slot);
            if (resource.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int extracted = Transactions.transact(simulate, tx -> handler.extract(slot, resource, amount, tx));
            return extracted == 0 ? ItemStack.EMPTY : resource.toStack(extracted);
        }
    }

    private final class Fluids implements MachineFluids {
        private MachineControlSpec.@Nullable Tank<T> tank(int index) {
            return index >= 0 && index < spec.tanks.size() ? spec.tanks.get(index) : null;
        }

        @Override
        public ResourceHandler<FluidResource> handler() {
            return new Tanks();
        }

        @Override
        public int tankCount() {
            return spec.tanks.size();
        }

        @Override
        public SlotRole role(int index) {
            MachineControlSpec.Tank<T> tank = tank(index);
            return tank == null ? SlotRole.OTHER : tank.role();
        }

        @Override
        public FluidStack fluid(int index) {
            MachineControlSpec.Tank<T> tank = tank(index);
            if (tank == null) {
                return FluidStack.EMPTY;
            }
            ResourceHandler<FluidResource> handler = tank.handler().apply(machine);
            FluidResource resource = handler.getResource(tank.index());
            return resource.isEmpty() ? FluidStack.EMPTY : resource.toStack(handler.getAmountAsInt(tank.index()));
        }

        @Override
        public int capacity(int index) {
            MachineControlSpec.Tank<T> tank = tank(index);
            if (tank == null) {
                return 0;
            }
            ResourceHandler<FluidResource> handler = tank.handler().apply(machine);
            return handler.getCapacityAsInt(tank.index(), handler.getResource(tank.index()));
        }

        @Override
        public int insert(int index, FluidStack fluid, boolean simulate) {
            if (fluid.isEmpty()) {
                return 0;
            }
            Tanks tanks = new Tanks();
            FluidResource resource = FluidResource.of(fluid);
            return Transactions.transact(simulate, tx -> tanks.insert(index, resource, fluid.getAmount(), tx));
        }

        @Override
        public int insert(FluidStack fluid, boolean simulate) {
            if (fluid.isEmpty()) {
                return 0;
            }
            Tanks tanks = new Tanks();
            FluidResource resource = FluidResource.of(fluid);
            return Transactions.transact(simulate, tx -> tanks.insert(resource, fluid.getAmount(), tx));
        }

        @Override
        public FluidStack extract(int index, int amount, boolean simulate) {
            Tanks tanks = new Tanks();
            if (amount <= 0 || index < 0 || index >= tanks.size()) {
                return FluidStack.EMPTY;
            }
            FluidResource resource = tanks.getResource(index);
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            int extracted = Transactions.transact(simulate, tx -> tanks.extract(index, resource, amount, tx));
            return extracted == 0 ? FluidStack.EMPTY : resource.toStack(extracted);
        }

        // The tanks as one handler, each index one tank, filling only insertable tanks and draining only extractable ones.
        private final class Tanks implements ResourceHandler<FluidResource> {
            @Override
            public int size() {
                return spec.tanks.size();
            }

            private ResourceHandler<FluidResource> handlerOf(MachineControlSpec.Tank<T> tank) {
                return tank.handler().apply(machine);
            }

            @Override
            public FluidResource getResource(int index) {
                MachineControlSpec.Tank<T> tank = tank(index);
                return tank == null ? FluidResource.EMPTY : handlerOf(tank).getResource(tank.index());
            }

            @Override
            public long getAmountAsLong(int index) {
                MachineControlSpec.Tank<T> tank = tank(index);
                return tank == null ? 0 : handlerOf(tank).getAmountAsLong(tank.index());
            }

            @Override
            public long getCapacityAsLong(int index, FluidResource resource) {
                MachineControlSpec.Tank<T> tank = tank(index);
                return tank == null ? 0 : handlerOf(tank).getCapacityAsLong(tank.index(), resource);
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                MachineControlSpec.Tank<T> tank = tank(index);
                return tank != null && tank.role().insertable() && handlerOf(tank).isValid(tank.index(), resource);
            }

            @Override
            public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
                MachineControlSpec.Tank<T> tank = tank(index);
                if (tank == null || !tank.role().insertable() || amount <= 0 || resource.isEmpty()) {
                    return 0;
                }
                return handlerOf(tank).insert(tank.index(), resource, amount, transaction);
            }

            @Override
            public int insert(FluidResource resource, int amount, TransactionContext transaction) {
                if (amount <= 0 || resource.isEmpty()) {
                    return 0;
                }
                if (spec.fluidAutomation != null) {
                    return spec.fluidAutomation.apply(machine).insert(resource, amount, transaction);
                }
                int inserted = 0;
                for (int index = 0; index < size() && inserted < amount; index++) {
                    inserted += insert(index, resource, amount - inserted, transaction);
                }
                return inserted;
            }

            @Override
            public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
                MachineControlSpec.Tank<T> tank = tank(index);
                if (tank == null || !tank.role().extractable() || amount <= 0 || resource.isEmpty()) {
                    return 0;
                }
                return handlerOf(tank).extract(tank.index(), resource, amount, transaction);
            }

            @Override
            public int extract(FluidResource resource, int amount, TransactionContext transaction) {
                if (amount <= 0 || resource.isEmpty()) {
                    return 0;
                }
                int extracted = 0;
                for (int index = 0; index < size() && extracted < amount; index++) {
                    extracted += extract(index, resource, amount - extracted, transaction);
                }
                return extracted;
            }
        }
    }

    // --- Settings ---

    private final class Settings implements MachineSettings {
        private @Nullable ConfigurableMachine configurable() {
            return machine instanceof ConfigurableMachine configurable ? configurable : null;
        }

        @Override
        public boolean isEnabled() {
            return state.isEnabled();
        }

        @Override
        public SettingResult setEnabled(boolean enabled) {
            return state.setEnabled(enabled) ? SettingResult.APPLIED : SettingResult.UNCHANGED;
        }

        // --- Redstone ---

        private boolean hasRedstone() {
            ConfigurableMachine configurable = configurable();
            return spec.redstone && configurable != null && configurable.getRedstoneMode() != null;
        }

        @Override
        public Optional<RedstoneMode> redstoneMode() {
            ConfigurableMachine configurable = configurable();
            if (!hasRedstone() || configurable == null || configurable.getRedstoneMode() == null) {
                return Optional.empty();
            }
            return Optional.of(RedstoneMode.valueOf(configurable.getRedstoneMode().name()));
        }

        @Override
        public List<RedstoneMode> allowedRedstoneModes() {
            ConfigurableMachine configurable = configurable();
            if (!hasRedstone() || configurable == null) {
                return List.of();
            }
            return configurable.getAllowedRedstoneModes().stream().map(mode -> RedstoneMode.valueOf(mode.name())).toList();
        }

        @Override
        public SettingResult setRedstoneMode(RedstoneMode mode) {
            ConfigurableMachine configurable = configurable();
            if (!hasRedstone() || configurable == null) {
                return SettingResult.UNSUPPORTED;
            }
            var own = net.zagdrath.arcforge.machine.config.RedstoneMode.valueOf(mode.name());
            // As the Redstone tab: only the modes it offers.
            if (!configurable.getAllowedRedstoneModes().contains(own)) {
                return SettingResult.INVALID;
            }
            if (configurable.getRedstoneMode() == own) {
                return SettingResult.UNCHANGED;
            }
            configurable.setRedstoneMode(own);
            changed();
            return SettingResult.APPLIED;
        }

        // --- Sides (single-block machines) ---

        @Override
        public boolean hasSideConfiguration() {
            return machine instanceof MachineBlockEntity && !(machine instanceof MultiblockController);
        }

        @Override
        public String sideMode(MachineSide side) {
            ConfigurableMachine configurable = configurable();
            if (!hasSideConfiguration() || configurable == null) {
                return SideModes.NONE;
            }
            return configurable.getSideMode(RelativeSide.valueOf(side.name())).getSerializedName();
        }

        @Override
        public List<String> allowedSideModes() {
            ConfigurableMachine configurable = configurable();
            if (!hasSideConfiguration() || configurable == null) {
                return List.of();
            }
            return names(configurable.getAllowedSideModes());
        }

        @Override
        public SettingResult setSideMode(MachineSide side, String mode) {
            ConfigurableMachine configurable = configurable();
            if (!hasSideConfiguration() || configurable == null) {
                return SettingResult.UNSUPPORTED;
            }
            SideMode own = sideModeNamed(mode);
            // As the Sides tab: the modes it cycles through, or none.
            if (own == null || own != SideMode.NONE && !configurable.getAllowedSideModes().contains(own)) {
                return SettingResult.INVALID;
            }
            RelativeSide relative = RelativeSide.valueOf(side.name());
            if (configurable.getSideMode(relative) == own) {
                return SettingResult.UNCHANGED;
            }
            configurable.setSideMode(relative, own);
            blockUpdated();
            return SettingResult.APPLIED;
        }

        @Override
        public SettingResult clearSideModes() {
            ConfigurableMachine configurable = configurable();
            if (!hasSideConfiguration() || configurable == null) {
                return SettingResult.UNSUPPORTED;
            }
            if (Arrays.stream(RelativeSide.values()).allMatch(side -> configurable.getSideMode(side) == SideMode.NONE)) {
                return SettingResult.UNCHANGED;
            }
            configurable.clearSideModes();
            blockUpdated();
            return SettingResult.APPLIED;
        }

        private void blockUpdated() {
            changed();
            Level level = machine.getLevel();
            if (level != null) {
                BlockState blockState = machine.getBlockState();
                level.sendBlockUpdated(machine.getBlockPos(), blockState, blockState, Block.UPDATE_ALL);
            }
        }

        // --- Ports (multiblocks) ---

        private @Nullable MultiblockController formedController() {
            return machine instanceof MultiblockController controller && controller.isFormed() && machine.getLevel() != null ? controller : null;
        }

        @Override
        public List<MachinePort> ports() {
            MultiblockController controller = formedController();
            if (controller == null || machine.getLevel() == null) {
                return List.of();
            }
            return MultiblockPorts.list(machine.getLevel(), controller).stream()
                    .map(port -> new MachinePort(port.pos(), port.face(), port.mode().getSerializedName())).toList();
        }

        @Override
        public List<String> allowedPortModes() {
            if (!(machine instanceof MultiblockController controller)) {
                return List.of();
            }
            return names(controller.getAllowedSideModes());
        }

        @Override
        public SettingResult setPort(BlockPos pos, Direction face, String mode) {
            if (!(machine instanceof MultiblockController controller)) {
                return SettingResult.UNSUPPORTED;
            }
            Level level = machine.getLevel();
            if (level == null || !controller.isFormed()) {
                return SettingResult.REJECTED;
            }
            SideMode own = sideModeNamed(mode);
            if (own == null || own != SideMode.NONE && !controller.getAllowedSideModes().contains(own)) {
                return SettingResult.INVALID;
            }
            // As the Wrench in Port mode: a part of this structure that holds ports, on a face pointing out of it.
            if (!controller.isInside(pos) || !MultiblockPorts.canHold(level, controller, pos) || controller.isInside(pos.relative(face))) {
                return SettingResult.REJECTED;
            }
            if (MultiblockPorts.get(level, pos, face) == own) {
                return SettingResult.UNCHANGED;
            }
            MultiblockPorts.set(level, controller, pos, own, face);
            return SettingResult.APPLIED;
        }

        // --- Auto-eject ---

        @Override
        public boolean supportsAutoEject() {
            ConfigurableMachine configurable = configurable();
            return configurable != null && configurable.getAllowedSideModes().stream().anyMatch(SideMode::isOutput);
        }

        @Override
        public boolean isAutoEject() {
            ConfigurableMachine configurable = configurable();
            return supportsAutoEject() && configurable != null && configurable.isAutoEject();
        }

        @Override
        public SettingResult setAutoEject(boolean autoEject) {
            ConfigurableMachine configurable = configurable();
            if (!supportsAutoEject() || configurable == null) {
                return SettingResult.UNSUPPORTED;
            }
            if (configurable.isAutoEject() == autoEject) {
                return SettingResult.UNCHANGED;
            }
            configurable.setAutoEject(autoEject);
            changed();
            return configurable.isAutoEject() == autoEject ? SettingResult.APPLIED : SettingResult.REJECTED;
        }

        // --- Machine-specific options ---

        @Override
        public List<MachineOption> options() {
            List<MachineOption> options = new ArrayList<>();
            for (MachineControlSpec.Option<T> option : spec.options) {
                options.add(new MachineOption(option.id(), option.name(), option.type(), option.get().apply(machine), option.min(), option.max(), option.choices()));
            }
            return options;
        }

        @Override
        public SettingResult setOption(String id, String value) {
            for (MachineControlSpec.Option<T> option : spec.options) {
                if (option.id().equals(id)) {
                    return setOption(option, value);
                }
            }
            return SettingResult.UNSUPPORTED;
        }

        private SettingResult setOption(MachineControlSpec.Option<T> option, String value) {
            String normalised = switch (option.type()) {
                case BOOLEAN -> value.equals("true") || value.equals("false") ? value : null;
                case INTEGER -> {
                    try {
                        int number = Integer.parseInt(value.trim());
                        yield number >= option.min() && number <= option.max() ? Integer.toString(number) : null;
                    } catch (NumberFormatException e) {
                        yield null;
                    }
                }
                case CHOICE -> option.choices().contains(value) ? value : null;
            };
            if (normalised == null) {
                return SettingResult.INVALID;
            }
            if (option.get().apply(machine).equals(normalised)) {
                return SettingResult.UNCHANGED;
            }
            option.set().accept(machine, normalised);
            changed();
            return option.get().apply(machine).equals(normalised) ? SettingResult.APPLIED : SettingResult.REJECTED;
        }

        // --- Upgrades ---

        @Override
        public List<InstalledUpgrade> upgrades() {
            if (!(machine instanceof MachineBlockEntity machineEntity)) {
                return List.of();
            }
            List<InstalledUpgrade> upgrades = new ArrayList<>();
            for (UpgradeType type : UpgradeType.values()) {
                int count = machineEntity.upgrades(type);
                if (count > 0) {
                    upgrades.add(new InstalledUpgrade(type.getSerializedName(), count));
                }
            }
            return upgrades;
        }

        @Override
        public Set<String> acceptedUpgrades() {
            if (!(machine instanceof MachineBlockEntity machineEntity)) {
                return Set.of();
            }
            Set<String> accepted = new LinkedHashSet<>();
            for (UpgradeType type : machineEntity.getItems().getAcceptedUpgrades()) {
                accepted.add(type.getSerializedName());
            }
            return Set.copyOf(accepted);
        }
    }

    private static List<String> names(List<SideMode> modes) {
        return modes.stream().map(SideMode::getSerializedName).toList();
    }

    private static @Nullable SideMode sideModeNamed(String name) {
        for (SideMode mode : SideMode.values()) {
            if (mode.getSerializedName().equals(name)) {
                return mode;
            }
        }
        return null;
    }

    // --- Statistics ---

    private final class Statistics implements MachineStatistics {
        @Override
        public long operationsCompleted() {
            return state.operations();
        }

        @Override
        public long itemsProduced() {
            return state.itemsProduced();
        }

        @Override
        public long itemsConsumed() {
            return state.itemsConsumed();
        }

        @Override
        public long fluidProduced() {
            return state.fluidProduced();
        }

        @Override
        public long fluidConsumed() {
            return state.fluidConsumed();
        }

        @Override
        public long uptimeTicks() {
            return state.uptime();
        }

        @Override
        public long loadedTicks() {
            return state.loaded();
        }

        @Override
        public double operationsPerMinute() {
            return state.operationsPerMinute();
        }
    }
}
