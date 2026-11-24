package com.ravecraft.mod.core.world;

/**
 * The ore and rock veins of the overworld (spec 3.7), shared by the datagen that writes the features and by the
 * recipe viewer plugins that show them.
 */
public final class OreVeins {
	/** Shale pockets, like granite. */
	public static final OreVein SHALE = new OreVein(64, 2, 0, 80, false);
	/** Oil shale veins, through stone and shale. */
	public static final OreVein OIL_SHALE = new OreVein(17, 12, 20, 80, false);
	/** Extra oil shale veins per chunk in deserts and badlands. */
	public static final int OIL_SHALE_DRY_COUNT = 8;
	/** Neodymium, deepslate only, densest at y -40. */
	public static final OreVein NEODYMIUM = new OreVein(6, 3, -64, -16, true);
	/** The share of neodymium blocks dropped when they would touch air, like vanilla's deep ores. */
	public static final float NEODYMIUM_AIR_DISCARD = 0.5F;

	private OreVeins() {
	}
}
