package com.ravecraft.mod.client.mixin;

import com.ravecraft.mod.client.ModTabTooltip;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla skips the blue tab name inside a category tab; show it for RaveCraft items everywhere in creative. */
@Mixin(CreativeModeInventoryScreen.class)
abstract class CreativeModeInventoryScreenMixin {
	@Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
	private void ravecraft$addModTab(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
		cir.setReturnValue(ModTabTooltip.withModTab(stack, cir.getReturnValue()));
	}
}
