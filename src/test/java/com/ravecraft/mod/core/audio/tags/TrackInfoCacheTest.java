package com.ravecraft.mod.core.audio.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackInfoCacheTest {
	private static TrackInfo info(String title) {
		return new TrackInfo(title, "", "", "", "", "MP3", 0, 0, 0, -1);
	}

	@Test
	void loadsOnce() {
		TrackInfoCache cache = new TrackInfoCache();
		TrackInfo a = info("a");
		TrackInfoCache.Lookup first = cache.lookup("a", 0);
		assertTrue(first.load());
		assertTrue(first.info().isEmpty());
		assertEquals(-1, first.knownModified());
		assertFalse(cache.lookup("a", 10).load());
		cache.loaded("a", 7, a, 20);
		TrackInfoCache.Lookup done = cache.lookup("a", 30);
		assertEquals(a, done.info().orElseThrow());
		assertFalse(done.load());
	}

	@Test
	void rechecksAfterDelay() {
		TrackInfoCache cache = new TrackInfoCache();
		TrackInfo a = info("a");
		cache.lookup("a", 0);
		cache.loaded("a", 7, a, 0);
		TrackInfoCache.Lookup due = cache.lookup("a", 5000);
		assertEquals(a, due.info().orElseThrow());
		assertTrue(due.load());
		assertEquals(7, due.knownModified());
		assertFalse(cache.lookup("a", 5001).load());
		cache.unchanged("a", 5002);
		TrackInfoCache.Lookup kept = cache.lookup("a", 10001);
		assertEquals(a, kept.info().orElseThrow());
		assertFalse(kept.load());
		assertTrue(cache.lookup("a", 10002).load());
	}

	@Test
	void changedFileReplaced() {
		TrackInfoCache cache = new TrackInfoCache();
		TrackInfo other = info("other");
		cache.lookup("a", 0);
		cache.loaded("a", 7, info("a"), 0);
		cache.lookup("a", 5000);
		cache.loaded("a", 8, other, 5003);
		assertEquals(other, cache.lookup("a", 5004).info().orElseThrow());
		TrackInfoCache.Lookup due = cache.lookup("a", 5003 + TrackInfoCache.RECHECK_MS);
		assertTrue(due.load());
		assertEquals(8, due.knownModified());
	}

	@Test
	void evictsLeastRecentlyUsed() {
		TrackInfoCache cache = new TrackInfoCache();
		for (int i = 0; i < TrackInfoCache.CAPACITY; i++) {
			cache.lookup("p" + i, 0);
			cache.loaded("p" + i, 1, info("p" + i), 0);
		}
		assertTrue(cache.lookup("p0", 1).info().isPresent());
		cache.lookup("p512", 1);
		cache.loaded("p512", 1, info("p512"), 1);
		TrackInfoCache.Lookup evicted = cache.lookup("p1", 2);
		assertTrue(evicted.info().isEmpty());
		assertTrue(evicted.load());
		assertTrue(cache.lookup("p0", 2).info().isPresent());
	}

	@Test
	void failedAllowsRetry() {
		TrackInfoCache cache = new TrackInfoCache();
		assertTrue(cache.lookup("a", 100).load());
		cache.failed("a", 100);
		TrackInfoCache.Lookup soon = cache.lookup("a", 101);
		assertFalse(soon.load());
		assertTrue(soon.info().isEmpty());
		assertEquals(-1, soon.knownModified());
		assertTrue(cache.lookup("a", 100 + TrackInfoCache.RECHECK_MS).load());
	}

	@Test
	void failedRecheckKeepsInfo() {
		TrackInfoCache cache = new TrackInfoCache();
		TrackInfo a = info("a");
		cache.lookup("a", 0);
		cache.loaded("a", 7, a, 0);
		assertTrue(cache.lookup("a", 5000).load());
		cache.failed("a", 5001);
		TrackInfoCache.Lookup after = cache.lookup("a", 5002);
		assertEquals(a, after.info().orElseThrow());
		assertFalse(after.load());
		assertEquals(7, after.knownModified());
	}
}
