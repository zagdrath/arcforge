/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.event;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.zagdrath.arcforge.Arcforge;
import net.zagdrath.arcforge.block.multiblock.PlantHitbox;
import net.zagdrath.arcforge.blockentity.multiblock.OilDerrickBlockEntity;
import net.zagdrath.arcforge.blockentity.multiblock.PumpjackBlockEntity;
import net.zagdrath.arcforge.multiblock.OilfieldStructures;
import net.zagdrath.arcforge.registry.ModBlocks;

// A formed Oil Derrick's ladder runs up the front of a leg that leans in, so near the ground its rungs stand at the
// rig's edge and a player climbing them is in the block in front, which isn't part of the rig (OilfieldCasingBlock's
// isLadder only covers the rig's own blocks); a Pumpjack's ladder stands on its side, climbed from outside its blocks.
// Every tick, on both sides (the player's own client moves them), a player on either ladder
// (OilDerrickBlockEntity.onLadder, PumpjackBlockEntity.onLadder) who isn't already climbing something climbs it as on a
// vanilla ladder: no fall damage, held to a slow slide down (none while sneaking), and up while walking into it.
@EventBusSubscriber(modid = Arcforge.MODID)
public final class DerrickLadders {
    // Vanilla's ladder speeds: the most it lets you move across or slide down, and how fast it lifts you.
    private static final double ACROSS = 0.15, SLIDE = 0.15, CLIMB = 0.12;

    private DerrickLadders() {}

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (player.isSpectator() || player.getAbilities().flying || player.onClimbable()) {
            return;
        }
        Predicate<AABB> ladder = ladderNear(player.level(), player.blockPosition());
        if (ladder == null || !ladder.test(player.getBoundingBox())) {
            return;
        }
        player.resetFallDistance();
        Vec3 motion = player.getDeltaMovement();
        double x = Math.max(-ACROSS, Math.min(ACROSS, motion.x)), z = Math.max(-ACROSS, Math.min(ACROSS, motion.z));
        double y = Math.max(-SLIDE, motion.y);
        if (y < 0.0 && player.isShiftKeyDown()) {
            y = 0.0;
        }
        if (player.horizontalCollision) {
            y = CLIMB;
        }
        player.setDeltaMovement(x, y, z);
    }

    // The ladder of the formed derrick or pumpjack a block beside or under the player (feet or head height) is part of,
    // or null.
    private static @Nullable Predicate<AABB> ladderNear(Level level, BlockPos feet) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = feet.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if ((state.is(ModBlocks.DERRICK_FRAME.get()) || state.is(ModBlocks.DERRICK_CASING.get())) && OilfieldStructures.isFormedPart(state)
                            && PlantHitbox.plant(level, pos) instanceof OilDerrickBlockEntity derrick) {
                        return derrick::onLadder;
                    }
                    if (state.is(ModBlocks.PUMPJACK_CASING.get()) && OilfieldStructures.isFormedPart(state)
                            && PlantHitbox.plant(level, pos) instanceof PumpjackBlockEntity pumpjack) {
                        return pumpjack::onLadder;
                    }
                }
            }
        }
        return null;
    }
}
