package com.ravecraft.mod.core.audio.stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenBucketTest {
	private static final long SECOND = 1_000_000_000L;

	@Test
	void bucketAllowsBurstThenRate() {
		TokenBucket bucket = new TokenBucket(25_000, 40_000, 0);
		assertTrue(bucket.tryConsume(40_000, 0));
		assertFalse(bucket.tryConsume(1, 0));
		assertTrue(bucket.tryConsume(25_000, SECOND));
		assertFalse(bucket.tryConsume(1, SECOND));
	}

	@Test
	void bucketCapsRefill() {
		TokenBucket bucket = new TokenBucket(25_000, 40_000, 0);
		assertTrue(bucket.tryConsume(40_000, 0));
		assertTrue(bucket.tryConsume(40_000, 10 * SECOND));
		assertFalse(bucket.tryConsume(1, 10 * SECOND));
	}

	@Test
	void refusedConsumeTakesNothing() {
		TokenBucket bucket = new TokenBucket(25_000, 40_000, 0);
		assertFalse(bucket.tryConsume(40_001, 0));
		assertTrue(bucket.tryConsume(40_000, 0));
	}
}
