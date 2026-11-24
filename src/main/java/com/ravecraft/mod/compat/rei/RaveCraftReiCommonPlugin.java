package com.ravecraft.mod.compat.rei;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.recipe.MachineRecipe;
import me.shedaniel.rei.api.common.display.DisplaySerializerRegistry;
import me.shedaniel.rei.api.common.plugins.REICommonPlugin;
import me.shedaniel.rei.api.common.registry.display.ServerDisplayRegistry;

/**
 * Sends the machine recipes to REI, when REI is installed. Loaded only through the {@code rei_common} entrypoint, so
 * nothing here runs without REI.
 */
public final class RaveCraftReiCommonPlugin implements REICommonPlugin {
	@Override
	public void registerDisplaySerializer(DisplaySerializerRegistry registry) {
		registry.register(RaveCraft.id("machine"), MachineDisplay.SERIALIZER);
	}

	@Override
	public void registerDisplays(ServerDisplayRegistry registry) {
		registry.beginRecipeFiller(MachineRecipe.class)
				.fill(holder -> new MachineDisplay(MachineDisplayData.of(holder)));
	}
}
