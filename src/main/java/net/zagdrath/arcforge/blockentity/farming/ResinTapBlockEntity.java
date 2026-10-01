/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.block.farming.ResinTapBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.interaction.FluidInteractable;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// The Resin Tap: every resinTap.interval ticks, while the log it hangs on belongs to a living tree (natural, not
// player-placed, leaves within leafSearchHeight above it), it gains what that log gives:
//  - a jungle log: latexPerDrip mB of Latex, into its own small tank (tankCapacity);
//  - a spruce log: a spruceResinChance of one Pine Resin, into its one slot (up to maxResin);
//  - any other log: an otherResinChance of one Pine Resin.
// Nothing goes in from outside. Buckets, hoppers and conduits take the Latex and the Pine Resin out (any face); an empty
// hand takes the Pine Resin. CONTENT on the block shows what's in its cup (checked every tick).
public class ResinTapBlockEntity extends BlockEntity implements ConduitConnectable, FluidInteractable {
    public enum Source { NONE, LATEX, SPRUCE, OTHER }

    private final FilteredFluidTank tank;
    private final FilteredItemHandler items;
    private final ResourceHandler<FluidResource> fluidOutput;
    private final ResourceHandler<ItemResource> itemOutput;
    private int timer;
    // Whether the last check found a living tree (for Jade), and whether it has checked since it loaded.
    private boolean alive;
    private boolean checked;

