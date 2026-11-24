package com.ravecraft.mod.menu;

import net.minecraft.world.inventory.RecipeBookType;

/**
 * The machines' own recipe book type, so the machine book remembers whether it is open and filtered apart from the
 * furnace's. The value is appended to the vanilla enum by {@code RecipeBookTypeMixin}; its settings are kept, saved
 * and sent by {@code RecipeBookSettingsMixin}.
 */
public final class MachineBookType {
	/** The name of the enum value added to {@link RecipeBookType}. */
	public static final String NAME = "RAVECRAFT_MACHINE";

	/** The recipe book type shared by every machine. */
	public static final RecipeBookType MACHINE = RecipeBookType.valueOf(NAME);

	private MachineBookType() {
	}
}
