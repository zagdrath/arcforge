/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.arcforge.nuclear;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.arcforge.blockentity.storage.ItemReservoirBlockEntity;
import net.zagdrath.arcforge.blockentity.storage.ReservoirBlockEntity;
import net.zagdrath.arcforge.config.ArcforgeConfig;
import net.zagdrath.arcforge.item.storage.LeadBoxItem;
import net.zagdrath.arcforge.registry.ModDataComponents;
import net.zagdrath.arcforge.registry.ModDataMaps;

// The dose rate at a point (mSv/h), from everything radioactive near it (the arcforge:radioactivity data maps):
//  - blocks in the world, per block; block entities' items and fluids (a chest of ore, a tank of UF6), per item and per
//    bucket; items on the ground; and other players' inventories. A stack holds its contents too (a shulker box, a
//    bundle, a Pressurized Cylinder's or tank's fluid) except in a Lead Box, which lets nothing out;
//  - each source within radiation.sourceRadius adds rate / d^2 (d in blocks, from the point to the source's middle, at
//    least half a block), times the transmission of every block on the straight line between (the
//    arcforge:radiation_shielding data map; see RadiationShielding). A source's own block and the point's block don't
//    count;
//  - an entity's own inventory (and armour) counts as being radiation.inventoryDistance away (half a block: four times
//    the rate at one block), unshielded; a Hazmat Suit cuts only what comes from outside.
// Each chunk's sources are found when first asked for and kept for radiation.chunkCacheTicks: radioactive blocks only
// in sections whose palette holds one, grouped into 4x4x4 cells (their summed rate at their centroid) so a vein is one
// source; block entities one each (a reservoir's blocks a share each of what it holds). The strongest
// radiation.maxSources (by unshielded rate) are traced through the blocks; the rest are left out.
public final class RadiationField {
    // A source: its middle (block coordinates), its rate at a block, and what holds it (a block entity's handler or
    // container, so one store seen from two chunks counts once; null for blocks and entities).
    public record Source(double x, double y, double z, double rate, @Nullable Object owner) {}

    private record Cached(long time, List<Source> sources) {}

    private static final Map<ServerLevel, Map<Long, Cached>> CACHE = new WeakHashMap<>();
    private static final double MIN_DISTANCE_SQR = 0.25;
    // Shielding below this is as good as none getting through.
    private static final double OPAQUE = 1.0E-4;
    // Nested containers counted, at most (a box in a box in a box).
    private static final int MAX_DEPTH = 3;

    private RadiationField() {}

    // --- Rates ---

    public static double rate(BlockState state) {
        Radioactivity radioactivity = BuiltInRegistries.BLOCK.wrapAsHolder(state.getBlock()).getData(ModDataMaps.RADIOACTIVE_BLOCKS);
        return radioactivity != null ? radioactivity.doseRate() : 0.0;
    }

    private static boolean isRadioactive(BlockState state) {
        return rate(state) > 0.0;
    }

    // A whole stack, with what it holds.
    public static double rate(ItemStack stack) {
        return rate(stack, 0);
    }

