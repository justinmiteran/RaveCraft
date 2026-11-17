package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S→C, to players with the speaker's screen open: its whole queue and whether it plays. */
public record SpeakerStatePayload(BlockPos pos, boolean playing, Optional<UUID> current, long positionMs,
		List<StateEntry> entries) implements CustomPacketPayload {
	public static final int MAX_ENTRIES = 200;

	public record StateEntry(UUID entryId, String ownerName, String title, boolean ownerOnline) {
		public static final StreamCodec<ByteBuf, StateEntry> CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, StateEntry::entryId,
				ByteBufCodecs.stringUtf8(16), StateEntry::ownerName,
				ByteBufCodecs.stringUtf8(128), StateEntry::title,
				ByteBufCodecs.BOOL, StateEntry::ownerOnline,
				StateEntry::new);
	}

	public static final Type<SpeakerStatePayload> TYPE = new Type<>(RaveCraft.id("speaker_state"));
	public static final StreamCodec<ByteBuf, SpeakerStatePayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, SpeakerStatePayload::pos,
			ByteBufCodecs.BOOL, SpeakerStatePayload::playing,
			ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), SpeakerStatePayload::current,
			ByteBufCodecs.VAR_LONG, SpeakerStatePayload::positionMs,
			StateEntry.CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), SpeakerStatePayload::entries,
			SpeakerStatePayload::new);

	public SpeakerStatePayload {
		entries = List.copyOf(entries);
	}

	@Override
	public Type<SpeakerStatePayload> type() {
		return TYPE;
	}
}
