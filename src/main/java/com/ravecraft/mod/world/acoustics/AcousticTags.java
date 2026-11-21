package com.ravecraft.mod.world.acoustics;

import com.ravecraft.mod.RaveCraft;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/** The tags that map blocks to acoustic materials and fluids to media; datapacks can override them. */
public final class AcousticTags {
	private static final Map<String, TagKey<Block>> MATERIALS = new ConcurrentHashMap<>();
	private static final Map<String, TagKey<Fluid>> MEDIA = new ConcurrentHashMap<>();

	private AcousticTags() {
	}

	/** {@code ravecraft:acoustic/<name>}: the blocks made of the material {@code name}. */
	public static TagKey<Block> material(String name) {
		return MATERIALS.computeIfAbsent(name, n -> TagKey.create(Registries.BLOCK,
				RaveCraft.id("acoustic/" + n)));
	}

	/** {@code ravecraft:medium/<name>}: the fluids that form the medium {@code name}. */
	public static TagKey<Fluid> medium(String name) {
		return MEDIA.computeIfAbsent(name, n -> TagKey.create(Registries.FLUID,
				RaveCraft.id("medium/" + n)));
	}
}
