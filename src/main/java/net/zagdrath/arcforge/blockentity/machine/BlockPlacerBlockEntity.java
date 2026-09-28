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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.ArcforgeFakePlayer;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.RedstoneMode;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.BlockPlacerMenu;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.energy.ConsumerEnergyHandler;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// Places the first block it can from its 9 slots into the block in front of it (air, water or anything
// replaceable), for energyPerPlace FE each, every placeInterval ticks (or once per pulse). It places as a fake
// player looking out of its front, so blocks orient and place events fire as for a player. Items that aren't
// blocks, or blocks that can't stand there, are skipped for the next slot. Speed upgrades shorten the interval,
// Energy upgrades cut the FE.
public class BlockPlacerBlockEntity extends MachineBlockEntity {
    public static final int SLOTS = 9;
    public static final Set<UpgradeType> UPGRADES = EnumSet.of(UpgradeType.SPEED, UpgradeType.ENERGY);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.ENERGY);
    // The running sound and lit front last this long after each placement.
    private static final int LIT_TICKS = 20;

    private final ConsumerEnergyHandler energy;
    private final ResourceHandler<ItemResource> itemInput;
    private final ContainerData data;

    private int cooldown;
    private long lastPlaced = Long.MIN_VALUE / 2;
    private int placements;

    public BlockPlacerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.BLOCK_PLACER.get(), pos, state, SLOTS, (slot, resource) -> slot < SLOTS, UPGRADES,
                new SideConfig(SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.ENERGY, SideMode.NONE),
                SIDE_MODES);
        this.energy = new ConsumerEnergyHandler(
                ArcforgeConfig.PLACER_ENERGY_CAPACITY.getAsInt(),
                ArcforgeConfig.PLACER_MAX_INPUT.getAsInt(),
                this::setChanged);
        this.itemInput = new AutomationResourceHandler<>(items, slot -> slot < SLOTS, slot -> false);
        this.data = new WideIntContainerData(BlockPlacerMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case BlockPlacerMenu.DATA_ENERGY -> energy.getAmountAsInt();
                    case BlockPlacerMenu.DATA_CAPACITY -> energy.getCapacityAsInt();
                    case BlockPlacerMenu.DATA_NEXT_SLOT -> nextSlot();
                    case BlockPlacerMenu.DATA_FRONT_BLOCKED -> frontBlocked() ? 1 : 0;
                    case BlockPlacerMenu.DATA_STATUS -> status.ordinal();
                    case BlockPlacerMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case BlockPlacerMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // A client-side copy of the slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(SLOTS, (slot, resource) -> slot < SLOTS, UPGRADES, () -> {});
    }

    @Override
    public List<RedstoneMode> getAllowedRedstoneModes() {
        return RedstoneMode.WITH_PULSE;
    }

    // FE per placement: Energy upgrades cut it.
    public int energyPerPlace() {
        return (int) Math.ceil(ArcforgeConfig.PLACER_ENERGY_PER_PLACE.getAsInt() * UpgradeType.energyCostMultiplier(upgrades(UpgradeType.ENERGY)));
    }

    // Ticks between placements: Speed upgrades shorten it.
    public int interval() {
        return UpgradeType.time(ArcforgeConfig.PLACER_INTERVAL.getAsInt(), upgrades(UpgradeType.SPEED));
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        boolean allowed = redstoneAllows(level);
        boolean pulse = redstoneMode == RedstoneMode.PULSE;
        if (cooldown > 0) {
            cooldown--;
        }
        if (!allowed) {
            status = pulse ? MachineStatus.WAITING_PULSE : MachineStatus.DISABLED;
        } else if (pulse || cooldown <= 0) {
            cooldown = interval();
            status = tryPlace(level, pos);
            if (status == MachineStatus.PLACING) {
                consumePulse();
            }
        }
        setLit(level.getGameTime() - lastPlaced < LIT_TICKS);
    }

    private MachineStatus tryPlace(ServerLevel level, BlockPos pos) {
        Direction facing = getFacing();
        BlockPos front = pos.relative(facing);
        if (!level.getBlockState(front).canBeReplaced()) {
            return MachineStatus.FRONT_BLOCKED;
        }
        if (nextSlot() < 0) {
            return MachineStatus.NOTHING_TO_PLACE;
        }
        int cost = energyPerPlace();
        if (energy.getAmountAsInt() < cost) {
            return MachineStatus.NO_POWER;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            ItemStack stack = items.getStack(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
                continue;
            }
            ItemStack one = stack.copyWithCount(1);
            FakePlayer player = ArcforgeFakePlayer.at(level, pos, facing, one.copy());
            BlockPlaceContext context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, one,
                    new BlockHitResult(Vec3.atCenterOf(front), facing.getOpposite(), front, false));
            boolean placed = blockItem.place(context).consumesAction();
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (placed) {
                items.setStack(slot, stack.copyWithCount(stack.getCount() - 1));
                energy.consume(cost);
                lastPlaced = level.getGameTime();
                placements++;
                setChanged();
                return MachineStatus.PLACING;
            }
        }
        return MachineStatus.NOTHING_TO_PLACE;
    }

    // The first slot holding a block (what it will try to place first), or -1.
    public int nextSlot() {
        for (int slot = 0; slot < SLOTS; slot++) {
            if (items.getStack(slot).getItem() instanceof BlockItem) {
                return slot;
            }
        }
        return -1;
    }

    public boolean frontBlocked() {
        return level != null && !level.getBlockState(worldPosition.relative(getFacing())).canBeReplaced();
    }

    public ConsumerEnergyHandler getEnergy() {
        return energy;
    }

    // Blocks placed since it was loaded, for tests.
    public int getPlacements() {
        return placements;
    }

    // --- Capabilities. A null side is an internal/unsided query and sees the full automation view. ---

    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.INPUT ? itemInput : null;
    }

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        SideMode mode = modeFor(side);
        return mode == null || mode == SideMode.ENERGY ? energy : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        SideMode mode = modeFor(side);
        return switch (type) {
            case ITEM -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case ENERGY -> mode == SideMode.ENERGY ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case FLUID, GAS, THERMAL -> ConnectionMode.NONE;
        };
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.deserialize(input.childOrEmpty("energy"));
        cooldown = input.getIntOr("cooldown", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        energy.serialize(output.child("energy"));
        output.putInt("cooldown", cooldown);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.arcforge.block_placer");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new BlockPlacerMenu(containerId, inventory, worldPosition, items, data);
    }
}
