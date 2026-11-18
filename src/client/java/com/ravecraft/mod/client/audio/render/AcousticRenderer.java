package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.options.RaveCraftOptions;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.Propagation;
import com.ravecraft.mod.core.acoustics.RetryCooldown;
import com.ravecraft.mod.core.acoustics.SourceSelector;
import com.ravecraft.mod.core.acoustics.occlusion.ListenerPerception;
import com.ravecraft.mod.core.acoustics.occlusion.LossSmoother;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionGate;
import com.ravecraft.mod.core.acoustics.reverb.Echo;
import com.ravecraft.mod.core.acoustics.reverb.EchoRamp;
import com.ravecraft.mod.core.acoustics.reverb.EchoSettings;
import com.ravecraft.mod.core.acoustics.reverb.ParamGlide;
import com.ravecraft.mod.core.acoustics.reverb.FluidReverb;
import com.ravecraft.mod.core.acoustics.reverb.ReverbParams;
import com.ravecraft.mod.core.acoustics.reverb.SendRoute;
import com.ravecraft.mod.core.audio.stream.StreamFormat;
import com.ravecraft.mod.core.audio.stream.StreamPlayout;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

/**
 * The speakers' audio output: a pool of OpenAL streaming sources owned by RaveCraft, driven by its own thread. Each
 * stream gets a gain from the propagation model; the loudest ones get a source, the others stay virtual (consumed
 * in real time, silently) so they come back at the right position. Follows the vanilla sound engine's lifecycle:
 * sources are deleted before its OpenAL context goes away and recreated when it starts again.
 */
public final class AcousticRenderer {
	private static final long LOOP_NANOS = 10_000_000L;
	private static final int BLOCK_SAMPLES = StreamFormat.CHUNK_SAMPLES;
	private static final long SOURCE_RETRY_MS = 5_000;
	/** A longer gap between two steps (a stall) does not move the occlusion ramps further. */
	private static final double MAX_RAMP_STEP_MS = 100;
	/** The reverb slots glide toward their targets at most this often. */
	private static final long GLIDE_PERIOD_MS = 100;
	/** Reverb slots: the listener's space (L), then two speaker spaces (S1, S2). */
	private static final int REVERB_SLOTS = 3;

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

	private static volatile @Nullable Worker worker;

