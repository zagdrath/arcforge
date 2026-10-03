/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.blockentity.multiblock;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.CombinedResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.arcforge.block.multiblock.CondenserArrayCasingBlock;
import net.zagdrath.arcforge.conduit.ConduitType;
import net.zagdrath.arcforge.conduit.ConnectionMode;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.machine.ClimateHelper;
import net.zagdrath.arcforge.machine.MachineStatus;
import net.zagdrath.arcforge.machine.config.SideConfig;
import net.zagdrath.arcforge.machine.config.SideMode;
import net.zagdrath.arcforge.menu.data.WideIntContainerData;
import net.zagdrath.arcforge.menu.multiblock.CondenserArrayMenu;
import net.zagdrath.arcforge.multiblock.CubeMultiblockStructure;
import net.zagdrath.arcforge.multiblock.MultiblockAutomation;
import net.zagdrath.arcforge.registry.ModBlockEntityTypes;
import net.zagdrath.arcforge.registry.ModFluids;
import net.zagdrath.arcforge.steam.BoilerCore;
import net.zagdrath.arcforge.transfer.AutomationResourceHandler;
import net.zagdrath.arcforge.transfer.fluid.FilteredFluidTank;
import net.zagdrath.arcforge.transfer.item.MachineItemHandler;
import net.zagdrath.arcforge.upgrade.UpgradeType;

// The Condenser Array (see CubeMultiblockBlockEntity): a solid box, 3x3x3 up to 7x7x7, that turns Exhaust Steam from a
// Steam Turbine Array's Exhaust ports back into water, 1 mB for 1 mB. It condenses 120 mB/t per cube (27 blocks of its
// volume) in open air; water sources and ice touching its outer faces add to that (20 each, ice 30, packed ice 40, blue
// ice 60), a cold biome multiplies it by 1.25 and the Nether by 0.5, up to 400 mB/t per cube (a 7x7x7: 5,081). Its
// tanks hold 16,000 mB per cube. It looks around every 40 ticks.
// Its ports push the water out on their own.
public class CondenserArrayBlockEntity extends CubeMultiblockBlockEntity {
    public static final Set<UpgradeType> UPGRADES = EnumSet.noneOf(UpgradeType.class);
    private static final List<SideMode> SIDE_MODES = List.of(SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT);

    // How the rate breaks down: open air, water and ice touching it, and the climate (multiplier x 100).
    public record Cooling(int air, int water, int ice, int multiplierPercent, int rate) {
        public static final Cooling NONE = new Cooling(0, 0, 0, 100, 0);
    }

    private final FilteredFluidTank exhaustIn;
    private final FilteredFluidTank waterOut;
    private final ResourceHandler<FluidResource> exhaustInput;
    private final ResourceHandler<FluidResource> waterOutput;
    private final ResourceHandler<FluidResource> fluidAll;
    private final ContainerData data;
    private Cooling cooling = Cooling.NONE;
    private long lastScan;
    private boolean scanDue = true;
    // mB condensed last tick.
    private int condensed;

