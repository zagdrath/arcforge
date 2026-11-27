/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.compat.jade;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.impl.BlockAccessorImpl;
import snownee.jade.impl.WailaCommonRegistration;

// For the game tests (only called with Jade installed): what Jade's server side sends for a block, provider by provider,
// so a multiblock's casings can be checked against its controller. Kept here so nothing else loads Jade's classes.
public final class JadeParity {
    private JadeParity() {}

    // Each server data provider Jade runs for the block at pos, and the data it sends (null when it sends nothing).
    public static Map<Identifier, CompoundTag> serverData(ServerLevel level, Player player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockAccessor accessor = new BlockAccessorImpl.Builder()
                .level(level)
                .player(player)
                .hit(new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false))
                .blockState(state)
                .blockEntity(() -> level.getBlockEntity(pos))
                .serverConnected(true)
                .showDetails(true)
                .build();
        Map<Identifier, CompoundTag> data = new LinkedHashMap<>();
        for (IServerDataProvider<BlockAccessor> provider : WailaCommonRegistration.instance().blockDataProvidersOf(state, level.getBlockEntity(pos), false)) {
            CompoundTag tag = new CompoundTag();
            try {
                provider.appendServerData(tag, accessor);
            } catch (RuntimeException e) {
                tag.putString("error", e.toString());
            }
            data.put(provider.getUid(), tag.isEmpty() ? null : tag);
        }
        return data;
    }

    // What a player sees of a provider's data: for Jade's storage views (fluids, FE, items) that's all of it but the name
    // of the provider that answered, written first (Jade's own for a controller, WindowProviders' for its parts).
    private static Object shown(Identifier uid, CompoundTag tag) {
        if (!uid.getNamespace().equals("minecraft") || !uid.getPath().endsWith("_storage") || !(tag.get(uid.toString()) instanceof net.minecraft.nbt.ByteArrayTag bytes)) {
            return tag;
        }
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.wrappedBuffer(bytes.getAsByteArray());
        net.minecraft.network.FriendlyByteBuf friendly = new net.minecraft.network.FriendlyByteBuf(buf);
        friendly.readUtf();
        byte[] rest = new byte[friendly.readableBytes()];
        friendly.readBytes(rest);
        return java.util.Arrays.toString(rest);
    }

    // How what Jade sends for the block at part differs from what it sends for the controller at controller: one line
    // per provider that sends something for one of them and not the same for the other.
    public static List<String> differences(ServerLevel level, Player player, BlockPos controller, BlockPos part) {
        Map<Identifier, CompoundTag> expected = serverData(level, player, controller);
        Map<Identifier, CompoundTag> actual = serverData(level, player, part);
        String name = BuiltInRegistries.BLOCK.getKey(level.getBlockState(part).getBlock()).getPath();
        List<String> lines = new ArrayList<>();
        for (Map.Entry<Identifier, CompoundTag> entry : expected.entrySet()) {
            CompoundTag mine = actual.get(entry.getKey());
            if (entry.getValue() != null && mine == null) {
                lines.add(name + ": no " + entry.getKey() + (actual.containsKey(entry.getKey()) ? " (sent nothing)" : " (not registered)"));
            } else if (entry.getValue() != null && !Objects.equals(shown(entry.getKey(), entry.getValue()), shown(entry.getKey(), mine))) {
                lines.add(name + ": different " + entry.getKey());
                net.zagdrath.arcforge.Arcforge.LOGGER.debug("[jade parity] {} {}: controller {} / part {}", name, entry.getKey(), entry.getValue(), mine);
            }
        }
        for (Map.Entry<Identifier, CompoundTag> entry : actual.entrySet()) {
            if (entry.getValue() != null && expected.get(entry.getKey()) == null) {
                lines.add(name + ": only it has " + entry.getKey());
            }
        }
        return lines;
    }
}
