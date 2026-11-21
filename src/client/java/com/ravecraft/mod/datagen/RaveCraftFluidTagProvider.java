package com.ravecraft.mod.datagen;

import com.ravecraft.mod.world.acoustics.AcousticTags;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.tags.FluidTags;

/** The fluids that form each acoustic medium. */
public class RaveCraftFluidTagProvider extends FabricTagsProvider.FluidTagsProvider {
	/** A provider writing to {@code output}. */
	public RaveCraftFluidTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		builder(AcousticTags.medium("water")).addOptionalTag(FluidTags.WATER);
		builder(AcousticTags.medium("lava")).addOptionalTag(FluidTags.LAVA);
	}
}
