package net.gamemode3.pickup.mixin;

import com.mojang.datafixers.util.Pair;
import net.gamemode3.pickup.config.ModConfig;
import net.gamemode3.pickup.inventory.ContainerHelper;
import net.gamemode3.pickup.inventory.PlayerInventoryExtension;
import net.gamemode3.pickup.inventory.PlayerInventoryHelper;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory; // add ".world"
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(Inventory.class)
public abstract class PlayerInventoryMixin implements PlayerInventoryExtension {
    @Shadow
    @Final
    public Player player;

    @Shadow
    protected abstract int addResource(ItemStack itemStack);

    @Shadow
    public abstract int getFreeSlot();

    @Shadow
    @Final
    private NonNullList<ItemStack> items;

    @Shadow
    public abstract void setItem(int slot, final ItemStack itemStack);

    @Shadow
    public abstract ItemStack getItem(int slot);

    @Shadow
    public abstract int getSelectedSlot();

    @Shadow
    public abstract ItemStack getSelectedItem();

    @Shadow
    public abstract ClientboundSetPlayerInventoryPacket createInventoryUpdatePacket(int slot);

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void insertStack(int slot, ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (itemStack.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }
        try {
            int initialStackCount = itemStack.getCount();
            if (slot != -1) {
                Thread.dumpStack();
                ItemStack slotStack = this.getItem(slot);
                if (slotStack.isEmpty()) {
                    this.setItem(slot, itemStack.copy());
                    itemStack.setCount(0);
                    cir.setReturnValue(true);
                    return;
                }
                if (!ItemStack.isSameItemSameComponents(slotStack, itemStack)) {
                    cir.setReturnValue(false);
                    return;
                }
                int freeSpace = slotStack.getMaxStackSize() - slotStack.getCount();
                int amountToAdd = Math.min(freeSpace, itemStack.getCount());
                slotStack.setCount(slotStack.count() + amountToAdd);
                itemStack.setCount(itemStack.count() - amountToAdd);
                cir.setReturnValue(itemStack.getCount() < initialStackCount);
                return;
            }

            boolean stackChanged = true;
            while (!itemStack.isEmpty() && stackChanged) {
                int previousCount = itemStack.getCount();
                itemStack.setCount(this.addResource(itemStack));
                stackChanged = itemStack.getCount() < previousCount;
            }

            if (!stackChanged && this.player.isCreative()) {
                itemStack.setCount(0);
                cir.setReturnValue(true);
                return;
            }
            cir.setReturnValue(stackChanged);
        } catch (Throwable e) {
            CrashReport crashReport = CrashReport.forThrowable(e, "Adding item to inventory");
            CrashReportCategory crashReportSection = crashReport.addCategory("Item being added");
            crashReportSection.setDetail("Item ID", Item.getId(itemStack.getItem()));
            crashReportSection.setDetail("Item data", itemStack.getDamageValue());
            crashReportSection.setDetail("Item name", (() -> itemStack.getHoverName().getString()));
            throw new ReportedException(crashReport);
        }
    }

    @Inject(method = "addResource(Lnet/minecraft/world/item/ItemStack;)I", at = @At("HEAD"), cancellable = true)
    private void addResource(ItemStack itemStack, CallbackInfoReturnable<Integer> cir) {
        Pair<Integer, Integer> result = addStackGetSlot(itemStack);
        cir.setReturnValue(result.getFirst());
    }

