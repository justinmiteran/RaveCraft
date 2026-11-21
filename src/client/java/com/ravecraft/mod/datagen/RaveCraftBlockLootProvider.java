package com.ravecraft.mod.datagen;

import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModDataComponents;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/** Block loot tables: each block drops itself. */
public class RaveCraftBlockLootProvider extends FabricBlockLootSubProvider {
	/** A provider writing to {@code output}. */
	public RaveCraftBlockLootProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	public void generate() {
		speaker(ModBlocks.PORTABLE_SPEAKER);
		speaker(ModBlocks.PARTY_SPEAKER);
	}

	/** Like vanilla shulker boxes: the dropped item keeps the speaker's identity, settings and dye colour. */
	private void speaker(Block block) {
		add(block, LootTable.lootTable()
				.withPool(applyExplosionCondition(block, LootPool.lootPool()
						.setRolls(ContextIntProviders.exactly(1))
						.add(LootItem.lootTableItem(block)
								.apply(CopyComponentsFunction.copyComponentsFromBlockEntity(LootContextParams.BLOCK_ENTITY)
										.include(ModDataComponents.SPEAKER)
										.include(DataComponents.DYED_COLOR)
										.include(DataComponents.CUSTOM_NAME))))));
	}
}
