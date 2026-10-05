/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.machine.control;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.api.machine.resource.EnergyRole;
import net.zagdrath.arcforge.api.machine.resource.HeatRole;
import net.zagdrath.arcforge.api.machine.status.MachineStatus;
import net.zagdrath.arcforge.api.machine.settings.OptionType;
import net.zagdrath.arcforge.api.machine.resource.SlotRole;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.heat.HeatHandler;

// How the machine control API reads one kind of machine: its energy, slots, tanks, heat, progress, outputs and own
// options, as getters on its block entity. Built once per block entity type and registered with MachineControls; the
// common parts (status, redstone, sides, auto-eject, upgrades, owner) come from the block entity's own interfaces.
//
//   MachineControls.register(ModBlockEntityTypes.INFUSER.get(), MachineControlSpec.builder(InfuserBlockEntity.class)
//           .energy(EnergyRole.CONSUMER, InfuserBlockEntity::getEnergy, InfuserBlockEntity::getUsage)
//           .slot(InfuserBlockEntity.SLOT_INPUT, SlotRole.INPUT).slot(InfuserBlockEntity.SLOT_OUTPUT, SlotRole.OUTPUT)
//           .items(InfuserBlockEntity::getItems, be -> be.getItemHandler(null))
//           .tank(SlotRole.INPUT, InfuserBlockEntity::getTank)
//           .progress(InfuserBlockEntity::getProgress, InfuserBlockEntity::getTotal)
//           .build());
public final class MachineControlSpec<T extends BlockEntity> {
    // A broad status and the machine's own words for it, for machines without a MachineStatus field.
    public record StatusReport(MachineStatus status, Component reason) {}

    record Energy<T>(EnergyRole role, ToLongFunction<T> stored, ToLongFunction<T> capacity, ToLongFunction<T> perTick) {}

    record Items<T>(Function<T, ResourceHandler<ItemResource>> contents, @Nullable Function<T, ResourceHandler<ItemResource>> automation,
            Map<Integer, SlotRole> roles, SlotRole otherwise) {}

    // liquid: never holds a gas, though its filter doesn't say so (an output tank that takes anything), so the gas API
    // leaves it out.
    record Tank<T>(SlotRole role, Function<T, ResourceHandler<FluidResource>> handler, int index, boolean liquid) {}

    record Heat<T>(HeatRole role, ToLongFunction<T> stored, ToLongFunction<T> capacity, ToIntFunction<T> temperature,
            ToIntFunction<T> maxTemperature, ToLongFunction<T> perTick, @Nullable Function<T, @Nullable HeatHandler> io) {}

    record Option<T>(String id, OptionType type, int min, int max, List<String> choices, Function<T, String> get, BiConsumer<T, String> set) {
        Component name() {
            return Component.translatable("option.arcforge." + id);
        }
    }

    final Class<T> type;
    final Predicate<T> primary;
    final @Nullable Function<T, net.zagdrath.arcforge.machine.MachineStatus> status;
    final @Nullable Function<T, StatusReport> report;
    final @Nullable Energy<T> energy;
    final @Nullable Items<T> items;
    final List<Tank<T>> tanks;
    final @Nullable Function<T, ResourceHandler<FluidResource>> fluidAutomation;
    final @Nullable Heat<T> heat;
    final @Nullable Function<T, OptionalDouble> progress;
    final @Nullable Function<T, OptionalInt> remaining;
    final Function<T, List<ItemStack>> outputs;
    final Function<T, List<FluidStack>> fluidOutputs;
    final List<Option<T>> options;
    final boolean redstone;

    private MachineControlSpec(Builder<T> builder) {
        type = builder.type;
        primary = builder.primary;
        status = builder.status;
        report = builder.report;
        energy = builder.energy;
        items = builder.itemContents == null ? null
                : new Items<>(builder.itemContents, builder.itemAutomation, Map.copyOf(builder.slotRoles), builder.otherSlots);
        tanks = List.copyOf(builder.tanks);
        fluidAutomation = builder.fluidAutomation;
        heat = builder.heat;
        progress = builder.progress;
        remaining = builder.remaining;
        outputs = builder.outputs;
        fluidOutputs = builder.fluidOutputs;
        options = List.copyOf(builder.options);
        redstone = builder.redstone;
    }

