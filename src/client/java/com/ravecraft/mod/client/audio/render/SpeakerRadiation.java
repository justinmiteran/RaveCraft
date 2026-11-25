package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.acoustics.radiation.Cabinets;
import com.ravecraft.mod.core.acoustics.radiation.RadiationPattern;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import java.util.EnumMap;
import java.util.Map;

/**
 * How every kind of speaker radiates, computed once when the client starts, on a thread of its own. Until then, or if
 * that fails, the speakers radiate the same in every direction.
 */
public final class SpeakerRadiation {
	private static volatile Map<SpeakerKind, RadiationPattern> computed = Map.of();

	private SpeakerRadiation() {
	}

	/** Starts computing the patterns. */
	public static void start() {
		Thread thread = new Thread(SpeakerRadiation::compute, "RaveCraft radiation");
		thread.setDaemon(true);
		thread.start();
	}

	private static void compute() {
		try {
			Map<SpeakerKind, RadiationPattern> all = new EnumMap<>(SpeakerKind.class);
			for (SpeakerKind kind : SpeakerKind.values()) {
				all.put(kind, RadiationPattern.of(Cabinets.of(kind)));
			}
			computed = Map.copyOf(all);
		} catch (RuntimeException e) {
			RaveCraft.LOGGER.error("Could not compute how the speakers radiate: they play without direction", e);
		}
	}

	/**
	 * How a kind of speaker radiates.
	 *
	 * @param kind the kind of speaker
	 * @return its pattern, flat until it is computed
	 */
	public static RadiationPattern pattern(SpeakerKind kind) {
		return computed.getOrDefault(kind, RadiationPattern.FLAT);
	}
}
