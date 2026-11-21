package com.ravecraft.mod.gametest;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.blockentity.SpeakerBlockEntity;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import com.ravecraft.mod.registry.ModBlockEntities;
import com.ravecraft.mod.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;

public class PartySpeakerGameTest {
	/** Index of the centre slot ({@code P}) in the flattened 3 x 3 shaped pattern. */
	private static final int CENTRE = 4;

	@GameTest
	public void placedPartySpeakerHasData(GameTestHelper h) {
		BlockPos pos = new BlockPos(1, 1, 1);
		h.setBlock(pos, ModBlocks.PARTY_SPEAKER);
		SpeakerBlockEntity speaker = h.getBlockEntity(pos, SpeakerBlockEntity.class);
		h.assertTrue(speaker.data() != null && speaker.data().id() != null, "speaker identity assigned");
		h.assertValueEqual(speaker.kind(), SpeakerKind.PARTY, "kind");
		h.succeed();
	}

	@GameTest
	public void portableStillUsesPortableType(GameTestHelper h) {
		h.assertValueEqual(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(ModBlockEntities.SPEAKER),
				RaveCraft.id("portable_speaker"), "block entity type id");
		h.assertTrue(ModBlockEntities.SPEAKER.isValid(ModBlocks.PARTY_SPEAKER.defaultBlockState()),
				"the speaker type covers the party speaker");
		h.succeed();
	}

	@GameTest
	public void recipeConsumesPortable(GameTestHelper h) {
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, RaveCraft.id("party_speaker"));
		Optional<RecipeHolder<?>> holder = h.getLevel().getServer().getRecipeManager().byKey(key);
		h.assertTrue(holder.isPresent(), "party speaker recipe exists");
		h.assertTrue(holder.get().value() instanceof ShapedRecipe, "shaped recipe");
		List<Optional<Ingredient>> ingredients = ((ShapedRecipe) holder.get().value()).getIngredients();
		h.assertTrue(Ingredient.testOptionalIngredient(ingredients.get(CENTRE), new ItemStack(ModBlocks.PORTABLE_SPEAKER)),
				"centre ingredient is the portable speaker");
		h.succeed();
	}
}