    public CondenserArrayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.CONDENSER_ARRAY.get(), pos, state, 0, (level, slot, resource) -> false, UPGRADES,
                new SideConfig(SideMode.NONE, SideMode.NONE, SideMode.INPUT, SideMode.OUTPUT, SideMode.NONE, SideMode.NONE),
                SIDE_MODES, false, 0, 0, new int[0], slot -> false);
        int tank = ArcforgeConfig.CONDENSER_TANK_PER_CUBE.getAsInt();
        this.exhaustIn = new FilteredFluidTank(tank, resource -> resource.is(ModFluids.EXHAUST_STEAM.get()), this::setChanged);
        this.waterOut = new FilteredFluidTank(tank, BoilerCore::isWater, this::setChanged);
        this.exhaustInput = new AutomationResourceHandler<>(exhaustIn, index -> true, index -> false);
        this.waterOutput = new AutomationResourceHandler<>(waterOut, index -> false, index -> true);
        this.fluidAll = new CombinedResourceHandler<>(exhaustInput, waterOutput);
        this.data = new WideIntContainerData(CondenserArrayMenu.DATA_VALUES) {
            @Override
            protected int getValue(int index) {
                return switch (index) {
                    case CondenserArrayMenu.DATA_EXHAUST -> exhaustIn.getAmount();
                    case CondenserArrayMenu.DATA_WATER -> waterOut.getAmount();
                    case CondenserArrayMenu.DATA_TANK_CAPACITY -> waterOut.getCapacity();
                    case CondenserArrayMenu.DATA_RATE -> cooling.rate();
                    case CondenserArrayMenu.DATA_CONDENSED -> condensed;
                    case CondenserArrayMenu.DATA_AIR -> cooling.air();
                    case CondenserArrayMenu.DATA_WATER_BONUS -> cooling.water();
                    case CondenserArrayMenu.DATA_ICE -> cooling.ice();
                    case CondenserArrayMenu.DATA_MULTIPLIER -> cooling.multiplierPercent();
                    case CondenserArrayMenu.DATA_STATUS -> status.ordinal();
                    case CondenserArrayMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                    case CondenserArrayMenu.DATA_SIDE_CONFIG -> sideConfig.pack();
                    default -> 0;
                };
            }
        };
    }

    // A client-side copy of the (no) slots, for the menu.
    public static MachineItemHandler clientItems() {
        return new MachineItemHandler(0, (slot, resource) -> false, UPGRADES, () -> {});
    }

    @Override
    protected CubeMultiblockStructure<?> structure() {
        return CondenserArrayCasingBlock.STRUCTURE;
    }

    // The cube grew into a box: its exhaust and water go to the box's master.
    @Override
    public void moveContentsTo(CubeMultiblockBlockEntity master) {
        if (master instanceof CondenserArrayBlockEntity to) {
            SuperheaterArrayBlockEntity.moveTank(exhaustIn, to.exhaustIn);
            SuperheaterArrayBlockEntity.moveTank(waterOut, to.waterOut);
            setChanged();
            to.setChanged();
        }
    }

    // The tanks follow the size; what they hold carries over.
    @Override
    protected void onSizeChanged() {
        int tank = perCube(ArcforgeConfig.CONDENSER_TANK_PER_CUBE.getAsInt());
        exhaustIn.setCapacity(tank);
        waterOut.setCapacity(tank);
        scanDue = true;
    }

    // What cools a 3x3x3 cube centred here: the 54 blocks touching its outer faces, and the climate at the centre.
    public static Cooling cooling(Level level, BlockPos center) {
        return cooling(level, center.offset(-1, -1, -1), center.offset(1, 1, 1));
    }

    // What cools the box from min to max: the blocks touching its outer faces, and the climate at its middle. Its
    // open-air rate and cap are per cube.
    public static Cooling cooling(Level level, BlockPos min, BlockPos max) {
        int water = 0, ice = 0;
        BlockPos center = new BlockPos((min.getX() + max.getX()) / 2, (min.getY() + max.getY()) / 2, (min.getZ() + max.getZ()) / 2);
        for (int x = min.getX() - 1; x <= max.getX() + 1; x++) {
            for (int y = min.getY() - 1; y <= max.getY() + 1; y++) {
                for (int z = min.getZ() - 1; z <= max.getZ() + 1; z++) {
                    // Exactly one coordinate on the outside, the other two over the face.
                    int outside = (x < min.getX() || x > max.getX() ? 1 : 0) + (y < min.getY() || y > max.getY() ? 1 : 0)
                            + (z < min.getZ() || z > max.getZ() ? 1 : 0);
                    if (outside != 1) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.BLUE_ICE)) {
                        ice += ArcforgeConfig.CONDENSER_BLUE_ICE_BONUS.getAsInt();
                    } else if (state.is(Blocks.PACKED_ICE)) {
                        ice += ArcforgeConfig.CONDENSER_PACKED_ICE_BONUS.getAsInt();
                    } else if (state.is(Blocks.ICE)) {
                        ice += ArcforgeConfig.CONDENSER_ICE_BONUS.getAsInt();
                    } else if (state.getFluidState().isSourceOfType(Fluids.WATER)) {
                        water += ArcforgeConfig.CONDENSER_WATER_BONUS.getAsInt();
                    }
                }
            }
        }
        double multiplier = ClimateHelper.waterEvaporates(level, center) ? ArcforgeConfig.CONDENSER_NETHER_MULTIPLIER.getAsDouble()
                : ClimateHelper.isCold(level, center, ArcforgeConfig.CONDENSER_COLD_BIOME_TAGS.get()) ? ArcforgeConfig.CONDENSER_COLD_MULTIPLIER.getAsDouble()
                : 1.0;
        double cubes = (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1) / 27.0;
        return cooling((int) Math.round(ArcforgeConfig.CONDENSER_BASE_RATE_PER_CUBE.getAsInt() * cubes), water, ice, multiplier, cubes);
    }

    // The rate from its parts for a 3x3x3: (air + water + ice) x climate, rounded down, at most maxRatePerCube.
    public static Cooling cooling(int air, int water, int ice, double multiplier) {
        return cooling(air, water, ice, multiplier, 1.0);
    }

    // As above, for a structure this many cubes big (the cap scales with it).
    public static Cooling cooling(int air, int water, int ice, double multiplier, double cubes) {
        int cap = (int) Math.round(ArcforgeConfig.CONDENSER_MAX_RATE_PER_CUBE.getAsInt() * cubes);
        int rate = Math.min(cap, (int) Math.floor((air + water + ice) * multiplier));
        return new Cooling(air, water, ice, (int) Math.round(multiplier * 100), rate);
    }

    // Looks again on the next tick (a casing moved, the cube turned or re-formed).
    @Override
    public void onStructureChanged() {
        super.onStructureChanged();
        scanDue = true;
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        tickPorts(level);
        if (!isFormed()) {
            return;
        }
        if (scanDue || level.getGameTime() - lastScan >= ArcforgeConfig.CONDENSER_SCAN_INTERVAL.getAsInt()) {
            scanDue = false;
            lastScan = level.getGameTime();
            cooling = cooling(level, getMinCorner(), getMaxCorner());
        }
        condensed = 0;
        if (!redstoneMode.canRun(isPowered(level))) {
            status = MachineStatus.DISABLED;
        } else if (exhaustIn.getAmount() == 0) {
            status = MachineStatus.NO_STEAM;
        } else if (waterOut.getSpace() == 0) {
            status = MachineStatus.OUTPUT_FULL;
        } else {
            int n = Math.min(cooling.rate(), Math.min(exhaustIn.getAmount(), waterOut.getSpace()));
            if (n > 0) {
                try (Transaction tx = Transaction.openRoot()) {
                    exhaustIn.extract(0, exhaustIn.getResource(0), n, tx);
                    waterOut.insert(0, FluidResource.of(Fluids.WATER), n, tx);
                    tx.commit();
                }
                condensed = n;
                setChanged();
            }
            status = n > 0 ? MachineStatus.CONDENSING : MachineStatus.IDLE;
        }
        setLit(condensed > 0);
        if (level.getGameTime() % ArcforgeConfig.MULTIBLOCK_PUSH_INTERVAL.getAsInt() == 0) {
            MultiblockAutomation.pushOutputs(level, this, true);
        }
    }

    public FilteredFluidTank getExhaustIn() {
        return exhaustIn;
    }

    public FilteredFluidTank getWaterOut() {
        return waterOut;
    }

    public Cooling getCooling() {
        return cooling;
    }

    public int getCondensed() {
        return condensed;
    }

    // --- Capabilities (MultiblockController) ---

    @Override
    public @Nullable ResourceHandler<ItemResource> getItemHandler(@Nullable SideMode mode) {
        return null;
    }

    @Override
    public @Nullable ResourceHandler<FluidResource> getFluidHandler(@Nullable SideMode mode) {
        if (mode == null) {
            return fluidAll;
        }
        return switch (mode) {
            case INPUT -> exhaustInput;
            case OUTPUT -> waterOutput;
            default -> null;
        };
    }

    // Exhaust Steam is a gas: it comes in by Pressurized Conduit. The water leaves by Fluid Conduit.
    @Override
    public ConnectionMode getConduitConnection(SideMode mode, ConduitType type) {
        return switch (type) {
            case GAS -> mode == SideMode.INPUT ? ConnectionMode.INPUT : ConnectionMode.NONE;
            case FLUID -> mode == SideMode.OUTPUT ? ConnectionMode.OUTPUT : ConnectionMode.NONE;
            case ITEM, ENERGY, THERMAL -> ConnectionMode.NONE;
        };
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        exhaustIn.deserialize(input.childOrEmpty("exhaust"));
        waterOut.deserialize(input.childOrEmpty("water"));
        scanDue = true;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        exhaustIn.serialize(output.child("exhaust"));
        waterOut.serialize(output.child("water"));
    }

    @Override
    public Component getDisplayName() {
        return getBox() != null ? Component.translatable("container.arcforge.condenser_array.sized", sizeText())
                : Component.translatable("container.arcforge.condenser_array");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CondenserArrayMenu(containerId, inventory, worldPosition, items, data);
    }
}
