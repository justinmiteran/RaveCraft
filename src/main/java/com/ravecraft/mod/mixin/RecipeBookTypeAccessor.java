package com.ravecraft.mod.mixin;

import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Reaches the enum's constructor and its values array, to append the machine book type. */
@Mixin(RecipeBookType.class)
public interface RecipeBookTypeAccessor {
	@Invoker("<init>")
	static RecipeBookType ravecraft$create(String name, int ordinal) {
		throw new AssertionError();
	}

	@Accessor("$VALUES")
	static RecipeBookType[] ravecraft$values() {
		throw new AssertionError();
	}

	@Mutable
	@Accessor("$VALUES")
	static void ravecraft$setValues(RecipeBookType[] values) {
		throw new AssertionError();
	}
}
