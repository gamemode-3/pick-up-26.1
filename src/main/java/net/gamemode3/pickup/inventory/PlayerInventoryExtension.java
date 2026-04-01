package net.gamemode3.pickup.inventory;

import net.minecraft.world.item.Item;

public interface PlayerInventoryExtension {
    Item pick_up$getGhostItem(int slot);

    boolean pick_up$setGhostItem(int slot, Item item);
}