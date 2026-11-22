package com.ravecraft.mod.datagen;

import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModDataComponents;
import com.ravecraft.mod.registry.ModItems;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/** Block loot tables: speakers keep their settings, ores drop their material, shale blocks drop themselves. */
public class RaveCraftBlockLootProvider extends FabricBlockLootSubProvider {
	private static final int BITUMEN_MIN = 1;
	private static final int BITUMEN_MAX = 3;

	/**
	 * A provider writing to {@code output}.
	 *
	 * @param output the pack the files go to
	 * @param registries the registries, once loaded
	 */
	public RaveCraftBlockLootProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, registries);
	}

	@Override
	public void generate() {
		speaker(ModBlocks.PORTABLE_SPEAKER);
		speaker(ModBlocks.PARTY_SPEAKER);
		add(ModBlocks.DEEPSLATE_NEODYMIUM_ORE, createOreDrop(ModBlocks.DEEPSLATE_NEODYMIUM_ORE, ModItems.RAW_NEODYMIUM));
		add(ModBlocks.OIL_SHALE, oilShale(ModBlocks.OIL_SHALE));
		for (Block block : ModBlocks.shaleFamily()) {
			if (block instanceof SlabBlock) {
				add(block, createSlabItemTable(block));
			} else {
				dropSelf(block);
			}
		}
	}

	/** Like vanilla copper ore: several bitumen, raised by Fortune, decayed by explosions. */
	private LootTable.Builder oilShale(Block block) {
		return createSilkTouchDispatchTable(block, applyExplosionDecay(block, LootItem.lootTableItem(ModItems.BITUMEN)
				.apply(SetItemCountFunction.setCount(ContextIntProviders.between(BITUMEN_MIN, BITUMEN_MAX)))
				.apply(ApplyBonusCount.addOreBonusCount(enchantments.getOrThrow(Enchantments.FORTUNE)))));
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
