package com.ravecraft.mod.registry;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Block entity types. */
public final class ModBlockEntities {
	public static final BlockEntityType<PortableSpeakerBlockEntity> PORTABLE_SPEAKER = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, RaveCraft.id("portable_speaker"),
			new BlockEntityType<>(PortableSpeakerBlockEntity::new, Set.of(ModBlocks.PORTABLE_SPEAKER)));

	private ModBlockEntities() {
	}

	public static void init() {
	}
}
