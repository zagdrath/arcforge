/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.machine;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.ArcforgeFakePlayer;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.MineRules;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.BlockBreakerMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Breaks the block in front of it with FE, keeping the drops in its 9 slots. There's no tool: it drops what the
// right tool would, unenchanted (stone gives cobblestone, iron ore raw iron, leaves nothing), and harder blocks
// take longer: max(minBreakTicks, hardness x ticksPerHardness) ticks at energyPerTick FE/t (stone 6 ticks, iron ore
// 12, obsidian 200). It never breaks unbreakable blocks, fluids, #arcforge:breaker_blacklist or multiblock parts,
// and breaks as a fake player so protection mods can refuse. A break that wouldn't fit its slots waits.
public class BlockBreakerBlockEntity extends MachineBlockEntity {
    public static final int SLOTS = 9;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.OUTPUT, SideMode.ENERGY);
    // The tools a break is worked out with: the first that suits the block.
    private static final List<ItemStack> TOOLS = List.of(new ItemStack(Items.NETHERITE_PICKAXE), new ItemStack(Items.NETHERITE_AXE),
            new ItemStack(Items.NETHERITE_SHOVEL), new ItemStack(Items.NETHERITE_HOE), new ItemStack(Items.SHEARS));

    private final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemOutput;
    private final ContainerData data;

    private int progress;
    private int total;
    private int usage;
    // The block being broken, so progress starts over when it changes.
    private @Nullable BlockState breaking;
    private int shownStage = -1;
    private int breaks;

    public BlockBreakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.BLOCK_BREAKER.get(), pos, state, SLOTS, (slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.OUTPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.BREAKER_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.BREAKER_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.itemOutput = new AutomationResourceHandler<>(items, slot -> false, slot -> slot < SLOTS);
        this.data = new WideIntContainerData(BlockBreakerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case BlockBreakerMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case BlockBreakerMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case BlockBreakerMenu.DATA_USAGE -> usage;
                    case BlockBreakerMenu.DATA_PROGRESS -> progress;
                    case BlockBreakerMenu.DATA_TOTAL -> total;
                    case BlockBreakerMenu.DATA_STATUS -> status.ordinal();
                    case BlockBreakerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case BlockBreakerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // A client-side copy of the slots, for the menu. Nothing goes in by hand.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(SLOTS, (slot, resource) -> false, UPGRADES, () -> {});
    }

    @Override
    public List<RedstoneMode> getAllowedRedstoneModes() {
        return RedstoneMode.WITH_PULSE;
    }

    // Whether it may break this block at all.
    public static boolean canBreak(ServerLevel level, BlockPos pos, BlockState state) {
        return MineRules.canBreak(level, pos, state);
    }

    // The tool that suits the block (never stored or damaged), or EMPTY to break it by hand.
    public static ItemStack virtualTool(BlockState state) {
        for (ItemStack tool : TOOLS) {
            if (tool.isCorrectToolForDrops(state) || tool.getDestroySpeed(state) > 1.0F) {
                return tool;
            }
        }
        return ItemStack.EMPTY;
    }

    // Ticks to break this block before Speed upgrades: harder blocks take longer, whatever the tool.
    public static int baseTicks(ServerLevel level, BlockPos pos, BlockState state) {
        return Math.max(ArcforgeConfig.BREAKER_MIN_TICKS.getAsInt(),
                (int) Math.ceil(state.getDestroySpeed(level, pos) * ArcforgeConfig.BREAKER_TICKS_PER_HARDNESS.getAsInt()));
    }

    // FE/t while breaking: Speed draws it faster, Energy cuts it.
    public int energyPerTick() {
        return (int) Math.ceil(ArcforgeConfig.BREAKER_ENERGY_PER_TICK.getAsInt() * speedMultiplier()
                * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        usage = 0;
        BlockPos target = pos.relative(getFacing());
        BlockState targetState = level.getBlockState(target);
        if (targetState != breaking) {
            progress = 0;
            breaking = targetState;
        }
        boolean allowed = redstoneAllows(level);
        if (targetState.isAir() || targetState.getBlock() instanceof LiquidBlock) {
            status = MachineStatus.NOTHING_TO_BREAK;
            progress = 0;
            total = 0;
        } else if (!canBreak(level, target, targetState)) {
            status = MachineStatus.CANNOT_BREAK;
            progress = 0;
            total = 0;
        } else {
            total = UpgradeType.time(baseTicks(level, target, targetState), upgrades(UpgradeType.SPEED));
            if (!allowed) {
                status = redstoneMode == RedstoneMode.PULSE ? MachineStatus.WAITING_PULSE : MachineStatus.DISABLED;
            } else {
                work(level, target, targetState);
            }
        }
        showCrack(level, target);
        setLit(status == MachineStatus.BREAKING);
        autoEject(level, itemOutput);
    }

    private void work(ServerLevel level, BlockPos target, BlockState targetState) {
        int perTick = energyPerTick();
        if (progress + 1 >= total) {
            // The last tick: only break if everything it drops fits.
            ItemStack tool = virtualTool(targetState);
            FakePlayer player = ArcforgeFakePlayer.at(level, worldPosition, getFacing(), tool.copy());
            List<ItemStack> drops = Block.getDrops(targetState, level, target, level.getBlockEntity(target), player, tool);
            if (!fitsAll(drops)) {
                status = MachineStatus.OUTPUT_FULL;
                return;
            }
            if (!energy.consume(perTick)) {
                status = MachineStatus.NO_POWER;
                return;
            }
            usage = perTick;
            if (NeoForge.EVENT_BUS.post(new BreakBlockEvent(level, target, targetState, player)).isCanceled()) {
                status = MachineStatus.CANNOT_BREAK;
                progress = 0;
                return;
            }
            targetState.spawnAfterBreak(level, target, tool, true);
            level.destroyBlock(target, false, player);
            drops.forEach(this::insert);
            progress = 0;
            breaking = null;
            breaks++;
            status = MachineStatus.BREAKING;
            consumePulse();
            setChanged();
            return;
        }
        if (!energy.consume(perTick)) {
            status = MachineStatus.NO_POWER;
            return;
        }
        usage = perTick;
        progress++;
        status = MachineStatus.BREAKING;
        setChanged();
    }

    // The crack overlay on the target, from its progress.
    private void showCrack(ServerLevel level, BlockPos target) {
        int stage = progress > 0 && total > 0 ? Math.min(9, progress * 10 / total) : -1;
        if (stage != shownStage) {
            level.destroyBlockProgress(crackId(), target, stage);
            shownStage = stage;
        }
    }

    // An id for the crack overlay that no entity uses.
    private int crackId() {
        return -1 - Math.abs(worldPosition.hashCode() % 1_000_000);
    }

    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel serverLevel && shownStage >= 0) {
            serverLevel.destroyBlockProgress(crackId(), worldPosition.relative(getFacing()), -1);
        }
        super.setRemoved();
    }

    // Whether all of these fit in the slots together.
    private boolean fitsAll(List<ItemStack> drops) {
        ItemStack[] slots = new ItemStack[SLOTS];
        for (int i = 0; i < SLOTS; i++) {
            slots[i] = items.getStack(i).copy();
        }
        for (ItemStack drop : drops) {
            int left = drop.getCount();
            for (int i = 0; i < SLOTS && left > 0; i++) {
                if (slots[i].isEmpty()) {
                    slots[i] = drop.copyWithCount(Math.min(left, drop.getMaxStackSize()));
                    left -= slots[i].getCount();
                } else if (ItemStack.isSameItemSameComponents(slots[i], drop)) {
                    int moved = Math.min(left, slots[i].getMaxStackSize() - slots[i].getCount());
                    slots[i].grow(moved);
                    left -= moved;
                }
            }
            if (left > 0) {
                return false;
            }
        }
        return true;
    }

    private void insert(ItemStack drop) {
        int left = drop.getCount();
        for (int i = 0; i < SLOTS && left > 0; i++) {
            ItemStack slot = items.getStack(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, drop)) {
                int moved = Math.min(left, slot.getMaxStackSize() - slot.getCount());
                items.setStack(i, slot.copyWithCount(slot.getCount() + moved));
                left -= moved;
            }
        }
        for (int i = 0; i < SLOTS && left > 0; i++) {
            if (items.getStack(i).isEmpty()) {
                int moved = Math.min(left, drop.getMaxStackSize());
                items.setStack(i, drop.copyWithCount(moved));
                left -= moved;
            }
        }
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    public int getProgress() {
        return progress;
    }

    public int getTotal() {
        return total;
    }

    // Blocks broken since it was loaded, for tests.
    public int getBreaks() {
        return breaks;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.OUTPUT ? itemOutput : null;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case FLUID, GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        output.putInt("progress", progress);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.block_breaker");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BlockBreakerMenu(containerId, inventory, worldPosition, items, data);
    }
}
