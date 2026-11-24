package com.ravecraft.mod.registry;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.block.CoilWinderBlock;
import com.ravecraft.mod.block.PartySpeakerBlock;
import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.item.SpeakerItem;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/** Blocks and their block items. */
public final class ModBlocks {
	public static final Block PORTABLE_SPEAKER = register("portable_speaker", PortableSpeakerBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GRAY)
					.sound(SoundType.METAL)
					.strength(1.0F)
					.noOcclusion(),
			SpeakerItem::new, new Item.Properties().stacksTo(1));
	public static final Block PARTY_SPEAKER = register("party_speaker", PartySpeakerBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_GRAY)
					.sound(SoundType.METAL)
					.strength(1.0F)
					.noOcclusion(),
			SpeakerItem::new, new Item.Properties().stacksTo(1)
					// Worn on the back in the chest slot; no asset id, so no armor model: BackSpeakerRenderer draws it.
					.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).build()));

	public static final Block OIL_SHALE = registerSimple("oil_shale",
			p -> new DropExperienceBlock(UniformInt.of(0, 2), p),
			stone(MapColor.COLOR_BLACK, SoundType.STONE).strength(3.0F, 3.0F));
	public static final Block DEEPSLATE_NEODYMIUM_ORE = registerSimple("deepslate_neodymium_ore",
			p -> new DropExperienceBlock(ConstantInt.of(0), p),
			stone(MapColor.DEEPSLATE, SoundType.DEEPSLATE).strength(4.5F, 3.0F));

	public static final Block SHALE = registerSimple("shale", Block::new, shale(SoundType.TUFF));
	public static final Block SHALE_STAIRS = stairs("shale_stairs", SHALE, SoundType.TUFF);
	public static final Block SHALE_SLAB = registerSimple("shale_slab", SlabBlock::new, shale(SoundType.TUFF));
	public static final Block SHALE_WALL = registerSimple("shale_wall", WallBlock::new,
			shale(SoundType.TUFF).forceSolidOn());
	public static final Block POLISHED_SHALE = registerSimple("polished_shale", Block::new,
			shale(SoundType.POLISHED_TUFF));
	public static final Block POLISHED_SHALE_STAIRS = stairs("polished_shale_stairs", POLISHED_SHALE,
			SoundType.POLISHED_TUFF);
	public static final Block POLISHED_SHALE_SLAB = registerSimple("polished_shale_slab", SlabBlock::new,
			shale(SoundType.POLISHED_TUFF));
	public static final Block POLISHED_SHALE_WALL = registerSimple("polished_shale_wall", WallBlock::new,
			shale(SoundType.POLISHED_TUFF).forceSolidOn());
	public static final Block SHALE_BRICKS = registerSimple("shale_bricks", Block::new, shale(SoundType.TUFF_BRICKS));
	public static final Block SHALE_BRICK_STAIRS = stairs("shale_brick_stairs", SHALE_BRICKS, SoundType.TUFF_BRICKS);
	public static final Block SHALE_BRICK_SLAB = registerSimple("shale_brick_slab", SlabBlock::new,
			shale(SoundType.TUFF_BRICKS));
	public static final Block SHALE_BRICK_WALL = registerSimple("shale_brick_wall", WallBlock::new,
			shale(SoundType.TUFF_BRICKS).forceSolidOn());
	public static final Block CHISELED_SHALE = registerSimple("chiseled_shale", Block::new,
			shale(SoundType.TUFF_BRICKS));

	public static final Block RAW_NEODYMIUM_BLOCK = registerSimple("raw_neodymium_block", Block::new,
			stone(MapColor.COLOR_PURPLE, SoundType.STONE).strength(5.0F, 6.0F));
	public static final Block NEODYMIUM_BLOCK = registerSimple("neodymium_block", Block::new,
			metal(MapColor.TERRACOTTA_WHITE).strength(5.0F, 6.0F));
	public static final Block SILICON_BLOCK = registerSimple("silicon_block", Block::new,
			metal(MapColor.COLOR_LIGHT_BLUE).strength(3.0F, 6.0F));
	/** Strength and light as the furnace, metal sound; needs a stone pickaxe. */
	public static final Block COIL_WINDER = registerSimple("coil_winder", CoilWinderBlock::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.sound(SoundType.METAL)
					.strength(3.5F)
					.requiresCorrectToolForDrops()
					.lightLevel(Blocks.litBlockEmission(13)));

	private ModBlocks() {
	}

	/**
	 * Every speaker block, whatever its kind: the blocks the speaker block entity type covers, in creative tab
	 * order.
	 *
	 * @return the speaker blocks
	 */
	public static List<Block> speakers() {
		return List.of(PORTABLE_SPEAKER, PARTY_SPEAKER);
	}

	/**
	 * Every machine block: the blocks the machine block entity type covers, in creative tab order.
	 *
	 * @return the machine blocks
	 */
	public static List<Block> machines() {
		return List.of(COIL_WINDER);
	}

	/**
	 * The mod's ore blocks, in creative tab order.
	 *
	 * @return the ores
	 */
	public static List<Block> ores() {
		return List.of(OIL_SHALE, DEEPSLATE_NEODYMIUM_ORE);
	}

	/**
	 * The shale rock and its decorative derivatives, in creative tab order.
	 *
	 * @return the thirteen shale blocks
	 */
	public static List<Block> shaleFamily() {
		return List.of(SHALE, SHALE_STAIRS, SHALE_SLAB, SHALE_WALL, POLISHED_SHALE, POLISHED_SHALE_STAIRS,
				POLISHED_SHALE_SLAB, POLISHED_SHALE_WALL, SHALE_BRICKS, SHALE_BRICK_STAIRS, SHALE_BRICK_SLAB,
				SHALE_BRICK_WALL, CHISELED_SHALE);
	}

	/**
	 * The storage blocks (nine items each), in creative tab order: raw neodymium, neodymium, silicon.
	 *
	 * @return the storage blocks
	 */
	public static List<Block> storageBlocks() {
		return List.of(RAW_NEODYMIUM_BLOCK, NEODYMIUM_BLOCK, SILICON_BLOCK);
	}

	/** Metal block properties, as a vanilla iron block: pickaxe required, iron xylophone note. */
	private static BlockBehaviour.Properties metal(MapColor color) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.instrument(NoteBlockInstrument.IRON_XYLOPHONE)
				.sound(SoundType.METAL)
				.requiresCorrectToolForDrops();
	}

	/** Stone-like properties: pickaxe required, bass drum note. */
	private static BlockBehaviour.Properties stone(MapColor color, SoundType sound) {
		return BlockBehaviour.Properties.of()
				.mapColor(color)
				.instrument(NoteBlockInstrument.BASEDRUM)
				.sound(sound)
				.requiresCorrectToolForDrops();
	}

	/** Shale family properties, as vanilla tuff. */
	private static BlockBehaviour.Properties shale(SoundType sound) {
		return stone(MapColor.COLOR_GRAY, sound).strength(1.5F, 6.0F);
	}

	/** Registers stairs shaped after {@code base}. */
	private static Block stairs(String name, Block base, SoundType sound) {
		return registerSimple(name, p -> new StairBlock(base.defaultBlockState(), p), shale(sound));
	}

	/** Registers a block with a plain {@link BlockItem}. */
	private static Block registerSimple(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties) {
		return register(name, factory, properties, BlockItem::new, new Item.Properties());
	}

	/** Registers a block and the item {@code itemFactory} makes for it, both under the same id. */
	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties, BiFunction<Block, Item.Properties, Item> itemFactory,
			Item.Properties baseItemProperties) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, RaveCraft.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, RaveCraft.id(name));
		Item.Properties itemProperties = baseItemProperties.setId(itemKey).useBlockDescriptionPrefix();
		Registry.register(BuiltInRegistries.ITEM, itemKey, itemFactory.apply(block, itemProperties));
		return block;
	}

	/** Forces class loading, hence registration. */
	public static void init() {
	}
}
