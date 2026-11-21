package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C→S: a player's command on speaker {@code speakerId}; {@code entryId} names the entry to remove or move, {@code value}
 * carries the argument of SEEK (ms), VOLUME, POWER, REPEAT (mode ordinal), SHUFFLE and MOVE (target index
 * among what is still to play).
 */
public record SpeakerCommandPayload(UUID speakerId, Command command, Optional<UUID> entryId, long value)
		implements CustomPacketPayload {
	public enum Command {
		PLAY, PAUSE, NEXT, REMOVE, CLEAR, PREVIOUS, SEEK, VOLUME, POWER, REPEAT, SHUFFLE, MOVE;

		private static final Command[] VALUES = values();
		public static final StreamCodec<ByteBuf, Command> CODEC = ByteBufCodecs.idMapper(Command::byId, Command::ordinal);

		private static Command byId(int id) {
			if (id < 0 || id >= VALUES.length) {
				throw new DecoderException("Unknown speaker command " + id);
			}
			return VALUES[id];
		}
	}

	public static final Type<SpeakerCommandPayload> TYPE = new Type<>(RaveCraft.id("speaker_command"));
	public static final StreamCodec<ByteBuf, SpeakerCommandPayload> CODEC = StreamCodec.composite(
			UUIDUtil.STREAM_CODEC, SpeakerCommandPayload::speakerId,
			Command.CODEC, SpeakerCommandPayload::command,
			ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), SpeakerCommandPayload::entryId,
			ByteBufCodecs.VAR_LONG, SpeakerCommandPayload::value,
			SpeakerCommandPayload::new);

	public SpeakerCommandPayload(UUID speakerId, Command command, Optional<UUID> entryId) {
		this(speakerId, command, entryId, 0L);
	}

	@Override
	public Type<SpeakerCommandPayload> type() {
		return TYPE;
	}
}
