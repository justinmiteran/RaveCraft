package com.ravecraft.mod.client.mixin;

import com.ravecraft.mod.client.debug.SoundViewEntries;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * F3 + U cycles the sound view, with the same "[Debug]:" feedback as vanilla's debug keys; not under reduced debug
 * info.
 */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
	@Shadow
	@Final
	private Minecraft minecraft;

	@Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
	private void ravecraft$cycleSoundView(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
		// Under reduced debug info the entries are hidden and nothing would be drawn: leave the key to vanilla, as
		// F3 + B and F3 + G do.
		if (SoundViewEntries.key().matches(event) && !minecraft.showOnlyReducedInfo()) {
			Component message = Component.empty()
					.append(Component.translatable("debug.prefix").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
					.append(CommonComponents.SPACE)
					.append(SoundViewEntries.cycle());
			minecraft.showDebugChat(message);
			cir.setReturnValue(true);
		}
	}
}
