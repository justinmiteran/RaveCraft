package com.ravecraft.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.client.screen.SpeakerScreen;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.speaker.SpeakerKeyTarget;
import com.ravecraft.mod.registry.ModDataComponents;
import com.ravecraft.mod.world.audio.SpeakerBackSlots;
import java.util.Optional;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The M key (for "music"): opens the screen of a speaker worn on the back or carried in the hotbar, offhand or inventory. */
public final class OpenSpeakerKey {
	/** The inventory index of the chest equipment slot, where a speaker is worn on the back. */
	private static final int CHEST_SLOT = EquipmentSlot.CHEST.getIndex(Inventory.INVENTORY_SIZE);
	/** Core's offhand number. */
	private static final int CORE_OFFHAND = 40;

	private static final KeyMapping KEY = new KeyMapping("key.ravecraft.open_speaker", InputConstants.Type.KEYBOARD,
			InputConstants.KEY_M, KeyMapping.Category.register(RaveCraft.id("ravecraft")));

	private OpenSpeakerKey() {
	}

	/**
	 * The key mapping, to show its current binding.
	 *
	 * @return the key mapping
	 */
	public static KeyMapping key() {
		return KEY;
	}

	/** Registers the key and listens to it each client tick. */
	public static void register() {
		KeyMappingHelper.registerKeyMapping(KEY);
		ClientTickEvents.END_CLIENT_TICK.register(OpenSpeakerKey::tick);
	}

	/**
	 * Each press with no screen open opens the first carried speaker: one worn in an accessory back slot (Trinkets),
	 * then the inventory in the order of {@link com.ravecraft.mod.core.speaker.SpeakerKeyTarget}.
	 */
	private static void tick(Minecraft minecraft) {
		while (KEY.consumeClick()) {
			Player player = minecraft.player;
			if (minecraft.gui.screen() != null || player == null) {
				continue;
			}
			SpeakerBackSlots.find(player, stack -> stack.has(ModDataComponents.SPEAKER))
					.or(() -> inventorySpeaker(player.getInventory()))
					.ifPresent(stack -> open(minecraft, stack));
		}
	}

	/** The inventory speaker the key opens, in the order of {@link SpeakerKeyTarget}. */
	private static Optional<ItemStack> inventorySpeaker(Inventory inventory) {
		OptionalInt slot = SpeakerKeyTarget.choose(inventory.getSelectedSlot(), i -> stack(inventory, i).has(ModDataComponents.SPEAKER));
		return slot.isPresent() ? Optional.of(stack(inventory, slot.getAsInt())) : Optional.empty();
	}

	private static void open(Minecraft minecraft, ItemStack stack) {
		SpeakerData data = stack.get(ModDataComponents.SPEAKER);
		minecraft.gui.setScreen(new SpeakerScreen(data.id(), stack.getHoverName()));
	}

	/** Core numbers the back 38 and the offhand 40; the inventory has its own constants for them. */
	private static ItemStack stack(Inventory inventory, int slot) {
		return switch (slot) {
			case SpeakerKeyTarget.BACK -> inventory.getItem(CHEST_SLOT);
			case CORE_OFFHAND -> inventory.getItem(Inventory.SLOT_OFFHAND);
			default -> inventory.getItem(slot);
		};
	}
}
