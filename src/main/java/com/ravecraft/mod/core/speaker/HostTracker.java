package com.ravecraft.mod.core.speaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Follows which host (block, dropped item, carrier) currently holds each speaker id, and spots
 * duplicated copies. Placed hosts stay until lost; carriers must report every tick.
 */
public final class HostTracker<H> {
	/** Ticks without a report (carrier) or after a loss before an id is forgotten. */
	public static final int LOST_AFTER_TICKS = 20;

	/** What a report or a placement means: the same host, a move to a new host, or a copy of the id. */
	public enum Verdict { SAME, MOVED, DUPLICATE }

	/** An id whose host was lost for longer than the grace, with the last host that held it. */
	public record Expired<H>(UUID id, H lastHost) {}

	/** What is known of one id. */
	private static final class Entry<H> {
		H host; // null while lost
		H lastHost;
		boolean placed;
		long lastReport;
		long lostTick;
	}

	private final Map<UUID, Entry<H>> entries = new HashMap<>();

	/**
	 * A host that stays until lost: placed block, item on the ground.
	 *
	 * @param id the speaker's id
	 * @param host where the speaker is
	 * @param tick the current tick
	 * @return what the tracker made of it
	 */
	public Verdict place(UUID id, H host, long tick) {
		Entry<H> e = entries.get(id);
		if (e != null && e.host != null) {
			if (e.placed) {
				return e.host.equals(host) ? Verdict.SAME : Verdict.DUPLICATE;
			}
		} else if (e == null) {
			e = new Entry<>();
			entries.put(id, e);
		}
		e.host = host;
		e.lastHost = host;
		e.placed = true;
		return Verdict.MOVED;
	}

	/**
	 * A host that must report every tick: a carrier.
	 *
	 * @param id the speaker's id
	 * @param host where the speaker is
	 * @param tick the current tick
	 * @return what the tracker made of it
	 */
	public Verdict report(UUID id, H host, long tick) {
		Entry<H> e = entries.computeIfAbsent(id, k -> new Entry<>());
		if (e.host != null) {
			Verdict held = reportWhileHeld(e, host, tick);
			if (held != null) {
				return held;
			}
		}
		e.host = host;
		e.lastHost = host;
		e.placed = false;
		e.lastReport = tick;
		return Verdict.MOVED;
	}

	/**
	 * A report for an id some host holds: a copy when a placed host or another carrier that reported this tick or
	 * the last one holds it, the same host when it is this carrier. Null: another carrier lost it, a move.
	 */
	private static <H> @Nullable Verdict reportWhileHeld(Entry<H> e, H host, long tick) {
		if (e.placed) {
			return Verdict.DUPLICATE;
		}
		if (!host.equals(e.host)) {
			return e.lastReport >= tick - 1 ? Verdict.DUPLICATE : null;
		}
		if (e.lastReport == tick) {
			return Verdict.DUPLICATE;
		}
		e.lastReport = tick;
		return Verdict.SAME;
	}

	/**
	 * {@code host} no longer holds {@code id}; ignored unless it is the current host. Grace starts.
	 *
	 * @param id the speaker's id
	 * @param host where the speaker is
	 * @param tick the current tick
	 */
	public void lose(UUID id, H host, long tick) {
		Entry<H> e = entries.get(id);
		if (e != null && e.host != null && e.host.equals(host)) {
			e.host = null;
			e.lostTick = tick;
		}
	}

	/**
	 * Current host, empty while lost or unknown.
	 *
	 * @param id the speaker's id
	 * @return the host, or empty
	 */
	public Optional<H> host(UUID id) {
		Entry<H> e = entries.get(id);
		return e == null ? Optional.empty() : Optional.ofNullable(e.host);
	}

	/**
	 * Removes and returns what expired.
	 *
	 * @param tick the current tick
	 * @return the expired ids with their last host
	 */
	public List<Expired<H>> expire(long tick) {
		List<Expired<H>> out = new ArrayList<>();
		Iterator<Map.Entry<UUID, Entry<H>>> it = entries.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Entry<H>> me = it.next();
			Entry<H> e = me.getValue();
			boolean gone = e.host == null ? e.lostTick <= tick - LOST_AFTER_TICKS
					: !e.placed && e.lastReport <= tick - LOST_AFTER_TICKS;
			if (gone) {
				out.add(new Expired<>(me.getKey(), e.lastHost));
				it.remove();
			}
		}
		return out;
	}

	/**
	 * Whether {@code id} has an entry, hosted or within its grace after a loss.
	 *
	 * @param id the speaker's id
	 * @return true when the id is tracked
	 */
	public boolean contains(UUID id) {
		return entries.containsKey(id);
	}

	/**
	 * Forgets {@code id} at once, with no grace.
	 *
	 * @param id the speaker's id
	 */
	public void forget(UUID id) {
		entries.remove(id);
	}

	/** Forgets every id. */
	public void clear() {
		entries.clear();
	}
}
