package com.ravecraft.mod.gametest;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.blockentity.SpeakerBlockEntity;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.speaker.CarryPlace;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import com.ravecraft.mod.registry.ModBlockEntities;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModDataComponents;
import com.ravecraft.mod.world.audio.SpeakerBackSlots;
import com.ravecraft.mod.world.audio.SpeakerHandle;
import com.ravecraft.mod.world.audio.SpeakerHost;
import com.ravecraft.mod.world.audio.SpeakerHosts;
import com.ravecraft.mod.world.audio.SpeakerPlayback;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.equipment.Equippable;

public class PartySpeakerGameTest {
	/** Index of the centre slot ({@code P}) in the flattened 3 x 3 shaped pattern. */
	private static final int CENTRE = 4;

	@GameTest
	public void placedPartySpeakerHasData(GameTestHelper h) {
		BlockPos pos = new BlockPos(1, 1, 1);
		h.setBlock(pos, ModBlocks.PARTY_SPEAKER);
		SpeakerBlockEntity speaker = h.getBlockEntity(pos, SpeakerBlockEntity.class);
		h.assertTrue(speaker.data() != null && speaker.data().id() != null, "speaker identity assigned");
		h.assertValueEqual(speaker.kind(), SpeakerKind.PARTY, "kind");
		h.succeed();
	}

	@GameTest
	public void portableStillUsesPortableType(GameTestHelper h) {
		h.assertValueEqual(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(ModBlockEntities.SPEAKER),
				RaveCraft.id("portable_speaker"), "block entity type id");
		h.assertTrue(ModBlockEntities.SPEAKER.isValid(ModBlocks.PARTY_SPEAKER.defaultBlockState()),
				"the speaker type covers the party speaker");
		h.succeed();
	}

	@GameTest
	public void recipeConsumesPortable(GameTestHelper h) {
		ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, RaveCraft.id("party_speaker"));
		Optional<RecipeHolder<?>> holder = h.getLevel().getServer().getRecipeManager().byKey(key);
		h.assertTrue(holder.isPresent(), "party speaker recipe exists");
		h.assertTrue(holder.get().value() instanceof ShapedRecipe, "shaped recipe");
		List<Optional<Ingredient>> ingredients = ((ShapedRecipe) holder.get().value()).getIngredients();
		h.assertTrue(Ingredient.testOptionalIngredient(ingredients.get(CENTRE), new ItemStack(ModBlocks.PORTABLE_SPEAKER)),
				"centre ingredient is the portable speaker");
		h.succeed();
	}

	/** A powered party speaker item with a fresh id. */
	private static ItemStack partyStack() {
		ItemStack stack = new ItemStack(ModBlocks.PARTY_SPEAKER);
		stack.set(ModDataComponents.SPEAKER, new SpeakerData(UUID.randomUUID(), new SpeakerSettings(true, SpeakerSettings.DEFAULT.volume())));
		return stack;
	}

	@GameTest
	public void partyIsWearable(GameTestHelper h) {
		Equippable equippable = new ItemStack(ModBlocks.PARTY_SPEAKER).get(DataComponents.EQUIPPABLE);
		h.assertTrue(equippable != null, "party speaker is equippable");
		h.assertValueEqual(equippable.slot(), EquipmentSlot.CHEST, "worn in the chest slot");
		h.assertTrue(equippable.assetId().isEmpty(), "no armor model");
		h.succeed();
	}

	@GameTest
	public void portableIsNotWearable(GameTestHelper h) {
		h.assertFalse(new ItemStack(ModBlocks.PORTABLE_SPEAKER).has(DataComponents.EQUIPPABLE), "portable not equippable");
		h.succeed();
	}

	/**
	 * Vanilla ticks the chest slot as {@link EquipmentSlot#CHEST}: the wearer hosts the speaker, on its back.
	 *
	 * @param h the test helper
	 */
	@GameTest(maxTicks = 40)
	public void chestSlotMakesWearerHost(GameTestHelper h) {
		ServerPlayer owner = SharedQueueGameTest.online(h, 2);
		ItemStack stack = partyStack();
		UUID id = stack.get(ModDataComponents.SPEAKER).id();
		owner.setItemSlot(EquipmentSlot.CHEST, stack);
		owner.aiStep(); // the mock player is not ticked by a connection: run its living tick by hand
		CarriedSpeakerGameTest.play(h, owner, id);
		h.startSequence()
				.thenIdle(1) // a second report in the setup tick would be a copy
				.thenExecuteFor(2, owner::aiStep)
				.thenExecute(() -> {
					SpeakerHost host = SpeakerHosts.handle(h.getLevel().getServer(), id).map(SpeakerHandle::host).orElse(null);
					h.assertTrue(host instanceof SpeakerHost.Carrier carrier && carrier.place() == CarryPlace.BACK,
							"carried on the back, got " + host);
					h.assertTrue(SpeakerPlayback.isPlaying(id), "still playing");
					CarriedSpeakerGameTest.cleanUp(h, List.of(id), owner);
				})
				.thenSucceed();
	}

	/**
	 * Sneaking and using the party speaker in the air puts it on the back; a plain use opens its screen instead.
	 *
	 * @param h the test helper
	 */
	@GameTest
	public void sneakUseWearsIt(GameTestHelper h) {
		ServerPlayer owner = SharedQueueGameTest.online(h, 2);
		ItemStack stack = partyStack();
		owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
		owner.setShiftKeyDown(true);
		stack.use(h.getLevel(), owner, InteractionHand.MAIN_HAND);
		h.assertTrue(owner.getItemBySlot(EquipmentSlot.CHEST).is(ModBlocks.PARTY_SPEAKER.asItem()), "worn on the back");
		CarriedSpeakerGameTest.cleanUp(h, List.of(stack.get(ModDataComponents.SPEAKER).id()), owner);
		h.succeed();
	}

	/**
	 * An accessory mod whose API changed fails at its first lookup: the finder is dropped, the chest slot remains.
	 *
	 * @param h the test helper
	 */
	@GameTest
	public void brokenBackFinderIsDropped(GameTestHelper h) {
		ServerPlayer owner = SharedQueueGameTest.online(h, 2);
		AtomicInteger calls = new AtomicInteger();
		SpeakerBackSlots.set((player, filter) -> {
			calls.incrementAndGet();
			throw new NoSuchMethodError("changed accessory API");
		});
		try {
			h.assertTrue(SpeakerBackSlots.find(owner, stack -> true).isEmpty(), "nothing found on failure");
			h.assertTrue(SpeakerBackSlots.find(owner, stack -> true).isEmpty(), "still nothing found");
			h.assertValueEqual(calls.get(), 1, "finder called once, then dropped");
		} finally {
			SpeakerBackSlots.set(null);
			CarriedSpeakerGameTest.cleanUp(h, List.of(), owner);
		}
		h.succeed();
	}
}
