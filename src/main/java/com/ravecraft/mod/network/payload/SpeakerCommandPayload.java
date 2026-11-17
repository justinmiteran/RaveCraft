package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C→S: a player's command on the speaker at {@code pos}; {@code entryId} names the entry to remove. */
public record SpeakerCommandPayload(BlockPos pos, Command command, Optional<UUID> entryId) implements CustomPacketPayload {
	public enum Command {
		PLAY, PAUSE, NEXT, REMOVE, CLEAR;

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
			BlockPos.STREAM_CODEC, SpeakerCommandPayload::pos,
			Command.CODEC, SpeakerCommandPayload::command,
			ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), SpeakerCommandPayload::entryId,
			SpeakerCommandPayload::new);

	@Override
	public Type<SpeakerCommandPayload> type() {
		return TYPE;
	}
}