    private static double rate(ItemStack stack, int depth) {
        if (stack.isEmpty() || stack.getItem() instanceof LeadBoxItem) {
            return 0.0;
        }
        Radioactivity radioactivity = BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).getData(ModDataMaps.RADIOACTIVE_ITEMS);
        double total = radioactivity != null ? (double) radioactivity.doseRate() * stack.getCount() : 0.0;
        // A Fuel Rod's own: fresh a little, irradiated far more (see FuelRod.doseRate).
        FuelRod rod = stack.get(ModDataComponents.FUEL_ROD.get());
        if (rod != null) {
            total += rod.doseRate() * stack.getCount();
        }
        // A Vitrified Waste Log's, decaying (see WasteLog), and dust on a contaminated item (see Contamination).
        total += (WasteLog.doseRate(stack) + net.zagdrath.arcforge.nuclear.safety.Contamination.of(stack)) * stack.getCount();
        if (depth >= MAX_DEPTH) {
            return total;
        }
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null) {
            // A Dry Cask's overpack lets only a little of its rods' out.
            double shield = stack.is(net.zagdrath.arcforge.registry.ModItems.DRY_CASK.get()) ? ArcforgeConfig.DRY_CASK_TRANSMISSION.getAsDouble() : 1.0;
            for (ItemStack held : (Iterable<ItemStack>) container.nonEmptyItemCopyStream()::iterator) {
                total += rate(held, depth + 1) * shield;
            }
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        if (bundle != null) {
            for (ItemStack held : (Iterable<ItemStack>) bundle.itemCopies()::iterator) {
                total += rate(held, depth + 1);
            }
        }
        SimpleFluidContent fluid = stack.get(ModDataComponents.FLUID_CONTENTS.get());
        if (fluid != null) {
            total += rate(fluid.copy());
        }
        return total;
    }

    // A fluid, per bucket; UF6 and its tails by their assay (see Enrichment.activityFactor), the data map's rate being
    // at the fluid's own default assay.
    public static double rate(FluidStack stack) {
        if (stack.isEmpty()) {
            return 0.0;
        }
        double perBucket = rate(stack.getFluid());
        if (perBucket <= 0.0) {
            return 0.0;
        }
        int assay = Enrichment.assay(stack);
        if (assay > 0) {
            perBucket *= Enrichment.activityFactor(assay) / Enrichment.activityFactor(Enrichment.defaultAssay(stack.getFluid()));
        }
        return perBucket * stack.getAmount() / 1000.0;
    }

    public static double rate(Fluid fluid) {
        Radioactivity radioactivity = BuiltInRegistries.FLUID.wrapAsHolder(fluid).getData(ModDataMaps.RADIOACTIVE_FLUIDS);
        return radioactivity != null ? radioactivity.doseRate() : 0.0;
    }

    // Everything an entity carries and wears.
    public static double carried(LivingEntity entity) {
        double total = 0.0;
        if (entity instanceof Player player) {
            Container inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                total += rate(inventory.getItem(i));
            }
            // Fallout on their skin (see Contamination).
            total += net.zagdrath.arcforge.nuclear.safety.Contamination.skin(player);
        } else {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                total += rate(entity.getItemBySlot(slot));
            }
        }
        return total;
    }

    // --- Shielding ---

    // What one block lets through (0 to 1).
    public static double transmission(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return 1.0;
        }
        RadiationShielding shielding = shielding(state.getBlock());
        if (shielding != null) {
            return shielding.transmission();
        }
        // A waterlogged block, or a liquid: the liquid's block's entry (water's for water).
        if (!state.getFluidState().isEmpty()) {
            RadiationShielding liquid = shielding(state.getFluidState().createLegacyBlock().getBlock());
            if (liquid != null) {
                return liquid.transmission();
            }
        }
        return state.isSolid() ? ArcforgeConfig.RADIATION_DEFAULT_SHIELDING.getAsDouble() : 1.0;
    }

    public static @Nullable RadiationShielding shielding(Block block) {
        return BuiltInRegistries.BLOCK.wrapAsHolder(block).getData(ModDataMaps.RADIATION_SHIELDING);
    }

    // What gets through every block on the straight line from a to b, leaving out the blocks a and b are in. Walks the
    // blocks the line passes through (Amanatides and Woo).
    public static double shielding(BlockGetter level, Vec3 a, Vec3 b) {
        int x = floor(a.x), y = floor(a.y), z = floor(a.z);
        int endX = floor(b.x), endY = floor(b.y), endZ = floor(b.z);
        double dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        int stepX = (int) Math.signum(dx), stepY = (int) Math.signum(dy), stepZ = (int) Math.signum(dz);
        double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;
        double tMaxX = stepX != 0 ? (stepX > 0 ? x + 1 - a.x : a.x - x) * tDeltaX : Double.POSITIVE_INFINITY;
        double tMaxY = stepY != 0 ? (stepY > 0 ? y + 1 - a.y : a.y - y) * tDeltaY : Double.POSITIVE_INFINITY;
        double tMaxZ = stepZ != 0 ? (stepZ > 0 ? z + 1 - a.z : a.z - z) * tDeltaZ : Double.POSITIVE_INFINITY;
        double through = 1.0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int limit = Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z);
        for (int steps = 0; steps < limit; steps++) {
            if (tMaxX < tMaxY && tMaxX < tMaxZ) {
                x += stepX;
                tMaxX += tDeltaX;
            } else if (tMaxY < tMaxZ) {
                y += stepY;
                tMaxY += tDeltaY;
            } else {
                z += stepZ;
                tMaxZ += tDeltaZ;
            }
            if (x == endX && y == endY && z == endZ) {
                break;
            }
            through *= transmission(level, pos.set(x, y, z));
            if (through < OPAQUE) {
                return 0.0;
            }
        }
        return through;
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }

    // --- The field ---

    // The dose rate (mSv/h) an entity takes now: what's round it, cut by its Hazmat Suit, plus what it carries.
    public static double doseRate(ServerLevel level, LivingEntity entity) {
        Vec3 at = entity.position().add(0.0, entity.getBbHeight() * 0.5, 0.0);
        double outside = doseRateAt(level, at, entity) * HazmatSuit.transmission(entity);
        double distance = ArcforgeConfig.RADIATION_INVENTORY_DISTANCE.getAsDouble();
        return outside + carried(entity) / Math.max(MIN_DISTANCE_SQR, distance * distance);
    }

    // The dose rate at a point from everything round it (not counting what self carries; self may be null): the
    // sources round it, the fallout on the ground there (see Fallout) and a containment's radioactive air (see
    // Containments).
    public static double doseRateAt(ServerLevel level, Vec3 at, @Nullable LivingEntity self) {
        return sourcesAt(level, at, self) + net.zagdrath.arcforge.nuclear.safety.Fallout.ambient(level, at)
                + net.zagdrath.arcforge.nuclear.safety.Containments.airDose(level, BlockPos.containing(at));
    }

    private static double sourcesAt(ServerLevel level, Vec3 at, @Nullable LivingEntity self) {
        int radius = ArcforgeConfig.RADIATION_RADIUS.getAsInt();
        double radiusSqr = (double) radius * radius;
        List<Source> sources = new ArrayList<>();
        Set<Object> owners = Collections.newSetFromMap(new IdentityHashMap<>());
        int minChunkX = floor(at.x - radius) >> 4, maxChunkX = floor(at.x + radius) >> 4;
        int minChunkZ = floor(at.z - radius) >> 4, maxChunkZ = floor(at.z + radius) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                for (Source source : sources(level, chunk)) {
                    if (distanceSqr(at, source) <= radiusSqr && (source.owner() == null || owners.add(source.owner()))) {
                        sources.add(source);
                    }
                }
            }
        }
        // Items on the ground, and other players' inventories.
        AABB area = new AABB(at, at).inflate(radius);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
            double rate = rate(item.getItem());
            if (rate > 0.0) {
                sources.add(new Source(item.getX(), item.getY() + 0.125, item.getZ(), rate, null));
            }
        }
        for (Player player : level.players()) {
            if (player != self && player.distanceToSqr(at) <= radiusSqr && !player.isSpectator()) {
                double rate = carried(player);
                if (rate > 0.0) {
                    sources.add(new Source(player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), rate, null));
                }
            }
        }
        if (sources.isEmpty()) {
            return 0.0;
        }
        // The strongest first, by their unshielded rate here.
        int most = ArcforgeConfig.RADIATION_MAX_SOURCES.getAsInt();
        if (sources.size() > most) {
            sources.sort((p, q) -> Double.compare(unshielded(at, q), unshielded(at, p)));
            sources = sources.subList(0, most);
        }
        double total = 0.0;
        for (Source source : sources) {
            double unshielded = unshielded(at, source);
            if (unshielded > 0.0) {
                total += unshielded * shielding(level, at, new Vec3(source.x(), source.y(), source.z()));
            }
        }
        return total;
    }

    private static double distanceSqr(Vec3 at, Source source) {
        double dx = source.x() - at.x, dy = source.y() - at.y, dz = source.z() - at.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double unshielded(Vec3 at, Source source) {
        return source.rate() / Math.max(MIN_DISTANCE_SQR, distanceSqr(at, source));
    }

    // --- Each chunk's sources, kept a while ---

    public static List<Source> sources(ServerLevel level, LevelChunk chunk) {
        Map<Long, Cached> cache = CACHE.computeIfAbsent(level, key -> new HashMap<>());
        long key = ChunkPos.pack(chunk.getPos().x(), chunk.getPos().z());
        long now = level.getGameTime();
        Cached cached = cache.get(key);
        if (cached == null || now - cached.time() >= ArcforgeConfig.RADIATION_CACHE_TICKS.getAsInt() || now < cached.time()) {
            cached = new Cached(now, scan(level, chunk));
            cache.put(key, cached);
        }
        return cached.sources();
    }

    // Forget a chunk (unloaded) or a whole level.
    public static void forget(ServerLevel level, ChunkPos pos) {
        Map<Long, Cached> cache = CACHE.get(level);
        if (cache != null) {
            cache.remove(ChunkPos.pack(pos.x(), pos.z()));
        }
    }

    public static void forget(ServerLevel level) {
        CACHE.remove(level);
    }

    private static List<Source> scan(ServerLevel level, LevelChunk chunk) {
        List<Source> found = new ArrayList<>();
        int baseX = chunk.getPos().x() << 4, baseZ = chunk.getPos().z() << 4;
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(RadiationField::isRadioactive)) {
                continue;
            }
            int baseY = chunk.getMinY() + (i << 4);
            // Cells of 4x4x4: {rate, rate * x, rate * y, rate * z} each.
            double[] cells = new double[64 * 4];
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        double rate = rate(section.getBlockState(x, y, z));
                        if (rate > 0.0) {
                            int cell = ((y >> 2) * 16 + (z >> 2) * 4 + (x >> 2)) * 4;
                            cells[cell] += rate;
                            cells[cell + 1] += rate * (x + 0.5);
                            cells[cell + 2] += rate * (y + 0.5);
                            cells[cell + 3] += rate * (z + 0.5);
                        }
                    }
                }
            }
            for (int cell = 0; cell < cells.length; cell += 4) {
                double rate = cells[cell];
                if (rate > 0.0) {
                    found.add(new Source(baseX + cells[cell + 1] / rate, baseY + cells[cell + 2] / rate, baseZ + cells[cell + 3] / rate, rate, null));
                }
            }
        }
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            Source source = source(level, blockEntity);
            if (source != null) {
                found.add(source);
            }
        }
        return List.copyOf(found);
    }

    // What a block entity holds: a vanilla-style container's items, or the items and fluids its capabilities show
    // (unsided).
    private static @Nullable Source source(ServerLevel level, BlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        if (blockEntity instanceof RadiationSource own) {
            double rate = own.radiationRate();
            Vec3 at = own.radiationCentre();
            return rate > 0.0 ? new Source(at.x, at.y, at.z, rate, blockEntity) : null;
        }
        double rate = 0.0;
        Object owner = blockEntity;
        if (blockEntity instanceof Container container) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                rate += rate(container.getItem(i));
            }
        } else {
            ResourceHandler<ItemResource> items = level.getCapability(Capabilities.Item.BLOCK, pos, null);
            if (items != null) {
                owner = items;
                for (int i = 0; i < items.size(); i++) {
                    ItemResource resource = items.getResource(i);
                    long amount = items.getAmountAsLong(i);
                    if (!resource.isEmpty() && amount > 0) {
                        rate += rate(resource.toStack((int) Math.min(Integer.MAX_VALUE, amount)));
                    }
                }
            }
        }
        ResourceHandler<FluidResource> fluids = level.getCapability(Capabilities.Fluid.BLOCK, pos, null);
        if (fluids != null) {
            for (int i = 0; i < fluids.size(); i++) {
                FluidResource resource = fluids.getResource(i);
                long amount = fluids.getAmountAsLong(i);
                if (!resource.isEmpty() && amount > 0) {
                    rate += rate(resource.toStack((int) Math.min(Integer.MAX_VALUE, amount)));
                }
            }
        }
        rate *= share(blockEntity);
        return rate > 0.0 ? new Source(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, rate, owner) : null;
    }

    // A reservoir's every block shows the whole group's contents, so each counts its share (the source spread over
    // the reservoir).
    private static double share(BlockEntity blockEntity) {
        if (blockEntity instanceof ReservoirBlockEntity reservoir) {
            return 1.0 / Math.max(1, reservoir.getGroup().size());
        }
        if (blockEntity instanceof ItemReservoirBlockEntity reservoir) {
            return 1.0 / Math.max(1, reservoir.getStore().members().size());
        }
        return 1.0;
    }
}
