package com.ravecraft.mod.gametest;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModDataComponents;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

public class PortableSpeakerGameTest {
	/** The mock player only overrides gameMode(), so abilities must be set for hasInfiniteMaterials() to hold. */
	private static Player creativePlayer(GameTestHelper h) {
		Player player = h.makeMockPlayer(GameType.CREATIVE);
		player.getAbilities().instabuild = true;
		return player;
	}

	/** Sneak + use toggles power; a plain use opens the music screen instead. */
	private static void powerOn(GameTestHelper h, BlockPos pos) {
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		player.setShiftKeyDown(true);
		h.useBlock(pos, player);
	}

	@GameTest
	public void speakerDataCodecRoundTrip(GameTestHelper helper) {
		SpeakerData data = new SpeakerData(UUID.randomUUID(), new SpeakerSettings(true, 42));
		var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		Tag encoded = SpeakerData.CODEC.encodeStart(ops, data).getOrThrow();
		helper.assertValueEqual(SpeakerData.CODEC.parse(ops, encoded).getOrThrow(), data, "decoded");
		helper.succeed();
	}

	@GameTest
	public void placedSpeakerHasIdentity(GameTestHelper h) {
		BlockPos pos = new BlockPos(1, 1, 1);
		h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
		BlockPos other = new BlockPos(3, 1, 1);
		h.setBlock(other, ModBlocks.PORTABLE_SPEAKER);
		SpeakerData data = h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data();
		SpeakerData otherData = h.getBlockEntity(other, PortableSpeakerBlockEntity.class).data();
		h.assertTrue(!data.id().equals(otherData.id()), "two speakers get different ids");
		h.assertValueEqual(data.settings(), SpeakerSettings.DEFAULT, "settings");
		h.succeed();
	}

	@GameTest
	public void sneakUseTogglesPower(GameTestHelper h) {
		BlockPos pos = new BlockPos(1, 1, 1);
		h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
		powerOn(h, pos);
		h.assertBlockProperty(pos, PortableSpeakerBlock.POWERED, true);
		h.assertTrue(h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data().settings().powered(), "data powered");
		h.succeed();
	}

	@GameTest
	public void plainUseDoesNotTogglePower(GameTestHelper h) {
		BlockPos pos = new BlockPos(1, 1, 1);
		h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
		h.useBlock(pos, h.makeMockPlayer(GameType.SURVIVAL));
		h.assertBlockProperty(pos, PortableSpeakerBlock.POWERED, false);
		h.succeed();
	}

	@GameTest
	public void breakingDropsItemWithSameData(GameTestHelper h) {
		BlockPos pos = new BlockPos(1, 1, 1);
		h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
		powerOn(h, pos);
		SpeakerData before = h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data();
		h.getLevel().destroyBlock(h.absolutePos(pos), true);   // drops, unlike helper.destroyBlock
		var drops = h.getEntities(EntityTypes.ITEM, pos, 2);
		h.assertTrue(!drops.isEmpty(), "speaker dropped");
		ItemEntity drop = drops.getFirst();
		h.assertValueEqual(drop.getItem().get(ModDataComponents.SPEAKER), before, "dropped data");
		h.succeed();
	}

