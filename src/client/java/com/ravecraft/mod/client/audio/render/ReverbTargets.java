package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.core.acoustics.reverb.Echo;
import com.ravecraft.mod.core.acoustics.reverb.EchoSettings;
import com.ravecraft.mod.core.acoustics.reverb.ReverbParams;

/** One consistent set of reverb targets: the listener's space L, the speaker spaces S1 and S2, and the echo. */
record ReverbTargets(ReverbParams listener, ReverbParams s1, ReverbParams s2, Echo echo, EchoSettings echoSettings) {
	/** Every space dry, and no echo. */
	static final ReverbTargets SILENT = new ReverbTargets(ReverbParams.SILENT, ReverbParams.SILENT,
			ReverbParams.SILENT, Echo.NONE, EchoSettings.DEFAULT);

	/** The reverb of slot {@code index}: 1 is S1, 2 is S2, anything else L. */
	ReverbParams slot(int index) {
		return switch (index) {
			case 1 -> s1;
			case 2 -> s2;
			default -> listener;
		};
	}
}
