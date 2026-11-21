package com.ravecraft.mod.core.acoustics;

import com.ravecraft.mod.core.speaker.SpeakerKind;

/** The data sheet of every kind of speaker. */
public record DeviceProfiles(DeviceProfile portable, DeviceProfile party) {
	public static final DeviceProfiles DEFAULT = new DeviceProfiles(DeviceProfile.PORTABLE, DeviceProfile.PARTY);

	/** The profile of the given speaker kind. */
	public DeviceProfile of(SpeakerKind kind) {
		return switch (kind) {
			case PORTABLE -> portable;
			case PARTY -> party;
		};
	}
}
