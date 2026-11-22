package com.ravecraft.mod.datagen;

import com.ravecraft.mod.registry.ModBlocks;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

/** Crafting recipes. */
public class RaveCraftRecipeProvider extends FabricRecipeProvider {
	/**
	 * A provider writing to {@code output}.
	 *
	 * @param output the pack the files go to
	 * @param registries the registries, once loaded
	 */
	public RaveCraftRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, BootstrapContext<Recipe<?>> recipes,
			BootstrapContext<Advancement> advancements) {
		return new RecipeProvider(recipes, advancements) {
			@Override
			public void buildRecipes() {
				// PROVISIONAL: replaced by the materials sub-project (docs/SPEC.md).
				shaped(RecipeCategory.REDSTONE, ModBlocks.PORTABLE_SPEAKER)
						.define('F', Items.IRON_INGOT)
						.define('N', Items.NOTE_BLOCK)
						.define('R', Items.REDSTONE)
						.pattern("FFF")
						.pattern("NRN")
						.pattern("FFF")
						.unlockedBy("has_note_block", has(Items.NOTE_BLOCK))
						.save(output);
				// Dyeable like leather armour: the speaker plus any dyes, colours mixed, other components kept.
				dyedItem(ModBlocks.PORTABLE_SPEAKER.asItem(), "portable_speaker_dye");
			}
		};
	}
}
