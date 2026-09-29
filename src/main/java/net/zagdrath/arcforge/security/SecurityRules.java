/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.security;

import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.zagdrath.arcforge.blockentity.machine.ArcQuarryBoundingBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.ArcforgeFakePlayer;
import net.zagdrath.arcforge.multiblock.MultiblockController;
import net.zagdrath.arcforge.multiblock.MultiblockPart;
import net.zagdrath.arcforge.tag.ModBlockTags;

// Who may use an Owned block. Unowned blocks (placed before security existed) are public; the owner and, with
// security.opsBypass, operators always may; otherwise the block's override, or else its owner's profile, decides:
// Public lets anyone in, Trusted the owner's trusted players, Private no one. Automation (conduits, hoppers,
// comparators) never asks. Arcforge's own machines act as their owner (canAccess(UUID, ...)).
public final class SecurityRules {
    private SecurityRules() {}

    public static boolean enabled() {
        return ArcforgeConfig.SECURITY_ENABLED.getAsBoolean();
    }

    public static boolean isOp(Player player) {
        return ArcforgeConfig.SECURITY_OPS_BYPASS.getAsBoolean() && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static boolean canAccess(Player player, Owned target) {
        if (!enabled() || target.owner() == null || isOp(player) || player.getUUID().equals(target.owner())) {
            return true;
        }
        if (player instanceof FakePlayer) {
            // Arcforge's machines check with their own owner before acting; other mods' fake players get Public only.
            return ArcforgeFakePlayer.is(player) || effectiveMode(target, player.level().getServer()) == SecurityMode.PUBLIC;
        }
        return allows(target, player.getUUID(), player.level().getServer());
    }

    // For a machine acting on its own (Block Breaker, Block Placer, Arc Quarry): as its owner would.
    public static boolean canAccess(@Nullable UUID actor, Owned target) {
        if (!enabled() || target.owner() == null || target.owner().equals(actor)) {
            return true;
        }
        Level level = target.getLevel();
        return actor != null && level != null && allows(target, actor, level.getServer());
    }

    private static boolean allows(Owned target, UUID player, @Nullable MinecraftServer server) {
        return switch (effectiveMode(target, server)) {
            case PUBLIC -> true;
            case TRUSTED -> server != null && SecurityProfiles.get(server).profile(target.owner()).trusts(player);
            case PRIVATE -> false;
        };
    }

    // Only the owner (or an operator) changes a block's security; trusted players may only use it.
    public static boolean canEditSecurity(Player player, Owned target) {
        return !enabled() || target.owner() == null || player.getUUID().equals(target.owner()) || isOp(player);
    }

    public static SecurityMode profileMode(Owned target, @Nullable MinecraftServer server) {
        if (target.owner() == null || server == null) {
            return ArcforgeConfig.SECURITY_DEFAULT_MODE.get();
        }
        return SecurityProfiles.get(server).profile(target.owner()).defaultMode();
    }

    public static SecurityMode effectiveMode(Owned target, @Nullable MinecraftServer server) {
        return target.securityOverride().orElseGet(() -> profileMode(target, server));
    }

    // The Owned block that decides access at pos: a formed multiblock part's controller, an Arc Quarry's main
    // block for its bounding blocks, or the block entity there.
    public static Optional<Owned> of(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlockTags.SECURITY_EXEMPT)) {
            return Optional.empty();
        }
        if (state.getBlock() instanceof MultiblockPart part) {
            MultiblockController controller = part.findController(level, pos);
            if (controller instanceof Owned owned) {
                return Optional.of(owned);
            }
        }
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ArcQuarryBoundingBlockEntity bounding && level.getBlockEntity(bounding.mainPos()) instanceof Owned quarry) {
            return Optional.of(quarry);
        }
        return blockEntity instanceof Owned owned ? Optional.of(owned) : Optional.empty();
    }

    // Tells a refused player whose block it is, with a "no" sound.
    public static void deny(Player player, Owned target, boolean breaking) {
        Level level = target.getLevel();
        Component block = level != null ? level.getBlockState(target.getBlockPos()).getBlock().getName() : Component.empty();
        Component owner = ownerName(target);
        player.sendOverlayMessage(breaking ? Component.translatable("message.arcforge.security.denied_break", owner, block)
                : Component.translatable("message.arcforge.security.denied", block, owner));
        player.level().playSound(null, target.getBlockPos(), SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.5F, 1.0F);
    }

    public static Component ownerName(Owned target) {
        return target.ownerName().isEmpty() ? Component.translatable("security.arcforge.unknown_owner") : Component.literal(target.ownerName());
    }
}