    public ResinTapBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.RESIN_TAP.get(), pos, state);
        this.tank = new FilteredFluidTank(ArcforgeConfig.RESIN_TAP_TANK_CAPACITY.getAsInt(),
                resource -> resource.getFluid() == ModFluids.LATEX.get(), this::setChanged);
        this.items = new FilteredItemHandler(1, (slot, resource) -> resource.is(ModItems.PINE_RESIN.get()), this::setChanged);
        this.fluidOutput = new AutomationResourceHandler<>(tank, index -> false, index -> true);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> true);
    }

    // --- What a log gives ---

    public static boolean dripsLatex(BlockState log) {
        return log.is(BlockTags.JUNGLE_LOGS);
    }

    public static Source sourceOf(BlockState log) {
        if (!ResinTapBlock.isLog(log)) {
            return Source.NONE;
        }
        return dripsLatex(log) ? Source.LATEX : log.is(BlockItemTags.SPRUCE_LOGS.block()) ? Source.SPRUCE : Source.OTHER;
    }

    // Natural leaves: player-placed (persistent) ones don't count, so a log in a wall isn't a tree.
    private static boolean isNaturalLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES) && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT));
    }

    // Whether the log at pos belongs to a living tree: natural leaves in the 3x3 round its column, up to leafSearchHeight
    // above it, before the trunk has ended for more than two blocks.
    public static boolean isLivingTree(BlockGetter level, BlockPos log) {
        int height = ArcforgeConfig.RESIN_TAP_LEAF_SEARCH.getAsInt();
        int gap = 0;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dy = 1; dy <= height; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (isNaturalLeaves(level.getBlockState(probe.setWithOffset(log, dx, dy, dz)))) {
                        return true;
                    }
                }
            }
            gap = ResinTapBlock.isLog(level.getBlockState(probe.setWithOffset(log, 0, dy, 0))) ? 0 : gap + 1;
            if (gap > 2) {
                return false;
            }
        }
        return false;
    }

    // --- Ticking ---

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!checked) {
            checked = true;
            BlockPos logPos = ResinTapBlock.logPos(state, pos);
            alive = sourceOf(level.getBlockState(logPos)) != Source.NONE && isLivingTree(level, logPos);
        }
        if (++timer >= ArcforgeConfig.RESIN_TAP_INTERVAL.getAsInt()) {
            timer = 0;
            drip(level, state);
        }
        // Emptied by a bucket, hand, hopper or conduit since: show it.
        updateContent(level);
    }

    // One drip: what the tree gives, if it's alive and there's room. Returns whether anything was gained.
    public boolean drip(ServerLevel level, BlockState state) {
        BlockPos logPos = ResinTapBlock.logPos(state, worldPosition);
        Source source = sourceOf(level.getBlockState(logPos));
        alive = source != Source.NONE && isLivingTree(level, logPos);
        boolean gained = false;
        if (alive) {
            if (source == Source.LATEX) {
                int amount = ArcforgeConfig.RESIN_TAP_LATEX_PER_DRIP.getAsInt();
                try (Transaction tx = Transaction.openRoot()) {
                    gained = tank.insert(0, FluidResource.of(ModFluids.LATEX.get()), amount, tx) > 0;
                    tx.commit();
                }
                if (gained) {
                    ArcforgeAdvancements.produced(this, ItemStack.EMPTY, ModFluids.LATEX.get(), "resin_tap");
                }
            } else {
                double chance = source == Source.SPRUCE ? ArcforgeConfig.RESIN_TAP_SPRUCE_CHANCE.getAsDouble()
                        : ArcforgeConfig.RESIN_TAP_OTHER_CHANCE.getAsDouble();
                ItemStack held = items.getStack(0);
                if (held.getCount() < ArcforgeConfig.RESIN_TAP_MAX_RESIN.getAsInt() && level.getRandom().nextDouble() < chance) {
                    items.setStack(0, held.isEmpty() ? new ItemStack(ModItems.PINE_RESIN.get()) : held.copyWithCount(held.getCount() + 1));
                    ArcforgeAdvancements.produced(this, new ItemStack(ModItems.PINE_RESIN.get()), null, "resin_tap");
                    gained = true;
                }
            }
        }
        setChanged();
        updateContent(level);
        return gained;
    }

    // The cup on the block: Latex, Pine Resin or nothing.
    private void updateContent(Level level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof ResinTapBlock)) {
            return;
        }
        ResinTapBlock.Content content = tank.getAmount() > 0 ? ResinTapBlock.Content.LATEX
                : !items.getStack(0).isEmpty() ? ResinTapBlock.Content.RESIN : ResinTapBlock.Content.EMPTY;
        if (state.getValue(ResinTapBlock.CONTENT) != content) {
            level.setBlock(worldPosition, state.setValue(ResinTapBlock.CONTENT, content), Block.UPDATE_CLIENTS);
        }
    }

    // An empty hand takes all the Pine Resin.
    public void takeResin(Level level, Player player) {
        ItemStack out = items.getStack(0);
        if (out.isEmpty()) {
            return;
        }
        items.setStack(0, ItemStack.EMPTY);
        if (!player.getInventory().add(out)) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, out);
        }
        level.playSound(null, worldPosition, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 0.6F, 1.2F);
    }

    // --- Access ---

    public FilteredFluidTank getTank() {
        return tank;
    }

    public ItemStack getResin() {
        return items.getStack(0);
    }

    public FilteredItemHandler getItems() {
        return items;
    }

    public boolean isAlive() {
        return alive;
    }

    public Source getSource() {
        return level != null ? sourceOf(level.getBlockState(ResinTapBlock.logPos(getBlockState(), worldPosition))) : Source.NONE;
    }

    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable Direction side) {
        return fluidOutput;
    }

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return itemOutput;
    }

    // A bucket fills from the tank.
    @Override
    public @Nullable ResourceHandler<FluidResource> getInteractionFluidHandler() {
        return fluidOutput;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return type == ConduitType.FLUID || type == ConduitType.ITEM ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        tank.deserialize(input.childOrEmpty("tank"));
        items.deserialize(input.childOrEmpty("items"));
        items.ensureSize(1);
        timer = input.getIntOr("timer", 0);
        alive = input.getBooleanOr("alive", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("tank"));
        items.serialize(output.child("items"));
        output.putInt("timer", timer);
        output.putBoolean("alive", alive);
    }
}
