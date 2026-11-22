package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.client.options.RaveCraftOptions;
import com.ravecraft.mod.core.acoustics.occlusion.ListenerPerception;
import com.ravecraft.mod.core.acoustics.reverb.Echo;
import com.ravecraft.mod.core.acoustics.reverb.EchoSettings;
import com.ravecraft.mod.core.acoustics.reverb.FluidReverb;
import com.ravecraft.mod.core.acoustics.reverb.ReverbParams;
import com.ravecraft.mod.core.audio.stream.StreamPlayout;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The speakers' audio output: a pool of OpenAL streaming sources owned by RaveCraft, driven by its own thread. Each
 * stream gets a gain from the propagation model; the loudest ones get a source, the others stay virtual (consumed
 * in real time, silently) so they come back at the right position. Follows the vanilla sound engine's lifecycle:
 * sources are deleted before its OpenAL context goes away and recreated when it starts again.
 */
public final class AcousticRenderer {
	private static final Map<Integer, RenderStream> STREAMS = new ConcurrentHashMap<>();

	// Published by the client and render threads, read by the audio thread.
	private static volatile Vec3 listener = Vec3.ZERO;
	/** How the medium around the listener's head changes what they hear; published by the occlusion scheduler. */
	private static volatile ListenerPerception head = ListenerPerception.NONE;
	private static volatile float playerVolume = 1f;
	private static volatile int maxSources = 8;
	private static volatile boolean efxWanted = true;
	private static volatile boolean paused;
	/** The fluid reverb whose chorus the head is in, or null; published by the scheduler. */
	private static volatile @Nullable FluidReverb headChorus;
	/** The reverb and echo every slot moves toward; published whole by the reverb scheduler. */
	private static volatile ReverbTargets reverbTargets = ReverbTargets.SILENT;

	private static volatile @Nullable AudioWorker worker;

	// Published by the audio thread for the debug screen.
	private static volatile boolean efxActive;
	private static volatile int efxSends;
	private static volatile double stepAverageMs;
	private static volatile double stepMaxMs;

	private AcousticRenderer() {
	}

	/** Registers the per-tick update of the streams. */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AcousticRenderer::clientTick);
	}

	/**
	 * Client thread: starts rendering a stream. A no-op when this playout is already attached; a session that
	 * restarts with a new playout replaces the old one.
	 *
	 * @param sessionId the stream's session
	 * @param pos where the speaker stands
	 * @param playout the decoded audio to play
	 * @param alive false once the stream must stop
	 */
	public static void attach(int sessionId, UUID speakerId, StreamPlayout playout, BooleanSupplier alive) {
		STREAMS.compute(sessionId, (id, old) -> {
			if (old != null && old.playout == playout) {
				return old;
			}
			RenderStream stream = new RenderStream(id, speakerId, playout, alive, Util.getMillis());
			stream.refresh(Minecraft.getInstance().level);
			return stream;
		});
	}

	/**
	 * Stops rendering session {@code sessionId}.
	 *
	 * @param sessionId the session to stop
	 */
	public static void detach(int sessionId) {
		STREAMS.remove(sessionId);
	}

	/** Stops rendering every session. */
	public static void detachAll() {
		STREAMS.clear();
	}

	// --- vanilla sound engine hooks (SoundEngineMixin) ---

	/** Render thread, after the OpenAL context is up. */
	public static synchronized void onEngineStarted() {
		if (worker == null) {
			worker = new AudioWorker();
			worker.start();
		}
	}

	/** Render thread, before the OpenAL context is destroyed: waits until every source is deleted. */
	public static synchronized void onEngineStopping() {
		if (worker != null) {
			worker.shutDown();
			worker = null;
		}
	}

	/** The game paused: the sources pause too. */
	public static void onPause() {
		paused = true;
	}

	/** The game resumed: the sources play again. */
	public static void onResume() {
		paused = false;
	}

	/**
	 * The listener is now at {@code camera}.
	 *
	 * @param camera the camera position
	 */
	public static void onListenerMoved(Vec3 camera) {
		listener = camera;
	}

	/** The attached streams, for the occlusion scheduler (client thread). */
	static Iterable<RenderStream> streams() {
		return STREAMS.values();
	}

	/**
	 * Client thread: the gain each attached stream is heard at, by session id (0: silent or not traced yet).
	 *
	 * @return the heard gain of each session
	 */
	public static Map<Integer, Double> heard() {
		Map<Integer, Double> gains = new HashMap<>();
		for (RenderStream stream : STREAMS.values()) {
			gains.put(stream.sessionId, stream.heardGain);
		}
		return gains;
	}

	/** Client thread: the perception of the medium the listener's head is in. */
	static void headPerception(ListenerPerception perception) {
		head = perception;
	}

	/** Client thread: the fluid the listener's head is in, whose chorus the streams apply (null for none). */
	static void headChorus(@Nullable FluidReverb fluid) {
		headChorus = fluid;
	}

	/**
	 * Client thread: what the listener reverb, the two speaker-space reverbs and the echo should become. Published
	 * as one snapshot, so the audio thread never mixes two updates.
	 */
	static void reverbTargets(ReverbParams listener, ReverbParams s1, ReverbParams s2, Echo echo,
			EchoSettings echoSettings) {
		reverbTargets = new ReverbTargets(listener, s1, s2, echo, echoSettings);
	}

	/** Where the listener was last placed. */
	static Vec3 listener() {
		return listener;
	}

	/** The perception of the medium around the head, as last published. */
	static ListenerPerception head() {
		return head;
	}

	/** The fluid whose chorus the streams apply, or null. */
	static @Nullable FluidReverb chorusFluid() {
		return headChorus;
	}

	static int maxSources() {
		return maxSources;
	}

	/** Whether the audio thread runs, and what it found and measured (debug screen). */
	static boolean audioRunning() {
		return worker != null;
	}

	static boolean efxActive() {
		return efxActive;
	}

	static int efxSends() {
		return efxSends;
	}

	static double stepAverageMs() {
		return stepAverageMs;
	}

	static double stepMaxMs() {
		return stepMaxMs;
	}

	/** The attached streams by session id, for the audio thread. */
	static Map<Integer, RenderStream> attached() {
		return STREAMS;
	}

	static float playerVolume() {
		return playerVolume;
	}

	/** Whether the player wants the acoustic effects (filters, reverb, chorus). */
	static boolean efxWanted() {
		return efxWanted;
	}

	static boolean paused() {
		return paused;
	}

	/** The reverb and echo targets last published. */
	static ReverbTargets targets() {
		return reverbTargets;
	}

	/** Audio thread: whether EFX runs, and with how many sends per source. */
	static void publishEfx(boolean active, int sends) {
		efxActive = active;
		efxSends = sends;
	}

	/** Audio thread: the average and longest step times, in ms. */
	static void publishStepTimes(double averageMs, double maxMs) {
		stepAverageMs = averageMs;
		stepMaxMs = maxMs;
	}

	/** Client thread, every tick: speaker states and the player's volume settings. */
	private static void clientTick(Minecraft minecraft) {
		// Also done by the audio thread, which may never run (no sound device): ended streams must not pile up.
		STREAMS.values().removeIf(stream -> !stream.alive.getAsBoolean());
		for (RenderStream stream : STREAMS.values()) {
			stream.refresh(minecraft.level);
		}
		playerVolume = (float) (minecraft.options.getFinalSoundSourceVolume(SoundSource.RECORDS)
				* RaveCraftOptions.volume().get());
		maxSources = RaveCraftOptions.maxSources().get();
		efxWanted = RaveCraftOptions.acousticEffects().get();
	}
}
