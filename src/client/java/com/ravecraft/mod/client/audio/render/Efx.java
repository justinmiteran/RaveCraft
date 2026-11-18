package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.core.acoustics.reverb.Echo;
import com.ravecraft.mod.core.acoustics.reverb.EchoSettings;
import com.ravecraft.mod.core.acoustics.reverb.ReverbParams;
import org.jspecify.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

/**
 * The few EFX calls the renderer needs: a direct low-pass filter per source, reverb and echo auxiliary effect slots,
 * and per-source auxiliary sends. Audio thread only.
 */
final class Efx {
	private static final float MAX_ECHO_DELAY = EXTEfx.AL_ECHO_MAX_DELAY;

	private Efx() {
	}

	/** An auxiliary effect slot and the effect loaded in it; {@code eax} when the effect is an EAX reverb. */
	record Slot(int slot, int effect, boolean eax) {
	}

	private static long device() {
		return ALC10.alcGetContextsDevice(ALC10.alcGetCurrentContext());
	}

	/** Whether the current OpenAL device supports EFX. */
	static boolean detect() {
		long device = device();
		return device != 0 && ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX");
	}

	/** How many auxiliary sends each source has on the current device; 0 without EFX. */
	static int maxSends() {
		long device = device();
		if (device == 0 || !ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {
			return 0;
		}
		return Math.max(0, ALC10.alcGetInteger(device, EXTEfx.ALC_MAX_AUXILIARY_SENDS));
	}

	/** A low-pass filter letting everything through, or 0 if it could not be created. */
	static int createLowpass() {
		int filter = EXTEfx.alGenFilters();
		if (filter == 0) {
			return 0;
		}
		EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
		EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, 1f);
		EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, 1f);
		return filter;
	}

	/** Sets the filter's high-frequency gain and (re)attaches it, since a source keeps a copy of its filter. */
	static void apply(int source, int filter, float gainHf) {
		EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, gainHf);
		AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
	}

	/**
	 * Routes the source's auxiliary send {@code index} into {@code slot} through {@code filter} (its copy is taken
	 * now). Slot 0 ({@code AL_EFFECTSLOT_NULL}) detaches the send.
	 */
	static void send(int source, int index, int slot, int filter) {
		AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, index, filter);
	}

	/** Sets the overall gain of a send's low-pass filter. */
	static void sendGain(int filter, float gain) {
		EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
	}

	static void delete(int filter) {
		EXTEfx.alDeleteFilters(filter);
	}

	/**
	 * A silent reverb slot: an EAX reverb, or a plain reverb when the driver does not take that type. Null if the
	 * slot or the effect could not be created.
	 */
	static @Nullable Slot createReverbSlot() {
		int effect = genEffect();
		if (effect == 0) {
			return null;
		}
		// The AL error state is shared with vanilla's sound thread: read the type back instead.
		boolean eax = setType(effect, EXTEfx.AL_EFFECT_EAXREVERB);
		if (!eax && !setType(effect, EXTEfx.AL_EFFECT_REVERB)) {
			EXTEfx.alDeleteEffects(effect);
			AL10.alGetError();
			return null;
		}
		Slot slot = slotFor(effect, eax);
		if (slot != null) {
			applyReverb(slot, ReverbParams.SILENT);
		}
		return slot;
	}

	/** A silent echo slot, or null if the slot or the effect could not be created. */
	static @Nullable Slot createEchoSlot() {
		int effect = genEffect();
		if (effect == 0) {
			return null;
		}
		if (!setType(effect, EXTEfx.AL_EFFECT_ECHO)) {
			EXTEfx.alDeleteEffects(effect);
			AL10.alGetError();
			return null;
		}
		Slot slot = slotFor(effect, false);
		if (slot != null) {
			applyEcho(slot, Echo.NONE, EchoSettings.DEFAULT);
		}
		return slot;
	}

	/** Loads {@code params} in the slot's reverb; a plain reverb takes the same fields minus the EAX-only ones. */
	static void applyReverb(Slot slot, ReverbParams params) {
		int e = slot.effect();
		if (slot.eax()) {
			EXTEfx.alEffectf(e, EXTEfx.AL_EAXREVERB_DENSITY, (float) params.density());
			EXTEfx.alEffectf(e, EXTEfx.AL_EAXREVERB_DIFFUSION, (float) params.diffusion());
			EXTEfx.alEffectf(e, EXTEfx.AL_EAXREVERB_GAIN, (float) params.gain());
			EXTEfx.alEffectf(e, EXTEfx.AL_EAXREVERB_DECAY_TIME, (float) params.decayTime());
			EXTEfx.alEffectf(e, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, (float) params.decayHfRatio());
			EXTEfx.alEffectf(e, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, (float) params.reflectionsGain());
			EXTEfx.alEffectf(e, EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, (float) params.reflectionsDelay());
			EXTEfx.alEffectf(e, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, (float) params.lateReverbDelay());
			EXTEfx.alEffectf(e, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, (float) params.airAbsorptionGainHf());
		} else {
			EXTEfx.alEffectf(e, EXTEfx.AL_REVERB_DENSITY, (float) params.density());
			EXTEfx.alEffectf(e, EXTEfx.AL_REVERB_DIFFUSION, (float) params.diffusion());
			EXTEfx.alEffectf(e, EXTEfx.AL_REVERB_GAIN, (float) params.gain());
			// Plain reverb caps the decay HF ratio like EAX does (0.1..2); the rest shares the EAX bounds.
			EXTEfx.alEffectf(e, EXTEfx.AL_REVERB_DECAY_TIME, (float) params.decayTime());
			EXTEfx.alEffectf(e, EXTEfx.AL_REVERB_DECAY_HFRATIO, (float) params.decayHfRatio());
			EXTEfx.alEffectf(e, EXTEfx.AL_REVERB_REFLECTIONS_GAIN, (float) params.reflectionsGain());
			EXTEfx.alEffectf(e, EXTEfx.AL_REVERB_REFLECTIONS_DELAY, (float) params.reflectionsDelay());
			EXTEfx.alEffectf(e, EXTEfx.AL_REVERB_LATE_REVERB_DELAY, (float) params.lateReverbDelay());
			EXTEfx.alEffectf(e, EXTEfx.AL_REVERB_AIR_ABSORPTION_GAINHF, (float) params.airAbsorptionGainHf());
		}
		// The slot keeps a copy of its effect: load it again.
		EXTEfx.alAuxiliaryEffectSloti(slot.slot(), EXTEfx.AL_EFFECTSLOT_EFFECT, e);
	}

	/** Loads the echo in the slot; the slot's gain is the echo's gain. */
	static void applyEcho(Slot slot, Echo echo, EchoSettings settings) {
		int e = slot.effect();
		EXTEfx.alEffectf(e, EXTEfx.AL_ECHO_DELAY, (float) Math.clamp(echo.delaySeconds(), 0, MAX_ECHO_DELAY));
		EXTEfx.alEffectf(e, EXTEfx.AL_ECHO_LRDELAY, 0f);
		EXTEfx.alEffectf(e, EXTEfx.AL_ECHO_DAMPING, (float) Math.clamp(settings.damping(), 0, 0.99));
		EXTEfx.alEffectf(e, EXTEfx.AL_ECHO_FEEDBACK, (float) Math.clamp(settings.feedback(), 0, 1));
		EXTEfx.alEffectf(e, EXTEfx.AL_ECHO_SPREAD, -1f);
		EXTEfx.alAuxiliaryEffectSloti(slot.slot(), EXTEfx.AL_EFFECTSLOT_EFFECT, e);
		EXTEfx.alAuxiliaryEffectSlotf(slot.slot(), EXTEfx.AL_EFFECTSLOT_GAIN, (float) Math.clamp(echo.gain(), 0, 1));
	}

	/** Empties and deletes the slot, then its effect. */
	static void deleteSlot(Slot slot) {
		EXTEfx.alAuxiliaryEffectSloti(slot.slot(), EXTEfx.AL_EFFECTSLOT_EFFECT, EXTEfx.AL_EFFECT_NULL);
		EXTEfx.alDeleteAuxiliaryEffectSlots(slot.slot());
		EXTEfx.alDeleteEffects(slot.effect());
		AL10.alGetError();
	}

	private static int genEffect() {
		int effect = EXTEfx.alGenEffects();
		if (effect == 0 || !EXTEfx.alIsEffect(effect)) {
			AL10.alGetError();
			return 0;
		}
		return effect;
	}

	private static boolean setType(int effect, int type) {
		EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, type);
		return EXTEfx.alGetEffecti(effect, EXTEfx.AL_EFFECT_TYPE) == type;
	}

	private static @Nullable Slot slotFor(int effect, boolean eax) {
		int slot = EXTEfx.alGenAuxiliaryEffectSlots();
		if (slot == 0 || !EXTEfx.alIsAuxiliaryEffectSlot(slot)) {
			EXTEfx.alDeleteEffects(effect);
			AL10.alGetError();
			return null;
		}
		return new Slot(slot, effect, eax);
	}
}
