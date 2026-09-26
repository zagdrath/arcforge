package net.zagdrath.arcforge.transfer.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;

// Item slots with a per-slot filter. The filter applies to players and automation;
// the owning machine bypasses it through set() (e.g. to fill an output slot).
public class FilteredItemHandler extends ItemStacksResourceHandler {
    @FunctionalInterface
    public interface SlotFilter {
        boolean test(int index, ItemResource resource);
    }

    private final SlotFilter filter;
    private final Runnable onChanged;

    public FilteredItemHandler(int size, SlotFilter filter, Runnable onChanged) {
        super(size);
        this.filter = filter;
        this.onChanged = onChanged;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return filter.test(index, resource);
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }

    public ItemStack getStack(int index) {
        return stacks.get(index);
    }

    public void setStack(int index, ItemStack stack) {
        set(index, ItemResource.of(stack), stack.getCount());
    }
}
