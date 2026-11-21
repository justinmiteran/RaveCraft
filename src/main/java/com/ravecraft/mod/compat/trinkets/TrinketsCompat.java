package com.ravecraft.mod.compat.trinkets;

import com.ravecraft.mod.core.speaker.CarryPlace;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.world.audio.SpeakerBackSlots;
import com.ravecraft.mod.world.audio.SpeakerHosts;
import eu.pb4.trinkets.api.DefaultTrinketSlots;
import eu.pb4.trinkets.api.TrinketSlotAccess;
import eu.pb4.trinkets.api.TrinketsApi;
import eu.pb4.trinkets.api.callback.TrinketCallback;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Lets the party speaker be worn in the Trinkets Updated back slot. Only loaded when Trinkets is, from a guarded call.
 *
 * <p>Trinkets Updated 4.2.1+26.3 API (package {@code eu.pb4.trinkets.api}, mod id {@code trinkets_updated}, which
 * provides {@code trinkets}), read from its jar:
 * <ul>
 *   <li>behaviour: {@code TrinketCallback.setCallback(Item, TrinketCallback)}; the callback's default method
 *   {@code tick(ItemStack, TrinketSlotAccess, LivingEntity)} runs each tick for an equipped stack;</li>
 *   <li>lookup: {@code TrinketsApi.getAttachment(LivingEntity)} returns a {@code TrinketAttachment}, whose
 *   {@code equipped(Predicate<ItemStack>, boolean)} lists the matching {@code TrinketSlotAccess} (the flag as
 *   {@code findFirst(Predicate)} passes it: {@code false}); {@code access.get()} is the
 *   stack and {@code access.slotType().getId()} the slot ({@code "group/name"});</li>
 *   <li>back slot: {@code DefaultTrinketSlots.CHEST_BACK = "chest/back"}, validator {@code trinkets:default}, which
 *   accepts an item in the tag {@code trinkets:chest/back} (file {@code data/trinkets/tags/item/chest/back.json}) or
 *   carrying the {@code trinkets:equipment} component;</li>
 *   <li>slot assignment: a slot type ({@code data/trinkets/slots/chest/back.json}) exists for no entity until a
 *   {@code data/trinkets/entities/*.json} file assigns it ({@code EntitySlotLoader}: keys {@code replace},
 *   {@code slots} as {@code "group/name"}, {@code entities} as entity ids or {@code #tags}); the jar ships none, so
 *   {@code data/trinkets/entities/ravecraft.json} gives players {@code chest/back};</li>
 *   <li>client render: {@code TrinketRendererRegistry.registerRenderer(Item, Function<EntityRendererProvider.Context,
 *   TrinketRenderer>)}, whose {@code submit} receives the entity model and the render state.</li>
 * </ul>
 */
public final class TrinketsCompat {
	private TrinketsCompat() {
	}

	/** Reports the back speaker each tick and installs the back slot finder. */
	public static void init() {
		TrinketCallback.setCallback(ModBlocks.PARTY_SPEAKER.asItem(), new BackSpeakerCallback());
		SpeakerBackSlots.set(TrinketsCompat::findBack);
	}

	/** The first stack in the player's Trinkets back slot ({@code chest/back}) that matches {@code filter}. */
	public static Optional<ItemStack> findBack(Player player, Predicate<ItemStack> filter) {
		return TrinketsApi.getAttachment(player).equipped(filter, false).stream()
				.filter(access -> DefaultTrinketSlots.CHEST_BACK.equals(access.slotType().getId()))
				.findFirst()
				.map(TrinketSlotAccess::get);
	}

	/** Reports a speaker worn in a Trinkets slot as carried on the back, on the server only. */
	private static final class BackSpeakerCallback implements TrinketCallback {
		@Override
		public void tick(ItemStack stack, TrinketSlotAccess slot, LivingEntity entity) {
			if (entity instanceof ServerPlayer player) {
				SpeakerHosts.carried(player, stack, CarryPlace.BACK);
			}
		}
	}
}
