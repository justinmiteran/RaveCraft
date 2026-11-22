package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Applies the requests of a speaker's screen to the speaker the player reaches; {@link SpeakerPlayback} plays the
 * result. Server thread only.
 */
public final class SpeakerCommands {
	/** What a command does to a speaker, given its entry and its value. */
	@FunctionalInterface
	private interface Handler {
		void apply(PortableSpeakerBlockEntity be, Optional<UUID> entryId, long value);
	}

	private static final Map<Command, Handler> HANDLERS = handlers();

	private SpeakerCommands() {
	}

	/**
	 * Appends tracks offered by {@code player}, who owns them. Ignored out of reach.
	 *
	 * @param player the player
	 * @param pos where the speaker stands
	 * @param added the tracks offered
	 */
	public static void add(ServerPlayer player, BlockPos pos, List<NewEntry> added) {
		PortableSpeakerBlockEntity be = speakerInReach(player, pos);
		if (be == null) {
			return;
		}
		String name = player.getName().getString();
		List<QueueEntry> entries = new ArrayList<>();
		for (NewEntry entry : added) {
			entries.add(new QueueEntry(entry.entryId(), player.getUUID(), name, entry.title()));
		}
		SpeakerPlayback.update(be, SpeakerPlayback.queueOf(be).add(entries, SpeakerPlayback.RNG));
	}

	/**
	 * A command without {@code value} (PLAY, PAUSE, NEXT, REMOVE, CLEAR, PREVIOUS).
	 *
	 * @param player the player
	 * @param pos where the speaker stands
	 * @param command the command
	 * @param entryId the entry it is about, or empty
	 */
	public static void command(ServerPlayer player, BlockPos pos, Command command, Optional<UUID> entryId) {
		command(player, pos, command, entryId, 0);
	}

	/**
	 * Applies a player's command; {@code value} is the argument of SEEK (ms), VOLUME, POWER, REPEAT (mode ordinal),
	 * SHUFFLE and MOVE (index among what is still to play), validated here. Ignored out of reach.
	 *
	 * @param player the player
	 * @param pos where the speaker stands
	 * @param command the command
	 * @param entryId the entry it is about, or empty
	 * @param value the command's value
	 */
	public static void command(ServerPlayer player, BlockPos pos, Command command, Optional<UUID> entryId, long value) {
		PortableSpeakerBlockEntity be = speakerInReach(player, pos);
		Handler handler = HANDLERS.get(command);
		if (be != null && handler != null) {
			handler.apply(be, entryId, value);
		}
	}

	/** What each command does. */
	private static Map<Command, Handler> handlers() {
		Map<Command, Handler> h = new EnumMap<>(Command.class);
		h.put(Command.PLAY, (be, id, v) -> play(be));
		h.put(Command.PAUSE, (be, id, v) -> SpeakerPlayback.pause(be));
		h.put(Command.NEXT, (be, id, v) -> SpeakerPlayback.advance(be, false));
		h.put(Command.PREVIOUS, (be, id, v) -> SpeakerPlayback.previous(be));
		h.put(Command.REMOVE, (be, id, v) -> queued(be, id).ifPresent(e -> SpeakerPlayback.removeEntry(be, e, false)));
		h.put(Command.CLEAR, (be, id, v) -> SpeakerPlayback.clear(be));
		h.put(Command.SEEK, (be, id, v) -> seek(be, v));
		h.put(Command.VOLUME, (be, id, v) -> volume(be, v));
		h.put(Command.POWER, (be, id, v) -> power(be, v != 0));
		h.put(Command.REPEAT, (be, id, v) -> repeat(be, v));
		h.put(Command.SHUFFLE, (be, id, v) -> shuffle(be, v != 0));
		h.put(Command.MOVE, (be, id, v) -> queued(be, id).ifPresent(e -> move(be, e, v)));
		return h;
	}

	/** {@code entryId}, when it names an entry of the speaker's queue. */
	private static Optional<UUID> queued(PortableSpeakerBlockEntity be, Optional<UUID> entryId) {
		return entryId.filter(id -> SpeakerPlayback.queueOf(be).entry(id).isPresent());
	}

	private static void play(PortableSpeakerBlockEntity be) {
		if (!SpeakerPlayback.isPlaying(be)) {
			SpeakerPlayback.play(be);
		}
	}

	/** Moves the current entry to {@code value} ms, clamped to its duration; ignored while that is unknown. */
	private static void seek(PortableSpeakerBlockEntity be, long value) {
		SpeakerQueue queue = SpeakerPlayback.queueOf(be);
		Optional<QueueEntry> current = queue.current().flatMap(queue::entry);
		if (current.isEmpty() || current.get().durationMs() < 0) {
			return;
		}
		long position = Math.clamp(value, 0, current.get().durationMs());
		boolean wasPlaying = SpeakerPlayback.stop(be);
		SpeakerPlayback.update(be, queue.withPosition(position));
		if (wasPlaying) {
			SpeakerPlayback.play(be);
		}
	}

	private static void volume(PortableSpeakerBlockEntity be, long value) {
		be.setVolume(Math.clamp(value, SpeakerSettings.MIN_VOLUME, SpeakerSettings.MAX_VOLUME));
		SpeakerPlayback.markDirty(be);
	}

	private static void power(PortableSpeakerBlockEntity be, boolean on) {
		if (on != be.data().settings().powered()) {
			be.togglePower();
			SpeakerPlayback.markDirty(be);
		}
	}

	private static void repeat(PortableSpeakerBlockEntity be, long mode) {
		if (mode >= 0 && mode < RepeatMode.values().length) {
			SpeakerPlayback.update(be, SpeakerPlayback.queueOf(be).withRepeat(RepeatMode.values()[(int) mode]));
		}
	}

	private static void shuffle(PortableSpeakerBlockEntity be, boolean on) {
		SpeakerQueue queue = SpeakerPlayback.queueOf(be);
		if (on != queue.shuffle()) {
			SpeakerPlayback.update(be, queue.withShuffle(on, SpeakerPlayback.RNG));
		}
	}

	private static void move(PortableSpeakerBlockEntity be, UUID entryId, long index) {
		int clamped = (int) Math.clamp(index, 0, SpeakerQueue.MAX_ENTRIES);
		SpeakerPlayback.update(be, SpeakerPlayback.queueOf(be).move(entryId, clamped));
	}

	/** A placed speaker the player can reach, or null. */
	private static @Nullable PortableSpeakerBlockEntity speakerInReach(ServerPlayer player, BlockPos pos) {
		if (!(player.level() instanceof ServerLevel level) || !level.isLoaded(pos)
				|| !player.isWithinBlockInteractionRange(pos, 1.0)) {
			return null;
		}
		return level.getBlockEntity(pos) instanceof PortableSpeakerBlockEntity be ? be : null;
	}
}
