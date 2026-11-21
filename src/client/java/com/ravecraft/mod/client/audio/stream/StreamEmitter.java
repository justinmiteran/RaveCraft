package com.ravecraft.mod.client.audio.stream;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.audio.Pcm16;
import com.ravecraft.mod.core.audio.decode.Decoders;
import com.ravecraft.mod.core.audio.decode.PcmDecoder;
import com.ravecraft.mod.core.audio.decode.PcmFormat;
import com.ravecraft.mod.core.audio.decode.TrackDuration;
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
import java.util.function.LongConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Streams one track of this player's PC into a session: decode, mono, 48 kHz, Opus, 100 ms chunks sent at most
 * {@link StreamFormat#LEAD_MS} ahead of real time. Runs on its own daemon thread; every network send and callback
 * happens on the client thread, and nothing is sent once {@link #cancel()} was called there.
 */
public final class StreamEmitter {
	/** PCM frames decoded per read. */
	private static final int READ_FRAMES = 4096;

	private final int sessionId;
	/** The speaker's dimension: this player hears its own stream only while there. */
	private final ResourceKey<Level> dimension;
	private final BlockPos pos;
	private final Path track;
	/** Where to start in the track: everything before is decoded and dropped. */
	private final long startMs;
	/** Called on the client thread with the track's duration in ms (-1 if unknown), once its header was read. */
	private final LongConsumer onStarted;
	private final Runnable onFinished;
	private final Consumer<Exception> onFailed;
	private volatile boolean cancelled;
	private Thread thread;

	/**
	 * What the emitter reports, on the client thread: the track's duration once its header was read (-1 if unknown),
	 * its natural end, or the failure that stopped it.
	 */
	public record Callbacks(LongConsumer onStarted, Runnable onFinished, Consumer<Exception> onFailed) {
	}

	/** An emitter for {@code track} from {@code startMs}, not started yet. */
	public StreamEmitter(int sessionId, ResourceKey<Level> dimension, BlockPos pos, Path track, long startMs,
			Callbacks callbacks) {
		this.sessionId = sessionId;
		this.dimension = dimension;
		this.pos = pos.immutable();
		this.track = track;
		this.startMs = startMs;
		this.onStarted = callbacks.onStarted();
		this.onFinished = callbacks.onFinished();
		this.onFailed = callbacks.onFailed();
	}

	/** Starts streaming on a daemon thread. */
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

	/** Decodes, encodes and sends the track, then reports how it ended. */
	private void run() {
		long durationMs = TrackDuration.of(track).orElse(-1);
		onClientThread(() -> onStarted.accept(durationMs));
		long start = System.nanoTime();
		try (PcmDecoder decoder = open(track)) {
			PcmFormat format = decoder.format();
			Pass pass = new Pass(format, start);
			byte[] buffer = new byte[format.frameBytes() * READ_FRAMES];
			for (int read = readUnlessCancelled(decoder, buffer); read >= 0; read = readUnlessCancelled(decoder, buffer)) {
				pass.push(buffer, read);
			}
			pass.finish();
			onClientThread(onFinished);
		} catch (InterruptedException e) {
			// Cancelled: the thread ends here, interrupted.
			Thread.currentThread().interrupt();
		} catch (IOException | RuntimeException e) {
			if (!cancelled) {
				RaveCraft.LOGGER.warn("Cannot play {}: {}", track.getFileName(), e.getMessage());
				onClientThread(() -> onFailed.accept(e));
			}
		}
	}

	/** The next PCM block into {@code buffer}: its length in bytes, or -1 at the end or once cancelled. */
	private int readUnlessCancelled(PcmDecoder decoder, byte[] buffer) throws IOException {
		return cancelled ? -1 : decoder.read(buffer);
	}

	/** One streaming of the track: the chain from decoded PCM to the Opus chunks sent. */
	private final class Pass {
		private final PcmFormat format;
		private final LinearResampler resampler;
		private final MonoFramer framer = new MonoFramer();
		private final OpusMonoEncoder encoder = new OpusMonoEncoder();
		private final EmitterPacer pacer = new EmitterPacer(StreamFormat.LEAD_MS);
		private final FrameSkipper skipper = new FrameSkipper(startMs);
		private List<byte[]> chunk = new ArrayList<>(StreamFormat.FRAMES_PER_CHUNK);
		private int seq;
		/** When real time started, in {@link System#nanoTime()}. */
		private long start;

		Pass(PcmFormat format, long start) {
			this.format = format;
			this.resampler = new LinearResampler(format.sampleRate());
			this.start = start;
		}

		/** Converts {@code read} bytes of decoded PCM and sends each chunk they complete. */
		void push(byte[] buffer, int read) throws InterruptedException {
			byte[] mono = Pcm16.downmixToMono(buffer, read, format.channels());
			short[] samples = resampler.process(Pcm16.toShorts(mono, mono.length), mono.length / 2);
			framer.push(samples, samples.length);
			for (short[] frame = framer.poll(); frame != null; frame = framer.poll()) {
				add(frame);
			}
		}

		/** Encodes {@code frame} unless it comes before the start position, and sends a full chunk. */
		private void add(short[] frame) throws InterruptedException {
			if (skipper.skip()) {
				// Real time starts with the first frame sent, not while the skipped part is decoded.
				start = System.nanoTime();
				return;
			}
			chunk.add(encoder.encode(frame));
			if (chunk.size() == StreamFormat.FRAMES_PER_CHUNK) {
				send(seq++, chunk, pacer, start);
				chunk = new ArrayList<>(StreamFormat.FRAMES_PER_CHUNK);
			}
		}

		/** Sends the last frames, then waits until real time caught up with what was sent. */
		void finish() throws InterruptedException {
			short[] tail = framer.flush();
			if (tail != null && !skipper.skip()) {
				chunk.add(encoder.encode(tail));
			}
			if (!chunk.isEmpty() && !cancelled) {
				send(seq++, chunk, pacer, start);
			}
			// Finish only once real time caught up, so the next track does not start over this one's lead.
			sleepUntil(start, (long) seq * StreamFormat.FRAMES_PER_CHUNK * StreamFormat.FRAME_MS);
		}
	}

	private static PcmDecoder open(Path track) throws IOException {
		Optional<PcmDecoder> decoder = Decoders.open(track);
		return decoder.isPresent() ? decoder.get() : new OggPcmDecoder(track);
	}

	/** Waits until chunk {@code seq} is due, then sends it on the client thread. */
	private void send(int seq, List<byte[]> frames, EmitterPacer pacer, long start) throws InterruptedException {
		long sentMillis = (long) (seq + 1) * StreamFormat.FRAMES_PER_CHUNK * StreamFormat.FRAME_MS;
		long delay = pacer.delayBeforeNextMillis(elapsedMillis(start), sentMillis);
		StreamPacing.sleep(delay);
		List<byte[]> copy = List.copyOf(frames);
		onClientThread(() -> {
			ClientPlayNetworking.send(new AudioChunkPayload(sessionId, seq, copy));
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft.level != null && minecraft.level.dimension().equals(dimension)) {
				ReceivedStreams.offer(sessionId, pos, seq, copy);
			}
		});
	}

	private void sleepUntil(long start, long audioMillis) throws InterruptedException {
		long wait = audioMillis - elapsedMillis(start);
		StreamPacing.sleep(wait);
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
