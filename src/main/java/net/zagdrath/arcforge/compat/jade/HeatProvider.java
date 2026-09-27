/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.blockentity.conduit.ConduitBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ShellMultiblockBlockEntity;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.network.ConduitNetworkManager;
import net.zagdrath.arcforge.conduit.network.ThermalConduitNetwork;
import net.zagdrath.arcforge.heat.HeatHandler;
import net.zagdrath.arcforge.registry.ModCapabilities;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IDisplayHelper;
import snownee.jade.api.ui.JadeUI;
import snownee.jade.api.view.ProgressView;

// Heat (HU) in anything that holds it: heat machines, Heat Cells, formed arrays and Thermodynamic Conduits
// (whose whole network is shown). A bar filled by how much is stored, coloured by how hot it is, with the
// temperature and the amount on it. This half gathers the data on the server; Client draws it.
public enum HeatProvider implements StreamServerDataProvider<BlockAccessor, HeatProvider.Data> {
    INSTANCE;

    public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "heat");
    // The bar goes from this colour when cold to that one at 1,400°C.
    private static final int COLD = 0xFF5A8FD6, HOT = 0xFFFF5A1F;
    private static final int COLD_CELSIUS = 20, HOT_CELSIUS = 1_400;

    public record Data(int stored, int capacity, int celsius) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::stored,
                ByteBufCodecs.VAR_INT, Data::capacity,
                ByteBufCodecs.VAR_INT, Data::celsius,
                Data::new);
    }

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        if (!(accessor.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        if (accessor.getBlockEntity() instanceof ConduitBlockEntity conduit) {
            if (conduit.getConduitType() != ConduitType.THERMAL
                    || !(ConduitNetworkManager.get(level).getNetwork(accessor.getPosition()) instanceof ThermalConduitNetwork network)) {
                return null;
            }
            return new Data(network.getStored(), network.getCapacity(), network.getTemperature());
        }
        // A pane of Pressure Glass shows its array's heat.
        ShellMultiblockBlockEntity window = WindowProviders.master(level, accessor.getPosition());
        BlockPos pos = window != null ? window.getBlockPos() : accessor.getPosition();
        HeatHandler heat = level.getCapability(ModCapabilities.HEAT, pos, null);
        return heat != null && heat.getMaxHeat() > 0 ? new Data(heat.getHeat(), heat.getMaxHeat(), heat.getTemperature()) : null;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return Data.STREAM_CODEC;
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
            HeatProvider.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                IDisplayHelper display = IDisplayHelper.get();
                Component text = Component.translatable("jade.arcforge.heat", data.celsius(),
                        display.humanReadableNumber(data.stored(), "", false), display.humanReadableNumber(data.capacity(), "", false));
                float ratio = Mth.clamp((float) data.stored() / data.capacity(), 0.0F, 1.0F);
                ProgressView view = new ProgressView(
                        new ProgressView.PartBuilder().progress(ratio).color(heatColor(data.celsius())).build(),
                        text, JadeUI.progressStyle(), BoxStyle.nestedBox());
                tooltip.add(JadeUI.progress(view));
            });
        }

        private static int heatColor(int celsius) {
            float t = Mth.clamp((celsius - COLD_CELSIUS) / (float) (HOT_CELSIUS - COLD_CELSIUS), 0.0F, 1.0F);
            return 0xFF000000 | ((int) Mth.lerp(t, COLD >> 16 & 0xFF, HOT >> 16 & 0xFF) << 16)
                    | ((int) Mth.lerp(t, COLD >> 8 & 0xFF, HOT >> 8 & 0xFF) << 8)
                    | (int) Mth.lerp(t, COLD & 0xFF, HOT & 0xFF);
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
