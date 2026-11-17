package com.ravecraft.mod.client.audio;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.registry.ModBlocks;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * A stream playing from a placed speaker. Stops itself when the speaker is broken or switched off, or when
 * {@code alive} turns false (the stream ended).
 */
public final class SpeakerSoundInstance extends AbstractTickableSoundInstance {
	static final Identifier STREAM = Identifier.fromNamespaceAndPath(RaveCraft.MOD_ID, "speaker_stream");

	private final BlockPos pos;
	private final Supplier<AudioStream> stream;
	private final BooleanSupplier alive;

	public SpeakerSoundInstance(BlockPos pos, Supplier<AudioStream> stream, BooleanSupplier alive) {
		super(SoundEvent.createVariableRangeEvent(STREAM), SoundSource.RECORDS, SoundInstance.createUnseededRandom());
		this.pos = pos.immutable();
		this.stream = stream;
		this.alive = alive;
		this.x = pos.getX() + 0.5;
		this.y = pos.getY() + 0.5;
		this.z = pos.getZ() + 0.5;
		this.attenuation = SoundInstance.Attenuation.LINEAR;
		this.relative = false;
		this.looping = false;
		this.volume = volumeAt(Minecraft.getInstance().level);
	}

	@Override
	public void tick() {
		ClientLevel level = Minecraft.getInstance().level;
		if (!isPoweredSpeaker(level, pos) || !alive.getAsBoolean()) {
			stop();
		} else {
			volume = volumeAt(level);
		}
	}

	/** Whether a speaker at {@code pos} is there and switched on, so a stream may play from it. */
	public static boolean isPoweredSpeaker(ClientLevel level, BlockPos pos) {
		if (level == null) {
			return false;
		}
		BlockState state = level.getBlockState(pos);
		return state.is(ModBlocks.PORTABLE_SPEAKER) && state.getValue(PortableSpeakerBlock.POWERED);
	}

	/** Starts even at volume 0, so raising the volume later is heard without restarting the stream. */
	@Override
	public boolean canStartSilent() {
		return true;
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
		return CompletableFuture.completedFuture(stream.get());
	}
}
