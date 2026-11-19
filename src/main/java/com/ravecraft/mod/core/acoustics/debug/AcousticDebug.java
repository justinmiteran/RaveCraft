package com.ravecraft.mod.core.acoustics.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The debug screen (F3) lines of the acoustic engine: speakers, listener, streams and cost. */
public final class AcousticDebug {
	private static final String[] SLOTS = {"L", "S1", "S2"};

	private AcousticDebug() {
	}

	/** The {@code shown} loudest audible speakers, after a summary line. */
	public static List<String> speakers(List<SpeakerRow> rows, int maxSources, int shown) {
		if (rows.isEmpty()) {
			return List.of("RaveCraft speakers: none audible");
		}
		long sounding = rows.stream().filter(SpeakerRow::sounding).count();
		List<String> lines = new ArrayList<>();
		lines.add(format("RaveCraft speakers: %d audible, %d sounding / %d", rows.size(), sounding, maxSources));
		rows.stream().sorted(Comparator.comparingDouble(SpeakerRow::heardGain).reversed()).limit(shown)
				.forEach(r -> lines.add(format("  #%d %.1f m | %s | %s | reverb %s | %s", r.session(), r.distance(),
						r.heardGain() <= 0 ? "silent" : format("%.1f dB", 20 * Math.log10(r.heardGain())),
						r.occlusionKnown() ? format("occlusion %.1f dB, treble +%.1f dB", r.lossDb(), r.highLossDb())
								: "occlusion tracing...",
						SLOTS[Math.clamp(r.reverbSlot(), 0, SLOTS.length - 1)], r.sounding() ? "on" : "culled")));
		return lines;
	}

	public static List<String> listener(ListenerRow r) {
		String head = "RaveCraft listener: head in " + r.medium();
		if (r.lossDb() > 0 || r.highLossDb() > 0 || r.blur() > 0) {
			head += format(" | -%.1f dB, treble -%.1f dB, blur %.2f", r.lossDb(), r.highLossDb(), r.blur());
		}
		if (r.chorus()) {
			head += " | chorus on";
		}
		String state = format("  effects option %s | %s | occlusion %s | reverb %s", r.effectsOption() ? "on" : "off",
				r.efx() ? format("EFX on, %d sends", r.sends()) : "EFX unavailable",
				r.occlusion() ? "on" : "off (server)", r.reverb() ? "on" : "off (server)");
		return List.of(head, state);
	}

	public static List<String> streams(List<StreamRow> rows) {
		List<String> lines = new ArrayList<>();
		lines.add("RaveCraft streams: " + (rows.isEmpty() ? "none" : String.valueOf(rows.size())));
		for (StreamRow r : rows) {
			String line = format("  #%d buffer %d ms | underruns %d | lost %d | %.1f kb/s", r.session(), r.bufferedMs(),
					r.underruns(), r.lostFrames(), r.kbps());
			if (r.title() != null) {
				line += " | " + r.title() + (r.owner() != null ? " (" + r.owner() + ")" : "");
			}
			lines.add(line);
		}
		return lines;
	}

	public static List<String> perf(PerfRow r) {
		String budgets = format("RaveCraft perf: occlusion %d/%d voxels | reverb %d/%d voxels, %d probes running",
				r.occlusionVoxels(), r.occlusionBudget(), r.reverbVoxels(), r.reverbBudget(), r.probesRunning());
		String audio = r.audioRunning()
				? format("  audio step %.2f ms avg, %.2f ms max", r.stepAverageMs(), r.stepMaxMs())
				: "  audio thread stopped";
		return List.of(budgets, audio);
	}

	private static String format(String pattern, Object... args) {
		return String.format(Locale.ROOT, pattern, args);
	}
}
