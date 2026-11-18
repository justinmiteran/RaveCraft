package com.ravecraft.mod.client.mixin;

import com.ravecraft.mod.client.options.RaveCraftOptions;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loads and saves the RaveCraft sound options in {@code options.txt}, next to the vanilla ones. */
@Mixin(Options.class)
abstract class OptionsMixin {
	@Inject(method = "processOptions", at = @At("TAIL"))
	private void ravecraft$processOptions(Options.FieldAccess access, CallbackInfo ci) {
		RaveCraftOptions.process(access);
	}
}
