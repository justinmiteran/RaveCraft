package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExclusionsTest {
	@Test
	void comparesNormalizedAbsolutePaths() {
		Path music = Path.of("music").toAbsolutePath();
		Exclusions exclusions = new Exclusions(List.of(music.resolve("Jingles")));
		assertTrue(exclusions.hides(music.resolve("Jingles")));
		assertTrue(exclusions.hides(music.resolve("Chill").resolve("..").resolve("Jingles")));
		assertFalse(exclusions.hides(music.resolve("Jingles").resolve("Old")), "only the folder itself");
		assertFalse(exclusions.hides(music));
	}
}
