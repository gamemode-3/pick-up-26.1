package net.gamemode3.pickup.inventory;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;

import java.util.Objects;
import java.util.Optional;

public class BundleHelper {
    public static boolean tryAddIntoBundle(ItemStack stack, ItemStack bundleStack) {
        BundleContents bundleContentsComponent = bundleStack.getOrDefault(
                DataComponents.BUNDLE_CONTENTS,
                BundleContents.EMPTY
        );
        BundleContents.Mutable builder = new BundleContents.Mutable(bundleContentsComponent);
        int addedItems = builder.tryInsert(stack);

        bundleStack.set(DataComponents.BUNDLE_CONTENTS, builder.toImmutable());
        //                this.onContentChanged(player);
        return addedItems > 0;
    }

    public static boolean tryStackIntoBundle(ItemStack stack, ItemStack bundleStack) {
        // check if the bundle contains a stack of the same item
        boolean hasSameItem = findItemInBundle(bundleStack, stack) != -1;

        if (hasSameItem) {
            return tryAddIntoBundle(stack, bundleStack);
        }
        return false;
    }

    public static void setSelectedItem(ItemStack stack, int i) {
        int nowSelected = BundleItem.getSelectedItemIndex(stack);
        if (nowSelected == i) {
            return;
        }
        BundleItem.toggleSelectedItem(stack, i);
    }

    public static int findItemInBundle(ItemStack bundleStack, ItemStack stack) {
        int selectedSlot = BundleItem.getSelectedItemIndex(bundleStack);
        for (int i = 0; i < BundleItem.getNumberOfItemsToShow(bundleStack); i++) {
            setSelectedItem(bundleStack, i);
            ItemStackTemplate bundleContent = BundleItem.getSelectedItem(bundleStack);
            assert bundleContent != null;
            if (bundleContent.count() == 0) continue;
            if (ItemStack.isSameItemSameComponents(bundleContent.create(), stack)) {
                setSelectedItem(bundleStack, selectedSlot);
                return i;
            }
        }
        // make sure we don't mess nothin' up innit
        setSelectedItem(bundleStack, selectedSlot);
        return -1;
    }

    public static Optional<Pair<Integer, ItemStack>> tryExtractFromBundle(ItemStack stack, ItemStack bundleStack) {
        int stackIndex = findItemInBundle(bundleStack, stack);
        if (stackIndex == -1) {
            return Optional.empty(); // Item not found in bundle
        }
        int selectedSlot = BundleItem.getSelectedItemIndex(bundleStack);
        setSelectedItem(bundleStack, stackIndex);
        ItemStackTemplate bundleContent = BundleItem.getSelectedItem(bundleStack);
        BundleContents bundleContentsComponent = Objects.requireNonNull(bundleStack.get(DataComponents.BUNDLE_CONTENTS));
        BundleContents.Mutable builder = new BundleContents.Mutable(bundleContentsComponent);
        builder.removeOne();
        bundleStack.set(DataComponents.BUNDLE_CONTENTS, builder.toImmutable());
        if (selectedSlot > stackIndex) selectedSlot--;
        setSelectedItem(bundleStack, selectedSlot);
        assert bundleContent != null;
        return Optional.of(new Pair<>(stackIndex, bundleContent.create()));
    }
}