	private AcousticRenderer() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(AcousticRenderer::clientTick);
	}

	/**
	 * Client thread: starts rendering a stream. A no-op when this playout is already attached; a session that
	 * restarts with a new playout replaces the old one.
	 */
	public static void attach(int sessionId, BlockPos pos, StreamPlayout playout, BooleanSupplier alive) {
		STREAMS.compute(sessionId, (id, old) -> {
			if (old != null && old.playout == playout) {
				return old;
			}
			RenderStream stream = new RenderStream(id, pos, playout, alive, Util.getMillis());
			stream.refresh(Minecraft.getInstance().level);
			return stream;
		});
	}

	public static void detach(int sessionId) {
		STREAMS.remove(sessionId);
	}

	public static void detachAll() {
		STREAMS.clear();
	}

	// --- vanilla sound engine hooks (SoundEngineMixin) ---

	/** Render thread, after the OpenAL context is up. */
	public static synchronized void onEngineStarted() {
		if (worker == null) {
			worker = new Worker();
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

	public static void onPause() {
		paused = true;
	}

	public static void onResume() {
		paused = false;
	}

	public static void onListenerMoved(Vec3 camera) {
		listener = camera;
	}

	/** The attached streams, for the occlusion scheduler (client thread). */
	static Iterable<RenderStream> streams() {
		return STREAMS.values();
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

	/** One consistent set of reverb targets: L, S1, S2 and the echo. */
	private record ReverbTargets(ReverbParams listener, ReverbParams s1, ReverbParams s2, Echo echo,
			EchoSettings echoSettings) {
		static final ReverbTargets SILENT = new ReverbTargets(ReverbParams.SILENT, ReverbParams.SILENT,
				ReverbParams.SILENT, Echo.NONE, EchoSettings.DEFAULT);

		ReverbParams slot(int index) {
			return switch (index) {
				case 1 -> s1;
				case 2 -> s2;
				default -> listener;
			};
		}
	}

	/** The audio thread and everything it owns: sources, scratch buffers. */
	private static final class Worker extends Thread {
		private volatile boolean running = true;
		private final List<AlSource> free = new ArrayList<>();
		/** The streams holding a source, by session id. */
		private final Map<Integer, RenderStream> held = new HashMap<>();
		/** The attached streams, copied once per step so every pass sees the same ones. */
		private final List<RenderStream> streams = new ArrayList<>();
		private final Map<Integer, Double> gains = new HashMap<>();
		private final float[] scratch = new float[BLOCK_SAMPLES];
		private final LossSmoother headLoss = new LossSmoother(RenderStream.OCCLUSION_RAMP_MS);
		private final LossSmoother headHighLoss = new LossSmoother(RenderStream.OCCLUSION_RAMP_MS);
		private final LossSmoother headBlurRamp = new LossSmoother(RenderStream.OCCLUSION_RAMP_MS);
		private final ByteBuffer pcm = BufferUtils.createByteBuffer(BLOCK_SAMPLES * 2);
		private boolean efx;
		private final RetryCooldown sourceRetry = new RetryCooldown(SOURCE_RETRY_MS);
		private boolean warnedOutOfSources;
		private boolean wasPaused;
		private @Nullable AcousticSettings configured;
		/** When the previous step ran, for the occlusion ramps; negative before the first one. */
		private long lastStep = -1;
		/** Auxiliary sends per source (0 without EFX): send 0 carries the reverb, send 1 the echo. */
		private int sends;
		/** L, S1, S2; an entry is null when its slot could not be created. */
		private final Efx.@Nullable Slot[] reverbSlots = new Efx.Slot[REVERB_SLOTS];
		/** Each slot's linear glide toward its target, and the reverb last loaded in the slot. */
		private final ParamGlide[] reverbGlides = {new ParamGlide(ReverbParams.SILENT),
				new ParamGlide(ReverbParams.SILENT), new ParamGlide(ReverbParams.SILENT)};
		private final ReverbParams[] reverbNow = {ReverbParams.SILENT, ReverbParams.SILENT, ReverbParams.SILENT};
		private Efx.@Nullable Slot echoSlot;
		private final EchoRamp echoRamp = new EchoRamp();
		private double echoDelayApplied;
		private double echoGainApplied;
		private long lastEchoMs = -1;
		private EchoSettings echoSettingsNow = EchoSettings.DEFAULT;
		/** When the slots last glided; negative before the first glide. */
		private long lastGlide = -1;
		/** When the game was paused, to hold the glides meanwhile. */
		private long pausedAt;

		Worker() {
			super("RaveCraft audio");
			setDaemon(true);
		}

		void shutDown() {
			running = false;
			LockSupport.unpark(this);
			// Its sources must be gone before the context is destroyed, so wait for it however long a step takes.
			boolean interrupted = false;
			while (isAlive()) {
				try {
					join(2_000);
					if (isAlive()) {
						RaveCraft.LOGGER.warn("Speaker audio thread is slow to stop, still waiting");
					}
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}

		@Override
		public void run() {
			// Streams kept playing out while no worker ran (engine reload): start their clocks afresh.
			long start = Util.getMillis();
			STREAMS.values().forEach(stream -> stream.pacer.reset(start));
			try {
				setUpEfx();
				while (running) {
					try {
						step(Util.getMillis());
					} catch (RuntimeException e) {
						RaveCraft.LOGGER.error("Speaker audio step failed", e);
					}
					LockSupport.parkNanos(LOOP_NANOS);
				}
			} finally {
				releaseAll();
			}
		}

		/** Detects EFX and creates the reverb slots; a failure leaves the speakers without reverb, never stops them. */
		private void setUpEfx() {
			try {
				efx = Efx.detect();
				if (!efx) {
					RaveCraft.LOGGER.warn("OpenAL EFX is not available: speakers play without air absorption or muffled treble");
				} else {
					createSlots();
				}
			} catch (RuntimeException e) {
				// Slots created before the failure are deleted by releaseAll(); without sends none is used.
				sends = 0;
				RaveCraft.LOGGER.error("Could not set up OpenAL EFX reverb: speakers play without reverb", e);
			}
		}

		private void step(long now) {
			dropDeadStreams();
			streams.clear();
			streams.addAll(STREAMS.values());

			AcousticSettings settings = AcousticSettingsClient.get();
			boolean reconfigure = settings != configured;
			configured = settings;
			for (RenderStream stream : streams) {
				if (reconfigure || stream.eq == null) {
					stream.configure(settings);
				}
			}
			if (paused != wasPaused) {
				wasPaused = paused;
				if (paused) {
					pausedAt = now;
				} else if (lastGlide >= 0) {
					// A pause holds the glides where they were.
					long held = Math.max(0, now - pausedAt);
					lastGlide += held;
					for (ParamGlide glide : reverbGlides) {
						glide.shift(held);
					}
				}
				for (RenderStream stream : streams) {
					if (stream.source != null) {
						if (paused) {
							stream.source.pause();
						} else {
							stream.source.resume();
						}
					}
					stream.pacer.reset(now);
				}
			}
			double dt = lastStep < 0 ? 0 : Math.clamp(now - lastStep, 0, MAX_RAMP_STEP_MS);
			lastStep = now;
			if (paused) {
				return;
			}

			Vec3 ear = listener;
			// Diving in or out of water changes everything heard at once, smoothly.
			ListenerPerception perception = head;
			double headLossDb = headLoss.step(perception.lossDb(), dt);
			double headHighLossDb = headHighLoss.step(perception.highLossDb(), dt);
			double headBlur = headBlurRamp.step(perception.blur(), dt);
			gains.clear();
			for (RenderStream stream : streams) {
				double dx = ear.x - (stream.pos.getX() + 0.5);
				double dy = ear.y - (stream.pos.getY() + 0.5);
				double dz = ear.z - (stream.pos.getZ() + 0.5);
				double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
				double cos = distance < 1e-6 ? 1 : (stream.facingX * dx + stream.facingZ * dz) / distance;
				stream.distance = distance;
				stream.gain = stream.powered ? Propagation.gain(settings, settings.portable(),
						stream.volume, distance, cos) : 0;
				boolean known = occlude(stream, dt);
				// Before the selection: the sources go to the speakers that are loudest once muffled. A stream not
				// traced yet ranks 0, gets no source and is consumed silently, in step with its playout.
				stream.gain = OcclusionGate.gain(stream.gain, known, stream.lossDb + headLossDb);
				stream.heardGain = stream.gain;
				gains.put(stream.sessionId, stream.gain);
			}

			int max = maxSources;
			Set<Integer> chosen = SourceSelector.select(gains, held.keySet(), max);
			for (RenderStream stream : streams) {
				if (stream.source != null && !chosen.contains(stream.sessionId)) {
					release(stream, now);
				}
			}
			while (free.size() + held.size() > max && !free.isEmpty()) {
				free.removeLast().delete();
			}
			for (RenderStream stream : streams) {
				// A replaced stream may still hold this session's source until the next step releases it.
				if (stream.source == null && chosen.contains(stream.sessionId) && !held.containsKey(stream.sessionId)) {
					stream.source = acquire(max, now);
					if (stream.source != null) {
						held.put(stream.sessionId, stream);
					}
				}
			}

			float volume = playerVolume;
			boolean filter = efxWanted;
			boolean reverb = efx && sends >= 1 && filter && settings.reverb().enabled();
			glide(reverb ? reverbTargets : ReverbTargets.SILENT, now);
			FluidReverb chorus = efxWanted && settings.reverb().enabled() ? headChorus : null;
			for (RenderStream stream : streams) {
				AlSource source = stream.source;
				if (source == null) {
					for (int i = stream.pacer.due(now); i > 0; i--) {
						stream.nextBlock(scratch, null, filter ? stream.highLossDb + headHighLossDb : 0, chorus);
					}
					continue;
				}
				while (source.wantsBlock()) {
					stream.nextBlock(scratch, pcm, filter ? stream.highLossDb + headHighLossDb : 0, chorus);
					source.queue(pcm);
				}
				source.place(stream.pos.getX() + 0.5, stream.pos.getY() + 0.5, stream.pos.getZ() + 0.5);
				source.gain((float) (stream.gain * volume));
				// The EFX shelf only cuts the top octaves: it carries the air's absorption, while occlusion's treble
				// loss goes through the stream's own low-pass (nextBlock), which reaches down into the mids.
				source.highGain(filter ? (float) Propagation.highFrequencyGain(settings.airAbsorptionDbPerBlock(),
						stream.distance) : 1f);
				source.radius(filter ? (float) (headBlur * stream.distance) : 0f);
				route(stream, source, reverb, dt);
			}
		}

		/** Creates L, S1, S2 and, with a second send, E. A slot that cannot be created is logged once and skipped. */
		private void createSlots() {
			sends = Efx.maxSends();
			if (sends < 1) {
				RaveCraft.LOGGER.warn("OpenAL has no auxiliary sends: speakers play without reverb");
				return;
			}
			int missing = 0;
			for (int i = 0; i < REVERB_SLOTS; i++) {
				reverbSlots[i] = Efx.createReverbSlot();
				if (reverbSlots[i] == null) {
					missing++;
				}
			}
			if (sends >= 2) {
				echoSlot = Efx.createEchoSlot();
				if (echoSlot == null) {
					missing++;
				}
			} else {
				RaveCraft.LOGGER.info("OpenAL has a single auxiliary send: speakers play without distant echoes");
			}
			if (missing > 0) {
				RaveCraft.LOGGER.warn("OpenAL refused {} reverb/echo effect slot(s): those spaces play dry", missing);
			}
		}

		/**
		 * Moves every slot linearly toward its target over {@link ParamGlide#GLIDE_MS}, updated at most every
		 * {@link #GLIDE_PERIOD_MS}; the echo is loaded as soon as it changes.
		 */
		private void glide(ReverbTargets targets, long now) {
			if (lastGlide < 0) {
				lastGlide = now;
			} else if (now - lastGlide >= GLIDE_PERIOD_MS) {
				lastGlide = now;
				for (int i = 0; i < REVERB_SLOTS; i++) {
					Efx.Slot slot = reverbSlots[i];
					ReverbParams next = reverbGlides[i].at(targets.slot(i), now);
					if (slot != null && !next.equals(reverbNow[i])) {
						Efx.applyReverb(slot, next);
					}
					reverbNow[i] = next;
				}
			}
			double dtMs = lastEchoMs < 0 ? 0 : now - lastEchoMs;
			lastEchoMs = now;
			echoRamp.step(targets.echo(), dtMs);
			Efx.Slot echo = echoSlot;
			boolean changed = Math.abs(echoRamp.delaySeconds() - echoDelayApplied) > 1e-4
					|| Math.abs(echoRamp.gain() - echoGainApplied) > 1e-4
					|| !targets.echoSettings().equals(echoSettingsNow);
			if (echo != null && changed) {
				Efx.applyEcho(echo, new Echo(echoRamp.delaySeconds(), echoRamp.gain()), targets.echoSettings());
			}
			if (changed) {
				echoDelayApplied = echoRamp.delaySeconds();
				echoGainApplied = echoRamp.gain();
			}
			echoSettingsNow = targets.echoSettings();
		}

		/**
		 * The stream's sends: send 0 into the reverb slot of its space (faded across slot changes), send 1 into the
		 * echo. All detached while reverb is off, and a send whose slot is missing stays detached.
		 */
		private void route(RenderStream stream, AlSource source, boolean reverb, double dt) {
			if (!efx || sends < 1) {
				return;
			}
			int target = stream.reverbSlot;
			stream.route.step(target >= 0 && target < REVERB_SLOTS ? target : SendRoute.LISTENER, dt);
			if (!reverb) {
				source.detachSends();
				return;
			}
			Efx.Slot slot = reverbSlots[stream.route.slot()];
			source.send(0, slot == null ? 0 : slot.slot(), (float) stream.route.gain());
			if (sends >= 2) {
				Efx.Slot echo = echoSlot;
				source.send(1, echo == null ? 0 : echo.slot(), 1f);
			}
		}

		/**
		 * Smooths the stream's occlusion toward what the scheduler last published, and says whether there is one
		 * (read once: the scheduler may publish meanwhile). None until it has published one, and the first value
		 * published is taken at once (a new stream does not fade from unmuffled).
		 */
		private static boolean occlude(RenderStream stream, double dt) {
			if (!stream.occlusionKnown) {
				stream.lossRamp.reset();
				stream.highRamp.reset();
				stream.lossDb = 0;
				stream.highLossDb = 0;
				return false;
			}
			double loss = stream.occlusionLossDb;
			double high = stream.occlusionHighLossDb;
			stream.lossDb = stream.lossRamp.step(loss, dt);
			stream.highLossDb = stream.highRamp.step(high, dt);
			return true;
		}

		/**
		 * Streams whose session ended (or drained) leave; any stream no longer attached (detached from the client
		 * thread or just dropped) gives its source back.
		 */
		private void dropDeadStreams() {
			STREAMS.values().removeIf(stream -> !stream.alive.getAsBoolean());
			for (Iterator<RenderStream> it = held.values().iterator(); it.hasNext(); ) {
				RenderStream stream = it.next();
				if (STREAMS.get(stream.sessionId) != stream) {
					it.remove();
					recycle(stream);
				}
			}
		}

		/** A free source, or a new one; after the driver refused one, waits a while before asking again. */
		private @Nullable AlSource acquire(int max, long now) {
			if (!free.isEmpty()) {
				return free.removeLast();
			}
			if (held.size() >= max || !sourceRetry.canTry(now)) {
				return null;
			}
			AlSource source = AlSource.create(efx);
			if (source == null) {
				sourceRetry.failed(now);
				if (!warnedOutOfSources) {
					warnedOutOfSources = true;
					RaveCraft.LOGGER.warn("OpenAL has no more sources for now: speakers are limited to {}", held.size());
				}
			}
			return source;
		}

		private void release(RenderStream stream, long now) {
			AlSource source = stream.source;
			if (source != null) {
				// Queued blocks were already taken from the playout: the virtual clock waits them out.
				stream.pacer.resetAfter(now, source.pendingBlocks());
				held.remove(stream.sessionId);
				recycle(stream);
			}
		}

		/** Takes the stream's source back into the free pool. */
		private void recycle(RenderStream stream) {
			AlSource source = stream.source;
			if (source != null) {
				source.reset();
				free.add(source);
				stream.source = null;
			}
		}

		/** Deletes every source while the OpenAL context still exists; streams stay attached. */
		private void releaseAll() {
			for (RenderStream stream : held.values()) {
				if (stream.source != null) {
					stream.source.delete();
					stream.source = null;
				}
			}
			held.clear();
			free.forEach(AlSource::delete);
			free.clear();
			// Every send was detached (by delete) before its slot goes.
			for (int i = 0; i < REVERB_SLOTS; i++) {
				Efx.Slot slot = reverbSlots[i];
				if (slot != null) {
					Efx.deleteSlot(slot);
					reverbSlots[i] = null;
				}
			}
			Efx.Slot echo = echoSlot;
			if (echo != null) {
				Efx.deleteSlot(echo);
				echoSlot = null;
			}
		}
	}
}
