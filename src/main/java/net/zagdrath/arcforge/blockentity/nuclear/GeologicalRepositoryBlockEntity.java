/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.advancement.ArcforgeAdvancements;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.menu.nuclear.GeologicalRepositoryMenu;
import net.zagdrath.arcforge.nuclear.RadiationField;
import net.zagdrath.arcforge.nuclear.RadiationSource;
import net.zagdrath.arcforge.nuclear.WasteLog;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModItems;
import net.zagdrath.arcforge.transfer.item.FilteredItemHandler;

// A Geological Repository: a vault for high-level waste, sunk deep in stable rock. It works only below
// nuclear.reprocessing.repositoryMaxY (Y -40) with deepslate (or tuff) on at least MIN_ROCK of its six sides. Vitrified
// Waste Logs and full Dry Casks put in its slot (by hand or by automation; nothing ever comes back out) are stored for
// good, safely: nothing radiates from it. Its GUI shows what it holds: the logs, the casks and the rods in them, and
// the radioactivity locked away (decaying as the logs do).
public class GeologicalRepositoryBlockEntity extends NuclearBlockEntity implements RadiationSource, MenuProvider {
    public static final int MIN_ROCK = 4;
    public static final int DATA_LOGS = 0, DATA_CASKS = 1, DATA_RODS = 2, DATA_ACTIVITY = 3, DATA_VALID = 4, DATA_VALUES = 5;

    private final FilteredItemHandler slot;
    private int logs, casks, rods;
    // The logs' radioactivity (mSv/h) when last written, and when; and the casks' (which doesn't decay here).
    private double logActivity;
    private long logTime;
    private double caskActivity;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_LOGS -> logs;
                case DATA_CASKS -> casks;
                case DATA_RODS -> rods;
                case DATA_ACTIVITY -> (int) Math.min(Integer.MAX_VALUE, Math.round(activity() * 10.0));
                case DATA_VALID -> level != null && isValidSite(level, worldPosition) ? 1 : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return DATA_VALUES;
        }
    };

    public GeologicalRepositoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.GEOLOGICAL_REPOSITORY.get(), pos, state);
        this.slot = new FilteredItemHandler(1, (index, resource) -> accepts(resource.toStack(1)), this::onSlotChanged);
    }

    public static boolean accepts(ItemStack stack) {
        return stack.is(ModItems.VITRIFIED_WASTE_LOG.get()) || stack.is(ModItems.DRY_CASK.get());
    }

    // Deep enough, in deepslate.
    public static boolean isValidSite(Level level, BlockPos pos) {
        if (pos.getY() > ArcforgeConfig.REPOSITORY_MAX_Y.getAsInt()) {
            return false;
        }
        int rock = 0;
        for (Direction direction : Direction.values()) {
            rock += level.getBlockState(pos.relative(direction)).is(BlockTags.DEEPSLATE_ORE_REPLACEABLES) ? 1 : 0;
        }
        return rock >= MIN_ROCK;
    }

    public FilteredItemHandler getSlot() {
        return slot;
    }

    public int logs() {
        return logs;
    }

    public int casks() {
        return casks;
    }

    public int rods() {
        return rods;
    }

    // The radioactivity locked away now (mSv/h, unshielded).
    public double activity() {
        long now = level != null ? level.getGameTime() : logTime;
        return WasteLog.decayed(logActivity, now - logTime) + caskActivity;
    }

    private void onSlotChanged() {
        if (level == null || level.isClientSide()) {
            return;
        }
        ItemStack stack = slot.getStack(0);
        if (stack.isEmpty() || !isValidSite(level, worldPosition)) {
            setChanged();
            return;
        }
        store(stack);
        slot.set(0, ItemResource.EMPTY, 0);
    }

    // Takes a stack in for good.
    public void store(ItemStack stack) {
        long now = level != null ? level.getGameTime() : 0L;
        if (stack.is(ModItems.VITRIFIED_WASTE_LOG.get())) {
            logActivity = WasteLog.decayed(logActivity, now - logTime) + RadiationField.rate(stack);
            logTime = now;
            logs += stack.getCount();
        } else if (stack.is(ModItems.DRY_CASK.get())) {
            casks += stack.getCount();
            ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            for (ItemStack held : (Iterable<ItemStack>) contents.nonEmptyItemCopyStream()::iterator) {
                rods += held.getCount();
                caskActivity += RadiationField.rate(held) * stack.getCount();
            }
        }
        ArcforgeAdvancements.produced(this, stack, null, "repository");
        sync();
    }

    // Nothing gets out of the vault.
    @Override
    public double radiationRate() {
        return 0.0;
    }

    @Override
    public Vec3 radiationCentre() {
        return Vec3.atCenterOf(worldPosition);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.geological_repository");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GeologicalRepositoryMenu(containerId, inventory, worldPosition, slot, data);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        logs = in.getIntOr("logs", 0);
        casks = in.getIntOr("casks", 0);
        rods = in.getIntOr("rods", 0);
        logActivity = in.getDoubleOr("log_activity", 0.0);
        logTime = in.getLongOr("log_time", 0L);
        caskActivity = in.getDoubleOr("cask_activity", 0.0);
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("logs", logs);
        out.putInt("casks", casks);
        out.putInt("rods", rods);
        out.putDouble("log_activity", logActivity);
        out.putLong("log_time", logTime);
        out.putDouble("cask_activity", caskActivity);
    }
}
