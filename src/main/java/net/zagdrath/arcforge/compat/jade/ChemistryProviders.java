/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.ChemicalReactorArrayCasingBlock;
import net.zagdrath.arcforge.blockentity.machine.ElectricEnchanterBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.ChemicalReactorArrayBlockEntity;
import net.zagdrath.arcforge.experience.LiquidExperience;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// The Chemical Reactor Array and the Electric Enchanter in Jade (their tanks, heat and energy show through Jade's own
// views). The array, on any casing: each lane's progress ("Lane 1: 42%", or "Lane 2: idle"). The Enchanter: how many
// enchantments its target has, and what the item in it costs ("Cost: 15 Lapis, 6,904 XP"). These halves gather the data
// on the server; their Client halves draw it.
public final class ChemistryProviders {
    private ChemistryProviders() {}

    public enum ReactorArray implements StreamServerDataProvider<BlockAccessor, ReactorArray.Data> {
        INSTANCE;

        public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "chemical_reactor_array");

        // Each lane's percent, -1 when it has no recipe.
        public record Data(List<Integer> lanes) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(ChemicalReactorArrayBlockEntity.LANES)), Data::lanes, Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            ChemicalReactorArrayBlockEntity array = ChemicalReactorArrayCasingBlock.STRUCTURE.findController(accessor.getLevel(), accessor.getPosition());
            if (array == null || !array.isFormed()) {
                return null;
            }
            List<Integer> lanes = new java.util.ArrayList<>();
            for (int lane = 0; lane < ChemicalReactorArrayBlockEntity.LANES; lane++) {
                int total = array.getTotal(lane);
                lanes.add(total > 0 ? 100 * array.getProgress(lane) / total : -1);
            }
            return new Data(lanes);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
            return Data.STREAM_CODEC;
        }

        @Override
        public Identifier getUid() {
            return UID;
        }

        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                ReactorArray.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                    for (int lane = 0; lane < data.lanes().size(); lane++) {
                        int percent = data.lanes().get(lane);
                        tooltip.add(percent < 0 ? Component.translatable("jade.arcforge.chemical_reactor_array.idle", lane + 1)
                                : Component.translatable("jade.arcforge.chemical_reactor_array.lane", lane + 1, percent));
                    }
                });
            }

            @Override
            public Identifier getUid() {
                return UID;
            }
        }
    }

    public enum Enchanter implements StreamServerDataProvider<BlockAccessor, Enchanter.Data> {
        INSTANCE;

        public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "electric_enchanter");

        public record Data(int targets, int lapis, int xp) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Data::targets, ByteBufCodecs.VAR_INT, Data::lapis, ByteBufCodecs.VAR_INT, Data::xp, Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof ElectricEnchanterBlockEntity enchanter)) {
                return null;
            }
            int lapis = 0, xp = 0;
            var input = enchanter.getItems().getStack(ElectricEnchanterBlockEntity.SLOT_INPUT);
            if (!input.isEmpty()) {
                var registry = accessor.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                List<java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer>> wanted = new java.util.ArrayList<>();
                enchanter.getTarget().forEach((key, level) -> registry.get(key).ifPresent(holder -> wanted.add(java.util.Map.entry(holder, level))));
                ElectricEnchanterBlockEntity.Plan plan = ElectricEnchanterBlockEntity.plan(input, wanted);
                lapis = plan.lapis();
                xp = plan.xp();
            }
            return new Data(enchanter.getTarget().size(), lapis, xp);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
            return Data.STREAM_CODEC;
        }

        @Override
        public Identifier getUid() {
            return UID;
        }

        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                Enchanter.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                    tooltip.add(Component.translatable("jade.arcforge.electric_enchanter.target", data.targets()));
                    if (data.lapis() > 0 || data.xp() > 0) {
                        tooltip.add(Component.translatable("gui.arcforge.enchanter.cost", data.lapis(),
                                String.format(java.util.Locale.ROOT, "%,d", data.xp() / LiquidExperience.MB_PER_POINT)));
                    }
                });
            }

            @Override
            public Identifier getUid() {
                return UID;
            }
        }
    }
}
