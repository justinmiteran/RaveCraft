package com.ravecraft.mod.registry;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipeDisplay;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Machine recipe kinds: their recipe types, serializers, recipe book tabs, and the shared recipe display. */
public final class ModRecipes {
	/** The coil winder's process: copper into wire, wire into voice coils. */
	public static final MachineKind WINDING = register(new MachineKind("winding", "coil_winder", 1, false));

	private ModRecipes() {
	}

	private static MachineKind register(MachineKind kind) {
		Registry.register(BuiltInRegistries.RECIPE_TYPE, RaveCraft.id(kind.process()), kind.type());
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, RaveCraft.id(kind.process()), kind.serializer());
		Registry.register(BuiltInRegistries.RECIPE_BOOK_CATEGORY, RaveCraft.id(kind.machine()), kind.category());
		return kind;
	}

	/** Registers the shared display type; the kinds register when the class loads. */
	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_DISPLAY, RaveCraft.id("machine"), MachineRecipeDisplay.TYPE);
	}
}
