/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.menu.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.zagdrath.arcforge.blockentity.multiblock.CentrifugeCascadeBlockEntity;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.machine.MachineMenu;
import net.zagdrath.arcforge.nuclear.Enrichment;
import net.zagdrath.arcforge.registry.ModBlocks;
import net.zagdrath.arcforge.registry.ModMenuTypes;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;

// The Gas Centrifuge Cascade's GUI: no slots. Its FE, its three tanks (feed, product, tails), its centrifuges and
// separative work, and the target and tails assays (in thousandths of a percent) with buttons to step them.
public class CentrifugeCascadeMenu extends MachineMenu {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_ENERGY_CAPACITY = 1;
    public static final int DATA_CENTRIFUGES = 2;
    public static final int DATA_TARGET = 3;
    public static final int DATA_TAILS_ASSAY = 4;
    public static final int DATA_FEED_ASSAY = 5;
    public static final int DATA_FEED = 6;
    public static final int DATA_FEED_CAPACITY = 7;
    public static final int DATA_PRODUCT = 8;
    public static final int DATA_PRODUCT_ASSAY = 9;
    public static final int DATA_PRODUCT_CAPACITY = 10;
    public static final int DATA_TAILS = 11;
    public static final int DATA_TAILS_CAPACITY = 12;
    // SWU done last tick and SWU a tick at full power, in thousandths.
    public static final int DATA_SWU_MILLI = 13;
    public static final int DATA_CAPACITY_MILLI = 14;
    public static final int DATA_ENERGY_USE = 15;
    public static final int DATA_FORMED = 16;
    public static final int DATA_STATUS = 17;
    public static final int DATA_REDSTONE_MODE = 18;
    public static final int DATA_SIDE_CONFIG = 19;
    public static final int DATA_VALUES = 20;

    // The player inventory's corner: the screen is taller than the standard one, and 40 px wider (the inventory centred).
    public static final int INVENTORY_X = 28, INVENTORY_Y = 104;

    // Buttons: the target down or up a small or a large step, the tails the same. In the 200s, each machine's own range
    // (the 300s are the Security tab's).
    public static final int TARGET_DOWN = 200, TARGET_UP = 201, TARGET_DOWN_BIG = 202, TARGET_UP_BIG = 203;
    public static final int TAILS_DOWN = 204, TAILS_UP = 205, TAILS_DOWN_BIG = 206, TAILS_UP_BIG = 207;
    public static final int TARGET_STEP = 100, TARGET_BIG_STEP = 1_000, TAILS_STEP = 10, TAILS_BIG_STEP = 50;

    // Client constructor, called with the block position written by the server.
    public CentrifugeCascadeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), CentrifugeCascadeBlockEntity.clientItems(), WideIntContainerData.client(DATA_VALUES));
    }

    public CentrifugeCascadeMenu(int containerId, Inventory inventory, BlockPos pos, MachineItemHandler items, ContainerData data) {
        super(ModMenuTypes.CENTRIFUGE_CASCADE.get(), containerId, inventory, pos, items, data, DATA_VALUES, ModBlocks.CENTRIFUGE_CASCADE_CONTROLLER.get());
        finish(inventory, INVENTORY_X, INVENTORY_Y);
    }

    @Override
    protected int statusIndex() {
        return DATA_STATUS;
    }

    @Override
    protected int redstoneIndex() {
        return DATA_REDSTONE_MODE;
    }

    @Override
    protected int sidesIndex() {
        return DATA_SIDE_CONFIG;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId < TARGET_DOWN || buttonId > TAILS_UP_BIG) {
            return super.clickMenuButton(player, buttonId);
        }
        access.execute((level, pos) -> {
            if (level.getBlockEntity(pos) instanceof CentrifugeCascadeBlockEntity cascade) {
                switch (buttonId) {
                    case TARGET_DOWN -> cascade.setTarget(cascade.getTarget() - TARGET_STEP);
                    case TARGET_UP -> cascade.setTarget(cascade.getTarget() + TARGET_STEP);
                    case TARGET_DOWN_BIG -> cascade.setTarget(cascade.getTarget() - TARGET_BIG_STEP);
                    case TARGET_UP_BIG -> cascade.setTarget(cascade.getTarget() + TARGET_BIG_STEP);
                    case TAILS_DOWN -> cascade.setTailsAssay(cascade.getTailsAssay() - TAILS_STEP);
                    case TAILS_UP -> cascade.setTailsAssay(cascade.getTailsAssay() + TAILS_STEP);
                    case TAILS_DOWN_BIG -> cascade.setTailsAssay(cascade.getTailsAssay() - TAILS_BIG_STEP);
                    default -> cascade.setTailsAssay(cascade.getTailsAssay() + TAILS_BIG_STEP);
                }
            }
        });
        return true;
    }

    public int getEnergy() {
        return value(DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        return value(DATA_ENERGY_CAPACITY);
    }

    public int getCentrifuges() {
        return value(DATA_CENTRIFUGES);
    }

    public int getTarget() {
        return value(DATA_TARGET);
    }

    public int getTailsAssay() {
        return value(DATA_TAILS_ASSAY);
    }

    // The feed's assay (0 when empty).
    public int getFeedAssay() {
        return value(DATA_FEED_ASSAY);
    }

    public int getFeed() {
        return value(DATA_FEED);
    }

    public int getFeedCapacity() {
        return value(DATA_FEED_CAPACITY);
    }

    public int getProduct() {
        return value(DATA_PRODUCT);
    }

    public int getProductAssay() {
        return value(DATA_PRODUCT_ASSAY);
    }

    public int getProductCapacity() {
        return value(DATA_PRODUCT_CAPACITY);
    }

    public int getTails() {
        return value(DATA_TAILS);
    }

    public int getTailsCapacity() {
        return value(DATA_TAILS_CAPACITY);
    }

    public double getSwu() {
        return value(DATA_SWU_MILLI) / 1000.0;
    }

    public double getSwuCapacity() {
        return value(DATA_CAPACITY_MILLI) / 1000.0;
    }

    public int getEnergyUse() {
        return value(DATA_ENERGY_USE);
    }

    public boolean isFormed() {
        return value(DATA_FORMED) != 0;
    }

    // Feed and SWU each mB of product takes from the feed in the tank (natural if it's empty), +inf if it can't.
    public double feedPerProduct() {
        return Enrichment.feedPerProduct(getTarget(), getFeedAssay() > 0 ? getFeedAssay() : Enrichment.NATURAL_PCM, getTailsAssay());
    }

    public double swuPerProduct() {
        return Enrichment.swuPerProduct(getTarget(), getFeedAssay() > 0 ? getFeedAssay() : Enrichment.NATURAL_PCM, getTailsAssay());
    }
}
