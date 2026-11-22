package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * S→C, to the players who hear a playing speaker: where its sound comes from, and its volume and power. With
 * {@code block}, a placed speaker; otherwise {@code entityId} is the network id of the player carrying it
 * ({@code inHand}: from the eyes, else the chest) or of the item entity on the ground. Sent when the session
 * starts, the host changes, or the volume or power change, and to each new listener before its first chunk.
 */
public record SpeakerHostPayload(UUID speakerId, ResourceKey<Level> dimension, Optional<BlockPos> block,
		int entityId, boolean inHand, int volume, boolean powered) implements CustomPacketPayload {
	/** {@code entityId} of a block host, which has no entity. */
	public static final int NO_ENTITY = -1;

	public static final Type<SpeakerHostPayload> TYPE = new Type<>(RaveCraft.id("speaker_host"));
	public static final StreamCodec<ByteBuf, SpeakerHostPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, SpeakerHostPayload::speakerId,
			ResourceKey.streamCodec(Registries.DIMENSION), SpeakerHostPayload::dimension,
			ByteBufCodecs.optional(BlockPos.STREAM_CODEC), SpeakerHostPayload::block,
			ByteBufCodecs.VAR_INT, SpeakerHostPayload::entityId,
			ByteBufCodecs.BOOL, SpeakerHostPayload::inHand,
			ByteBufCodecs.VAR_INT, SpeakerHostPayload::volume,
			ByteBufCodecs.BOOL, SpeakerHostPayload::powered,
			SpeakerHostPayload::new);

	/** Keeps an immutable copy of the block position. */
	public SpeakerHostPayload {
		block = block.map(BlockPos::immutable);
	}

	@Override
	public Type<SpeakerHostPayload> type() {
		return TYPE;
	}
}
