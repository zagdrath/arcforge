/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBlockEntity;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBoundingBlockEntity;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.quarry.QuarrySettings;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The Arc Quarry in Jade, on its main block or any part: "Mining · 56 / 1,234", the layer it's on, why it's paused
// (if it is), and its area. The server builds the lines; Client draws them.
public enum ArcQuarryProvider implements StreamServerDataProvider<BlockAccessor, List<Component>> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "arc_quarry");
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> CODEC = ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    private static boolean isPause(MachineStatus status) {
        return status == MachineStatus.NO_POWER || status == MachineStatus.OUTPUT_FULL || status == MachineStatus.OUT_OF_REPLACE
                || status == MachineStatus.WAITING_CHUNK;
    }

    @Override
    public @Nullable List<Component> streamData(BlockAccessor accessor) {
        ArcQuarryBlockEntity quarry = accessor.getBlockEntity() instanceof ArcQuarryBlockEntity main ? main
                : accessor.getBlockEntity() instanceof ArcQuarryBoundingBlockEntity part ? part.main() : null;
        if (quarry == null) {
            return null;
        }
        List<Component> lines = new ArrayList<>();
        MachineStatus status = quarry.getStatus();
        Component word = isPause(status) ? MachineStatus.MINING.getDescription() : quarry.statusText();
        lines.add(Component.translatable("jade.arcforge.quarry", word, String.format("%,d", quarry.getMinedCount()), String.format("%,d", quarry.getTargetCount())));
        if (quarry.getQuarryState() == ArcQuarryBlockEntity.State.MINING && quarry.getLastTarget() != null) {
            lines.add(Component.translatable("jade.arcforge.quarry.layer", quarry.getLastTarget().getY()));
        }
        if (isPause(status)) {
            lines.add(Component.translatable("jade.arcforge.quarry.why", quarry.statusText()));
        }
        QuarrySettings settings = quarry.getSettings();
        lines.add(Component.translatable("tooltip.arcforge.quarry.area", settings.radius(), settings.minY(), settings.maxY()));
        return lines;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, List<Component>> streamCodec() {
        return CODEC;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    // Jade needs the tooltip half as its own provider, under the same uid.
    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            ArcQuarryProvider.INSTANCE.decodeFromData(accessor).ifPresent(lines -> lines.forEach(tooltip::add));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
