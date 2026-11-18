package com.ravecraft.mod.core.acoustics.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class AcousticDebugTest {
	@Test
	void speakersAreListedLoudestFirstAndCapped() {
		List<SpeakerRow> rows = List.of(
				new SpeakerRow(3, 20.04, 0.01, false, true, 6, 12, 1),
				new SpeakerRow(7, 4.2, 0.5, true, true, 0, 0, 0),
				new SpeakerRow(9, 30, 0, false, false, 0, 0, 0));
		assertEquals(List.of(
				"RaveCraft speakers: 3 audible, 1 sounding / 8",
				"  #7 4.2 m | -6.0 dB | occlusion 0.0 dB, treble +0.0 dB | reverb L | on",
				"  #3 20.0 m | -40.0 dB | occlusion 6.0 dB, treble +12.0 dB | reverb S1 | culled"),
				AcousticDebug.speakers(rows, 8, 2));
		assertEquals("  #9 30.0 m | silent | occlusion tracing... | reverb L | culled",
				AcousticDebug.speakers(rows, 8, 3).get(3));
	}

	@Test
	void noSpeaker() {
		assertEquals(List.of("RaveCraft speakers: none audible"), AcousticDebug.speakers(List.of(), 8, 4));
	}

	@Test
	void listenerSaysWhatIsOnAndWhatTheHeadHears() {
		ListenerRow row = new ListenerRow("water", 6, 30, 0.8, true, true, true, 2, true, true);
		assertEquals(List.of(
				"RaveCraft listener: head in water | -6.0 dB, treble -30.0 dB, blur 0.80 | chorus on",
				"  effects option on | EFX on, 2 sends | occlusion on | reverb on"),
				AcousticDebug.listener(row));
		ListenerRow air = new ListenerRow("air", 0, 0, 0, false, false, false, 0, false, true);
		assertEquals(List.of(
				"RaveCraft listener: head in air",
				"  effects option off | EFX unavailable | occlusion off (server) | reverb on"),
				AcousticDebug.listener(air));
	}

	@Test
	void streamsShowTheirHealth() {
		List<StreamRow> rows = List.of(new StreamRow(7, 980, 2, 1, 63.84, "Track", "Alex"));
		assertEquals(List.of(
				"RaveCraft streams: 1",
				"  #7 buffer 980 ms | underruns 1 | lost 2 | 63.8 kb/s | Track (Alex)"),
				AcousticDebug.streams(rows));
		assertEquals("  #7 buffer 980 ms | underruns 1 | lost 2 | 63.8 kb/s",
				AcousticDebug.streams(List.of(new StreamRow(7, 980, 2, 1, 63.84, null, null))).get(1));
	}

	@Test
	void perfShowsBudgetsAndAudioStep() {
		PerfRow row = new PerfRow(1200, 20000, 3000, 3000, 2, 0.42, 1.9, true);
		assertEquals(List.of(
				"RaveCraft perf: occlusion 1200/20000 voxels | reverb 3000/3000 voxels, 2 probes running",
				"  audio step 0.42 ms avg, 1.90 ms max"),
				AcousticDebug.perf(row));
		assertEquals("  audio thread stopped",
				AcousticDebug.perf(new PerfRow(0, 5000, 0, 1500, 0, 0, 0, false)).get(1));
	}
}
