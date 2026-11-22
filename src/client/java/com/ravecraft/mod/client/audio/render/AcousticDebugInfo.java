package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.blockentity.SpeakerBlockEntity;
import com.ravecraft.mod.client.audio.ClientSpeakerHosts;
import com.ravecraft.mod.client.audio.occlusion.WorldAcousticGrid;
import com.ravecraft.mod.client.options.RaveCraftOptions;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.debug.AcousticDebug;
import com.ravecraft.mod.core.acoustics.debug.ListenerRow;
import com.ravecraft.mod.core.acoustics.debug.PerfRow;
import com.ravecraft.mod.core.acoustics.debug.SpeakerRow;
import com.ravecraft.mod.core.acoustics.debug.StreamRow;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.ListenerPerception;
import com.ravecraft.mod.core.acoustics.reverb.FluidReverb;
import com.ravecraft.mod.core.audio.stream.PlayoutStats;
import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.network.payload.SpeakerHostPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Gathers what the acoustic engine knows into the debug screen lines. Client thread only. */
public final class AcousticDebugInfo {
	/** Speakers listed one by one under the summary line. */
	private static final int SHOWN_SPEAKERS = 4;
	/** The bit rate is measured over at least this long. */
	private static final long RATE_WINDOW_MS = 1_000;

	/** Per session: the bytes and time of the last rate sample, and the rate it gave. */
	private static final Map<Integer, Rate> RATES = new HashMap<>();

	private AcousticDebugInfo() {
	}

	/**
	 * The debug lines about the speakers heard.
	 *
	 * @return one line per speaker heard
	 */
	public static List<String> speakers() {
		AcousticSettings settings = AcousticSettingsClient.get();
		Vec3 ear = AcousticRenderer.listener();
		List<SpeakerRow> rows = new ArrayList<>();
		for (RenderStream stream : AcousticRenderer.streams()) {
			if (!OcclusionScheduler.isAudible(settings, stream, ear)) {
				continue;
			}
			double distance = ear.distanceTo(new Vec3(stream.x, stream.y, stream.z));
			rows.add(new SpeakerRow(stream.sessionId, distance, stream.heardGain, stream.sounding,
					stream.occlusionKnown, stream.occlusionLossDb, stream.occlusionHighLossDb, stream.reverbSlot));
		}
		return AcousticDebug.speakers(rows, AcousticRenderer.maxSources(), SHOWN_SPEAKERS);
	}

	/**
	 * The debug lines about the listener.
	 *
	 * @return the lines about the listener
	 */
	public static List<String> listener() {
		AcousticSettings settings = AcousticSettingsClient.get();
		WorldAcousticGrid grid = OcclusionScheduler.grid();
		String medium = "?";
		if (grid != null) {
			AcousticMedium head = grid.headMedium(AcousticRenderer.listener());
			medium = (head != null ? head : grid.defaultMedium()).name();
		}
		ListenerPerception p = AcousticRenderer.head();
		FluidReverb chorus = AcousticRenderer.chorusFluid();
		return AcousticDebug.listener(new ListenerRow(medium, p.lossDb(), p.highLossDb(), p.blur(),
				chorus != null && chorus.chorus(), RaveCraftOptions.acousticEffects().get(),
				AcousticRenderer.efxActive(), AcousticRenderer.efxSends(), settings.occlusion().enabled(),
				settings.reverb().enabled()));
	}

	/**
	 * The debug lines about the received streams.
	 *
	 * @return one line per received stream
	 */
	public static List<String> streams() {
		long now = Util.getMillis();
		ClientLevel level = Minecraft.getInstance().level;
		List<RenderStream> streams = new ArrayList<>();
		AcousticRenderer.streams().forEach(streams::add);
		streams.sort(Comparator.comparingInt(stream -> stream.sessionId));
		List<StreamRow> rows = new ArrayList<>();
		Map<Integer, Rate> seen = new HashMap<>();
		for (RenderStream stream : streams) {
			PlayoutStats stats = stream.playout.stats();
			Rate rate = RATES.get(stream.sessionId);
			if (rate == null || stats.receivedBytes() < rate.bytes) {
				rate = new Rate(stats.receivedBytes(), now, 0);
			} else if (now - rate.ms >= RATE_WINDOW_MS) {
				double kbps = (stats.receivedBytes() - rate.bytes) * 8.0 / (now - rate.ms);
				rate = new Rate(stats.receivedBytes(), now, kbps);
			}
			seen.put(stream.sessionId, rate);
			Optional<QueueEntry> track = current(level, stream);
			rows.add(new StreamRow(stream.sessionId, stats.bufferedMs(), stats.lostFrames(), stats.underruns(),
					rate.kbps, track.map(QueueEntry::title).orElse(null), track.map(QueueEntry::ownerName).orElse(null)));
		}
		RATES.clear();
		RATES.putAll(seen);
		return AcousticDebug.streams(rows);
	}

	/**
	 * The debug lines about the engine's cost.
	 *
	 * @return the lines about the engine's cost
	 */
	public static List<String> perf() {
		return AcousticDebug.perf(new PerfRow(OcclusionScheduler.lastVoxels(), OcclusionScheduler.lastBudget(),
				ReverbScheduler.lastVoxels(), ReverbScheduler.lastBudget(), ReverbScheduler.probesRunning(),
				AcousticRenderer.stepAverageMs(), AcousticRenderer.stepMaxMs(), AcousticRenderer.audioRunning()));
	}

	/** The track the speaker of {@code stream} is playing, when it is a block whose block entity knows it. */
	private static Optional<QueueEntry> current(@Nullable ClientLevel level, RenderStream stream) {
		Optional<BlockPos> block = ClientSpeakerHosts.get(stream.speakerId).flatMap(SpeakerHostPayload::block);
		if (level != null && block.isPresent() && level.getBlockEntity(block.get()) instanceof SpeakerBlockEntity speaker
				&& speaker.data().id().equals(stream.speakerId)) {
			SpeakerQueue queue = speaker.data().queue();
			return queue.current().flatMap(queue::entry);
		}
		return Optional.empty();
	}

	/** A rate sample: the bytes received and the time then, and the rate in kbit/s. */
	private record Rate(long bytes, long ms, double kbps) {
	}
}
