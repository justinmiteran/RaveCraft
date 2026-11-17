package com.ravecraft.mod.client.mixin;

import com.ravecraft.mod.client.audio.render.AcousticRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ties the speaker renderer to the vanilla sound engine: its sources live in the engine's OpenAL context, so they are
 * deleted before the context is destroyed (reload, device change, shutdown) and recreated once it is back. Pause,
 * resume and the camera position follow the engine too.
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
	@Shadow
	private boolean loaded;

	@Inject(method = "destroy", at = @At("HEAD"))
	private void ravecraft$beforeDestroy(CallbackInfo ci) {
		AcousticRenderer.onEngineStopping();
	}

	@Inject(method = "emergencyShutdown", at = @At("HEAD"))
	private void ravecraft$beforeEmergencyShutdown(CallbackInfo ci) {
		AcousticRenderer.onEngineStopping();
	}

	@Inject(method = "loadLibrary", at = @At("TAIL"))
	private void ravecraft$afterLoad(CallbackInfo ci) {
		if (loaded) {
			AcousticRenderer.onEngineStarted();
		}
	}

	@Inject(method = "pauseAllExcept", at = @At("HEAD"))
	private void ravecraft$pause(SoundSource[] ignoredSources, CallbackInfo ci) {
		AcousticRenderer.onPause();
	}

	@Inject(method = "resume", at = @At("HEAD"))
	private void ravecraft$resume(CallbackInfo ci) {
		AcousticRenderer.onResume();
	}

	@Inject(method = "updateSource", at = @At("TAIL"))
	private void ravecraft$listener(Camera camera, CallbackInfo ci) {
		if (camera.isInitialized()) {
			AcousticRenderer.onListenerMoved(camera.position());
		}
	}
}
