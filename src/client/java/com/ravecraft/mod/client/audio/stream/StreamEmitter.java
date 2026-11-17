package com.ravecraft.mod.client.audio.stream;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.audio.Pcm16;
import com.ravecraft.mod.core.audio.decode.Decoders;
import com.ravecraft.mod.core.audio.decode.PcmDecoder;
import com.ravecraft.mod.core.audio.decode.PcmFormat;
import com.ravecraft.mod.core.audio.opus.OpusMonoEncoder;
import com.ravecraft.mod.core.audio.stream.EmitterPacer;
import com.ravecraft.mod.core.audio.stream.FrameSkipper;
import com.ravecraft.mod.core.audio.stream.LinearResampler;
import com.ravecraft.mod.core.audio.stream.MonoFramer;
import com.ravecraft.mod.core.audio.stream.StreamFormat;
import com.ravecraft.mod.network.payload.AudioChunkPayload;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Streams one track of this player's PC into a session: decode, mono, 48 kHz, Opus, 100 ms chunks sent at most
 * {@link StreamFormat#LEAD_MS} ahead of real time. Runs on its own daemon thread; every network send and callback
 * happens on the client thread, and nothing is sent once {@link #cancel()} was called there.
 */
public final class StreamEmitter {
	private final int sessionId;
	private final BlockPos pos;
	private final Path track;
	/** Where to start in the track: everything before is decoded and dropped. */
	private final long startMs;
	private final Runnable onFinished;
	private final Consumer<Exception> onFailed;
	private volatile boolean cancelled;
	private Thread thread;

	public StreamEmitter(int sessionId, BlockPos pos, Path track, long startMs, Runnable onFinished, Consumer<Exception> onFailed) {
		this.sessionId = sessionId;
		this.pos = pos.immutable();
		this.track = track;
		this.startMs = startMs;
		this.onFinished = onFinished;
		this.onFailed = onFailed;
	}

	public void start() {
		thread = new Thread(this::run, "RaveCraft emitter #" + sessionId);
		thread.setDaemon(true);
		thread.start();
	}

	/** Client thread. Stops sending immediately; the thread winds down on its own. */
	public void cancel() {
		cancelled = true;
		if (thread != null) {
			thread.interrupt();
		}
	}

	private void run() {
		long start = System.nanoTime();
		try (PcmDecoder decoder = open(track)) {
			PcmFormat format = decoder.format();
			LinearResampler resampler = new LinearResampler(format.sampleRate());
			MonoFramer framer = new MonoFramer();
			OpusMonoEncoder encoder = new OpusMonoEncoder();
			EmitterPacer pacer = new EmitterPacer(StreamFormat.LEAD_MS);
			FrameSkipper skipper = new FrameSkipper(startMs);
			byte[] buffer = new byte[format.frameBytes() * 4096];
			List<byte[]> chunk = new ArrayList<>(StreamFormat.FRAMES_PER_CHUNK);
			int seq = 0;
			while (!cancelled) {
				int read = decoder.read(buffer);
				if (read < 0) {
					break;
				}
				byte[] mono = Pcm16.downmixToMono(buffer, read, format.channels());
				short[] samples = resampler.process(Pcm16.toShorts(mono, mono.length), mono.length / 2);
				framer.push(samples, samples.length);
				for (short[] frame = framer.poll(); frame != null; frame = framer.poll()) {
					if (skipper.skip()) {
						continue;
					}
					chunk.add(encoder.encode(frame));
					if (chunk.size() == StreamFormat.FRAMES_PER_CHUNK) {
						send(seq++, chunk, pacer, start);
						chunk = new ArrayList<>(StreamFormat.FRAMES_PER_CHUNK);
					}
				}
			}
			short[] tail = framer.flush();
			if (tail != null && !skipper.skip()) {
				chunk.add(encoder.encode(tail));
			}
			if (!chunk.isEmpty() && !cancelled) {
				send(seq++, chunk, pacer, start);
			}
			// Finish only once real time caught up, so the next track does not start over this one's lead.
			sleepUntil(start, (long) seq * StreamFormat.FRAMES_PER_CHUNK * StreamFormat.FRAME_MS);
			onClientThread(onFinished);
		} catch (InterruptedException e) {
			// Cancelled.
		} catch (IOException | RuntimeException e) {
			if (!cancelled) {
				RaveCraft.LOGGER.warn("Cannot play {}: {}", track.getFileName(), e.getMessage());
				onClientThread(() -> onFailed.accept(e));
			}
		}
	}

	private static PcmDecoder open(Path track) throws IOException {
		Optional<PcmDecoder> decoder = Decoders.open(track);
		return decoder.isPresent() ? decoder.get() : new OggPcmDecoder(track);
	}

	private void send(int seq, List<byte[]> frames, EmitterPacer pacer, long start) throws InterruptedException {
		long sentMillis = (long) (seq + 1) * StreamFormat.FRAMES_PER_CHUNK * StreamFormat.FRAME_MS;
		long delay = pacer.delayBeforeNextMillis(elapsedMillis(start), sentMillis);
		if (delay > 0) {
			Thread.sleep(delay);
		}
		List<byte[]> copy = List.copyOf(frames);
		onClientThread(() -> {
			ClientPlayNetworking.send(new AudioChunkPayload(sessionId, seq, copy));
			ReceivedStreams.offer(sessionId, pos, seq, copy);
		});
	}

	private void sleepUntil(long start, long audioMillis) throws InterruptedException {
		long wait = audioMillis - elapsedMillis(start);
		if (wait > 0) {
			Thread.sleep(wait);
		}
	}

	private static long elapsedMillis(long start) {
		return (System.nanoTime() - start) / 1_000_000;
	}

	private void onClientThread(Runnable action) {
		Minecraft.getInstance().execute(() -> {
			if (!cancelled) {
				action.run();
			}
		});
	}
}
