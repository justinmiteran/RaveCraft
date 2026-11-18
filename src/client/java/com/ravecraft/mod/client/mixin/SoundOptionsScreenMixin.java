package com.ravecraft.mod.client.mixin;

import com.ravecraft.mod.client.options.RaveCraftOptions;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the RaveCraft rows at the bottom of Options › Music &amp; Sounds. */
@Mixin(SoundOptionsScreen.class)
abstract class SoundOptionsScreenMixin extends OptionsSubScreen {
	private SoundOptionsScreenMixin(Screen lastScreen, Options options, Component title) {
		super(lastScreen, options, title);
	}

	@Inject(method = "addOptions", at = @At("TAIL"))
	private void ravecraft$addOptions(CallbackInfo ci) {
		if (this.list != null) {
			this.list.addSmall(RaveCraftOptions.volume(), RaveCraftOptions.maxSources());
			this.list.addSmall(RaveCraftOptions.acousticEffects(), RaveCraftOptions.occlusionQuality());
		}
	}
}
