package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.component.QueueCodecs;
import com.ravecraft.mod.core.queue.RepeatMode;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S→C, to players with the speaker's screen open: its queue in display order (current first), the playback modes
 * and the speaker's settings. {@code nextRoundIndex} is the index in {@code entries} where the next round starts, or
 * -1 when there is no separator.
 */
public record SpeakerStatePayload(UUID speakerId, boolean playing, Optional<UUID> current, long positionMs,
		List<StateEntry> entries, RepeatMode repeat, boolean shuffle, int volume, boolean powered,
		int nextRoundIndex) implements CustomPacketPayload {
	public static final int MAX_ENTRIES = 200;

	/** A queued track as the screen shows it. */
	public record StateEntry(UUID entryId, String ownerName, String title, boolean ownerOnline, long durationMs) {
		public static final StreamCodec<ByteBuf, StateEntry> CODEC = StreamCodec.composite(
				UUIDUtil.STREAM_CODEC, StateEntry::entryId,
				ByteBufCodecs.stringUtf8(16), StateEntry::ownerName,
				ByteBufCodecs.stringUtf8(128), StateEntry::title,
				ByteBufCodecs.BOOL, StateEntry::ownerOnline,
				ByteBufCodecs.VAR_LONG, StateEntry::durationMs,
				StateEntry::new);
	}

	public static final Type<SpeakerStatePayload> TYPE = new Type<>(RaveCraft.id("speaker_state"));

	private static final StreamCodec<ByteBuf, Optional<UUID>> CURRENT = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC);
	private static final StreamCodec<ByteBuf, List<StateEntry>> ENTRIES =
			StateEntry.CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES));

	// Too many fields for StreamCodec.composite: written by hand, in field order.
	public static final StreamCodec<ByteBuf, SpeakerStatePayload> CODEC = StreamCodec.of(
			(buf, p) -> {
				UUIDUtil.STREAM_CODEC.encode(buf, p.speakerId);
				ByteBufCodecs.BOOL.encode(buf, p.playing);
				CURRENT.encode(buf, p.current);
				ByteBufCodecs.VAR_LONG.encode(buf, p.positionMs);
				ENTRIES.encode(buf, p.entries);
				QueueCodecs.REPEAT_STREAM.encode(buf, p.repeat);
				ByteBufCodecs.BOOL.encode(buf, p.shuffle);
				ByteBufCodecs.VAR_INT.encode(buf, p.volume);
				ByteBufCodecs.BOOL.encode(buf, p.powered);
				ByteBufCodecs.VAR_INT.encode(buf, p.nextRoundIndex);
			},
			buf -> new SpeakerStatePayload(
					UUIDUtil.STREAM_CODEC.decode(buf),
					ByteBufCodecs.BOOL.decode(buf),
					CURRENT.decode(buf),
					ByteBufCodecs.VAR_LONG.decode(buf),
					ENTRIES.decode(buf),
					QueueCodecs.REPEAT_STREAM.decode(buf),
					ByteBufCodecs.BOOL.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.BOOL.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf)));

	/** Keeps the first {@link #MAX_ENTRIES} entries; a separator past that cap becomes -1. */
	public SpeakerStatePayload {
		entries = entries.size() > MAX_ENTRIES ? List.copyOf(entries.subList(0, MAX_ENTRIES)) : List.copyOf(entries);
		if (nextRoundIndex >= entries.size()) {
			nextRoundIndex = -1;
		}
	}

	@Override
	public Type<SpeakerStatePayload> type() {
		return TYPE;
	}
}
