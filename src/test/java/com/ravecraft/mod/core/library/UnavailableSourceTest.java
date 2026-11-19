package com.ravecraft.mod.core.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnavailableSourceTest {
	@Test
	void isLockedAndEmpty() throws IOException {
		MusicSource usb = new UnavailableSource("usb");
		SourceNode node = new SourceNode("x", SourceNode.Kind.FOLDER, "x", 0, false);
		assertEquals("usb", usb.id());
		assertFalse(usb.available());
		assertEquals(List.of(), usb.roots());
		assertEquals(List.of(), usb.children(node));
		assertEquals(List.of(), usb.tracksOf(node));
	}
}
