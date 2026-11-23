package com.ravecraft.mod.gametest;

import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipe;
import com.ravecraft.mod.registry.ModRecipes;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.impl.recipe.sync.RecipeSyncImpl;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeViewerGameTest {
	@GameTest
	public void machineRecipesSyncToClients(GameTestHelper h) {
		h.assertValueEqual(ModRecipes.kinds().size(), 4, "four machines");
		for (MachineKind kind : ModRecipes.kinds()) {
			h.assertTrue(RecipeSyncImpl.isSynced(kind.serializer()), kind + " recipes reach the client");
		}
		h.succeed();
	}

	@GameTest
	public void everyMachineRecipeSurvivesTheNetwork(GameTestHelper h) {
		int checked = 0;
		for (MachineKind kind : ModRecipes.kinds()) {
			for (RecipeHolder<MachineRecipe> holder : h.getLevel().getServer().getRecipeManager()
					.getAllOfType(kind.type())) {
				RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
						h.getLevel().registryAccess());
				kind.serializer().streamCodec().encode(buf, holder.value());
				MachineRecipe copy = kind.serializer().streamCodec().decode(buf);
				h.assertTrue(ItemStack.matches(copy.result().create(), holder.value().result().create()),
						holder.id() + " result");
				h.assertValueEqual(copy.ingredients().size(), holder.value().ingredients().size(),
						holder.id() + " ingredients");
				h.assertValueEqual(copy.mold().isPresent(), holder.value().mold().isPresent(), holder.id() + " mold");
				checked++;
			}
		}
		h.assertTrue(checked >= 10, "all machine recipes checked, got " + checked);
		h.succeed();
	}
}
