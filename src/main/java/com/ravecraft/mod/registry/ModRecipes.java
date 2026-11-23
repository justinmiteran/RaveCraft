package com.ravecraft.mod.registry;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.recipe.MachineEnergy;
import com.ravecraft.mod.recipe.MachineKind;
import com.ravecraft.mod.recipe.MachineRecipeDisplay;
import java.util.List;
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Machine recipe kinds: their recipe types, serializers, recipe book tabs, and the shared recipe display. */
public final class ModRecipes {
	/** The coil winder's process: copper into wire, wire into voice coils. */
	public static final MachineKind WINDING = register(new MachineKind("winding", "coil_winder", 1, false));
	/** The magnetizer: two inputs. */
	public static final MachineKind MAGNETIZING = register(new MachineKind("magnetizing", "magnetizer", 2, false));
	/** The etcher: three inputs. */
	public static final MachineKind ETCHING = register(new MachineKind("etching", "etcher", 3, false));
	/** The extruder: one input and a mold. */
	public static final MachineKind EXTRUDING = register(new MachineKind("extruding", "extruder", 1, true));

	private static final List<MachineKind> KINDS = List.of(WINDING, MAGNETIZING, ETCHING, EXTRUDING);

	private ModRecipes() {
	}

	/**
	 * Returns every machine kind, in the order of the production chain.
	 *
	 * @return the four machine kinds
	 */
	public static List<MachineKind> kinds() {
		return KINDS;
	}

	private static MachineKind register(MachineKind kind) {
		Registry.register(BuiltInRegistries.RECIPE_TYPE, RaveCraft.id(kind.process()), kind.type());
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, RaveCraft.id(kind.process()), kind.serializer());
		Registry.register(BuiltInRegistries.RECIPE_BOOK_CATEGORY, RaveCraft.id(kind.machine()), kind.category());
		return kind;
	}

	/**
	 * Registers the shared display type and the machine energy recipes, and sends machine and energy recipes to
	 * clients (energy slot, recipe viewers); the kinds register when the class loads.
	 */
	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_DISPLAY, RaveCraft.id("machine"), MachineRecipeDisplay.TYPE);
		Registry.register(BuiltInRegistries.RECIPE_TYPE, RaveCraft.id("machine_energy"), MachineEnergy.TYPE);
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, RaveCraft.id("machine_energy"), MachineEnergy.SERIALIZER);
		RecipeSynchronization.synchronizeRecipeSerializer(MachineEnergy.SERIALIZER);
		KINDS.forEach(kind -> RecipeSynchronization.synchronizeRecipeSerializer(kind.serializer()));
	}
}
