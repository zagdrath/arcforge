/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.farming.CompostBinBlock;
import net.zagdrath.arcforge.block.farming.LoamFarmlandBlock;
import net.zagdrath.arcforge.block.farming.ScarecrowBlock;
import net.zagdrath.arcforge.block.farming.TrellisBlock;
import net.zagdrath.arcforge.blockentity.farming.CopperSprinklerBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.FarmMachineBlockEntity;
import net.zagdrath.arcforge.blockentity.farming.FertilizerSpreaderBlockEntity;
import net.zagdrath.arcforge.client.gui.ArcforgeGui;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Farming blocks in Jade, read from their block states (no server data needed): Loam Farmland's nutrients and whether
// it's moist ("Nutrients: 7 / 15", "Moist"), a Compost Bin's fill ("Compost: 3 / 7", or "Compost ready") and a
// Trellis's hop vine. The rustic machines send a little server data: how a Planter or Harvester is triggered ("Runs on
// each redstone pulse", "Next run in 3 s"), a Fertilizer Spreader's stock and a Copper Sprinkler's water; their items
// and water also show through Jade's own storage views. A Scarecrow shows its range.
public final class FarmingProviders {
    private FarmingProviders() {}

    public enum LoamFarmland implements IBlockComponentProvider {
        INSTANCE;

        public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "loam_farmland");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            if (!(state.getBlock() instanceof LoamFarmlandBlock)) {
                return;
            }
            tooltip.add(Component.translatable("jade.arcforge.loam_farmland.nutrients", state.getValue(LoamFarmlandBlock.NUTRIENTS),
                    LoamFarmlandBlock.MAX_NUTRIENTS));
            tooltip.add(Component.translatable(state.getValue(FarmlandBlock.MOISTURE) > 0 ? "jade.arcforge.loam_farmland.moist" : "jade.arcforge.loam_farmland.dry"));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    public enum CompostBin implements IBlockComponentProvider {
        INSTANCE;

        public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "compost_bin");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            if (!(state.getBlock() instanceof CompostBinBlock)) {
                return;
            }
            int level = state.getValue(CompostBinBlock.LEVEL);
            tooltip.add(level == CompostBinBlock.READY
                    ? Component.translatable("jade.arcforge.compost_bin.ready")
                    : Component.translatable("jade.arcforge.compost_bin.level", level, CompostBinBlock.FULL));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    // A Trellis: bare, or its hop vine's stage ("Hops: growing (2 / 4)", "Hops: cones ready"). Crops show their growth
    // through Jade's own crop view.
    public enum Trellis implements IBlockComponentProvider {
        INSTANCE;

        public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "trellis");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockState state = accessor.getBlockState();
            if (!(state.getBlock() instanceof TrellisBlock)) {
                return;
            }
            int age = state.getValue(TrellisBlock.AGE);
            tooltip.add(switch (age) {
                case TrellisBlock.BARE -> Component.translatable("jade.arcforge.trellis.bare");
                case TrellisBlock.BEARING -> Component.translatable("jade.arcforge.trellis.bearing");
                default -> Component.translatable("jade.arcforge.trellis.growing", age, TrellisBlock.BEARING);
            });
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    // Planter, Harvester, Fertilizer Spreader and Copper Sprinkler. kind: 0 Planter/Harvester (a = on redstone, b =
    // ticks to the next run), 1 Spreader (a = fertilizer held), 2 Sprinkler (a = water, b = capacity).
    public enum RusticMachine implements StreamServerDataProvider<BlockAccessor, RusticMachine.Data> {
        INSTANCE;

        public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "rustic_machine");

        public record Data(int kind, int a, int b) {
            public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, Data::kind,
                    ByteBufCodecs.VAR_INT, Data::a,
                    ByteBufCodecs.VAR_INT, Data::b,
                    Data::new);
        }

        @Override
        public @Nullable Data streamData(BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof FarmMachineBlockEntity machine) {
                return new Data(0, machine.onRedstone() ? 1 : 0, machine.ticksUntilRun());
            }
            if (accessor.getBlockEntity() instanceof FertilizerSpreaderBlockEntity spreader) {
                return new Data(1, spreader.fertilizerCount(), 0);
            }
            if (accessor.getBlockEntity() instanceof CopperSprinklerBlockEntity sprinkler) {
                return new Data(2, sprinkler.getTank().getAmount(), sprinkler.getTank().getCapacity());
            }
            return null;
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
                RusticMachine.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                    switch (data.kind()) {
                        case 0 -> tooltip.add(data.a() == 1
                                ? Component.translatable("jade.arcforge.farm_machine.redstone")
                                : Component.translatable("jade.arcforge.farm_machine.timer", (data.b() + 19) / 20));
                        case 1 -> tooltip.add(data.a() > 0
                                ? Component.translatable("jade.arcforge.fertilizer_spreader.stock", data.a())
                                : Component.translatable("jade.arcforge.fertilizer_spreader.empty").withStyle(ChatFormatting.RED));
                        case 2 -> {
                            tooltip.add(Component.translatable("jade.arcforge.copper_sprinkler.water", ArcforgeGui.grouped(data.a()), ArcforgeGui.grouped(data.b())));
                            tooltip.add(data.a() >= ArcforgeConfig.SPRINKLER_WATER_PER_TICK.getAsInt()
                                    ? Component.translatable("jade.arcforge.copper_sprinkler.running").withStyle(ChatFormatting.AQUA)
                                    : Component.translatable("jade.arcforge.copper_sprinkler.dry").withStyle(ChatFormatting.RED));
                        }
                        default -> {}
                    }
                });
            }

            @Override
            public Identifier getUid() {
                return UID;
            }
        }
    }

    // A Scarecrow: its range (from the local config; servers normally keep the default).
    public enum Scarecrow implements IBlockComponentProvider {
        INSTANCE;

        public static final Identifier UID = Identifier.fromNamespaceAndPath(Arcforge.MODID, "scarecrow");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlockState().getBlock() instanceof ScarecrowBlock) {
                tooltip.add(Component.translatable("jade.arcforge.scarecrow.range", ArcforgeConfig.SCARECROW_RADIUS.getAsInt()));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
