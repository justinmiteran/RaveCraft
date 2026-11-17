package com.ravecraft.mod.gametest;

import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModDataComponents;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

public class PortableSpeakerGameTest {
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
		SpeakerData data = h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data();
		h.assertTrue(data.id() != null, "id set");
		h.assertValueEqual(data.settings(), SpeakerSettings.DEFAULT, "settings");
		h.succeed();
	}

	@GameTest
	public void usingBlockTogglesPower(GameTestHelper h) {
		BlockPos pos = new BlockPos(1, 1, 1);
		h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
		h.useBlock(pos);
		h.assertBlockProperty(pos, PortableSpeakerBlock.POWERED, true);
		h.assertTrue(h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data().settings().powered(), "data powered");
		h.succeed();
	}

	@GameTest
	public void breakingDropsItemWithSameData(GameTestHelper h) {
		BlockPos pos = new BlockPos(1, 1, 1);
		h.setBlock(pos, ModBlocks.PORTABLE_SPEAKER);
		h.useBlock(pos);
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
		BlockPos pos = new BlockPos(1, 1, 1);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack); // UseOnContext reads the stack from the hand
		h.placeAt(player, stack, pos.below(), Direction.UP);
		h.assertValueEqual(h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data(), data, "restored data");
		h.assertBlockProperty(pos, PortableSpeakerBlock.POWERED, true);
		h.succeed();
	}

	@GameTest
	public void creativeItemWithoutDataGetsIdentityOnPlace(GameTestHelper h) {
		Player player = h.makeMockPlayer(GameType.CREATIVE);
		BlockPos pos = new BlockPos(1, 1, 1);
		ItemStack stack = new ItemStack(ModBlocks.PORTABLE_SPEAKER);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack); // UseOnContext reads the stack from the hand
		h.placeAt(player, stack, pos.below(), Direction.UP);
		SpeakerData data = h.getBlockEntity(pos, PortableSpeakerBlockEntity.class).data();
		h.assertTrue(data.id() != null, "fresh id");
		h.assertBlockProperty(pos, PortableSpeakerBlock.POWERED, false);
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
	public void speakerDoesNotStack(GameTestHelper h) {
		h.assertValueEqual(new ItemStack(ModBlocks.PORTABLE_SPEAKER).getMaxStackSize(), 1, "max stack size");
		h.succeed();
	}
}
