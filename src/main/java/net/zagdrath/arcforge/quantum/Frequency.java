/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.quantum;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.heat.HeatBuffer;
import net.zagdrath.arcforge.security.SecurityProfiles;
import net.zagdrath.arcforge.security.SecurityRules;
import net.zagdrath.arcforge.steam.Gases;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

import io.netty.buffer.ByteBuf;

// One Quantum Tunnel frequency: a name, public (anyone may use it) or private (its owner and the players they trust at a
// Security Terminal), and one buffer of each resource shared by every tunnel on it, anywhere: FE, heat (a heat buffer
// that warms as it fills, up to MAX_CELSIUS), a liquid tank, a gas tank and item slots. Sizes come from the
// quantumTunnel config each time the server loads it.
public final class Frequency {
    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_CELSIUS = 1_200;

    // Public frequencies are named once for the whole server; private ones once per owner.
    public record Key(String name, Optional<UUID> owner) {
        public static final Codec<Key> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Key::name),
                UUIDUtil.CODEC.optionalFieldOf("owner").forGetter(Key::owner))
                .apply(i, Key::new));
        public static final StreamCodec<ByteBuf, Key> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH * 4), Key::name,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Key::owner,
                Key::new);

        public boolean isPrivate() {
            return owner.isPresent();
        }

        public static Key ofPublic(String name) {
            return new Key(name, Optional.empty());
        }

        public static Key ofPrivate(String name, UUID owner) {
            return new Key(name, Optional.of(owner));
        }
    }

    // What's saved: the key, who made it, and the buffers' contents.
    public record Data(Key key, UUID creator, String creatorName, int energy, int heat, FluidStack fluid, FluidStack gas, List<ItemStack> items) {
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Key.CODEC.fieldOf("key").forGetter(Data::key),
                UUIDUtil.CODEC.fieldOf("creator").forGetter(Data::creator),
                Codec.STRING.optionalFieldOf("creator_name", "").forGetter(Data::creatorName),
                Codec.INT.optionalFieldOf("energy", 0).forGetter(Data::energy),
                Codec.INT.optionalFieldOf("heat", 0).forGetter(Data::heat),
                FluidStack.OPTIONAL_CODEC.optionalFieldOf("fluid", FluidStack.EMPTY).forGetter(Data::fluid),
                FluidStack.OPTIONAL_CODEC.optionalFieldOf("gas", FluidStack.EMPTY).forGetter(Data::gas),
                ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Data::items))
                .apply(i, Data::new));
    }

    private final Key key;
    private final UUID creator;
    private final String creatorName;
    private final Energy energy;
    private final HeatBuffer heat;
    private final FilteredFluidTank fluid;
    private final FilteredFluidTank gas;
    private final FilteredItemHandler items;
    // Where the buffers report changes (the saved data, to mark itself dirty); nothing while loading.
    private Runnable onChanged = () -> {};

    Frequency(Key key, UUID creator, String creatorName) {
        this.key = key;
        this.creator = creator;
        this.creatorName = creatorName;
        Runnable changed = () -> onChanged.run();
        this.energy = new Energy(ArcforgeConfig.QUANTUM_ENERGY_BUFFER.getAsInt(), changed);
        this.heat = new HeatBuffer(ArcforgeConfig.QUANTUM_HEAT_BUFFER.getAsInt(), MAX_CELSIUS, changed);
        this.fluid = new FilteredFluidTank(ArcforgeConfig.QUANTUM_FLUID_BUFFER.getAsInt(), resource -> !Gases.isGas(resource), changed);
        this.gas = new FilteredFluidTank(ArcforgeConfig.QUANTUM_GAS_BUFFER.getAsInt(), Gases::isGas, changed);
        this.items = new FilteredItemHandler(ArcforgeConfig.QUANTUM_ITEM_SLOTS.getAsInt(), (slot, resource) -> true, changed);
    }

    void listen(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    static Frequency load(Data data) {
        Frequency frequency = new Frequency(data.key(), data.creator(), data.creatorName());
        frequency.energy.load(data.energy());
        frequency.heat.add(data.heat());
        if (!data.fluid().isEmpty()) {
            frequency.fluid.set(0, FluidResource.of(data.fluid()), data.fluid().getAmount());
        }
        if (!data.gas().isEmpty()) {
            frequency.gas.set(0, FluidResource.of(data.gas()), data.gas().getAmount());
        }
        // Items in slots the config no longer has still come back (the handler grows to fit them).
        frequency.items.ensureSize(data.items().size());
        for (int slot = 0; slot < data.items().size(); slot++) {
            ItemStack stack = data.items().get(slot);
            if (!stack.isEmpty()) {
                frequency.items.set(slot, ItemResource.of(stack), stack.getCount());
            }
        }
        return frequency;
    }

    Data save() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < items.size(); slot++) {
            stacks.add(items.getStack(slot));
        }
        return new Data(key, creator, creatorName, energy.getAmountAsInt(), heat.getStored(), fluid.getResource(0).toStack(fluid.getAmount()),
                gas.getResource(0).toStack(gas.getAmount()), stacks);
    }

    public Key key() {
        return key;
    }

    public UUID creator() {
        return creator;
    }

    public String creatorName() {
        return creatorName;
    }

    public SimpleEnergyHandler energy() {
        return energy;
    }

    public HeatBuffer heat() {
        return heat;
    }

    public FilteredFluidTank fluid() {
        return fluid;
    }

    public FilteredFluidTank gas() {
        return gas;
    }

    public FilteredItemHandler items() {
        return items;
    }

    // Whether a player (or a tunnel acting for its owner) may use it: anyone for a public one; for a private one its
    // owner and the players the owner trusts (and anyone while security is off).
    public boolean canUse(UUID player, MinecraftServer server) {
        if (key.owner().isEmpty() || !SecurityRules.enabled()) {
            return true;
        }
        UUID owner = key.owner().get();
        return owner.equals(player) || SecurityProfiles.get(server).profile(owner).trusts(player);
    }

    // The FE buffer: takes and gives any amount at once (the tunnels' faces set the rates).
    static final class Energy extends SimpleEnergyHandler {
        private final Runnable onChanged;

        Energy(int capacity, Runnable onChanged) {
            super(capacity, Integer.MAX_VALUE, Integer.MAX_VALUE);
            this.onChanged = onChanged;
        }

        void load(int amount) {
            set(Math.max(0, Math.min(amount, capacity)));
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            onChanged.run();
        }
    }
}
