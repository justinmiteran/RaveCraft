package com.ravecraft.mod.recipe;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ravecraft.mod.core.machine.MachineConstants;
import com.ravecraft.mod.core.machine.ShapelessMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * A shapeless machine recipe: one to three counted ingredients, each in its own input slot, an optional mold that stays
 * in the machine, a result and an operation time. Every machine kind shares this class; the kind gives the type, the
 * serializer, the recipe book tab and whether a mold is required.
 */
public final class MachineRecipe implements Recipe<MachineRecipeInput> {
	private final MachineKind kind;
	private final Recipe.CommonInfo commonInfo;
	private final List<CountedIngredient> ingredients;
	private final Optional<Ingredient> mold;
	private final ItemStackTemplate result;
	private final int processingTime;
	private @Nullable PlacementInfo placementInfo;

	/**
	 * Creates a recipe.
	 *
	 * @param kind the machine kind
	 * @param showNotification whether unlocking the recipe shows a toast
	 * @param ingredients the counted ingredients, 1 to {@link MachineConstants#MAX_INGREDIENTS}
	 * @param mold the mold, present exactly when the kind needs one
	 * @param result the result of one operation
	 * @param processingTime the operation time, in ticks
	 */
	public MachineRecipe(MachineKind kind, boolean showNotification, List<CountedIngredient> ingredients,
			Optional<Ingredient> mold, ItemStackTemplate result, int processingTime) {
		this(kind, new Recipe.CommonInfo(showNotification), ingredients, mold, result, processingTime);
	}

	private MachineRecipe(MachineKind kind, Recipe.CommonInfo commonInfo, List<CountedIngredient> ingredients,
			Optional<Ingredient> mold, ItemStackTemplate result, int processingTime) {
		this.kind = kind;
		this.commonInfo = commonInfo;
		this.ingredients = List.copyOf(ingredients);
		this.mold = mold;
		this.result = result;
		this.processingTime = processingTime;
	}

	/**
	 * The JSON form of a kind's recipes; a mold where the kind has none, or a missing one where it needs one, is an
	 * error.
	 *
	 * @param kind the machine kind
	 * @return the map codec
	 */
	static MapCodec<MachineRecipe> mapCodec(MachineKind kind) {
		MapCodec<MachineRecipe> codec = RecordCodecBuilder.mapCodec(i -> i.group(
				Recipe.CommonInfo.MAP_CODEC.forGetter(r -> r.commonInfo),
				CountedIngredient.CODEC.listOf(1, MachineConstants.MAX_INGREDIENTS).fieldOf("ingredients")
						.forGetter(MachineRecipe::ingredients),
				Ingredient.CODEC.optionalFieldOf("mold").forGetter(MachineRecipe::mold),
				ItemStackTemplate.CODEC.fieldOf("result").forGetter(MachineRecipe::result),
				ExtraCodecs.POSITIVE_INT.optionalFieldOf("processing_time", MachineConstants.DEFAULT_PROCESSING_TICKS)
						.forGetter(MachineRecipe::processingTime))
				.apply(i, (info, ingredients, mold, result, time) ->
						new MachineRecipe(kind, info, ingredients, mold, result, time)));
		return codec.validate(recipe -> check(kind, recipe));
	}

	/** A recipe fits its machine: a mold exactly when the kind needs one, no more ingredients than inputs. */
	private static DataResult<MachineRecipe> check(MachineKind kind, MachineRecipe recipe) {
		if (recipe.ingredients.size() > kind.inputs()) {
			return DataResult.error(() -> kind + " recipes take at most " + kind.inputs() + " ingredients");
		}
		return recipe.mold.isPresent() == kind.needsMold() ? DataResult.success(recipe)
				: DataResult.error(() -> kind.needsMold() ? kind + " recipes need a mold"
						: kind + " recipes take no mold");
	}

