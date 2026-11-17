package com.ravecraft.mod.client.audio.render;

import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

/** The few EFX calls the renderer needs: a direct low-pass filter per source. Audio thread only. */
final class Efx {
	private Efx() {
	}

	/** Whether the current OpenAL device supports EFX. */
	static boolean detect() {
		long device = ALC10.alcGetContextsDevice(ALC10.alcGetCurrentContext());
		return device != 0 && ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX");
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
		org.lwjgl.openal.AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
	}

	static void delete(int filter) {
		EXTEfx.alDeleteFilters(filter);
	}
}
