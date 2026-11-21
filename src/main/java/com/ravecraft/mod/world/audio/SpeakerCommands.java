package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.core.queue.QueueEntry;
import com.ravecraft.mod.core.queue.RepeatMode;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.network.payload.QueueAddPayload.NewEntry;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Applies the requests of a speaker's screen to the speaker the player can control; {@link SpeakerPlayback} plays
 * the result. Server thread only.
 */
public final class SpeakerCommands {
	/** What a command does to a speaker, given its entry and its value. */
	@FunctionalInterface
	private interface Handler {
		void apply(SpeakerHandle speaker, Optional<UUID> entryId, long value);
	}

	private static final Map<Command, Handler> HANDLERS = handlers();

	private SpeakerCommands() {
	}

	/** Appends tracks offered by {@code player}, who owns them. Ignored out of reach. */
	public static void add(ServerPlayer player, UUID speakerId, List<NewEntry> added) {
		SpeakerHandle speaker = speakerInReach(player, speakerId);
		if (speaker == null) {
			return;
		}
		String name = player.getName().getString();
		List<QueueEntry> entries = new ArrayList<>();
		for (NewEntry entry : added) {
			entries.add(new QueueEntry(entry.entryId(), player.getUUID(), name, entry.title()));
		}
		SpeakerPlayback.update(speaker, SpeakerPlayback.queueOf(speaker).add(entries, SpeakerPlayback.RNG));
	}

	/** A command without {@code value} (PLAY, PAUSE, NEXT, REMOVE, CLEAR, PREVIOUS). */
	public static void command(ServerPlayer player, UUID speakerId, Command command, Optional<UUID> entryId) {
		command(player, speakerId, command, entryId, 0);
	}

	/**
	 * Applies a player's command; {@code value} is the argument of SEEK (ms), VOLUME, POWER, REPEAT (mode ordinal),
	 * SHUFFLE and MOVE (index among what is still to play), validated here. Ignored out of reach.
	 */
	public static void command(ServerPlayer player, UUID speakerId, Command command, Optional<UUID> entryId,
			long value) {
		SpeakerHandle speaker = speakerInReach(player, speakerId);
		Handler handler = HANDLERS.get(command);
		if (speaker != null && handler != null) {
			handler.apply(speaker, entryId, value);
		}
	}

	/** What each command does. */
	private static Map<Command, Handler> handlers() {
		Map<Command, Handler> h = new EnumMap<>(Command.class);
		h.put(Command.PLAY, (s, id, v) -> play(s));
		h.put(Command.PAUSE, (s, id, v) -> SpeakerPlayback.pause(s));
		h.put(Command.NEXT, (s, id, v) -> SpeakerPlayback.advance(s, false));
		h.put(Command.PREVIOUS, (s, id, v) -> SpeakerPlayback.previous(s));
		h.put(Command.REMOVE, (s, id, v) -> queued(s, id).ifPresent(e -> SpeakerPlayback.removeEntry(s, e, false)));
		h.put(Command.CLEAR, (s, id, v) -> SpeakerPlayback.clear(s));
		h.put(Command.SEEK, (s, id, v) -> seek(s, v));
		h.put(Command.VOLUME, (s, id, v) -> volume(s, v));
		h.put(Command.POWER, (s, id, v) -> power(s, v != 0));
		h.put(Command.REPEAT, (s, id, v) -> repeat(s, v));
		h.put(Command.SHUFFLE, (s, id, v) -> shuffle(s, v != 0));
		h.put(Command.MOVE, (s, id, v) -> queued(s, id).ifPresent(e -> move(s, e, v)));
		return h;
	}

	/** {@code entryId}, when it names an entry of the speaker's queue. */
	private static Optional<UUID> queued(SpeakerHandle speaker, Optional<UUID> entryId) {
		return entryId.filter(id -> SpeakerPlayback.queueOf(speaker).entry(id).isPresent());
	}

	private static void play(SpeakerHandle speaker) {
		if (!SpeakerPlayback.isPlaying(speaker.id())) {
			SpeakerPlayback.play(speaker);
		}
	}

	private static void volume(SpeakerHandle speaker, long value) {
		setVolume(speaker, Math.clamp(value, SpeakerSettings.MIN_VOLUME, SpeakerSettings.MAX_VOLUME));
		SpeakerHostNotices.notice(speaker.level().getServer(), speaker.id());
		SpeakerPlayback.markDirty(speaker);
	}

	private static void power(SpeakerHandle speaker, boolean on) {
		if (on != speaker.powered()) {
			togglePower(speaker);
			SpeakerHostNotices.notice(speaker.level().getServer(), speaker.id());
			SpeakerPlayback.markDirty(speaker);
		}
	}

	private static void repeat(SpeakerHandle speaker, long mode) {
		if (mode >= 0 && mode < RepeatMode.values().length) {
			SpeakerPlayback.update(speaker, SpeakerPlayback.queueOf(speaker).withRepeat(RepeatMode.values()[(int) mode]));
		}
	}

	private static void shuffle(SpeakerHandle speaker, boolean on) {
		SpeakerQueue queue = SpeakerPlayback.queueOf(speaker);
		if (on != queue.shuffle()) {
			SpeakerPlayback.update(speaker, queue.withShuffle(on, SpeakerPlayback.RNG));
		}
	}

	private static void move(SpeakerHandle speaker, UUID entryId, long index) {
		int clamped = (int) Math.clamp(index, 0, SpeakerQueue.MAX_ENTRIES);
		SpeakerPlayback.update(speaker, SpeakerPlayback.queueOf(speaker).move(entryId, clamped));
	}

	/** Moves the current entry to {@code value} ms, clamped to its duration; ignored while that is unknown. */
	private static void seek(SpeakerHandle speaker, long value) {
		SpeakerQueue queue = SpeakerPlayback.queueOf(speaker);
		Optional<QueueEntry> current = queue.current().flatMap(queue::entry);
		if (current.isEmpty() || current.get().durationMs() < 0) {
			return;
		}
		long position = Math.clamp(value, 0, current.get().durationMs());
		boolean wasPlaying = SpeakerPlayback.stop(speaker);
		SpeakerPlayback.update(speaker, queue.withPosition(position));
		if (wasPlaying) {
			SpeakerPlayback.play(speaker);
		}
	}

	/** A block also sends the new volume to everyone tracking it: listeners read it from their copy of the block. */
	private static void setVolume(SpeakerHandle speaker, int volume) {
		if (speaker instanceof BlockHandle block) {
			block.speaker().setVolume(volume);
		} else {
			speaker.setData(speaker.data().withSettings(speaker.data().settings().withVolume(volume)));
		}
	}

	/** A block keeps its POWERED property in sync, and pauses itself when switched off. */
	private static void togglePower(SpeakerHandle speaker) {
		if (speaker instanceof BlockHandle block) {
			block.speaker().togglePower();
			return;
		}
		speaker.setData(speaker.data().withSettings(speaker.data().settings().togglePower()));
		if (!speaker.powered()) {
			SpeakerPlayback.pause(speaker);
		}
	}

	/** Speaker {@code speakerId}, when {@code player} can control it, or null. */
	private static @Nullable SpeakerHandle speakerInReach(ServerPlayer player, UUID speakerId) {
		return SpeakerHosts.handle(player.level().getServer(), speakerId).filter(h -> h.canControl(player)).orElse(null);
	}
}
