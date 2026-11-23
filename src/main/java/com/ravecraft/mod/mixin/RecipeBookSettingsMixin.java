package com.ravecraft.mod.mixin;

import com.ravecraft.mod.menu.MachineBookSettings;
import com.ravecraft.mod.menu.MachineBookType;
import java.util.function.UnaryOperator;
import net.minecraft.stats.RecipeBookSettings;
import net.minecraft.stats.RecipeBookSettings.TypeSettings;
import net.minecraft.world.inventory.RecipeBookType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the machine book its own settings next to the four vanilla ones: read and written before the vanilla switches
 * (which do not know the value), copied with the others, saved and sent after them.
 */
@Mixin(RecipeBookSettings.class)
abstract class RecipeBookSettingsMixin implements MachineBookSettings {
	@Unique
	private TypeSettings machineBook = TypeSettings.DEFAULT;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void ravecraft$extendCodecs(CallbackInfo ci) {
		RecipeBookSettingsAccessor.ravecraft$setStreamCodec(MachineBookSettings.withMachine(RecipeBookSettings.STREAM_CODEC));
		RecipeBookSettingsAccessor.ravecraft$setMapCodec(MachineBookSettings.withMachine(RecipeBookSettings.MAP_CODEC));
	}

	@Override
	public TypeSettings ravecraft$machine() {
		return machineBook;
	}

	@Override
	public void ravecraft$setMachine(TypeSettings settings) {
		machineBook = settings;
	}

	@Inject(method = "getSettings", at = @At("HEAD"), cancellable = true)
	private void ravecraft$getMachine(RecipeBookType type, CallbackInfoReturnable<TypeSettings> cir) {
		if (type == MachineBookType.MACHINE) {
			cir.setReturnValue(machineBook);
		}
	}

	@Inject(method = "updateSettings", at = @At("HEAD"), cancellable = true)
	private void ravecraft$updateMachine(RecipeBookType type, UnaryOperator<TypeSettings> operator, CallbackInfo ci) {
		if (type == MachineBookType.MACHINE) {
			machineBook = operator.apply(machineBook);
			ci.cancel();
		}
	}

	@Inject(method = "copy", at = @At("RETURN"))
	private void ravecraft$copyMachine(CallbackInfoReturnable<RecipeBookSettings> cir) {
		MachineBookSettings.of(cir.getReturnValue()).ravecraft$setMachine(machineBook);
	}

	@Inject(method = "replaceFrom", at = @At("TAIL"))
	private void ravecraft$replaceMachine(RecipeBookSettings other, CallbackInfo ci) {
		machineBook = MachineBookSettings.of(other).ravecraft$machine();
	}
}
