package com.ravecraft.mod.config;

import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.DeviceProfiles;

/** The {@code profiles} section of the commented settings file. */
final class ProfilesText {
	private ProfilesText() {
	}

	/** The section as JSON text, ending with a blank line. */
	static String section(DeviceProfiles profiles) {
		DeviceProfile p = profiles.portable();
		DeviceProfile q = profiles.party();
		return """
				  // Each device is described by its data sheet; its range is computed from it:
				  //   level at 1 m = sensitivityDb + 10 x log10(powerWatts)
				  //   range = distance where that level, falling with "rolloff", reaches "audibilityThresholdDb"
				  // Four times the power doubles the range (with rolloff 1). The speaker's volume scales it down.
				  "profiles": {
				    "portable": {
				      // Amplifier power, in watts. Bounds: 0.1 to 100000.
				      "powerWatts": %s,

				      // Sound level 1 m in front of the speaker for 1 W (dB SPL). Bounds: 70 to 115.
				      "sensitivityDb": %s,

				      // Bass shelf: gain applied below "bassCutHz", in dB. Negative = less bass (small drivers).
				      // 0 keeps the bass intact. Bounds: -24 to 0.
				      "bassCutDb": %s,

				      // Frequency under which the bass shelf applies, in Hz. Bounds: 40 to 500.
				      "bassCutHz": %s
				    },

				    // The party speaker: the same settings as the portable one. It is also directional (full level
				    // within 60 degrees of its axis, 10 dB less straight behind), which is not configurable.
				    "party": {
				      "powerWatts": %s,
				      "sensitivityDb": %s,
				      "bassCutDb": %s,
				      "bassCutHz": %s
				    }
				  },

				""".formatted(AcousticSettingsJson.n(p.powerWatts()), AcousticSettingsJson.n(p.sensitivityDb()),
				AcousticSettingsJson.n(p.bassCutDb()), AcousticSettingsJson.n(p.bassCutHz()),
				AcousticSettingsJson.n(q.powerWatts()), AcousticSettingsJson.n(q.sensitivityDb()),
				AcousticSettingsJson.n(q.bassCutDb()), AcousticSettingsJson.n(q.bassCutHz()));
	}
}
