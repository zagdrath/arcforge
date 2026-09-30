/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.farming;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.farming.MillstoneBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.ItemLane;
import net.zagdrath.arcforge.recipe.MachineRecipes;
import net.zagdrath.arcforge.recipe.MillingRecipe;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// The Millstone: an unpowered quern. Its input slot takes anything with an arcforge:milling recipe; each turn of the
// top stone (a use with an empty hand, at most one every handCooldown ticks, or turnsPerPulse on each rising redstone
// pulse) grinds the item a step, and after the recipe's turns it goes into the result slot, with the bonus rolled
// once. Hoppers and conduits feed it from any side but the bottom, and take the results out of the bottom.
public class MillstoneBlockEntity extends BlockEntity implements ConduitConnectable {
    public static final int SLOT_INPUT = 0, SLOT_OUTPUT = 1, SLOT_BONUS = 2, SLOTS = 3;

    private final FilteredItemHandler items;
    private final ResourceHandler<ItemResource> input;
    private final ResourceHandler<ItemResource> output;
    private int turns;
    private boolean wasPowered;
    private long lastHandTurn = Long.MIN_VALUE;

    public MillstoneBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.MILLSTONE.get(), pos, state);
        this.items = new FilteredItemHandler(SLOTS, (slot, resource) -> slot == SLOT_INPUT && MachineRecipes.isMillingInput(level, resource.toStack(1)),
                this::setChanged);
        this.input = new AutomationResourceHandler<>(items, slot -> slot == SLOT_INPUT, slot -> false);
        this.output = new AutomationResourceHandler<>(items, slot -> false, slot -> slot == SLOT_OUTPUT || slot == SLOT_BONUS);
    }

    public FilteredItemHandler getItems() {
        return items;
    }

    // Turns done on the item in the input slot.
    public int getTurns() {
        return turns;
    }

    // Turns the item in the input slot needs, or 0 if nothing can be milled.
    public int turnsNeeded() {
        return recipe().map(holder -> holder.value().turns()).orElse(0);
    }

    private java.util.Optional<RecipeHolder<MillingRecipe>> recipe() {
        ItemStack stack = items.getStack(SLOT_INPUT);
        return stack.isEmpty() ? java.util.Optional.empty() : MachineRecipes.milling(level, stack);
    }

    // Results out of the bottom; the input in from the other faces.
    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return side == Direction.DOWN ? output : input;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        if (type != ConduitType.ITEM) {
            return ConnectionMode.NONE;
        }
        return side == Direction.DOWN ? ConnectionMode.OUTPUT : ConnectionMode.INPUT;
    }

    // A turn by hand, unless the last was too recent. Returns whether the stone turned.
    public boolean turnByHand(ServerLevel level) {
        long now = level.getGameTime();
        // Compared as lastHandTurn + cooldown: now - Long.MIN_VALUE (never turned) would overflow.
        if (now < lastHandTurn + ArcforgeConfig.MILLSTONE_HAND_COOLDOWN.getAsInt()) {
            return false;
        }
        lastHandTurn = now;
        return grind(level);
    }

    // One turn: advances the item in the input slot and, once it has had its turns, mills it. Returns whether the
    // stone turned (it doesn't with nothing to mill, or with no room for the result).
    public boolean grind(ServerLevel level) {
        MillingRecipe recipe = recipe().map(RecipeHolder::value).orElse(null);
        if (recipe == null) {
            turns = 0;
            return false;
        }
        if (!ItemLane.fits(items, SLOT_OUTPUT, SLOT_BONUS, recipe)) {
            return false;
        }
        turns++;
        if (turns >= recipe.turns()) {
            ItemLane.finish(level, items, SLOT_INPUT, SLOT_OUTPUT, SLOT_BONUS, recipe);
            turns = 0;
        }
        BlockState state = getBlockState();
        level.setBlock(worldPosition, state.setValue(MillstoneBlock.TURN, (state.getValue(MillstoneBlock.TURN) + 1) % 4), Block.UPDATE_CLIENTS);
        level.playSound(null, worldPosition, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5F, 0.8F + level.getRandom().nextFloat() * 0.3F);
        level.sendParticles(ParticleTypes.WHITE_ASH, worldPosition.getX() + 0.5, worldPosition.getY() + 0.7, worldPosition.getZ() + 0.5,
                6, 0.3, 0.05, 0.3, 0.02);
        setChanged();
        return true;
    }

    // A rising redstone pulse turns it turnsPerPulse times.
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered && !wasPowered) {
            for (int i = 0; i < ArcforgeConfig.MILLSTONE_TURNS_PER_PULSE.getAsInt(); i++) {
                grind(level);
            }
        }
        if (powered != wasPowered) {
            wasPowered = powered;
            setChanged();
        }
    }

    // A player puts a stack in by hand. Returns how many went in.
    public int insertByHand(ItemStack stack) {
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = items.insert(SLOT_INPUT, ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
            return inserted;
        }
    }

    // The results go to the player (or drop on top); with none, the input comes back instead. Returns whether
    // anything did.
    public boolean takeByHand(Level level, Player player) {
        boolean any = give(level, player, SLOT_OUTPUT) | give(level, player, SLOT_BONUS);
        if (!any) {
            any = give(level, player, SLOT_INPUT);
            turns = 0;
        }
        return any;
    }

    private boolean give(Level level, Player player, int slot) {
        ItemStack stack = items.getStack(slot);
        if (stack.isEmpty()) {
            return false;
        }
        items.setStack(slot, ItemStack.EMPTY);
        if (!player.getInventory().add(stack)) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, stack);
        }
        return true;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            for (int slot = 0; slot < SLOTS; slot++) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), items.getStack(slot));
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items.deserialize(input.childOrEmpty("items"));
        items.ensureSize(SLOTS);
        turns = input.getIntOr("turns", 0);
        wasPowered = input.getBooleanOr("was_powered", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        items.serialize(output.child("items"));
        output.putInt("turns", turns);
        output.putBoolean("was_powered", wasPowered);
    }
}
