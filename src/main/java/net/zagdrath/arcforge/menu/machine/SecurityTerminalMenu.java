/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.arcforge.network.SecurityProfilePayload;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.security.SecurityMode;
import net.zagdrath.arcforge.security.SecurityProfiles;
import net.zagdrath.arcforge.security.SecurityRules;

// The Security Terminal's screen has no slots: it shows the viewer's own profile (SecurityProfilePayload) and sends
// edits (SecurityEditPayload), applied here.
public class SecurityTerminalMenu extends AbstractContainerMenu {
    private static final double MAX_DISTANCE = 8.0;

    private final BlockPos pos;
    private final Player player;

    // Client constructor, called with the terminal's position written by the server.
    public SecurityTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    public SecurityTerminalMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuTypes.SECURITY_TERMINAL.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
    }

    // The player's profile to their screen; `error` (a lang key, "" for none) with `errorArg` explains a refused edit.
    public static void sendProfile(ServerPlayer player, String error, String errorArg) {
        MinecraftServer server = player.level().getServer();
        SecurityProfiles.Profile profile = SecurityProfiles.get(server).profile(player.getUUID());
        List<SecurityProfilePayload.Entry> trusted = new ArrayList<>();
        profile.trusted().forEach((id, name) -> trusted.add(new SecurityProfilePayload.Entry(id, name)));
        SecurityProfilePayload payload = new SecurityProfilePayload(profile.defaultMode(), trusted, SecurityRules.enabled(), error, errorArg);
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public void setMode(ServerPlayer player, SecurityMode mode) {
        SecurityProfiles.get(player.level().getServer()).setDefault(player.getUUID(), mode);
        sendProfile(player, "", "");
    }

    // Trusts a player by name: someone online first, else anyone the server has seen.
    public void add(ServerPlayer player, String name) {
        MinecraftServer server = player.level().getServer();
        String trimmed = name.trim();
        Optional<NameAndId> found = Optional.ofNullable(server.getPlayerList().getPlayerByName(trimmed))
                .map(online -> new NameAndId(online.getUUID(), online.getGameProfile().name()));
        if (found.isEmpty()) {
            found = server.services().nameToIdCache().get(trimmed);
        }
        if (found.isEmpty()) {
            sendProfile(player, "gui.arcforge.security_terminal.no_player", trimmed);
            return;
        }
        UUID id = found.get().id();
        SecurityProfiles profiles = SecurityProfiles.get(server);
        if (id.equals(player.getUUID())) {
            sendProfile(player, "gui.arcforge.security_terminal.self", "");
        } else if (profiles.profile(player.getUUID()).trusts(id)) {
            sendProfile(player, "gui.arcforge.security_terminal.already", found.get().name());
        } else {
            profiles.trust(player.getUUID(), id, found.get().name());
            sendProfile(player, "", "");
        }
    }

    public void remove(ServerPlayer player, UUID trusted) {
        SecurityProfiles.get(player.level().getServer()).untrust(player.getUUID(), trusted);
        sendProfile(player, "", "");
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(ModBlocks.SECURITY_TERMINAL.get())
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= MAX_DISTANCE * MAX_DISTANCE;
    }
}
