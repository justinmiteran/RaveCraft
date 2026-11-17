package com.ravecraft.mod.client.audio;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.core.audio.decode.Decoders;
import com.ravecraft.mod.core.audio.decode.PcmDecoder;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.registry.ModBlocks;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.level.block.state.BlockState;

/** One track playing from a placed speaker. Stops itself when the speaker is broken or switched off. */
public final class SpeakerSoundInstance extends AbstractTickableSoundInstance {
	static final Identifier STREAM = Identifier.fromNamespaceAndPath(RaveCraft.MOD_ID, "speaker_stream");

	private final BlockPos pos;
	private final Path track;
	private volatile boolean failed;
	private boolean speakerGone;

	SpeakerSoundInstance(BlockPos pos, Path track) {
		super(SoundEvent.createVariableRangeEvent(STREAM), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
		this.pos = pos.immutable();
		this.track = track;
		this.x = pos.getX() + 0.5;
		this.y = pos.getY() + 0.5;
		this.z = pos.getZ() + 0.5;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.relative = false;
		this.looping = false;
		this.volume = volumeAt(Minecraft.getInstance().level);
	}

	public Path track() {
		return track;
	}

	/** True once the speaker was broken or switched off: the queue should end rather than move on. */
	boolean speakerGone() {
		return speakerGone;
	}

	@Override
	public void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		BlockState state = level == null ? null : level.getBlockState(pos);
		if (state == null || !state.is(ModBlocks.PORTABLE_SPEAKER) || !state.getValue(PortableSpeakerBlock.POWERED)) {
			speakerGone = true;
			stop();
		} else if (failed) {
			stop();
		} else {
			volume = volumeAt(level);
		}
	}

	void halt() {
		stop();
	}

	private float volumeAt(ClientLevel level) {
		int volume = SpeakerSettings.DEFAULT_VOLUME;
		if (level != null && level.getBlockEntity(pos) instanceof PortableSpeakerBlockEntity speaker) {
			volume = speaker.data().settings().volume();
		}
		return volume / (float) SpeakerSettings.MAX_VOLUME;
	}

	@Override
	public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary loader, Identifier id, boolean repeatInstantly) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				return open(track);
			} catch (IOException | RuntimeException e) {
				failed = true;
				RaveCraft.LOGGER.warn("Cannot play {}: {}", track.getFileName(), e.getMessage());
				throw new CompletionException(e);
			}
		}, Util.ioPool());
	}

	private static AudioStream open(Path track) throws IOException {
		Optional<PcmDecoder> decoder = Decoders.open(track);
		return decoder.isPresent() ? new MonoPcmAudioStream(decoder.get()) : new MonoOggAudioStream(track);
	}
}