	@GameTest
	public void placingItemRestoresDataAndPower(GameTestHelper h) {
		SpeakerData data = new SpeakerData(UUID.randomUUID(), new SpeakerSettings(true, 33));
		ItemStack stack = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
		stack.set(ModDataComponents.SPEAKER, data);
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		player.setYRot(0.0F); // yaw 0 looks south, so the speaker must face north (towards the player)
		BlockPos pos = new BlockPos(1, 1, 1);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack); // UseOnContext reads the stack from the hand
		h.setBlock(pos.below(), Blocks.STONE); // the speaker needs a floor
		h.placeAt(player, stack, pos.below(), Direction.UP);
		h.assertValueEqual(h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data(), data, "restored data");
		h.assertBlockProperty(pos, PortableSpeakerBlock.FACING, player.getDirection().getOpposite());
		h.assertBlockProperty(pos, PortableSpeakerBlock.POWERED, true);
		h.succeed();
	}

	@GameTest
	public void creativeItemWithoutDataGetsIdentityOnPlace(GameTestHelper h) {
		Player player = creativePlayer(h);
		BlockPos pos = new BlockPos(1, 1, 1);
		ItemStack stack = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack); // UseOnContext reads the stack from the hand
		h.setBlock(pos.below(), Blocks.STONE); // the speaker needs a floor
		h.placeAt(player, stack, pos.below(), Direction.UP);
		h.assertBlockProperty(pos, PortableSpeakerBlock.POWERED, false);
		// A second placement of an identical stack must get its own identity.
		BlockPos other = new BlockPos(3, 1, 1);
		ItemStack second = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
		player.setItemInHand(InteractionHand.MAIN_HAND, second);
		h.setBlock(other.below(), Blocks.STONE); // the speaker needs a floor
		h.placeAt(player, second, other.below(), Direction.UP);
		SpeakerData first = h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data();
		SpeakerData secondData = h.getBlockEntity(other, PortableSpeakerBlockEntity.class).data();
		h.assertTrue(!first.id().equals(secondData.id()), "distinct ids");
		h.succeed();
	}

	@GameTest
	public void creativeCopiesGetFreshIdentityKeepingSettings(GameTestHelper h) {
		UUID stackId = UUID.randomUUID();
		SpeakerSettings settings = new SpeakerSettings(true, 25);
		ItemStack stack = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
		stack.set(ModDataComponents.SPEAKER, new SpeakerData(stackId, settings));
		Player player = creativePlayer(h);
		BlockPos a = new BlockPos(1, 1, 1);
		BlockPos b = new BlockPos(3, 1, 1);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		h.setBlock(a.below(), Blocks.STONE); // the speaker needs a floor
		h.placeAt(player, stack, a.below(), Direction.UP);
		h.setBlock(b.below(), Blocks.STONE); // the speaker needs a floor
		h.placeAt(player, stack, b.below(), Direction.UP); // creative does not consume the stack
		SpeakerData first = h.getBlockEntity(a, PortableSpeakerBlockEntity.class).data();
		SpeakerData second = h.getBlockEntity(b, PortableSpeakerBlockEntity.class).data();
		h.assertTrue(!first.id().equals(stackId), "first id differs from stack id");
		h.assertTrue(!second.id().equals(stackId), "second id differs from stack id");
		h.assertTrue(!first.id().equals(second.id()), "placed speakers have distinct ids");
		h.assertValueEqual(first.settings(), settings, "first settings kept");
		h.assertValueEqual(second.settings(), settings, "second settings kept");
		h.succeed();
	}

	@GameTest
	public void blockEntitySaveLoadRoundTrip(GameTestHelper h) {
		BlockPos pos = new BlockPos(1, 1, 1);
		h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
		powerOn(h, pos); // powered on, so the data is not the default
		PortableSpeakerBlockEntity original = h.getBlockEntity(pos, PortableSpeakerBlockEntity.class);
		var access = h.getLevel().registryAccess();
		CompoundTag tag = original.saveWithFullMetadata(access);
		BlockEntity loaded = BlockEntity.loadStatic(h.absolutePos(pos), original.getBlockState(), tag, access);
		h.assertTrue(loaded instanceof PortableSpeakerBlockEntity, "loaded a speaker block entity");
		h.assertValueEqual(((PortableSpeakerBlockEntity) loaded).data(), original.data(), "loaded data");
		h.succeed();
	}

	@GameTest
	public void useInAirWithoutDataAssignsIdentity(GameTestHelper h) {
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		ItemStack stack = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		stack.use(h.getLevel(), player, InteractionHand.MAIN_HAND);
		SpeakerData data = player.getMainHandItem().get(ModDataComponents.SPEAKER);
		h.assertTrue(data != null && data.settings().powered(), "identity assigned and powered on");
		h.succeed();
	}

	@GameTest
	public void dyeRecipeColoursSpeakerAndKeepsData(GameTestHelper h) {
		SpeakerData data = new SpeakerData(UUID.randomUUID(), new SpeakerSettings(true, 40));
		ItemStack speaker = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
		speaker.set(ModDataComponents.SPEAKER, data);
		CraftingInput input = CraftingInput.of(2, 1, List.of(speaker, new ItemStack(Items.DYE.red())));
		var recipe = h.getLevel().recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel());
		h.assertTrue(recipe.isPresent(), "speaker + dye matches a recipe");
		ItemStack result = recipe.get().value().assemble(input);
		h.assertTrue(result.is(ModBlocks.PORTABLE_SPEAKER.asItem()), "result is a speaker");
		h.assertValueEqual(result.get(DataComponents.DYED_COLOR), DyedItemColor.applyDyes((DyedItemColor) null, List.of(DyeColor.RED)), "dyed red");
		h.assertValueEqual(result.get(ModDataComponents.SPEAKER), data, "identity and settings kept");
		h.succeed();
	}

	@GameTest
	public void undyedSpeakerUsesDefaultColour(GameTestHelper h) {
		BlockPos pos = new BlockPos(1, 1, 1);
		h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
		PortableSpeakerBlockEntity speaker = h.getBlockEntity(pos, PortableSpeakerBlockEntity.class);
		h.assertTrue(speaker.dyedColor() == null, "not dyed");
		h.assertValueEqual(speaker.fabricColor(), PortableSpeakerBlock.DEFAULT_COLOR, "default fabric colour");
		h.succeed();
	}

	@GameTest
	public void dyedColourSurvivesPlaceBreakAndSave(GameTestHelper h) {
		DyedItemColor blue = new DyedItemColor(0x3AB3DA);
		ItemStack stack = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
		stack.set(DataComponents.DYED_COLOR, blue);
		Player player = h.makeMockPlayer(GameType.SURVIVAL);
		BlockPos pos = new BlockPos(1, 1, 1);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		h.setBlock(pos.below(), Blocks.STONE); // the speaker needs a floor
		h.placeAt(player, stack, pos.below(), Direction.UP);
		PortableSpeakerBlockEntity speaker = h.getBlockEntity(pos, PortableSpeakerBlockEntity.class);
		h.assertValueEqual(speaker.dyedColor(), blue, "placed colour");
		h.assertValueEqual(speaker.fabricColor(), 0x3AB3DA, "fabric colour");

		var access = h.getLevel().registryAccess();
		BlockEntity loaded = BlockEntity.loadStatic(h.absolutePos(pos), speaker.getBlockState(), speaker.saveWithFullMetadata(access), access);
		h.assertValueEqual(((PortableSpeakerBlockEntity) loaded).dyedColor(), blue, "saved colour");

		h.getLevel().destroyBlock(h.absolutePos(pos), true);
		var drops = h.getEntities(EntityTypes.ITEM, pos, 2);
		h.assertTrue(!drops.isEmpty(), "speaker dropped");
		h.assertValueEqual(drops.getFirst().getItem().get(DataComponents.DYED_COLOR), blue, "dropped colour");
		h.succeed();
	}

	@GameTest
	public void pickBlockKeepsDyedColour(GameTestHelper h) {
		DyedItemColor lime = new DyedItemColor(0x80C71F);
		BlockPos pos = new BlockPos(1, 2, 1);
		h.setBlock(pos.below(), Blocks.STONE);
		h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
		h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).setDyedColor(lime);
		ItemStack picked = h.getBlockState(pos).getCloneItemStack(h.getLevel(), h.absolutePos(pos), false);
		h.assertValueEqual(picked.get(DataComponents.DYED_COLOR), lime, "picked colour");
		h.assertTrue(!picked.has(ModDataComponents.SPEAKER), "plain pick does not copy the speaker identity");
		h.succeed();
	}

	@GameTest
	public void speakerNeedsSupportBelow(GameTestHelper h) {
		BlockPos floating = new BlockPos(1, 4, 1);
		h.assertTrue(!ModBlocks.PORTABLE_SPEAKER.defaultBlockState().canSurvive(h.getLevel(), h.absolutePos(floating)),
				"cannot stand in mid-air");
		BlockPos support = new BlockPos(1, 1, 1);
		BlockPos pos = support.above();
		h.setBlock(support, Blocks.STONE);
		h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
		powerOn(h, pos);
		SpeakerData before = h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data();
		h.getLevel().destroyBlock(h.absolutePos(support), false);
		h.assertBlockNotPresent(ModBlocks.PORTABLE_SPEAKER, pos);
		var drops = h.getEntities(EntityTypes.ITEM, pos, 2);
		h.assertTrue(!drops.isEmpty(), "speaker dropped when its support broke");
		h.assertValueEqual(drops.getFirst().getItem().get(ModDataComponents.SPEAKER), before, "dropped data");
		h.succeed();
	}

	@GameTest
	public void speakerDoesNotStack(GameTestHelper h) {
		h.assertValueEqual(new ItemStack(ModBlocks.PORTABLE_SPEAKER).getMaxStackSize(), 1, "max stack size");
		h.succeed();
	}
}
