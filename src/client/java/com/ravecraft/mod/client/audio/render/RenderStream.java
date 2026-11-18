package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.Equalizer;
import com.ravecraft.mod.core.acoustics.VirtualPacer;
import com.ravecraft.mod.core.acoustics.occlusion.LossSmoother;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionFilter;
import com.ravecraft.mod.core.acoustics.reverb.Chorus;
import com.ravecraft.mod.core.acoustics.reverb.FluidReverb;
import com.ravecraft.mod.core.acoustics.reverb.SendRoute;
import com.ravecraft.mod.core.audio.stream.StreamFormat;
import com.ravecraft.mod.core.audio.stream.StreamPlayout;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.registry.ModBlocks;
import java.nio.ByteBuffer;
import java.util.function.BooleanSupplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * One speaker stream as the renderer sees it: where it plays from, the speaker's state (refreshed each client tick)
 * and the audio-thread side (equalizer, virtual pacing, source).
 */
final class RenderStream {
	/** How long an occlusion change takes to be heard in full. */
	static final double OCCLUSION_RAMP_MS = 200;

	final int sessionId;
	final BlockPos pos;
	final StreamPlayout playout;
	final BooleanSupplier alive;

	// Written on the client thread each tick, read on the audio thread.
	volatile double volume;
	volatile boolean powered;
	volatile double facingX;
	volatile double facingZ = 1;

	// Written on the client thread by the occlusion scheduler, read on the audio thread: both losses are published
	// before occlusionKnown is set.
	volatile double occlusionLossDb;
	volatile double occlusionHighLossDb;
	volatile boolean occlusionKnown;

	/** Which reverb slot this stream's space feeds (0 = listener, 1 = S1, 2 = S2); written by the reverb scheduler. */
	volatile int reverbSlot = SendRoute.LISTENER;

	/** Written on the audio thread each step, read by the occlusion scheduler to trace the loudest streams first. */
	volatile double heardGain;
	/** Client thread only: {@code heardGain} as the scheduler read it before sorting, stable during the sort. */
	double sortGain;

	// Audio thread only.
	@Nullable Equalizer eq;
	final OcclusionFilter occlusionFilter = new OcclusionFilter(StreamFormat.SAMPLE_RATE);
	final Chorus chorus = new Chorus(StreamFormat.SAMPLE_RATE);
	final VirtualPacer pacer;
	@Nullable AlSource source;
	/** Fades the reverb send between slots when {@code reverbSlot} changes. */
	final SendRoute route = new SendRoute();
	/** This step's distance to the listener, and its gain (propagation, then occlusion). */
	double distance;
	double gain;
	/** This step's smoothed occlusion losses, in dB. */
	double lossDb;
	double highLossDb;
	final LossSmoother lossRamp = new LossSmoother(OCCLUSION_RAMP_MS);
	final LossSmoother highRamp = new LossSmoother(OCCLUSION_RAMP_MS);

	RenderStream(int sessionId, BlockPos pos, StreamPlayout playout, BooleanSupplier alive, long nowMs) {
		this.sessionId = sessionId;
		this.pos = pos;
		this.playout = playout;
		this.alive = alive;
		this.pacer = new VirtualPacer(nowMs, StreamFormat.FRAMES_PER_CHUNK * StreamFormat.FRAME_MS);
	}

	/** Whether a speaker at {@code pos} is there and switched on, so a stream may play from it. */
	static boolean isPoweredSpeaker(@Nullable ClientLevel level, BlockPos pos) {
		if (level == null) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.is(ModBlocks.PORTABLE_SPEAKER) && state.getValue(PortableSpeakerBlock.POWERED);
	}

	/** Client thread: copies the speaker's volume, power and facing for the audio thread. */
	void refresh(@Nullable ClientLevel level) {
		powered = isPoweredSpeaker(level, pos);
		if (powered && level.getBlockEntity(pos) instanceof PortableSpeakerBlockEntity speaker) {
			volume = speaker.data().settings().volume() / (double) SpeakerSettings.MAX_VOLUME;
			Direction facing = level.getBlockState(pos).getValue(PortableSpeakerBlock.FACING);
			facingX = facing.getStepX();
			facingZ = facing.getStepZ();
		} else {
			volume = 0;
		}
	}

	/** Audio thread: (re)builds the equalizer for the server's portable profile. */
	void configure(AcousticSettings settings) {
		eq = new Equalizer(StreamFormat.SAMPLE_RATE, settings.portable().bassCutHz(), settings.portable().bassCutDb());
	}

	/**
	 * Audio thread: takes the next 100 ms from the playout, through the equalizer and the occlusion low-pass at
	 * {@code highLossDb}, then the underwater {@code chorus} when given. Written as 16-bit PCM into {@code out} (cleared and flipped) when given; only consumed
	 * otherwise (still filtered, so the filter's state follows the stream).
	 */
	void nextBlock(float[] scratch, @Nullable ByteBuffer out, double highLossDb, @Nullable FluidReverb fluid) {
		byte[] block = playout.nextBlock();
		int samples = block.length / 2;
		for (int i = 0; i < samples; i++) {
			short s = (short) ((block[2 * i] & 0xFF) | (block[2 * i + 1] << 8));
			scratch[i] = s / 32768f;
		}
		if (eq != null) {
			eq.process(scratch, samples);
		}
		occlusionFilter.process(scratch, samples, highLossDb);
		chorus.process(scratch, samples, fluid);
		if (out != null) {
			out.clear();
			for (int i = 0; i < samples; i++) {
				short s = (short) Math.round(Math.clamp(scratch[i], -1f, 32767f / 32768f) * 32768f);
				out.put((byte) s).put((byte) (s >> 8));
			}
			out.flip();
		}
	}
}
