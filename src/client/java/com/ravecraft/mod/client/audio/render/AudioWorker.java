package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.Propagation;
import com.ravecraft.mod.core.acoustics.RetryCooldown;
import com.ravecraft.mod.core.acoustics.SourceSelector;
import com.ravecraft.mod.core.acoustics.debug.StepTimer;
import com.ravecraft.mod.core.acoustics.occlusion.ListenerPerception;
import com.ravecraft.mod.core.acoustics.occlusion.LossSmoother;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionGate;
import com.ravecraft.mod.core.acoustics.reverb.FluidReverb;
import com.ravecraft.mod.core.audio.stream.StreamFormat;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.LockSupport;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;

/**
 * The audio thread of {@link AcousticRenderer} and everything it owns: the OpenAL sources, the effect slots and the
 * scratch buffers. Every step weighs the attached streams, gives the loudest ones a source and feeds them.
 */
final class AudioWorker extends Thread {
	private static final long LOOP_NANOS = 10_000_000L;
	private static final int BLOCK_SAMPLES = StreamFormat.CHUNK_SAMPLES;
	private static final long SOURCE_RETRY_MS = 5_000;
	/** A longer gap between two steps (a stall) does not move the occlusion ramps further. */
	private static final double MAX_RAMP_STEP_MS = 100;

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
	private final StepTimer stepTimer = new StepTimer();
	private final EffectSlots slots = new EffectSlots();
	private boolean efx;
	private final RetryCooldown sourceRetry = new RetryCooldown(SOURCE_RETRY_MS);
	private boolean warnedOutOfSources;
	private boolean wasPaused;
	private @Nullable AcousticSettings configured;
	/** When the previous step ran, for the occlusion ramps; negative before the first one. */
	private long lastStep = -1;
	/** The medium around the head, smoothed: its loss, its treble loss and its blur, for the current step. */
	private double headLossDb;
	private double headHighLossDb;
	private double headBlur;

	/** What every stream of one step is rendered with. */
	private record Mix(AcousticSettings settings, long now, double dt, float volume, boolean filter, boolean reverb,
			@Nullable FluidReverb chorus) {
	}

	AudioWorker() {
		super("RaveCraft audio");
		setDaemon(true);
	}

