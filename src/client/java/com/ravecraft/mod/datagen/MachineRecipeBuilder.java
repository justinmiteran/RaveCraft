package com.ravecraft.mod.datagen;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.machine.MachineConstants;
import com.ravecraft.mod.recipe.CountedIngredient;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.RecipeUnlockAdvancementBuilder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ItemLike;

/**
 * Builds a machine recipe and its unlock advancement: one {@code has_<item>} criterion per ingredient (any of them
 * unlocks it), {@code has_<tag path>} for a tag. Saved as {@code ravecraft:<result>_from_<process>}.
 */
public final class MachineRecipeBuilder {
	private final MachineKind kind;
	private final ItemStackTemplate result;
	private final RecipeProvider recipes;
	private final List<CountedIngredient> ingredients = new ArrayList<>();
	private final RecipeUnlockAdvancementBuilder advancement = new RecipeUnlockAdvancementBuilder();
	private Optional<Ingredient> mold = Optional.empty();
	private int processingTime = MachineConstants.DEFAULT_PROCESSING_TICKS;

	private MachineRecipeBuilder(MachineKind kind, ItemStackTemplate result, RecipeProvider recipes) {
		this.kind = kind;
		this.result = result;
		this.recipes = recipes;
	}

	/**
	 * Starts a recipe.
	 *
	 * @param kind the machine kind
	 * @param result the result item
	 * @param count the result count
	 * @param recipes the provider, for tag ingredients and unlock criteria
	 * @return the builder
	 */
	public static MachineRecipeBuilder machine(MachineKind kind, ItemLike result, int count, RecipeProvider recipes) {
		return new MachineRecipeBuilder(kind, new ItemStackTemplate(result.asItem(), count), recipes);
	}

	/**
	 * Adds an ingredient and its unlock criterion.
	 *
	 * @param item the ingredient
	 * @param count the items consumed per operation
	 * @return this builder
	 */
	public MachineRecipeBuilder input(ItemLike item, int count) {
		ingredients.add(new CountedIngredient(Ingredient.of(item), count));
		advancement.unlockedBy("has_" + name(item), recipes.has(item));
		return this;
	}

	/**
	 * Adds an ingredient by tag (a convention tag, so other mods' materials work) and its unlock criterion.
	 *
	 * @param tag the ingredient tag
	 * @param count the items consumed per operation
	 * @return this builder
	 */
	public MachineRecipeBuilder input(TagKey<Item> tag, int count) {
		ingredients.add(new CountedIngredient(recipes.tag(tag), count));
		advancement.unlockedBy("has_" + tag.location().getPath().replace('/', '_'), recipes.has(tag));
		return this;
	}

	/**
	 * Sets the mold, for kinds that need one.
	 *
	 * @param item the mold
	 * @return this builder
	 */
	public MachineRecipeBuilder mold(ItemLike item) {
		mold = Optional.of(Ingredient.of(item));
		advancement.unlockedBy("has_" + name(item), recipes.has(item));
		return this;
	}

	/**
	 * Overrides the operation time.
	 *
	 * @param ticks the operation time, in ticks
	 * @return this builder
	 */
	public MachineRecipeBuilder processingTime(int ticks) {
		processingTime = ticks;
		return this;
	}

	/**
	 * Writes the recipe and its advancement.
	 *
	 * @param output the recipe output
	 */
	public void save(RecipeOutput output) {
		ResourceKey<Recipe<?>> id = ResourceKey.create(Registries.RECIPE,
				RaveCraft.id(name(result.item().value()) + "_from_" + kind.process()));
		MachineRecipe recipe = new MachineRecipe(kind, true, ingredients, mold, result, processingTime);
		output.accept(id, recipe, advancement.build(output, id, RecipeCategory.MISC));
	}

	private static String name(ItemLike item) {
		return BuiltInRegistries.ITEM.getKey(item.asItem()).getPath();
	}
}