    public static <T extends BlockEntity> Builder<T> builder(Class<T> type) {
        return new Builder<>(type);
    }

    public static final class Builder<T extends BlockEntity> {
        private final Class<T> type;
        private Predicate<T> primary = be -> true;
        private @Nullable Function<T, net.zagdrath.arcforge.machine.MachineStatus> status;
        private @Nullable Function<T, StatusReport> report;
        private @Nullable Energy<T> energy;
        private @Nullable Function<T, ResourceHandler<ItemResource>> itemContents;
        private @Nullable Function<T, ResourceHandler<ItemResource>> itemAutomation;
        private final Map<Integer, SlotRole> slotRoles = new HashMap<>();
        private SlotRole otherSlots = SlotRole.OTHER;
        private final List<Tank<T>> tanks = new ArrayList<>();
        private @Nullable Function<T, ResourceHandler<FluidResource>> fluidAutomation;
        private @Nullable Heat<T> heat;
        private @Nullable Function<T, OptionalDouble> progress;
        private @Nullable Function<T, OptionalInt> remaining;
        private Function<T, List<ItemStack>> outputs = be -> List.of();
        private Function<T, List<FluidStack>> fluidOutputs = be -> List.of();
        private final List<Option<T>> options = new ArrayList<>();
        private boolean redstone = true;

        private Builder(Class<T> type) {
            this.type = type;
        }

        // Which block entities of the type are the machine (a cube array's centre, a shell array's master). Others resolve
        // through their structure instead. Default: all.
        public Builder<T> primary(Predicate<T> primary) {
            this.primary = primary;
            return this;
        }

        // The machine's own status. Default for a MachineBlockEntity: getStatus().
        public Builder<T> status(Function<T, net.zagdrath.arcforge.machine.MachineStatus> status) {
            this.status = status;
            return this;
        }

        // The status worked out some other way, for machines without a MachineStatus (overrides status()).
        public Builder<T> report(Function<T, StatusReport> report) {
            this.report = report;
            return this;
        }

        // FE, from its energy handler. perTick: the FE used (consumer), generated (generator) or net flow (storage) last tick.
        public Builder<T> energy(EnergyRole role, Function<T, ? extends EnergyHandler> handler, ToLongFunction<T> perTick) {
            return energy(role, be -> handler.apply(be).getAmountAsLong(), be -> handler.apply(be).getCapacityAsLong(), perTick);
        }

        public Builder<T> energy(EnergyRole role, ToLongFunction<T> stored, ToLongFunction<T> capacity, ToLongFunction<T> perTick) {
            energy = new Energy<>(role, stored, capacity, perTick);
            return this;
        }

        // A slot's role (slots without one get OTHER, or otherSlots; a MachineItemHandler's upgrade slots are UPGRADE).
        public Builder<T> slot(int slot, SlotRole role) {
            slotRoles.put(slot, role);
            return this;
        }

        public Builder<T> slots(int from, int toExclusive, SlotRole role) {
            for (int slot = from; slot < toExclusive; slot++) {
                slotRoles.put(slot, role);
            }
            return this;
        }

        public Builder<T> otherSlots(SlotRole role) {
            otherSlots = role;
            return this;
        }

        // The item slots. contents: every slot, read as is. automation: what pipes get with no side (same indices), which
        // all writes go through; its own insert(resource) is used for a fill without a slot. Null automation: writes go to
        // contents' own validation.
        public Builder<T> items(Function<T, ResourceHandler<ItemResource>> contents, @Nullable Function<T, ResourceHandler<ItemResource>> automation) {
            itemContents = contents;
            itemAutomation = automation;
            return this;
        }

        // A fluid tank: index 0 of the handler, or the given index. Tanks are numbered in the order added. Fills and drains go
        // to the tank itself, through its own fluid filter.
        public Builder<T> tank(SlotRole role, Function<T, ? extends ResourceHandler<FluidResource>> handler) {
            return tank(role, handler, 0);
        }

        @SuppressWarnings("unchecked")
        public Builder<T> tank(SlotRole role, Function<T, ? extends ResourceHandler<FluidResource>> handler, int index) {
            tanks.add(new Tank<>(role, (Function<T, ResourceHandler<FluidResource>>) handler, index, false));
            return this;
        }

