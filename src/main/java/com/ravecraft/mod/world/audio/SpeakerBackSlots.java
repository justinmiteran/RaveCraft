package com.ravecraft.mod.world.audio;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Where an optional accessory mod (Trinkets) keeps a speaker worn on the back. The compat layer installs a finder at
 * startup; without it, nothing is found and the vanilla chest slot alone holds a back speaker. Lets the lookup and the
 * open key search that slot without importing the accessory mod.
 */
public final class SpeakerBackSlots {
	/** Searches a player's accessory back slots for a stack. */
	@FunctionalInterface
	public interface Finder {
		/** The first stack in the player's back slots that matches {@code filter}. */
		Optional<ItemStack> find(Player player, Predicate<ItemStack> filter);
	}

	private static volatile Finder finder;

	private SpeakerBackSlots() {
	}

	/** Installs the finder of the loaded accessory mod. */
	public static void set(Finder newFinder) {
		finder = newFinder;
	}

	/** The first stack worn in an accessory back slot that matches {@code filter}; empty without an accessory mod. */
	public static Optional<ItemStack> find(Player player, Predicate<ItemStack> filter) {
		Finder current = finder;
		return current == null ? Optional.empty() : current.find(player, filter);
	}
}
