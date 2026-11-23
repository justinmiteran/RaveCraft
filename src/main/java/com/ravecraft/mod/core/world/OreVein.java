package com.ravecraft.mod.core.world;

/**
 * One kind of ore or rock vein: its size, how many veins a chunk gets and between which heights.
 *
 * @param size the blocks per vein, as in vanilla ore features
 * @param count the veins per chunk
 * @param minY the lowest height, inclusive
 * @param maxY the highest height, inclusive
 * @param triangle whether the veins thicken towards the middle height, like vanilla's trapezoid height range,
 *     rather than spreading evenly
 */
public record OreVein(int size, int count, int minY, int maxY, boolean triangle) {
	/**
	 * Checks the values.
	 *
	 * @throws IllegalArgumentException when the size or count is not positive, or the heights are reversed
	 */
	public OreVein {
		if (size <= 0 || count <= 0 || minY > maxY) {
			throw new IllegalArgumentException("Bad ore vein: size " + size + ", count " + count + ", y " + minY
					+ ".." + maxY);
		}
	}

	/**
	 * Returns the middle height, where a triangle range is the densest.
	 *
	 * @return the middle height, rounded down
	 */
	public int middleY() {
		return Math.floorDiv(minY + maxY, 2);
	}

	/**
	 * Returns half the height range.
	 *
	 * @return the distance from the middle height to either bound, rounded down
	 */
	public int halfRange() {
		return (maxY - minY) / 2;
	}
}