	/** Stops the worker and waits until it deleted every source. */
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
		AcousticRenderer.attached().values().forEach(stream -> stream.pacer.reset(start));
		try {
			setUpEfx();
			while (running) {
				timedStep();
				LockSupport.parkNanos(LOOP_NANOS);
			}
		} finally {
			releaseAll();
			AcousticRenderer.publishEfx(false, 0);
		}
	}

	/** One step, timed for the debug screen; a failing step is logged and the next one runs anyway. */
	private void timedStep() {
		long began = System.nanoTime();
		try {
			step(Util.getMillis());
		} catch (RuntimeException e) {
			RaveCraft.LOGGER.error("Speaker audio step failed", e);
		}
		stepTimer.record(System.nanoTime() - began, Util.getMillis());
		AcousticRenderer.publishStepTimes(stepTimer.averageMs(), stepTimer.maxMs());
	}

	/** Detects EFX and creates the reverb slots; a failure leaves the speakers without reverb, never stops them. */
	private void setUpEfx() {
		try {
			efx = Efx.detect();
			if (!efx) {
				RaveCraft.LOGGER.warn("OpenAL EFX is not available: speakers play without air absorption or muffled treble");
			} else {
				slots.create();
			}
		} catch (RuntimeException e) {
			// Slots created before the failure are deleted by releaseAll(); without sends none is used.
			slots.disable();
			RaveCraft.LOGGER.error("Could not set up OpenAL EFX reverb: speakers play without reverb", e);
		}
	}

	/** One pass of the worker: updates the source of each live stream. */
	private void step(long now) {
		dropDeadStreams();
		streams.clear();
		streams.addAll(AcousticRenderer.attached().values());
		AcousticSettings settings = AcousticSettingsClient.get();
		configure(settings);
		boolean paused = AcousticRenderer.paused();
		if (paused != wasPaused) {
			togglePause(paused, now);
		}
		double dt = lastStep < 0 ? 0 : Math.clamp(now - lastStep, 0, MAX_RAMP_STEP_MS);
		lastStep = now;
		if (paused) {
			return;
		}
		stepHead(dt);
		weigh(settings, dt);
		assignSources(now);
		AcousticRenderer.publishEfx(efx, slots.sends());
		Mix mix = mix(settings, now, dt);
		slots.glide(mix.reverb() ? AcousticRenderer.targets() : ReverbTargets.SILENT, now);
		for (RenderStream stream : streams) {
			render(stream, mix);
		}
	}

	/** Configures the new streams, and every stream when the settings changed. */
	private void configure(AcousticSettings settings) {
		boolean reconfigure = settings != configured;
		configured = settings;
		for (RenderStream stream : streams) {
			if (reconfigure || stream.eq == null) {
				stream.configure(settings);
			}
		}
	}

	/** The game paused or resumed: the sources and the glides hold, and the clocks start afresh. */
	private void togglePause(boolean paused, long now) {
		wasPaused = paused;
		if (paused) {
			slots.pause(now);
		} else {
			slots.resume(now);
		}
		for (RenderStream stream : streams) {
			pause(stream.source, paused);
			stream.pacer.reset(now);
		}
	}

	private static void pause(@Nullable AlSource source, boolean paused) {
		if (source == null) {
			return;
		}
		if (paused) {
			source.pause();
		} else {
			source.resume();
		}
	}

	/** Diving in or out of water changes everything heard at once, smoothly. */
	private void stepHead(double dt) {
		ListenerPerception perception = AcousticRenderer.head();
		headLossDb = headLoss.step(perception.lossDb(), dt);
		headHighLossDb = headHighLoss.step(perception.highLossDb(), dt);
		headBlur = headBlurRamp.step(perception.blur(), dt);
	}

	/** The gain of every stream at the ear, muffled, for the selection of the sources. */
	private void weigh(AcousticSettings settings, double dt) {
		Vec3 ear = AcousticRenderer.listener();
		gains.clear();
		for (RenderStream stream : streams) {
			weigh(stream, settings, ear, dt);
		}
	}

	/** The gain of {@code stream} at {@code ear}: its propagation, then its occlusion and the head's. */
	private void weigh(RenderStream stream, AcousticSettings settings, Vec3 ear, double dt) {
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

	/** Gives the loudest streams a source and takes it back from the others. */
	private void assignSources(long now) {
		int max = AcousticRenderer.maxSources();
		Set<Integer> chosen = SourceSelector.select(gains, held.keySet(), max);
		for (RenderStream stream : streams) {
			if (stream.source != null && !chosen.contains(stream.sessionId)) {
				release(stream, now);
			}
		}
		while (free.size() + held.size() > max && !free.isEmpty()) {
			free.removeLast().delete();
		}
		acquireChosen(chosen, max, now);
		for (RenderStream stream : streams) {
			stream.sounding = stream.source != null;
		}
	}

	/** A source for each chosen stream without one. */
	private void acquireChosen(Set<Integer> chosen, int max, long now) {
		for (RenderStream stream : streams) {
			// A replaced stream may still hold this session's source until the next step releases it.
			if (stream.source == null && chosen.contains(stream.sessionId) && !held.containsKey(stream.sessionId)) {
				acquireFor(stream, max, now);
			}
		}
	}

	private void acquireFor(RenderStream stream, int max, long now) {
		stream.source = acquire(max, now);
		if (stream.source != null) {
			held.put(stream.sessionId, stream);
		}
	}

	/** What the streams of this step are rendered with: the player's volume, the filters, reverb and chorus. */
	private Mix mix(AcousticSettings settings, long now, double dt) {
		boolean filter = AcousticRenderer.efxWanted();
		boolean enabled = settings.reverb().enabled();
		boolean reverb = efx && slots.sends() >= 1 && filter && enabled;
		FluidReverb chorus = filter && enabled ? AcousticRenderer.chorusFluid() : null;
		return new Mix(settings, now, dt, AcousticRenderer.playerVolume(), filter, reverb, chorus);
	}

	/** Feeds {@code stream}'s source and places it, or consumes its due blocks silently when it has none. */
	private void render(RenderStream stream, Mix mix) {
		AlSource source = stream.source;
		double highLossDb = mix.filter() ? stream.highLossDb + headHighLossDb : 0;
		if (source == null) {
			for (int i = stream.pacer.due(mix.now()); i > 0; i--) {
				stream.nextBlock(scratch, null, highLossDb, mix.chorus());
			}
			return;
		}
		while (source.wantsBlock()) {
			stream.nextBlock(scratch, pcm, highLossDb, mix.chorus());
			source.queue(pcm);
		}
		source.place(stream.pos.getX() + 0.5, stream.pos.getY() + 0.5, stream.pos.getZ() + 0.5);
		source.gain((float) (stream.gain * mix.volume()));
		// The EFX shelf only cuts the top octaves: it carries the air's absorption, while occlusion's treble
		// loss goes through the stream's own low-pass (nextBlock), which reaches down into the mids.
		source.highGain(mix.filter() ? (float) Propagation.highFrequencyGain(mix.settings().airAbsorptionDbPerBlock(),
				stream.distance) : 1f);
		source.radius(mix.filter() ? (float) (headBlur * stream.distance) : 0f);
		slots.route(stream, source, mix.reverb(), mix.dt());
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
		Map<Integer, RenderStream> attached = AcousticRenderer.attached();
		attached.values().removeIf(stream -> !stream.alive.getAsBoolean());
		for (Iterator<RenderStream> it = held.values().iterator(); it.hasNext(); ) {
			RenderStream stream = it.next();
			if (attached.get(stream.sessionId) != stream) {
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
		slots.release();
	}
}
