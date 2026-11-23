package com.ravecraft.mod.core.world;

/**
 * What the ores drop (spec 3.7), shared by the loot tables and by the recipe viewer plugins that show them. Both drops
 * use vanilla's ore formula with Fortune.
 */
public final class OreDrops {
	/** Raw neodymium per ore block, before Fortune. */
	public static final int RAW_NEODYMIUM = 1;
	/** Least bitumen per oil shale block, before Fortune. */
	public static final int BITUMEN_MIN = 1;
	/** Most bitumen per oil shale block, before Fortune. */
	public static final int BITUMEN_MAX = 3;
	/** Largest multiplier of vanilla's ore formula, reached with Fortune III. */
	private static final int FORTUNE_MAX_MULTIPLIER = 4;

	private OreDrops() {
	}

	/**
	 * Returns the largest drop with Fortune III, as the ore formula multiplies the base count by one to four.
	 *
	 * @param base the largest drop without Fortune
	 * @return the largest drop with Fortune III
	 */
	public static int maxWithFortune(int base) {
		return base * FORTUNE_MAX_MULTIPLIER;
	}
}