	/**
	 * The network form of a kind's recipes.
	 *
	 * @param kind the machine kind
	 * @return the stream codec
	 */
	static StreamCodec<RegistryFriendlyByteBuf, MachineRecipe> streamCodec(MachineKind kind) {
		return StreamCodec.composite(
				Recipe.CommonInfo.STREAM_CODEC, r -> r.commonInfo,
				CountedIngredient.STREAM_CODEC.apply(ByteBufCodecs.list(MachineConstants.MAX_INGREDIENTS)),
				MachineRecipe::ingredients,
				ByteBufCodecs.optional(Ingredient.CONTENTS_STREAM_CODEC), MachineRecipe::mold,
				ItemStackTemplate.STREAM_CODEC, MachineRecipe::result,
				ByteBufCodecs.VAR_INT, MachineRecipe::processingTime,
				(info, ingredients, mold, result, time) -> new MachineRecipe(kind, info, ingredients, mold, result, time));
	}

	/**
	 * Finds the input slot of each ingredient.
	 *
	 * @param input the machine contents
	 * @return for each ingredient the index of its slot, or empty when the inputs do not match
	 */
	public Optional<int[]> assignment(MachineRecipeInput input) {
		return ShapelessMatcher.match(ingredients, CountedIngredient::count,
				(ingredient, stack) -> ingredient.ingredient().test(stack), input.inputs(), ItemStack::isEmpty,
				ItemStack::getCount);
	}

	@Override
	public boolean matches(MachineRecipeInput input, Level level) {
		return mold.map(m -> m.test(input.mold())).orElse(true) && assignment(input).isPresent();
	}

	@Override
	public ItemStack assemble(MachineRecipeInput input) {
		return result.create();
	}

	@Override
	public boolean showNotification() {
		return commonInfo.showNotification();
	}

	@Override
	public String group() {
		return "";
	}

	@Override
	public RecipeSerializer<MachineRecipe> getSerializer() {
		return kind.serializer();
	}

	@Override
	public RecipeType<MachineRecipe> getType() {
		return kind.type();
	}

	/** Each ingredient repeated by its count, then the mold, so the recipe book counts the items a craft needs. */
	@Override
	public PlacementInfo placementInfo() {
		if (placementInfo == null) {
			List<Ingredient> needed = new ArrayList<>();
			for (CountedIngredient ingredient : ingredients) {
				needed.addAll(Collections.nCopies(ingredient.count(), ingredient.ingredient()));
			}
			mold.ifPresent(needed::add);
			placementInfo = PlacementInfo.create(needed);
		}
		return placementInfo;
	}

	@Override
	public List<RecipeDisplay> display() {
		return List.of(new MachineRecipeDisplay(
				ingredients.stream().map(i -> i.ingredient().display()).toList(),
				ingredients.stream().map(CountedIngredient::count).toList(),
				mold.map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE),
				MachineRecipeDisplay.energyItems(),
				new SlotDisplay.ItemStackSlotDisplay(result),
				new SlotDisplay.ItemSlotDisplay(kind.machineItem()),
				processingTime));
	}

	@Override
	public RecipeBookCategory recipeBookCategory() {
		return kind.category();
	}

	/**
	 * Returns the machine kind.
	 *
	 * @return the kind this recipe belongs to
	 */
	public MachineKind kind() {
		return kind;
	}

	/**
	 * Returns the ingredients.
	 *
	 * @return the counted ingredients, in recipe order
	 */
	public List<CountedIngredient> ingredients() {
		return ingredients;
	}

	/**
	 * Returns the mold.
	 *
	 * @return the mold, empty for kinds without one
	 */
	public Optional<Ingredient> mold() {
		return mold;
	}

	/**
	 * Returns the result.
	 *
	 * @return the result of one operation
	 */
	public ItemStackTemplate result() {
		return result;
	}

	/**
	 * Returns the operation time.
	 *
	 * @return the operation time, in ticks
	 */
	public int processingTime() {
		return processingTime;
	}
}
