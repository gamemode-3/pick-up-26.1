package net.gamemode3.pickup.mixin;

import com.mojang.datafixers.util.Pair;
import net.gamemode3.pickup.config.ModConfig;
import net.gamemode3.pickup.inventory.PlayerInventoryHelper;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow public ServerPlayer player;

    @Unique
    private ServerGamePacketListenerImpl self() {
        return (ServerGamePacketListenerImpl)(Object)this;
    }

    @Inject(method="tryPickItem", at=@At("HEAD"), cancellable = true)
    private void onPickItem(ItemStack itemStack, CallbackInfo ci) {
        if (itemStack.isItemEnabled(this.player.level().enabledFeatures())) {
            Inventory playerInventory = this.player.getInventory();
            int i = playerInventory.findSlotMatchingItem(itemStack);
            if (i != -1) {
                if (Inventory.isHotbarSlot(i)) {
                    playerInventory.setSelectedSlot(i);
                } else {
                    playerInventory.pickSlot(i);
                }
            } else {
                boolean enableShulkerBox = ModConfig.getPickFromShulkerBox();
                boolean enableBundle = ModConfig.getPickFromBundle();
                Optional<Pair<Integer, Pair<Integer, ItemStack>>> extractedItemInfo = PlayerInventoryHelper.tryExtractStackFromContainer(playerInventory,
                    itemStack, enableShulkerBox, enableBundle);

                if (extractedItemInfo.isPresent()) {
                    Pair<Integer, Pair<Integer, ItemStack>> extractedItem = extractedItemInfo.get();
                    int containerSlotInInventory = extractedItem.getFirst();
                    Pair<Integer, ItemStack> containerItem = extractedItem.getSecond();
                    int itemSlotInContainer = containerItem.getFirst();
                    ItemStack extractedItemStack = containerItem.getSecond().copy();

                    Pair<Integer, ItemStack> hotbarReplaceInfo = PlayerInventoryHelper.findHotbarStackToReplace(playerInventory);
                    Integer hotbarReplaceIndex = hotbarReplaceInfo.getFirst();
                    ItemStack previousHotbarStack = hotbarReplaceInfo.getSecond();

                    if (previousHotbarStack.isEmpty()) {
                        playerInventory.setItem(hotbarReplaceIndex, extractedItemStack);
                        playerInventory.setSelectedSlot(hotbarReplaceIndex);
                    } else {
                        boolean success = PlayerInventoryHelper.tryFillEmptySlot(playerInventory, previousHotbarStack);
                        if (!success) {
                            success = PlayerInventoryHelper.tryPutIntoContainer(playerInventory, containerSlotInInventory, itemSlotInContainer, previousHotbarStack);
                        }
                        if (!success) {
                            if (!PlayerInventoryHelper.tryPutIntoContainer(playerInventory, containerSlotInInventory, itemSlotInContainer, extractedItemStack)) {
                                // Can't put the item back where it came from, that should not happen
                                throw new RuntimeException("Failed to put item back into container: " + extractedItemStack);
                            }
                            if (this.player.isCreative()) {
                                playerInventory.addAndPickItem(itemStack);
                            }

                        }
                        else {
                            playerInventory.setItem(hotbarReplaceIndex, extractedItemStack);
                            playerInventory.setSelectedSlot(hotbarReplaceIndex);
                        }
                    }

                } else if (this.player.isCreative()) {
                    playerInventory.addAndPickItem(itemStack);
                }
            }

            self().send(new ClientboundSetHeldSlotPacket(playerInventory.getSelectedSlot()));
            this.player.inventoryMenu.broadcastChanges();
            ci.cancel();
        }
    }
}
