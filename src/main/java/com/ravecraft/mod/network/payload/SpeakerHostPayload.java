package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.speaker.CarryPlace;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
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
 * S→C, to the players who hear a playing speaker: where its sound comes from, its kind, and its volume and power.
 * With {@code block}, a placed speaker; otherwise {@code entityId} is the network id of the player carrying it at
 * {@code place} (hand: from the eyes, inventory or back: from the chest) or of the item entity on the ground
 * ({@code place} empty, as for a block). Sent when the session starts, the host changes, or the volume or power
 * change, and to each new listener before its first chunk.
 */
public record SpeakerHostPayload(UUID speakerId, ResourceKey<Level> dimension, Optional<BlockPos> block,
		int entityId, SpeakerKind kind, Optional<CarryPlace> place, int volume, boolean powered)
		implements CustomPacketPayload {
	/** {@code entityId} of a block host, which has no entity. */
	public static final int NO_ENTITY = -1;

	/** A speaker kind, by ordinal; an unknown one is a malformed packet. */
	public static final StreamCodec<ByteBuf, SpeakerKind> KIND_CODEC = ordinalCodec(SpeakerKind.values());
	/** A carry place, by ordinal; an unknown one is a malformed packet. */
	public static final StreamCodec<ByteBuf, CarryPlace> PLACE_CODEC = ordinalCodec(CarryPlace.values());

	public static final Type<SpeakerHostPayload> TYPE = new Type<>(RaveCraft.id("speaker_host"));
	public static final StreamCodec<ByteBuf, SpeakerHostPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, SpeakerHostPayload::speakerId,
			ResourceKey.streamCodec(Registries.DIMENSION), SpeakerHostPayload::dimension,
			ByteBufCodecs.optional(BlockPos.STREAM_CODEC), SpeakerHostPayload::block,
			ByteBufCodecs.VAR_INT, SpeakerHostPayload::entityId,
			KIND_CODEC, SpeakerHostPayload::kind,
			ByteBufCodecs.optional(PLACE_CODEC), SpeakerHostPayload::place,
			ByteBufCodecs.VAR_INT, SpeakerHostPayload::volume,
			ByteBufCodecs.BOOL, SpeakerHostPayload::powered,
			SpeakerHostPayload::new);

	/** Keeps an immutable copy of the block position. */
	public SpeakerHostPayload {
		block = block.map(BlockPos::immutable);
	}

	/** Encodes one of {@code values} by its ordinal, rejecting an ordinal out of range when decoding. */
	private static <E extends Enum<E>> StreamCodec<ByteBuf, E> ordinalCodec(E[] values) {
		return ByteBufCodecs.idMapper(id -> {
			if (id < 0 || id >= values.length) {
				throw new DecoderException("Unknown " + values[0].getDeclaringClass().getSimpleName() + " " + id);
			}
			return values[id];
		}, Enum::ordinal);
	}

	@Override
	public Type<SpeakerHostPayload> type() {
		return TYPE;
	}
}
