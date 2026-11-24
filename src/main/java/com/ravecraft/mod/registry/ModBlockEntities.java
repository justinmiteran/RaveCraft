package com.ravecraft.mod.registry;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.blockentity.MachineBlockEntity;
import com.ravecraft.mod.blockentity.SpeakerBlockEntity;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Block entity types. */
public final class ModBlockEntities {
	/** Every speaker block, whatever its kind; keeps the portable's id so existing worlds load unchanged. */
	public static final BlockEntityType<SpeakerBlockEntity> SPEAKER = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, RaveCraft.id("portable_speaker"),
			new BlockEntityType<>(SpeakerBlockEntity::new, Set.copyOf(ModBlocks.speakers())));

	/** Every machine block, whatever its kind. */
	public static final BlockEntityType<MachineBlockEntity> MACHINE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE, RaveCraft.id("machine"),
			new BlockEntityType<>(MachineBlockEntity::new, Set.copyOf(ModBlocks.machines())));

	private ModBlockEntities() {
	}

	/** Forces class loading, hence registration. */
	public static void init() {
	}
}
