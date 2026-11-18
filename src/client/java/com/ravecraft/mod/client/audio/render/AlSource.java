package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.core.audio.stream.StreamFormat;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jspecify.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.EXTSourceRadius;

/**
 * One OpenAL streaming source with its own small ring of buffers (4 × 100 ms), an optional EFX low-pass filter and
 * up to two auxiliary sends (reverb, echo).
 * World-positioned, no OpenAL distance attenuation: the gain comes from RaveCraft's propagation model. Audio
 * thread only.
 */
final class AlSource {
	private static final int BUFFERS = 4;
	private static final int AL_DISTANCE_MODEL = 0xD000;
	/** Auxiliary sends this class drives: 0 the reverb, 1 the echo. */
	private static final int SENDS = 2;

	private final int source;
	private final int[] buffers;
	private final int filter;
	/** Whether the driver has AL_EXT_SOURCE_RADIUS (OpenAL Soft does): without it, no blur of direction. */
	private final boolean radius;
	private final Deque<Integer> free = new ArrayDeque<>();
	private boolean paused;
	private float lastHighGain = -1;
	private float lastRadius = 0;
	/** Per auxiliary send: its low-pass filter (only its overall gain is used; 0 if none), slot and gain. */
	private final int[] sendFilters = new int[SENDS];
	private final int[] sendSlots = new int[SENDS];
	private final float[] sendGains = new float[SENDS];

	private AlSource(int source, int[] buffers, int filter, boolean radius) {
		this.source = source;
		this.buffers = buffers;
		this.filter = filter;
		this.radius = radius;
		for (int buffer : buffers) {
			free.add(buffer);
		}
	}

	/** A new source, or null when the driver has none left. */
	static @Nullable AlSource create(boolean efx) {
		// The AL error state is shared with vanilla's sound thread: check the names themselves instead.
		int source = AL10.alGenSources();
		if (source == 0 || !AL10.alIsSource(source)) {
			return null;
		}
		int[] buffers = new int[BUFFERS];
		AL10.alGenBuffers(buffers);
		for (int buffer : buffers) {
			if (buffer == 0 || !AL10.alIsBuffer(buffer)) {
				for (int created : buffers) {
					if (created != 0 && AL10.alIsBuffer(created)) {
						AL10.alDeleteBuffers(created);
					}
				}
				AL10.alDeleteSources(source);
				AL10.alGetError();
				return null;
			}
		}
		AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
		AL10.alSourcei(source, AL_DISTANCE_MODEL, AL10.AL_NONE);
		AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0f);
		AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
		int filter = efx ? Efx.createLowpass() : 0;
		boolean radius = AL10.alIsExtensionPresent("AL_EXT_SOURCE_RADIUS");
		AL10.alGetError();
		return new AlSource(source, buffers, filter, radius);
	}

	/** Blocks queued but not played yet. */
	int pendingBlocks() {
		int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
		for (int i = 0; i < processed; i++) {
			free.add(AL10.alSourceUnqueueBuffers(source));
		}
		return BUFFERS - free.size();
	}

	/** Stops, detaches the sends and empties the queue, ready for another stream. */
	void reset() {
		detachSends();
		AL10.alSourceStop(source);
		AL10.alSourcei(source, AL10.AL_BUFFER, 0);
		free.clear();
		for (int buffer : buffers) {
			free.add(buffer);
		}
		paused = false;
	}

	/** Whether a buffer is free for the next block, after taking back the ones already played. */
	boolean wantsBlock() {
		return pendingBlocks() < BUFFERS;
	}

	/** Queues one block of 16-bit mono PCM and (re)starts playback if the source ran dry. */
	void queue(ByteBuffer pcm) {
		int buffer = free.removeFirst();
		AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, pcm, StreamFormat.SAMPLE_RATE);
		AL10.alSourceQueueBuffers(source, buffer);
		if (!paused && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
			AL10.alSourcePlay(source);
		}
	}

	void place(double x, double y, double z) {
		AL10.alSource3f(source, AL10.AL_POSITION, (float) x, (float) y, (float) z);
	}

	void gain(float gain) {
		AL10.alSourcef(source, AL10.AL_GAIN, gain);
	}

	/** High-frequency gain of the low-pass filter; ignored without EFX. */
	void highGain(float gain) {
		if (filter != 0 && Math.abs(gain - lastHighGain) > 1e-3f) {
			Efx.apply(source, filter, gain);
			lastHighGain = gain;
		}
	}

	/**
	 * The source's radius: a listener inside it hears the sound from everywhere, and a radius approaching the
	 * distance blurs the direction gradually. 0 is a point source. Ignored without AL_EXT_SOURCE_RADIUS.
	 */
	void radius(float r) {
		if (radius && Math.abs(r - lastRadius) > 1e-3f) {
			AL10.alSourcef(source, EXTSourceRadius.AL_SOURCE_RADIUS, r);
			lastRadius = r;
		}
	}

	/**
	 * Routes auxiliary send {@code index} into effect slot {@code slotId} at {@code gain}; slot 0 detaches it.
	 * Skips the AL calls when nothing changed. Without EFX (no direct filter), sends are never used.
	 */
	void send(int index, int slotId, float gain) {
		if (index < 0 || index >= SENDS) {
			return;
		}
		if (slotId == 0) {
			if (sendSlots[index] != 0) {
				Efx.send(source, index, EXTEfx.AL_EFFECTSLOT_NULL, EXTEfx.AL_FILTER_NULL);
				sendSlots[index] = 0;
			}
			return;
		}
		if (slotId == sendSlots[index] && Math.abs(gain - sendGains[index]) <= 1e-3f) {
			return;
		}
		if (sendFilters[index] == 0) {
			sendFilters[index] = Efx.createLowpass();
		}
		int filter = sendFilters[index];
		if (filter != 0) {
			Efx.sendGain(filter, gain);
			Efx.send(source, index, slotId, filter);
		} else if (gain > 1e-3f) {
			// No filter to carry the gain: the send plays at full level rather than not at all.
			Efx.send(source, index, slotId, EXTEfx.AL_FILTER_NULL);
		} else {
			Efx.send(source, index, EXTEfx.AL_EFFECTSLOT_NULL, EXTEfx.AL_FILTER_NULL);
			sendSlots[index] = 0;
			return;
		}
		sendSlots[index] = slotId;
		sendGains[index] = gain;
	}

	/** Detaches every auxiliary send still routed somewhere. */
	void detachSends() {
		for (int i = 0; i < SENDS; i++) {
			send(i, 0, 0f);
		}
	}

	void pause() {
		paused = true;
		AL10.alSourcePause(source);
	}

	void resume() {
		paused = false;
		if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PAUSED) {
			AL10.alSourcePlay(source);
		}
	}

	void delete() {
		detachSends();
		AL10.alSourceStop(source);
		AL10.alSourcei(source, AL10.AL_BUFFER, 0);
		AL10.alDeleteSources(source);
		AL10.alDeleteBuffers(buffers);
		if (filter != 0) {
			Efx.delete(filter);
		}
		for (int sendFilter : sendFilters) {
			if (sendFilter != 0) {
				Efx.delete(sendFilter);
			}
		}
		AL10.alGetError();
	}
}
