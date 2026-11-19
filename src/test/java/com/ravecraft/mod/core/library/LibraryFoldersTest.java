package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.library.LibraryFolders.Added;
import com.ravecraft.mod.core.library.LibraryFolders.Result;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryFoldersTest {
	@TempDir
	Path dir;

	private static String quoted(Path path) {
		return "\"" + path.toString().replace("\\", "\\\\") + "\"";
	}

	@Test
	void addRejectsDuplicate() {
		LibraryFolders one = LibraryFolders.empty().add(dir.resolve("a")).folders();
		Added again = one.add(dir.resolve("a"));
		assertEquals(Result.DUPLICATE, again.result());
		assertEquals(one, again.folders());
	}

	@Test
	void addRejectsFolderInsideExisting() {
		LibraryFolders one = LibraryFolders.empty().add(dir.resolve("a")).folders();
		Added inner = one.add(dir.resolve("a").resolve("b"));
		assertEquals(Result.INSIDE_EXISTING, inner.result());
		assertEquals(one, inner.folders());
	}

	@Test
	void addReplacesChildren() {
		LibraryFolders folders = LibraryFolders.empty()
				.add(dir.resolve("a").resolve("x")).folders()
				.add(dir.resolve("c")).folders()
				.add(dir.resolve("a").resolve("y")).folders();
		Added parent = folders.add(dir.resolve("a"));
		assertEquals(Result.REPLACED_CHILDREN, parent.result());
		assertEquals(List.of(dir.resolve("c"), dir.resolve("a")), parent.folders().paths());
	}

	@Test
	void capsAtThirtyTwo() {
		LibraryFolders folders = LibraryFolders.empty();
		for (int i = 0; i < LibraryFolders.MAX; i++) {
			Added added = folders.add(dir.resolve("f" + i));
			assertEquals(Result.ADDED, added.result());
			folders = added.folders();
		}
		Added extra = folders.add(dir.resolve("extra"));
		assertEquals(Result.FULL, extra.result());
		assertEquals(LibraryFolders.MAX, extra.folders().paths().size());
	}

	@Test
	void removeAbsentKeepsList() {
		LibraryFolders one = LibraryFolders.empty().add(dir.resolve("a")).folders();
		assertEquals(one, one.remove(dir.resolve("zzz")));
		assertTrue(one.remove(dir.resolve("a")).paths().isEmpty());
	}

	@Test
	void malformedJsonIsEmpty() {
		assertTrue(LibraryFolders.fromJson("{not json").paths().isEmpty());
		assertTrue(LibraryFolders.fromJson("[1,2]").paths().isEmpty());
		assertTrue(LibraryFolders.fromJson("").paths().isEmpty());
	}

	@Test
	void unknownFieldsAndBadEntriesAreSkipped() {
		String json = "{\"other\":1,\"folders\":[3,null," + quoted(dir.resolve("a")) + "]}";
		assertEquals(List.of(dir.resolve("a")), LibraryFolders.fromJson(json).paths());
	}

	@Test
	void tooManyFoldersKeepsFirstThirtyTwo() {
		List<String> names = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			names.add(quoted(dir.resolve("f" + i)));
		}
		LibraryFolders folders = LibraryFolders.fromJson("{\"folders\":[" + String.join(",", names) + "]}");
		assertEquals(LibraryFolders.MAX, folders.paths().size());
		assertEquals(dir.resolve("f0"), folders.paths().get(0));
	}

	@Test
	void jsonRoundTrips() {
		LibraryFolders folders = LibraryFolders.empty()
				.add(dir.resolve("a")).folders()
				.add(dir.resolve("b")).folders();
		assertEquals(folders, LibraryFolders.fromJson(folders.toJson()));
	}

	@Test
	void firstRunUsesMusicFolder() throws IOException {
		assertTrue(LibraryFolders.firstRun(dir).paths().isEmpty());
		Files.createDirectory(dir.resolve("Music"));
		assertEquals(List.of(dir.resolve("Music")), LibraryFolders.firstRun(dir).paths());
	}

	@Test
	void siblingWithSharedPrefixIsNotInside() {
		LibraryFolders a = LibraryFolders.empty().add(dir.resolve("a")).folders();
		assertEquals(Result.ADDED, a.add(dir.resolve("ab")).result());
		LibraryFolders ab = LibraryFolders.empty().add(dir.resolve("ab")).folders();
		assertEquals(Result.ADDED, ab.add(dir.resolve("a")).result());
	}

	@Test
	void addNormalizesPath() {
		LibraryFolders a = LibraryFolders.empty().add(dir.resolve("a")).folders();
		assertEquals(Result.DUPLICATE, a.add(dir.resolve("x").resolve("..").resolve("a")).result());
	}

	@Test
	void fromJsonSkipsBlankAndRelative() {
		String json = "{\"folders\":[\"\",\"   \",\"music\",\"../up\"," + quoted(dir.resolve("a")) + "]}";
		assertEquals(List.of(dir.resolve("a")), LibraryFolders.fromJson(json).paths());
	}
}
