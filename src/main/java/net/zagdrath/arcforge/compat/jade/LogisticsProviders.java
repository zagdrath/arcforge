/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.experience.XpShowerBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.ChunkLoaderBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.HeatPipeBlockEntity;
import net.zagdrath.arcforge.blockentity.logistics.QuantumTunnelBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.ItemReservoirBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.ItemReservoirStore;
import net.zagdrath.arcforge.blockentity.storage.ReservoirBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.ReservoirGroup;
import net.zagdrath.arcforge.chunkloading.ChunkLoaderStatus;
import net.zagdrath.arcforge.experience.LiquidExperience;
import net.zagdrath.arcforge.quantum.Frequency;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Jade for the Fluid and Gas Reservoirs ("Tank of 12 blocks", "This block: 32,000 / 32,000 mB"; the group's contents
// show as its fluid storage), the Item Reservoir ("Store of 27 blocks (3×3×3)", or "A store of its own: not a box" when it
// touches others it can't join; "12 / 540 slots used (3% full)"), the XP Drain ("Drains into the tank below" or "Place on
// a fluid container"), the XP Shower and the Vacuum Collector's experience ("Liquid Experience: 2,000 mB (100 points)",
// "Off: redstone signal"), the Quantum Tunnel ("Frequency: Base (private)" or "No frequency") and the Chunk Loader
// ("Loading 9 chunks (radius 1)" or its status), and the Heat Pipe ("Pipe of 12 blocks, 6.0% lost", "Carrying 50,400 HU/t at
// 1,100°C" or "Idle"), from any block of it.
public final class LogisticsProviders {
    private LogisticsProviders() {}

    private static String grouped(long value) {
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }

    private static Identifier uid(String name) {
        return Identifier.fromNamespaceAndPath(Arcforge.MODID, name);
    }

    public enum Reservoir implements StreamServerDataProvider<BlockAccessor, Reservoir.Data> {
        INSTANCE;

        public record Data(int blocks, int own, int capacity) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Data::blocks, ByteBufCodecs.VAR_INT, Data::own, ByteBufCodecs.VAR_INT, Data::capacity, Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof ReservoirBlockEntity reservoir)) {
                return null;
            }
            ReservoirGroup group = reservoir.getGroup();
            return new Data(group.size(), reservoir.ownAmount(), reservoir.capacity());
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
            return Data.STREAM_CODEC;
        }

        @Override
        public Identifier getUid() {
            return uid("reservoir");
        }

        // Jade needs the tooltip half as its own provider, under the same uid.
        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                Reservoir.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                    if (data.blocks() > 1) {
                        tooltip.add(Component.translatable("jade.arcforge.reservoir.blocks", data.blocks()));
                    }
                    tooltip.add(Component.translatable("jade.arcforge.reservoir.own", grouped(data.own()),
                            grouped(data.capacity())).withStyle(ChatFormatting.GRAY));
                });
            }

            @Override
            public Identifier getUid() {
                return uid("reservoir");
            }
        }
    }

    public enum ItemReservoir implements StreamServerDataProvider<BlockAccessor, ItemReservoir.Data> {
        INSTANCE;

        // size is the store's box ("3×3×3"), empty for a block that isn't part of one; percent is how full by stacks.
        public record Data(int blocks, String size, int used, int slots, int percent) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Data::blocks, ByteBufCodecs.STRING_UTF8, Data::size, ByteBufCodecs.VAR_INT, Data::used,
                    ByteBufCodecs.VAR_INT, Data::slots, ByteBufCodecs.VAR_INT, Data::percent, Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof ItemReservoirBlockEntity reservoir)) {
                return null;
            }
            ItemReservoirStore store = reservoir.getStore();
            int slots = store.slots();
            int used = 0;
            for (int index = 0; index < slots; index++) {
                if (!store.getStack(index).isEmpty()) {
                    used++;
                }
            }
            ItemReservoirStore.Box box = store.box();
            return new Data(store.blocks(), box != null ? box.describe() : "", used, slots, Math.round(store.fullness() * 100.0F));
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
            return Data.STREAM_CODEC;
        }

        @Override
        public Identifier getUid() {
            return uid("item_reservoir");
        }

        // Jade needs the tooltip half as its own provider, under the same uid.
        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                ItemReservoir.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                    if (!data.size().isEmpty()) {
                        tooltip.add(Component.translatable("jade.arcforge.item_reservoir.store", data.blocks(), data.size()));
                    } else if (accessor.getLevel() != null && hasReservoirNeighbour(accessor)) {
                        tooltip.add(Component.translatable("jade.arcforge.item_reservoir.alone").withStyle(ChatFormatting.GRAY));
                    }
                    tooltip.add(Component.translatable("jade.arcforge.item_reservoir.slots", grouped(data.used()), grouped(data.slots()),
                            data.percent()).withStyle(ChatFormatting.GRAY));
                });
            }

            // "Not a box" only says something when it touches other Item Reservoirs it would otherwise have joined.
            private static boolean hasReservoirNeighbour(BlockAccessor accessor) {
                for (Direction direction : Direction.values()) {
                    if (accessor.getLevel().getBlockState(accessor.getPosition().relative(direction)).is(accessor.getBlock())) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public Identifier getUid() {
                return uid("item_reservoir");
            }
        }
    }

    public enum HeatPipe implements StreamServerDataProvider<BlockAccessor, HeatPipe.Data> {
        INSTANCE;

        // loss: tenths of a percent of the heat the pipe loses over its length.
        public record Data(int length, int rate, int celsius, int loss) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Data::length, ByteBufCodecs.VAR_INT, Data::rate, ByteBufCodecs.VAR_INT, Data::celsius,
                    ByteBufCodecs.VAR_INT, Data::loss, Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            HeatPipeBlockEntity pipe = HeatPipeBlockEntity.controllerOf(accessor.getLevel(), accessor.getPosition());
            if (pipe == null || pipe.getLength() <= 0) {
                return null;
            }
            int loss = (int) Math.round((1.0 - HeatPipeBlockEntity.efficiency(pipe.getLength())) * 1000.0);
            return new Data(pipe.getLength(), pipe.getRate(), pipe.getCarrying(), loss);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
            return Data.STREAM_CODEC;
        }

        @Override
        public Identifier getUid() {
            return uid("heat_pipe");
        }

        // Jade needs the tooltip half as its own provider, under the same uid.
        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                HeatPipe.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                    String loss = String.format(java.util.Locale.ROOT, "%.1f", data.loss() / 10.0);
                    tooltip.add(Component.translatable("jade.arcforge.heat_pipe.length", data.length(), loss));
                    tooltip.add((data.rate() > 0
                            ? Component.translatable("jade.arcforge.heat_pipe.carrying", grouped(data.rate()), grouped(data.celsius()))
                            : Component.translatable("jade.arcforge.heat_pipe.idle")).withStyle(ChatFormatting.GRAY));
                });
            }

            @Override
            public Identifier getUid() {
                return uid("heat_pipe");
            }
        }
    }

    public enum XpDrain implements StreamServerDataProvider<BlockAccessor, Boolean> {
        INSTANCE;

        @Override
        public @Nullable Boolean streamData(BlockAccessor accessor) {
            return accessor.getLevel().getCapability(Capabilities.Fluid.BLOCK, accessor.getPosition().below(), Direction.UP) != null;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Boolean> streamCodec() {
            return ByteBufCodecs.BOOL.cast();
        }

        @Override
        public Identifier getUid() {
            return uid("xp_drain");
        }

        // Jade needs the tooltip half as its own provider, under the same uid.
        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                XpDrain.INSTANCE.decodeFromData(accessor).ifPresent(container -> tooltip.add(container
                        ? Component.translatable("jade.arcforge.xp_drain.ready").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("jade.arcforge.xp_drain.no_container").withStyle(ChatFormatting.RED)));
            }

            @Override
            public Identifier getUid() {
                return uid("xp_drain");
            }
        }
    }

    // Liquid Experience held: the XP Shower's tank (and whether redstone has turned it off), or the Vacuum Collector's.
    public enum Experience implements StreamServerDataProvider<BlockAccessor, Experience.Data> {
        INSTANCE;

        public record Data(int amount, int capacity, boolean off) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Data::amount, ByteBufCodecs.VAR_INT, Data::capacity, ByteBufCodecs.BOOL, Data::off, Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof XpShowerBlockEntity shower) {
                return new Data(shower.getTank().getAmount(), shower.getTank().getCapacity(), shower.isOff());
            }
            if (accessor.getBlockEntity() instanceof VacuumCollectorBlockEntity collector && collector.getXpTank().getCapacity() > 0) {
                return new Data(collector.getXpTank().getAmount(), collector.getXpTank().getCapacity(), false);
            }
            return null;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
            return Data.STREAM_CODEC;
        }

        @Override
        public Identifier getUid() {
            return uid("liquid_experience");
        }

        // Jade needs the tooltip half as its own provider, under the same uid.
        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                Experience.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                    tooltip.add(Component.translatable("jade.arcforge.experience.stored", grouped(data.amount()),
                            grouped(data.capacity()), grouped(data.amount() / LiquidExperience.MB_PER_POINT))
                            .withStyle(ChatFormatting.GREEN));
                    if (data.off()) {
                        tooltip.add(Component.translatable("jade.arcforge.xp_shower.off").withStyle(ChatFormatting.RED));
                    }
                });
            }

            @Override
            public Identifier getUid() {
                return uid("liquid_experience");
            }
        }
    }

    public enum QuantumTunnel implements StreamServerDataProvider<BlockAccessor, QuantumTunnel.Data> {
        INSTANCE;

        public record Data(String name, boolean isPrivate, boolean linked) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(Frequency.MAX_NAME_LENGTH * 4), Data::name, ByteBufCodecs.BOOL, Data::isPrivate,
                    ByteBufCodecs.BOOL, Data::linked, Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof QuantumTunnelBlockEntity tunnel)) {
                return null;
            }
            return tunnel.getKey().map(key -> new Data(key.name(), key.isPrivate(), tunnel.frequency() != null)).orElse(new Data("", false, false));
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
            return Data.STREAM_CODEC;
        }

        @Override
        public Identifier getUid() {
            return uid("quantum_tunnel");
        }

        // Jade needs the tooltip half as its own provider, under the same uid.
        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                QuantumTunnel.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                    if (data.name().isEmpty()) {
                        tooltip.add(Component.translatable("jade.arcforge.quantum_tunnel.none").withStyle(ChatFormatting.GRAY));
                        return;
                    }
                    tooltip.add(Component.translatable(data.isPrivate() ? "jade.arcforge.quantum_tunnel.private" : "jade.arcforge.quantum_tunnel.public",
                            data.name()).withStyle(data.linked() ? ChatFormatting.AQUA : ChatFormatting.RED));
                    if (!data.linked()) {
                        tooltip.add(Component.translatable("jade.arcforge.quantum_tunnel.not_linked").withStyle(ChatFormatting.RED));
                    }
                });
            }

            @Override
            public Identifier getUid() {
                return uid("quantum_tunnel");
            }
        }
    }

    public enum ChunkLoader implements StreamServerDataProvider<BlockAccessor, ChunkLoader.Data> {
        INSTANCE;

        public record Data(int status, int radius, int chunks) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Data::status, ByteBufCodecs.VAR_INT, Data::radius, ByteBufCodecs.VAR_INT, Data::chunks, Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof ChunkLoaderBlockEntity loader)) {
                return null;
            }
            return new Data(loader.getStatus().ordinal(), loader.getRadius(), loader.getHeld().size());
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
            return Data.STREAM_CODEC;
        }

        @Override
        public Identifier getUid() {
            return uid("chunk_loader");
        }

        // Jade needs the tooltip half as its own provider, under the same uid.
        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                ChunkLoader.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                    ChunkLoaderStatus status = ChunkLoaderStatus.byId(data.status());
                    if (status == ChunkLoaderStatus.ACTIVE) {
                        tooltip.add(Component.translatable("jade.arcforge.chunk_loader.loading", data.chunks(), data.radius()).withStyle(ChatFormatting.AQUA));
                    } else {
                        tooltip.add(status.description().copy().withStyle(ChatFormatting.RED));
                    }
                });
            }

            @Override
            public Identifier getUid() {
                return uid("chunk_loader");
            }
        }
    }
}
