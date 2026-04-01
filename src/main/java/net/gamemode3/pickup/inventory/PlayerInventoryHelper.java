package net.gamemode3.pickup.inventory;

import com.mojang.datafixers.util.Pair;
import net.gamemode3.pickup.mixin.PlayerInventoryInvoker;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public class PlayerInventoryHelper {
    public static int OFFHAND_SLOT = 45;

    public static ClientboundSetPlayerInventoryPacket createOffhandSetPacket(Player player) {
        return new ClientboundSetPlayerInventoryPacket(
                PlayerInventoryHelper.OFFHAND_SLOT, player.getOffhandItem()
        );
    }

    public static Optional<Pair<Integer, Pair<Integer, ItemStack>>> tryExtractStackFromContainer(Inventory inventory, ItemStack stack, boolean enableShulkerBox, boolean enableBundle) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack containerStack = inventory.getItem(i);
            if (enableShulkerBox && containerStack.is(ItemTags.SHULKER_BOXES)) {
                Optional<Pair<Integer, ItemStack>> extractedItemInfo = ShulkerBoxHelper.tryExtractFromShulkerBox(stack, containerStack);
                if (extractedItemInfo.isPresent()) {
                    Pair<Integer, ItemStack> extractedItem = extractedItemInfo.get();
                    return Optional.of(new Pair<>(i, extractedItem));
                }
            }
            else if (enableBundle && containerStack.is(ItemTags.BUNDLES)) {
                Optional<Pair<Integer, ItemStack>> extractedItemInfo = BundleHelper.tryExtractFromBundle(stack, containerStack);
                if (extractedItemInfo.isPresent()) {
                    Pair<Integer, ItemStack> extractedItem = extractedItemInfo.get();
                    return Optional.of(new Pair<>(i, extractedItem));
                }
            }
        }
        return Optional.empty();
    }

    public static boolean tryFillEmptySlot(Inventory inventory, ItemStack stack) {
        int emptySlot = inventory.getFreeSlot();
        if (emptySlot != -1) {
            stack.setCount(((PlayerInventoryInvoker) inventory).invokeAddStack(emptySlot, stack));
            return stack.getCount() < stack.getMaxStackSize();
        }
        return false;
    }

    public static Pair<Integer, ItemStack> findHotbarStackToReplace(Inventory playerInventory) {
        for (int i = 0; i < 9; i++) {
            ItemStack hotbarStack = playerInventory.getItem(i);
            if (hotbarStack.isEmpty()) {
                return new Pair<>(i, hotbarStack);
            }
        }
        int selectedSlot = playerInventory.getSelectedSlot();
        ItemStack selectedStack = playerInventory.getItem(selectedSlot);
        return new Pair<>(selectedSlot, selectedStack);
    }

    public static boolean tryPutIntoContainer(Inventory playerInventory, int containerSlotInInventory, int itemSlotInContainer, ItemStack stack) {
        ItemStack containerStack = playerInventory.getItem(containerSlotInInventory);
        if (containerStack.is(ItemTags.SHULKER_BOXES)) {
            return ShulkerBoxHelper.tryPutIntoShulkerBox(stack, containerStack, itemSlotInContainer);
        }
        if (containerStack.is(ItemTags.BUNDLES)) {
            return BundleHelper.tryAddIntoBundle(stack, containerStack);
        }
        return false;
    }
}
