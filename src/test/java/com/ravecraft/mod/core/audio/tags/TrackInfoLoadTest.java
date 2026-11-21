package com.ravecraft.mod.core.audio.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackInfoLoadTest {
	@TempDir
	Path dir;

	@Test
	void missingFileNeverCachedGivesEmptyInfo() {
		TrackInfoLoad.Result result = TrackInfoLoad.load(dir.resolve("gone.mp3"), TrackInfoCache.UNKNOWN_MODIFIED);
		assertFalse(result.unchanged());
		assertEquals(TrackInfo.empty("MP3"), result.info());
	}

	@Test
	void missingFileRecheckedIsUnchanged() {
		Path gone = dir.resolve("gone.mp3");
		long recorded = TrackInfoLoad.load(gone, TrackInfoCache.UNKNOWN_MODIFIED).modified();
		assertTrue(TrackInfoLoad.load(gone, recorded).unchanged());
	}

	@Test
	void fileBackAfterMissingIsRead() throws Exception {
		Path track = dir.resolve("back.wav");
		long recorded = TrackInfoLoad.load(track, TrackInfoCache.UNKNOWN_MODIFIED).modified();
		Files.write(track, new byte[16]);
		TrackInfoLoad.Result result = TrackInfoLoad.load(track, recorded);
		assertFalse(result.unchanged());
		assertEquals(Files.getLastModifiedTime(track).toMillis(), result.modified());
	}

	@Test
	void sameDateIsUnchanged() throws Exception {
		Path track = dir.resolve("same.wav");
		Files.write(track, new byte[16]);
		long modified = Files.getLastModifiedTime(track).toMillis();
		assertTrue(TrackInfoLoad.load(track, modified).unchanged());
	}
}
