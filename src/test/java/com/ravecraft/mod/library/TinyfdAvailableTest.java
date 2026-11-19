package com.ravecraft.mod.library;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/** Loads the tinyfd native library without opening any window. */
class TinyfdAvailableTest {

	@Test
	void nativeLibraryLoads() {
		assertNotNull(TinyFileDialogs.tinyfd_getGlobalChar("tinyfd_version"));
	}
}
