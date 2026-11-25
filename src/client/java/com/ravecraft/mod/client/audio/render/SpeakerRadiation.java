package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.acoustics.radiation.Cabinets;
import com.ravecraft.mod.core.acoustics.radiation.RadiationPattern;
import com.ravecraft.mod.core.acoustics.radiation.RadiationSummary;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import java.util.EnumMap;
import java.util.Map;

/**
 * How every kind of speaker radiates, computed once when the client starts, on a thread of its own. Until then, or if
 * that fails, the speakers radiate the same in every direction.
 */
public final class SpeakerRadiation {
	private static final Radiation FLAT = Radiation.of(RadiationPattern.FLAT);

	private static volatile Map<SpeakerKind, Radiation> computed = Map.of();

	/** A pattern and what the tooltip says of it. */
	private record Radiation(RadiationPattern pattern, RadiationSummary summary) {
		private static Radiation of(RadiationPattern pattern) {
			return new Radiation(pattern, RadiationSummary.of(pattern));
		}
	}

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
			Map<SpeakerKind, Radiation> all = new EnumMap<>(SpeakerKind.class);
			for (SpeakerKind kind : SpeakerKind.values()) {
				all.put(kind, Radiation.of(RadiationPattern.of(Cabinets.of(kind))));
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
		return computed.getOrDefault(kind, FLAT).pattern();
	}

	/**
	 * How a kind of speaker radiates, for its tooltip.
	 *
	 * @param kind the kind of speaker
	 * @return its summary, omnidirectional until it is computed
	 */
	public static RadiationSummary summary(SpeakerKind kind) {
		return computed.getOrDefault(kind, FLAT).summary();
	}
}
