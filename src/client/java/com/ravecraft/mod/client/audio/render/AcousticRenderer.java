package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.options.RaveCraftOptions;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.Propagation;
import com.ravecraft.mod.core.acoustics.SourceSelector;
import com.ravecraft.mod.core.audio.stream.StreamFormat;
import com.ravecraft.mod.core.audio.stream.StreamPlayout;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
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

	private static final Map<Integer, RenderStream> STREAMS = new ConcurrentHashMap<>();

	// Published by the client and render threads, read by the audio thread.
	private static volatile Vec3 listener = Vec3.ZERO;
	private static volatile float playerVolume = 1f;
	private static volatile int maxSources = 8;
	private static volatile boolean efxWanted = true;
	private static volatile boolean paused;

	private static @Nullable Worker worker;

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

	/** Client thread, every tick: speaker states and the player's volume settings. */
	private static void clientTick(Minecraft minecraft) {
		for (RenderStream stream : STREAMS.values()) {
			stream.refresh(minecraft.level);
		}
		playerVolume = (float) (minecraft.options.getFinalSoundSourceVolume(SoundSource.RECORDS)
				* RaveCraftOptions.volume().get());
		maxSources = RaveCraftOptions.maxSources().get();
		efxWanted = RaveCraftOptions.acousticEffects().get();
	}

	/** The audio thread and everything it owns: sources, scratch buffers. */
	private static final class Worker extends Thread {
		private volatile boolean running = true;
		private final List<AlSource> free = new ArrayList<>();
		/** The streams holding a source, by session id. */
		private final Map<Integer, RenderStream> held = new HashMap<>();
		private final float[] scratch = new float[BLOCK_SAMPLES];
		private final ByteBuffer pcm = BufferUtils.createByteBuffer(BLOCK_SAMPLES * 2);
		private boolean efx;
		private boolean outOfSources;
		private boolean wasPaused;
		private @Nullable AcousticSettings configured;

		Worker() {
			super("RaveCraft audio");
			setDaemon(true);
		}

		void shutDown() {
			running = false;
			LockSupport.unpark(this);
			try {
				join(2_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}

		@Override
		public void run() {
			efx = Efx.detect();
			if (!efx) {
				RaveCraft.LOGGER.warn("OpenAL EFX is not available: speakers play without air absorption");
			}
			try {
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

		private void step(long now) {
			AcousticSettings settings = AcousticSettingsClient.get();
			if (settings != configured) {
				STREAMS.values().forEach(stream -> stream.configure(settings));
				configured = settings;
			}
			for (RenderStream stream : STREAMS.values()) {
				if (stream.eq == null) {
					stream.configure(settings);
				}
			}
			dropDeadStreams();
			if (paused != wasPaused) {
				wasPaused = paused;
				for (RenderStream stream : STREAMS.values()) {
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
			if (paused) {
				return;
			}

			Vec3 ear = listener;
			Map<Integer, Double> gains = new HashMap<>();
			Map<Integer, Double> distances = new HashMap<>();
			for (RenderStream stream : STREAMS.values()) {
				double dx = ear.x - (stream.pos.getX() + 0.5);
				double dy = ear.y - (stream.pos.getY() + 0.5);
				double dz = ear.z - (stream.pos.getZ() + 0.5);
				double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
				double cos = distance < 1e-6 ? 1 : (stream.facingX * dx + stream.facingZ * dz) / distance;
				double gain = stream.powered ? Propagation.gain(settings.rolloff(), settings.fadeFraction(),
						settings.portable(), stream.volume, distance, cos) : 0;
				gains.put(stream.sessionId, gain);
				distances.put(stream.sessionId, distance);
			}

			int max = maxSources;
			Set<Integer> chosen = SourceSelector.select(gains, held.keySet(), max);
			for (RenderStream stream : STREAMS.values()) {
				if (stream.source != null && !chosen.contains(stream.sessionId)) {
					release(stream, now);
				}
			}
			while (free.size() + held.size() > max && !free.isEmpty()) {
				free.removeLast().delete();
			}
			for (RenderStream stream : STREAMS.values()) {
				if (stream.source == null && chosen.contains(stream.sessionId)) {
					stream.source = acquire(max);
					if (stream.source != null) {
						held.put(stream.sessionId, stream);
					}
				}
			}

			float volume = playerVolume;
			boolean filter = efxWanted;
			for (RenderStream stream : STREAMS.values()) {
				AlSource source = stream.source;
				if (source == null) {
					for (int i = stream.pacer.due(now); i > 0; i--) {
						stream.nextBlock(scratch, null);
					}
					continue;
				}
				while (source.wantsBlock()) {
					stream.nextBlock(scratch, pcm);
					source.queue(pcm);
				}
				double distance = distances.get(stream.sessionId);
				source.place(stream.pos.getX() + 0.5, stream.pos.getY() + 0.5, stream.pos.getZ() + 0.5);
				source.gain((float) (gains.get(stream.sessionId) * volume));
				source.highGain(filter ? (float) Propagation.highFrequencyGain(settings.airAbsorptionDbPerBlock(), distance) : 1f);
			}
		}

		/**
		 * Streams whose session ended (or drained) leave; any stream no longer attached (detached from the client
		 * thread or just dropped) gives its source back.
		 */
		private void dropDeadStreams() {
			for (RenderStream stream : List.copyOf(STREAMS.values())) {
				if (!stream.alive.getAsBoolean()) {
					STREAMS.remove(stream.sessionId, stream);
				}
			}
			for (RenderStream stream : List.copyOf(held.values())) {
				if (STREAMS.get(stream.sessionId) != stream) {
					release(stream, 0);
				}
			}
		}

		private @Nullable AlSource acquire(int max) {
			if (!free.isEmpty()) {
				return free.removeLast();
			}
			if (outOfSources || held.size() >= max) {
				return null;
			}
			AlSource source = AlSource.create(efx);
			if (source == null) {
				outOfSources = true;
				RaveCraft.LOGGER.warn("OpenAL has no more sources: speakers are limited to {}", held.size());
			}
			return source;
		}

		private void release(RenderStream stream, long now) {
			AlSource source = stream.source;
			if (source != null) {
				source.reset();
				free.add(source);
				stream.source = null;
				held.remove(stream.sessionId);
				stream.pacer.reset(now);
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
		}
	}
}
