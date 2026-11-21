package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.RaveCraft;
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

	/**
	 * The first stack worn in an accessory back slot that matches {@code filter}; empty without an accessory mod. An
	 * accessory mod whose API no longer links (an incompatible version) is dropped at its first failure, logged once.
	 */
	public static Optional<ItemStack> find(Player player, Predicate<ItemStack> filter) {
		Finder current = finder;
		if (current == null) {
			return Optional.empty();
		}
		try {
			return current.find(player, filter);
		} catch (LinkageError e) {
			finder = null;
			RaveCraft.LOGGER.warn("Accessory back slot disabled: incompatible accessory mod", e);
			return Optional.empty();
		}
	}
}
