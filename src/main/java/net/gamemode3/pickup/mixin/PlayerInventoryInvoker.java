package net.gamemode3.pickup.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Inventory.class)
public interface PlayerInventoryInvoker {
    @Invoker("addResource")
    int invokeAddStack(int slot, ItemStack stack);
}
