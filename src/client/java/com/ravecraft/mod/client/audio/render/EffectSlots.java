package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.acoustics.reverb.Echo;
import com.ravecraft.mod.core.acoustics.reverb.EchoRamp;
import com.ravecraft.mod.core.acoustics.reverb.EchoSettings;
import com.ravecraft.mod.core.acoustics.reverb.ParamGlide;
import com.ravecraft.mod.core.acoustics.reverb.ReverbParams;
import com.ravecraft.mod.core.acoustics.reverb.SendRoute;
import org.jspecify.annotations.Nullable;

/**
 * The audio thread's EFX effect slots: the reverbs of the listener's space (L) and of two speaker spaces (S1, S2),
 * each gliding toward its target, and the echo (E). Without EFX there is no send and nothing is created.
 */
final class EffectSlots {
	/** Reverb slots: L, then S1 and S2. */
	private static final int REVERB_SLOTS = 3;
	/** The reverb slots glide toward their targets at most this often. */
	private static final long GLIDE_PERIOD_MS = 100;

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

	/** Auxiliary sends per source, 0 without EFX. */
	int sends() {
		return sends;
	}

	/** EFX failed while setting up: no send is used. */
	void disable() {
		sends = 0;
	}

	/** Creates L, S1, S2 and, with a second send, E. A slot that cannot be created is logged once and skipped. */
	void create() {
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

	/** The game paused at {@code now}: the glides hold until it resumes. */
	void pause(long now) {
		pausedAt = now;
	}

	/** The game resumed at {@code now}: the glides go on from where the pause held them. */
	void resume(long now) {
		if (lastGlide < 0) {
			return;
		}
		long held = Math.max(0, now - pausedAt);
		lastGlide += held;
		for (ParamGlide glide : reverbGlides) {
			glide.shift(held);
		}
	}

	/**
	 * Moves every slot linearly toward its target over {@link ParamGlide#GLIDE_MS}, updated at most every
	 * {@link #GLIDE_PERIOD_MS}; the echo is loaded as soon as it changes.
	 */
	void glide(ReverbTargets targets, long now) {
		glideReverbs(targets, now);
		glideEcho(targets, now);
	}

	/** Every {@link #GLIDE_PERIOD_MS}, moves each reverb slot along its glide and loads it when it changed. */
	private void glideReverbs(ReverbTargets targets, long now) {
		if (lastGlide < 0) {
			lastGlide = now;
			return;
		}
		if (now - lastGlide < GLIDE_PERIOD_MS) {
			return;
		}
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

	/** Ramps the echo toward its target and loads it in E when it moved or its settings changed. */
	private void glideEcho(ReverbTargets targets, long now) {
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
	void route(RenderStream stream, AlSource source, boolean reverb, double dt) {
		if (sends < 1) {
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

	/** Deletes every slot; the sends into them must already be detached. */
	void release() {
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
