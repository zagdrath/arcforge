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
import net.zagdrath.arcforge.blockentity.logistics.QuantumTunnelBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.VacuumCollectorBlockEntity;
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

// Jade for the Reservoir ("Tank of 12 blocks", "This block: 32,000 mB"; the group's fluid shows as its fluid storage),
// the XP Drain ("Drains into the tank below" or "Place on a fluid container"), the XP Shower and the Vacuum Collector's
// experience ("Liquid Experience: 2,000 mB (100 points)", "Off: redstone signal"), the Quantum Tunnel ("Frequency:
// Base (private)" or "No frequency") and the Chunk Loader ("Loading 9 chunks (radius 1)" or its status).
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

        public record Data(int blocks, int own) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Data::blocks, ByteBufCodecs.VAR_INT, Data::own, Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof ReservoirBlockEntity reservoir)) {
                return null;
            }
            ReservoirGroup group = reservoir.getGroup();
            return new Data(group.size(), reservoir.ownAmount());
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
                            grouped(ReservoirBlockEntity.CAPACITY)).withStyle(ChatFormatting.GRAY));
                });
            }

            @Override
            public Identifier getUid() {
                return uid("reservoir");
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