    /**
     * @param stack The stack to add to the inventory.
     * @return A pair where the first element is the remaining stack count
     * and the second element is the slot index where the stack was added.
     * If the slot index is -1, it means the stack was added to the off-hand.
     */
    @Unique
    private Pair<Integer, Integer> addStackGetSlot(ItemStack stack) {
        Optional<Integer> stackingInfo;
        if (ModConfig.getAlwaysStackIntoEquippedContainer()) {
            stackingInfo = tryStackIntoEquippedContainer(stack);
            if (stackingInfo.isPresent()) {
                return new Pair<>(stack.getCount(), stackingInfo.get());
            }
        }
        if (ModConfig.getAlwaysPickUpIntoEquippedContainer()) {
            stackingInfo = tryPickUpIntoEquippedContainer(stack);
            if (stackingInfo.isPresent()) {
                return new Pair<>(stack.getCount(), stackingInfo.get());
            }
        }

        stackingInfo = tryStackIntoInventory(stack);
        if (stackingInfo.isPresent()) {
            return new Pair<>(stack.getCount(), stackingInfo.get());
        }

        if (ModConfig.getStackIntoContainers()) {
            stackingInfo = tryStackIntoContainer(stack);
            if (stackingInfo.isPresent()) {
                return new Pair<>(stack.getCount(), stackingInfo.get());
            }
        }

        stackingInfo = tryFillEmptySlot(stack);
        if (stackingInfo.isPresent()) {
            return new Pair<>(stack.getCount(), stackingInfo.get());
        }

        if (ModConfig.getPickUpIntoContainers()) {
            stackingInfo = tryPickUpIntoContainer(stack);
            if (stackingInfo.isPresent()) {
                return new Pair<>(stack.getCount(), stackingInfo.get());
            }
        }

        return new Pair<>(stack.getCount(), -2); // No slots available
    }

