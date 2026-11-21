package com.ravecraft.mod.core.speaker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Follows which host (block, dropped item, carrier) currently holds each speaker id, and spots
 * duplicated copies. Placed hosts stay until lost; carriers must report every tick.
 */
public final class HostTracker<H> {
	/** Ticks without a report (carrier) or after a loss before an id is forgotten. */
	public static final int LOST_AFTER_TICKS = 20;

	public enum Verdict { SAME, MOVED, DUPLICATE }

	public record Expired<H>(UUID id, H lastHost) {}

	private static final class Entry<H> {
		H host; // null while lost
		H lastHost;
		boolean placed;
		long lastReport;
		long lostTick;
	}

	private final Map<UUID, Entry<H>> entries = new HashMap<>();

	/** A host that stays until lost: placed block, item on the ground. */
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

	/** A host that must report every tick: a carrier. */
	public Verdict report(UUID id, H host, long tick) {
		Entry<H> e = entries.get(id);
		if (e == null) {
			e = new Entry<>();
			entries.put(id, e);
		} else if (e.host != null) {
			if (e.placed) {
				return Verdict.DUPLICATE;
			}
			if (e.host.equals(host)) {
				if (e.lastReport == tick) {
					return Verdict.DUPLICATE;
				}
				e.lastReport = tick;
				return Verdict.SAME;
			}
			if (e.lastReport >= tick - 1) {
				return Verdict.DUPLICATE;
			}
		}
		e.host = host;
		e.lastHost = host;
		e.placed = false;
		e.lastReport = tick;
		return Verdict.MOVED;
	}

	/** {@code host} no longer holds {@code id}; ignored unless it is the current host. Grace starts. */
	public void lose(UUID id, H host, long tick) {
		Entry<H> e = entries.get(id);
		if (e != null && e.host != null && e.host.equals(host)) {
			e.host = null;
			e.lostTick = tick;
		}
	}

	/** Current host, empty while lost or unknown. */
	public Optional<H> host(UUID id) {
		Entry<H> e = entries.get(id);
		return e == null ? Optional.empty() : Optional.ofNullable(e.host);
	}

	/** Removes and returns what expired. */
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

	public void forget(UUID id) {
		entries.remove(id);
	}

	public void clear() {
		entries.clear();
	}
}
