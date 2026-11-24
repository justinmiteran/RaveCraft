package com.ravecraft.mod.mixin;

import com.ravecraft.mod.menu.MachineBookType;
import java.util.Arrays;
import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Appends the machine book type to the enum as soon as it is initialised, before anything can read its values: the
 * network reads book types by ordinal, so the value must exist on both sides with the same index.
 */
@Mixin(RecipeBookType.class)
abstract class RecipeBookTypeMixin {
	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void ravecraft$addMachine(CallbackInfo ci) {
		RecipeBookType[] values = RecipeBookTypeAccessor.ravecraft$values();
		RecipeBookType[] extended = Arrays.copyOf(values, values.length + 1);
		extended[values.length] = RecipeBookTypeAccessor.ravecraft$create(MachineBookType.NAME, values.length);
		RecipeBookTypeAccessor.ravecraft$setValues(extended);
	}
}
