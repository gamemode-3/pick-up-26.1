package net.gamemode3.pickup.inventory;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ShulkerBoxHelper {
    public static boolean tryAddIntoShulkerBox(ItemStack stack, ItemStack shulkerBoxStack) {
        if (ShulkerBoxHelper.tryStackIntoShulkerBox(stack, shulkerBoxStack)) {
            return true;
        }

        if (stack.is(ItemTags.SHULKER_BOXES)) {
            return false;
        }

        ItemContainerContents containerComponent = shulkerBoxStack.get(
                DataComponents.CONTAINER
        );
        if (containerComponent == null) {
            return false;
        }

        List<ItemStack> stacks = new ArrayList<>(containerComponent.allItemsCopyStream().toList());
        if (stacks.size() < 27) {
            for (int i = stacks.size(); i < 27; i++) {
                stacks.add(ItemStack.EMPTY);
            }
        }
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack storedStack = stacks.get(i);
            if (!storedStack.isEmpty()) continue;

            stacks.set(i, stack.copy());
            stack.setCount(0);

            ItemContainerContents newContainerComponent = ItemContainerContents.fromItems(stacks);
            shulkerBoxStack.set(DataComponents.CONTAINER, newContainerComponent);
            return true;
        }
        return false;
    }

    public static boolean tryStackIntoShulkerBox(ItemStack stack, ItemStack shulkerBoxStack) {
        ItemContainerContents containerComponent = shulkerBoxStack.get(
            DataComponents.CONTAINER
        );
        if (containerComponent == null) {
            return false;
        }

        List<ItemStack> stacks = containerComponent.allItemsCopyStream().toList();
        for (ItemStack storedStack : stacks) {
            if (storedStack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(storedStack, stack)) continue;

            int freeSpace = storedStack.getMaxStackSize() - storedStack.getCount();
            if (!(freeSpace > 0)) continue;

            int amountToAdd = Math.min(freeSpace, stack.getCount());
            storedStack.setCount(storedStack.count() + amountToAdd);
            stack.setCount(stack.count() - amountToAdd);
            shulkerBoxStack.set(DataComponents.CONTAINER, containerComponent);

            ItemContainerContents newContainerComponent = ItemContainerContents.fromItems(stacks);
            shulkerBoxStack.set(DataComponents.CONTAINER, newContainerComponent);
            return true;
        }
        return false;
    }

    public static Optional<Pair<Integer, ItemStack>> tryExtractFromShulkerBox(ItemStack stack, ItemStack shulkerBoxStack) {
        ItemContainerContents containerComponent = shulkerBoxStack.get(
            DataComponents.CONTAINER
        );
        if (containerComponent == null) {
            return Optional.empty();
        }

        List<ItemStack> stacks = new ArrayList<>(containerComponent.allItemsCopyStream().toList());

        for (int i = 0; i < stacks.size(); i++) {
            ItemStack storedStack = stacks.get(i);
            if (storedStack.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(storedStack, stack)) continue;

            ItemStack removedStack = storedStack.copy();
            stacks.set(i, ItemStack.EMPTY);

            ItemContainerContents newContainerComponent = ItemContainerContents.fromItems(stacks);
            shulkerBoxStack.set(DataComponents.CONTAINER, newContainerComponent);

            return Optional.of(new Pair<>(i, removedStack));
        }
        return Optional.empty();
    }

    public static boolean tryPutIntoShulkerBox(ItemStack stack, ItemStack shulkerBoxStack, int slot) {
        ItemContainerContents containerComponent = shulkerBoxStack.get(DataComponents.CONTAINER);
        if (containerComponent == null) {
            return false;
        }

        List<ItemStack> stacks = new ArrayList<>(containerComponent.allItemsCopyStream().toList());
        if (stacks.size() <= slot) {
            for (int i = stacks.size(); i <= slot; i++) {
                stacks.add(ItemStack.EMPTY);
            }
        }
        stacks.set(slot, stack.copy());
        shulkerBoxStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stacks));
        return true;
    }
}