package com.ravecraft.mod.core.acoustics.radiation;

import com.ravecraft.mod.core.speaker.SpeakerKind;
import java.util.ArrayList;
import java.util.List;

/** The cabinet of every kind of speaker. */
public final class Cabinets {
	private static final int PORTABLE_SIDES = 4;
	private static final double FULL_TURN_DEG = 360;
	/** The portable speaker: a small woofer and a tweeter on each of its four sides. */
	private static final Cabinet PORTABLE = new Cabinet(0.875, 0.5625, 0.5625, portableDrivers());
	/** The party speaker: a woofer under a tweeter, both to the front. */
	private static final Cabinet PARTY = new Cabinet(0.75, 1, 0.62, List.of(
			new Driver(0, 0, 0, -0.25, 0, 0.13, 0, 1800),
			new Driver(0, 0, 0, 0.25, 0, 0.025, 1800, 0)));

	private Cabinets() {
	}

	/**
	 * The cabinet of {@code kind}.
	 *
	 * @param kind the kind of speaker
	 * @return its cabinet
	 */
	public static Cabinet of(SpeakerKind kind) {
		return switch (kind) {
			case PORTABLE -> PORTABLE;
			case PARTY -> PARTY;
		};
	}

	private static List<Driver> portableDrivers() {
		List<Driver> drivers = new ArrayList<>();
		for (int side = 0; side < PORTABLE_SIDES; side++) {
			double azimuth = side * FULL_TURN_DEG / PORTABLE_SIDES;
			drivers.add(new Driver(azimuth, 0, 0, 0, 0, 0.04, 0, 2500));
			drivers.add(new Driver(azimuth, 0, 0, 0, 0, 0.0125, 2500, 0));
		}
		return drivers;
	}
}
