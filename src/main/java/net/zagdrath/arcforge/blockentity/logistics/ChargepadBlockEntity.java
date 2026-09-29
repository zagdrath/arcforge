/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.logistics;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.zagdrath.arcforge.block.logistics.ChargepadBlock;
import net.zagdrath.arcforge.conduit.ConduitConnectable;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.interaction.Dismantleable;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.transfer.energy.SidedEnergyHandler;
import net.zagdrath.arcforge.transfer.energy.TrackingEnergyHandler;

// The Chargepad: FE goes in through the port on its back (and only there), and every tick it charges the FE items of
// players standing on it, the held item first, then the offhand, the hotbar and the armour, each up to
// chargeRatePerItem and all together up to maxTransferPerTick. Its FE stays with the item when it's broken.
public class ChargepadBlockEntity extends BlockEntity implements ConduitConnectable, Dismantleable {
    // How long after its last charge it still shows as charging.
    private static final int CHARGING_TICKS = 10;
    private static final int[] ARMOUR_SLOTS = { 36, 37, 38, 39 };

    private final TrackingEnergyHandler energy;
    private final EnergyHandler input;
    private long lastCharged = Long.MIN_VALUE / 2;
    private int itemsCharging;

    public ChargepadBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CHARGEPAD.get(), pos, state);
        this.energy = new TrackingEnergyHandler(ArcforgeConfig.CHARGEPAD_CAPACITY.getAsInt(),
                Math.max(ArcforgeConfig.CHARGEPAD_MAX_INPUT.getAsInt(), ArcforgeConfig.CHARGEPAD_MAX_TRANSFER.getAsInt()), this::setChanged);
        this.input = new InputView();
    }

    public TrackingEnergyHandler getEnergy() {
        return energy;
    }

    // How many items it charged on its last charging tick (while it's still charging).
    public int getItemsCharging() {
        return isCharging() ? itemsCharging : 0;
    }

    private boolean isCharging() {
        return level != null && level.getGameTime() - lastCharged < CHARGING_TICKS;
    }

    private Direction back() {
        return getBlockState().getValue(ChargepadBlock.FACING).getOpposite();
    }

    // The standing area, above the pad and in front of the housing: x/z 1..15 and 1..13 px facing north, turned with it.
    public AABB standingArea() {
        double x0 = 1 / 16.0, x1 = 15 / 16.0, z0 = 1 / 16.0, z1 = 13 / 16.0;
        Direction facing = getBlockState().getValue(ChargepadBlock.FACING);
        double[] box = switch (facing) {
            case SOUTH -> new double[] { 1 - x1, 1 - z1, 1 - x0, 1 - z0 };
            case EAST -> new double[] { 1 - z1, x0, 1 - z0, x1 };
            case WEST -> new double[] { z0, 1 - x1, z1, 1 - x0 };
            default -> new double[] { x0, z0, x1, z1 };
        };
        BlockPos pos = worldPosition;
        return new AABB(pos.getX() + box[0], pos.getY() + 0.12, pos.getZ() + box[1], pos.getX() + box[2], pos.getY() + 1.2, pos.getZ() + box[3]);
    }

    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        energy.resetTotals();
        int budget = Math.min(ArcforgeConfig.CHARGEPAD_MAX_TRANSFER.getAsInt(), energy.getAmountAsInt());
        int perItem = ArcforgeConfig.CHARGEPAD_RATE_PER_ITEM.getAsInt();
        int charged = 0;
        if (budget > 0) {
            for (Player player : level.getEntitiesOfClass(Player.class, standingArea(), player -> !player.isSpectator())) {
                for (int slot : slotsInOrder(player)) {
                    if (budget <= 0) {
                        break;
                    }
                    if (player.getInventory().getItem(slot).isEmpty()) {
                        continue;
                    }
                    EnergyHandler item = ItemAccess.forPlayerSlot(player, slot).getCapability(Capabilities.Energy.ITEM);
                    if (item == null) {
                        continue;
                    }
                    int moved = EnergyHandlerUtil.move(energy, item, Math.min(perItem, budget), null);
                    if (moved > 0) {
                        budget -= moved;
                        charged++;
                    }
                }
            }
        }
        if (charged > 0) {
            lastCharged = level.getGameTime();
            itemsCharging = charged;
        }
        int stored = energy.getAmountAsInt();
        int capacity = energy.getCapacityAsInt();
        int charge = stored <= 0 ? 0 : Math.max(1, Math.round(4.0F * stored / capacity));
        BlockState now = state.setValue(ChargepadBlock.CHARGE, charge).setValue(ChargepadBlock.CHARGING, isCharging());
        if (now != state) {
            level.setBlock(pos, now, Block.UPDATE_CLIENTS);
        }
    }

    // The main hand, the offhand, the rest of the hotbar, then the armour.
    private static List<Integer> slotsInOrder(Player player) {
        List<Integer> slots = new ArrayList<>();
        int selected = player.getInventory().getSelectedSlot();
        slots.add(selected);
        slots.add(Inventory.SLOT_OFFHAND);
        for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
            if (i != selected) {
                slots.add(i);
            }
        }
        for (int slot : ARMOUR_SLOTS) {
            slots.add(slot);
        }
        return slots;
    }

    // --- Capabilities: FE in through the back only, capped at maxInput a call ---

    public @Nullable EnergyHandler getEnergyHandler(@Nullable Direction side) {
        return side == back() ? input : null;
    }

    @Override
    public ConnectionMode getConduitConnection(Direction side, ConduitType type) {
        return type == ConduitType.ENERGY && side == back() ? ConnectionMode.INPUT : ConnectionMode.NONE;
    }

    private final class InputView extends SidedEnergyHandler {
        InputView() {
            super(energy, true, false);
        }

        @Override
        public int insert(int amount, net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return super.insert(Math.min(amount, ArcforgeConfig.CHARGEPAD_MAX_INPUT.getAsInt()), transaction);
        }
    }

    // --- Item components: the FE goes with the item ---

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Integer stored = components.get(ModDataComponents.ENERGY.get());
        if (stored != null) {
            energy.load(stored);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (energy.getAmountAsInt() > 0) {
            components.set(ModDataComponents.ENERGY.get(), energy.getAmountAsInt());
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("energy");
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        energy.load(input.getIntOr("energy", 0));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("energy", energy.getAmountAsInt());
    }
}
