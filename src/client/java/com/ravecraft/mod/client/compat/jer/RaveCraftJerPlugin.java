package com.ravecraft.mod.client.compat.jer;

import com.ravecraft.mod.core.world.OreDrops;
import com.ravecraft.mod.core.world.OreVein;
import com.ravecraft.mod.core.world.OreVeins;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModItems;
import jeresources.api.IJERAPI;
import jeresources.api.IJERPlugin;
import jeresources.api.IWorldGenRegistry;
import jeresources.api.conditionals.Conditional;
import jeresources.api.distributions.DistributionBase;
import jeresources.api.distributions.DistributionSquare;
import jeresources.api.distributions.DistributionTriangular;
import jeresources.api.drop.LootDrop;
import jeresources.api.restrictions.Restriction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Shows in Just Enough Resources where shale, oil shale and neodymium generate, from the same {@link OreVeins} the
 * world generation uses. Loaded only through the {@code jer_mod_plugin} entrypoint, so nothing here runs without JER.
 */
public final class RaveCraftJerPlugin implements IJERPlugin {
	@Override
	public void receive(IJERAPI api) {
		IWorldGenRegistry worldGen = api.getWorldGenRegistry();
		worldGen.register(stack(ModBlocks.SHALE), distribution(OreVeins.SHALE), Restriction.OVERWORLD, true);
		worldGen.register(stack(ModBlocks.OIL_SHALE), distribution(OreVeins.OIL_SHALE), Restriction.OVERWORLD, true,
				fortune(ModItems.BITUMEN, OreDrops.BITUMEN_MIN, OreDrops.BITUMEN_MAX));
		worldGen.register(stack(ModBlocks.DEEPSLATE_NEODYMIUM_ORE), distribution(OreVeins.NEODYMIUM),
				Restriction.OVERWORLD, true, fortune(ModItems.RAW_NEODYMIUM, OreDrops.RAW_NEODYMIUM, OreDrops.RAW_NEODYMIUM));
	}

	/** The height distribution of a vein, as JER draws it: a triangle around the middle height, or a flat band. */
	private static DistributionBase distribution(OreVein vein) {
		return vein.triangle()
				? new DistributionTriangular(vein.count(), vein.size(), vein.middleY(), vein.halfRange())
				: new DistributionSquare(vein.count(), vein.size(), vein.minY(), vein.maxY());
	}

	/** A drop raised by Fortune, shown from its smallest count to its largest with Fortune III, like vanilla ores. */
	private static LootDrop fortune(ItemLike item, int min, int max) {
		return new LootDrop(stack(item), min, OreDrops.maxWithFortune(max), Conditional.affectedByFortune);
	}

	private static ItemStack stack(ItemLike item) {
		return new ItemStack(item);
	}
}