        // A tank that only ever holds liquids although its filter takes anything (a pump's, a melter's output), so the gas
        // API doesn't list it as a gas tank.
        @SuppressWarnings("unchecked")
        public Builder<T> liquidTank(SlotRole role, Function<T, ? extends ResourceHandler<FluidResource>> handler) {
            tanks.add(new Tank<>(role, (Function<T, ResourceHandler<FluidResource>>) handler, 0, true));
            return this;
        }

        // What pipes get with no side, used for a fill without a tank (routes each fluid to its tank).
        public Builder<T> fluidAutomation(Function<T, ? extends ResourceHandler<FluidResource>> handler) {
            fluidAutomation = handler::apply;
            return this;
        }

        // Heat, from its HeatBuffer. io: the machine's heat handler with no side (or null for none).
        public Builder<T> heat(HeatRole role, Function<T, HeatBuffer> buffer, ToLongFunction<T> perTick, @Nullable Function<T, @Nullable HeatHandler> io) {
            return heat(role, be -> buffer.apply(be).getStored(), be -> buffer.apply(be).getCapacity(), be -> buffer.apply(be).getTemperature(),
                    be -> buffer.apply(be).getMaxCelsius(), perTick, io);
        }

        public Builder<T> heat(HeatRole role, ToLongFunction<T> stored, ToLongFunction<T> capacity, ToIntFunction<T> temperature,
                ToIntFunction<T> maxTemperature, ToLongFunction<T> perTick, @Nullable Function<T, @Nullable HeatHandler> io) {
            heat = new Heat<>(role, stored, capacity, temperature, maxTemperature, perTick, io);
            return this;
        }

        // Progress as ticks done of ticks needed (in an operation while done > 0); remaining is needed - done.
        public Builder<T> progress(ToIntFunction<T> done, ToIntFunction<T> needed) {
            progress = be -> {
                int total = needed.applyAsInt(be);
                int current = done.applyAsInt(be);
                return total > 0 && current > 0 ? OptionalDouble.of(Math.min(1.0, (double) current / total)) : OptionalDouble.empty();
            };
            if (remaining == null) {
                remaining = be -> {
                    int total = needed.applyAsInt(be);
                    int current = done.applyAsInt(be);
                    return total > 0 && current > 0 ? OptionalInt.of(Math.max(0, total - current)) : OptionalInt.empty();
                };
            }
            return this;
        }

        public Builder<T> progress(Function<T, OptionalDouble> fraction) {
            progress = fraction;
            return this;
        }

        public Builder<T> remaining(Function<T, OptionalInt> ticks) {
            remaining = ticks;
            return this;
        }

        // What the current operation will make (empty when not in one).
        public Builder<T> outputs(Function<T, List<ItemStack>> outputs) {
            this.outputs = outputs;
            return this;
        }

        public Builder<T> fluidOutputs(Function<T, List<FluidStack>> fluidOutputs) {
            this.fluidOutputs = fluidOutputs;
            return this;
        }

        // A machine-specific option, named by the translation key option.arcforge.<id>.
        public Builder<T> booleanOption(String id, Predicate<T> get, BiConsumer<T, Boolean> set) {
            options.add(new Option<>(id, OptionType.BOOLEAN, 0, 0, List.of(), be -> Boolean.toString(get.test(be)),
                    (be, value) -> set.accept(be, Boolean.parseBoolean(value))));
            return this;
        }

        public Builder<T> intOption(String id, int min, int max, ToIntFunction<T> get, ObjIntConsumer<T> set) {
            options.add(new Option<>(id, OptionType.INTEGER, min, max, List.of(), be -> Integer.toString(get.applyAsInt(be)),
                    (be, value) -> set.accept(be, Integer.parseInt(value))));
            return this;
        }

        public Builder<T> choiceOption(String id, List<String> choices, Function<T, String> get, BiConsumer<T, String> set) {
            options.add(new Option<>(id, OptionType.CHOICE, 0, 0, List.copyOf(choices), get, set));
            return this;
        }

        // For a machine without a redstone mode.
        public Builder<T> noRedstone() {
            redstone = false;
            return this;
        }

        public MachineControlSpec<T> build() {
            return new MachineControlSpec<>(this);
        }
    }
}
