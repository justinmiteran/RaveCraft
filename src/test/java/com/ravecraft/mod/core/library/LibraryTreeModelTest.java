package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ravecraft.mod.core.library.LibraryTreeModel.Row;
import com.ravecraft.mod.core.library.SourceNode.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class LibraryTreeModelTest {
	private static SourceNode folder(String id, String name) {
		return new SourceNode(id, Kind.FOLDER, name, 1, false);
	}

	private static SourceNode track(String id, String name) {
		return new SourceNode(id, Kind.TRACK, name, -1, false);
	}

	private static final SourceNode MUSIC = folder("/m", "Musique");
	private static final SourceNode SETS = folder("/s", "Sets DJ");
	private static final SourceNode CHILL = folder("/m/chill", "Chill");
	private static final SourceNode INTRO = track("/m/intro.ogg", "Intro");
	private static final SourceNode NIGHT = track("/m/chill/night.mp3", "Night Drive");

	/** A model with both roots loaded. */
	private static LibraryTreeModel loaded() {
		LibraryTreeModel model = new LibraryTreeModel();
		model.startLoading();
		assertTrue(model.setRoots(model.generation(), List.of(MUSIC, SETS)));
		return model;
	}

	private static List<String> render(LibraryTreeModel model) {
		return model.rows().stream()
				.map(row -> "  ".repeat(row.depth()) + (row.loading() ? "…" : row.node().name()) + (row.expanded() ? "/" : ""))
				.toList();
	}

	@Test
	void showsALoadingRowUntilTheRootsArrive() {
		LibraryTreeModel model = new LibraryTreeModel();
		model.startLoading();
		assertTrue(model.loadingRoots());
		assertFalse(model.isEmpty());
		assertEquals(List.of("…"), render(model));
		model.setRoots(model.generation(), List.of());
		assertFalse(model.loadingRoots());
		assertTrue(model.isEmpty());
		assertEquals(List.of(), render(model));
	}

	@Test
	void staleResultsAfterAReloadAreDropped() {
		LibraryTreeModel model = loaded();
		int old = model.generation();
		assertTrue(model.toggle(MUSIC));
		model.startLoading();
		assertFalse(model.setRoots(old, List.of(SETS)));
		assertFalse(model.setChildren(old, MUSIC.id(), List.of(INTRO)));
		assertEquals(List.of("…"), render(model));
		model.setRoots(model.generation(), List.of(MUSIC));
		assertEquals(List.of("Musique"), render(model));
	}

	@Test
	void expandingLoadsOnceAndShowsALoadingRowMeanwhile() {
		LibraryTreeModel model = loaded();
		assertTrue(model.toggle(MUSIC), "first expansion needs a load");
		assertEquals(List.of("Musique/", "  …", "Sets DJ"), render(model));
		assertTrue(model.setChildren(model.generation(), MUSIC.id(), List.of(CHILL, INTRO)));
		assertEquals(List.of("Musique/", "  Chill", "  Intro", "Sets DJ"), render(model));
		assertFalse(model.toggle(MUSIC), "collapse");
		assertEquals(List.of("Musique", "Sets DJ"), render(model));
		assertFalse(model.toggle(MUSIC), "children already loaded");
		assertEquals(List.of("Musique/", "  Chill", "  Intro", "Sets DJ"), render(model));
	}

	@Test
	void tracksAndMissingFoldersDoNotExpand() {
		LibraryTreeModel model = new LibraryTreeModel();
		model.startLoading();
		SourceNode gone = new SourceNode("/gone", Kind.FOLDER, "gone", 0, true);
		model.setRoots(model.generation(), List.of(gone, INTRO));
		assertFalse(model.toggle(gone));
		assertFalse(model.toggle(INTRO));
		assertEquals(List.of("gone", "Intro"), render(model));
	}

	@Test
	void filterKeepsTheAncestorsOfLoadedMatchesCaseInsensitively() {
		LibraryTreeModel model = loaded();
		model.toggle(MUSIC);
		model.setChildren(model.generation(), MUSIC.id(), List.of(CHILL, INTRO));
		model.toggle(CHILL);
		model.setChildren(model.generation(), CHILL.id(), List.of(NIGHT));
		model.toggle(CHILL); // collapsed, but its children are loaded: the search still finds them
		model.setFilter("  NIGHT ");
		assertEquals(List.of("Musique/", "  Chill/", "    Night Drive"), render(model));
		model.setFilter("sets");
		assertEquals(List.of("Sets DJ"), render(model));
		model.setFilter("nothing");
		assertEquals(List.of(), render(model));
		model.setFilter("");
		assertEquals(List.of("Musique/", "  Chill", "  Intro", "Sets DJ"), render(model));
	}

	@Test
	void filterHidesLoadingRows() {
		LibraryTreeModel model = loaded();
		model.toggle(MUSIC);
		model.setFilter("mus");
		assertEquals(List.of("Musique"), render(model));
	}

	@Test
	void rowsAreRebuiltOnlyAfterAChange() {
		LibraryTreeModel model = loaded();
		List<Row> first = model.rows();
		assertSame(first, model.rows());
		model.setFilter("");
		assertSame(first, model.rows(), "same filter is not a change");
		model.toggle(MUSIC);
		assertFalse(first == model.rows());
	}


	@Test
	void flatViewShowsTheIndexEntriesAsGiven() {
		LibraryTreeModel model = loaded();
		model.setFlat(true);
		assertEquals(List.of(), render(model));
		model.setFlatNodes(List.of(INTRO, NIGHT));
		assertEquals(List.of("Intro", "Night Drive"), render(model));
		assertEquals(2, model.flatCount());
		model.setFlatNodes(List.of(NIGHT));
		assertEquals(List.of("Night Drive"), render(model), "a newer index replaces the list, never adds to it");
	}

	@Test
	void flatViewIsFilteredByName() {
		LibraryTreeModel model = loaded();
		model.setFlat(true);
		model.setFlatNodes(List.of(INTRO, NIGHT));
		model.setFilter(" night ");
		assertEquals(List.of("Night Drive"), render(model));
	}

	@Test
	void theFlatListIsKeptWhenSwitchingViewsAndReloading() {
		LibraryTreeModel model = loaded();
		model.setFlatNodes(List.of(INTRO));
		model.setFlat(true);
		model.setFlat(false);
		model.startLoading();
		model.setFlat(true);
		assertEquals(List.of("Intro"), render(model));
	}

	@Test
	void switchingBackToTheTreeKeepsItsExpansion() {
		LibraryTreeModel model = loaded();
		model.toggle(MUSIC);
		model.setChildren(model.generation(), MUSIC.id(), List.of(CHILL, INTRO));
		model.setFlat(true);
		model.setFlat(false);
		assertEquals(List.of("Musique/", "  Chill", "  Intro", "Sets DJ"), render(model));
	}

	@Test
	void refreshKeepsTheTreeShownAndAsksForTheOpenFoldersAgain() {
		LibraryTreeModel model = loaded();
		model.toggle(MUSIC);
		model.setChildren(model.generation(), MUSIC.id(), List.of(CHILL, INTRO));
		int old = model.generation();
		assertEquals(List.of(MUSIC.id()), model.refresh());
		assertFalse(model.loadingRoots(), "the old roots stay shown");
		assertEquals(List.of("Musique/", "  Chill", "  Intro", "Sets DJ"), render(model));
		assertFalse(model.setChildren(old, MUSIC.id(), List.of()), "results of before the refresh are dropped");
		assertTrue(model.setRoots(model.generation(), List.of(MUSIC)));
		assertTrue(model.setChildren(model.generation(), MUSIC.id(), List.of(INTRO)));
		assertEquals(List.of("Musique/", "  Intro"), render(model));
	}

	@Test
	void expandOpensSeveralFoldersAndAsksOnlyForTheUnloadedOnes() {
		LibraryTreeModel model = loaded();
		assertTrue(model.toggle(MUSIC));
		model.setChildren(model.generation(), MUSIC.id(), List.of(CHILL, INTRO));
		model.toggle(MUSIC);
		assertEquals(List.of(CHILL.id()), model.expand(List.of(MUSIC.id(), CHILL.id())));
		assertEquals(List.of("Musique/", "  Chill/", "    …", "  Intro", "Sets DJ"), render(model));
		assertEquals(List.of(), model.expand(List.of(CHILL.id())), "already loading");
	}
}
