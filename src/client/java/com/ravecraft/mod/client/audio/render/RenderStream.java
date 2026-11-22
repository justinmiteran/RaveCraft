package com.ravecraft.mod.client.audio.render;

import com.ravecraft.mod.block.SpeakerBlock;
import com.ravecraft.mod.client.audio.ClientSpeakerHosts;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.Directivity;
import com.ravecraft.mod.core.acoustics.SpeakerAxis;
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
import com.ravecraft.mod.network.payload.SpeakerHostPayload;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One speaker stream as the renderer sees it: where it plays from and the speaker's state, both refreshed each
 * client tick from its host ({@link ClientSpeakerHosts}), and the audio-thread side (equalizer, virtual pacing,
 * source).
 */
final class RenderStream {
	/** How long an occlusion change takes to be heard in full. */
	static final double OCCLUSION_RAMP_MS = 200;

	final int sessionId;
	final UUID speakerId;
	final StreamPlayout playout;
	final BooleanSupplier alive;

	// Written on the client thread each tick, read on the audio thread. The position is refreshed per tick, not per
	// frame: the audio thread reads it once per 100 ms block anyway.
	volatile double x;
	volatile double y;
	volatile double z;
	volatile double volume;
	volatile boolean powered;
	volatile double facingX;
	volatile double facingZ = 1;
	/** The profile of the speaker's kind, and how it radiates where it is now (omnidirectional on the ground). */
	volatile DeviceProfile profile = DeviceProfile.PORTABLE;
	volatile Directivity directivity = Directivity.OMNI;

	// Written on the client thread by the occlusion scheduler, read on the audio thread: both losses are published
	// before occlusionKnown is set.
	volatile double occlusionLossDb;
	volatile double occlusionHighLossDb;
	volatile boolean occlusionKnown;

	/** Which reverb slot this stream's space feeds (0 = listener, 1 = S1, 2 = S2); written by the reverb scheduler. */
	volatile int reverbSlot = SendRoute.LISTENER;

	/** Written on the audio thread each step, read by the occlusion scheduler to trace the loudest streams first. */
	volatile double heardGain;
	/** Written on the audio thread each step: whether this stream holds a sound source (for the debug screen). */
	volatile boolean sounding;
	/** Client thread only: {@code heardGain} as the scheduler read it before sorting, stable during the sort. */
	double sortGain;

	// Audio thread only.
	@Nullable Equalizer eq;
	/** The profile {@code eq} was built for. */
	@Nullable DeviceProfile eqProfile;
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

	RenderStream(int sessionId, UUID speakerId, StreamPlayout playout, BooleanSupplier alive, long nowMs) {
		this.sessionId = sessionId;
		this.speakerId = speakerId;
		this.playout = playout;
		this.alive = alive;
		this.pacer = new VirtualPacer(nowMs, StreamFormat.FRAMES_PER_CHUNK * StreamFormat.FRAME_MS);
	}

	/**
	 * Client thread: copies the speaker's position, volume, power, sound axis and profile for the audio thread.
	 * Silent (volume 0) while its host is unknown or not here (another dimension, entity not loaded, block not there).
	 */
	void refresh(@Nullable ClientLevel level) {
		Vec3 at = ClientSpeakerHosts.position(speakerId, level);
		Optional<SpeakerHostPayload> found = ClientSpeakerHosts.get(speakerId);
		SpeakerAxis axis = at == null || found.isEmpty() ? null : axisOf(found.get(), level);
		if (axis == null) {
			volume = 0;
			return;
		}
		SpeakerHostPayload host = found.get();
		x = at.x;
		y = at.y;
		z = at.z;
		powered = host.powered();
		facingX = axis.x();
		facingZ = axis.z();
		DeviceProfile kindProfile = AcousticSettingsClient.get().profile(host.kind());
		profile = kindProfile;
		directivity = axis.directivity(kindProfile);
		volume = host.volume() / (double) SpeakerSettings.MAX_VOLUME;
	}

	/**
	 * The way {@code host} points: a block along its facing, a carrier along its look or behind its body, the
	 * ground every way. Null when its block is no longer a speaker.
	 */
	private static @Nullable SpeakerAxis axisOf(SpeakerHostPayload host, ClientLevel level) {
		if (host.block().isPresent()) {
			BlockState state = level.getBlockState(host.block().get());
			if (!(state.getBlock() instanceof SpeakerBlock)) {
				return null;
			}
			Direction facing = state.getValue(SpeakerBlock.FACING);
			return SpeakerAxis.block(facing.getStepX(), facing.getStepZ());
		}
		Entity entity = level.getEntity(host.entityId());
		if (host.place().isPresent() && entity instanceof LivingEntity carrier) {
			return SpeakerAxis.carried(host.place().get(), carrier.getYRot(), carrier.yBodyRot);
		}
		return SpeakerAxis.GROUND;
	}

	/** Audio thread: (re)builds the equalizer for the profile of the speaker's kind, as last refreshed. */
	void configure() {
		DeviceProfile current = profile;
		eq = new Equalizer(StreamFormat.SAMPLE_RATE, current.bassCutHz(), current.bassCutDb());
		eqProfile = current;
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