    @Inject(method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V", at = @At("HEAD"), cancellable = true)
    private void offer(ItemStack itemStack, boolean shouldSendSetSlotPacket, CallbackInfo ci) {
        boolean stackChanged = true;
        while (!itemStack.isEmpty() && stackChanged) {
            int previousCount = itemStack.getCount();
            Pair<Integer, Integer> result = this.addStackGetSlot(itemStack);


            itemStack.setCount(result.getFirst());
            stackChanged = itemStack.getCount() < previousCount;
            if (stackChanged && shouldSendSetSlotPacket && this.player instanceof ServerPlayer serverPlayerEntity) {
                int slot = result.getFirst();
                if (slot >= 0) {
                    serverPlayerEntity.connection.send(this.createInventoryUpdatePacket(slot));
                } else if (slot == -1) {
                    serverPlayerEntity.connection.send(PlayerInventoryHelper.createOffhandSetPacket(this.player));
                }
            }
        }

        if (!stackChanged && this.player.isCreative()) {
            itemStack.setCount(0);
            return;
        }

        if (!itemStack.isEmpty()) {
            this.player.drop(itemStack, false);
        }

        ci.cancel();
    }

    @Unique
    private Optional<Integer> tryStackIntoEquippedContainer(ItemStack stack) {
        boolean enableShulkers = ModConfig.getAlwaysStackIntoEquippedShulkerBox();
        boolean enableBundles = ModConfig.getAlwaysStackIntoEquippedBundle();

        ItemStack mainHandStack = this.getSelectedItem();
        if (ContainerHelper.tryStackIntoContainer(stack, mainHandStack, enableShulkers, enableBundles)) {
            int mainHandSlot = this.getSelectedSlot();
            return Optional.of(mainHandSlot);
        }
        ItemStack offHandStack = this.player.getOffhandItem();
        if (ContainerHelper.tryStackIntoContainer(stack, offHandStack, enableShulkers, enableBundles)) {
            return Optional.of(-1);
        }
        return Optional.empty();
    }

    @Unique
    private Optional<Integer> tryPickUpIntoEquippedContainer(ItemStack stack) {
        boolean enableShulkers = ModConfig.getAlwaysPickUpIntoEquippedShulkerBox();
        boolean enableBundles = ModConfig.getAlwaysPickUpIntoEquippedBundle();

        ItemStack mainHandStack = this.getSelectedItem();
        if (ContainerHelper.tryPickUpIntoContainer(stack, mainHandStack, enableShulkers, enableBundles)) {
            int mainHandSlot = this.getSelectedSlot();
            return Optional.of(mainHandSlot);
        }

        ItemStack offHandStack = this.player.getOffhandItem();
        if (ContainerHelper.tryPickUpIntoContainer(stack, offHandStack, enableShulkers, enableBundles)) {
            return Optional.of(-1);
        }
        return Optional.empty();
    }

    @Unique
    private Optional<Integer> tryPickUpIntoContainer(ItemStack stack) {
        boolean enableShulkers = ModConfig.getPickUpIntoShulkerBox();
        boolean enableBundles = ModConfig.getPickUpIntoBundle();

        ItemStack mainHandStack = this.getSelectedItem();
        if (ContainerHelper.tryPickUpIntoContainer(stack, mainHandStack, enableShulkers, enableBundles)) {
            return Optional.of(this.getSelectedSlot());
        }

        ItemStack offHandStack = this.player.getOffhandItem();
        if (ContainerHelper.tryPickUpIntoContainer(stack, offHandStack, enableShulkers, enableBundles)) return Optional.of(-1);

        for (int i = 0; i < this.items.size(); i++) {
            ItemStack containerStack = this.items.get(i);
            if (ContainerHelper.tryPickUpIntoContainer(stack, containerStack, enableShulkers, enableBundles)) return Optional.of(i);
        }

        return Optional.empty();
    }

    @Unique
    private Optional<Integer> tryStackIntoInventory(ItemStack stack) {
        ItemStack selectedStack = this.getSelectedItem();
        if (addStackToOther(stack, selectedStack)) {
            return Optional.of(this.getSelectedSlot());
        }
        ItemStack offHandStack = this.player.getOffhandItem();
        if (addStackToOther(stack, offHandStack)) {
            return Optional.of(-1);
        }

        for (int i = 0; i < this.items.size(); i++) {
            ItemStack existingStack = this.items.get(i);
            if (!addStackToOther(stack, existingStack)) continue;
            this.items.set(i, existingStack);
            return Optional.of(i);
        }
        return Optional.empty();
    }

    @Unique
    private static boolean addStackToOther(ItemStack stack, ItemStack existingStack) {
        if (existingStack.isEmpty()) return false;

        if (!ItemStack.isSameItemSameComponents(stack, existingStack)) {
            return false;
        }
        int freeSpace = existingStack.getMaxStackSize() - existingStack.getCount();
        if (freeSpace <= 0) {
            return false;
        }
        int amountToAdd = Math.min(freeSpace, stack.getCount());
        existingStack.setCount(existingStack.count() + amountToAdd);
        stack.setCount(stack.count() - amountToAdd);
        return true;
    }

    @Unique
    private Optional<Integer> tryFillEmptySlot(ItemStack stack) {
        int emptySlot = this.getFreeSlot();
        if (emptySlot != -1) {
            this.setItem(emptySlot, stack.copy());
            stack.setCount(0);
            return Optional.of(emptySlot);
        }
        return Optional.empty();
    }

    @Unique
    private Optional<Integer> tryStackIntoContainer(ItemStack stack) {
        boolean enableShulkers = ModConfig.getStackIntoShulkerBox();
        boolean enableBundles = ModConfig.getStackIntoBundle();

        ItemStack mainHandStack = this.getSelectedItem();
        if (ContainerHelper.tryStackIntoContainer(stack, mainHandStack, enableShulkers, enableBundles)) {
            return Optional.of(this.getSelectedSlot());
        }

        ItemStack offHandStack = this.player.getOffhandItem();
        if (ContainerHelper.tryStackIntoContainer(stack, offHandStack, enableShulkers, enableBundles)) {
            return Optional.of(-1);
        }

        for (int i = 0; i < this.items.size(); i++) {
            ItemStack containerStack = this.items.get(i);
            if (ContainerHelper.tryStackIntoContainer(stack, containerStack, enableShulkers, enableBundles)) {
                return Optional.of(i);
            }
        }

        return Optional.empty();
    }

    // ====== GHOST SLOTS ======

    @Unique
    private final List<Item> ghostSlots = NonNullList.withSize(Inventory.INVENTORY_SIZE, Items.COAL);

    @Override
    public Item pick_up$getGhostItem(int slot) {
        if (slot < 0 || slot >= ghostSlots.size()) {
            return Items.AIR;
        }
        return ghostSlots.get(slot);
    }

    public boolean pick_up$setGhostItem(int slot, Item item) {
        if (slot < 0 || slot >= ghostSlots.size()) {
            return false;
        }
        ghostSlots.set(slot, item);
        return true;
    }
}