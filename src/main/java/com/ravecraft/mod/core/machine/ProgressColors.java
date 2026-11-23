package com.ravecraft.mod.core.machine;

import java.util.HashMap;
import java.util.Map;

/**
 * The colour of a machine's progress arrow for each input item, read from a resource pack file so packs can recolour
 * it or add items from other mods. Items without a colour get a white arrow, like the furnace's.
 */
public final class ProgressColors {
	/** A table without colours: every arrow is white. */
	public static final ProgressColors EMPTY = new ProgressColors(Map.of(), 0);

	/** The arrow colour of items without an entry, opaque white. */
	public static final int WHITE = 0xFFFFFFFF;

	private static final int OPAQUE = 0xFF000000;
	private static final int HEX_DIGITS = 6;
	private static final int HEX_RADIX = 16;
	private static final String HASH = "#";

	private final Map<String, Integer> colors;
	private final int skipped;

	private ProgressColors(Map<String, Integer> colors, int skipped) {
		this.colors = Map.copyOf(colors);
		this.skipped = skipped;
	}

	/**
	 * Reads a table of {@code "#RRGGBB"} colours (the {@code #} is optional, case ignored); malformed entries are
	 * skipped and counted.
	 *
	 * @param entries item id to colour text
	 * @return the colour table
	 */
	public static ProgressColors parse(Map<String, String> entries) {
		Map<String, Integer> colors = new HashMap<>();
		int skipped = 0;
		for (Map.Entry<String, String> entry : entries.entrySet()) {
			Integer rgb = parseHex(entry.getValue());
			if (rgb == null) {
				skipped++;
			} else {
				colors.put(entry.getKey(), OPAQUE | rgb);
			}
		}
		return new ProgressColors(colors, skipped);
	}

	/** Parses {@code RRGGBB} with an optional leading {@code #}, or returns null when it is not six hex digits. */
	private static Integer parseHex(String text) {
		String hex = text.startsWith(HASH) ? text.substring(HASH.length()) : text;
		if (hex.length() != HEX_DIGITS) {
			return null;
		}
		for (int i = 0; i < hex.length(); i++) {
			if (Character.digit(hex.charAt(i), HEX_RADIX) < 0) {
				return null;
			}
		}
		return Integer.parseInt(hex, HEX_RADIX);
	}

	/**
	 * Returns the arrow colour for an input item.
	 *
	 * @param itemId the item id, like {@code minecraft:copper_ingot}
	 * @return the opaque ARGB colour, white when the item has none
	 */
	public int colorOf(String itemId) {
		return colors.getOrDefault(itemId, WHITE);
	}

	/**
	 * Returns how many entries were malformed and skipped.
	 *
	 * @return the number of skipped entries
	 */
	public int skipped() {
		return skipped;
	}
}
