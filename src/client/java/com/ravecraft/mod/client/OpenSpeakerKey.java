package com.ravecraft.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.speaker.SpeakerKeyTarget;
import com.ravecraft.mod.registry.ModDataComponents;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The J key: opens the screen of a speaker carried in the hotbar, offhand or inventory. */
public final class OpenSpeakerKey {
	private static final KeyMapping KEY = new KeyMapping("key.ravecraft.open_speaker", InputConstants.Type.KEYBOARD,
			InputConstants.KEY_J, KeyMapping.Category.register(Identifier.fromNamespaceAndPath(RaveCraft.MOD_ID, "ravecraft")));

	private OpenSpeakerKey() {
	}

	public static KeyMapping key() {
		return KEY;
	}

	public static void register() {
		KeyMappingHelper.registerKeyMapping(KEY);
		ClientTickEvents.END_CLIENT_TICK.register(OpenSpeakerKey::tick);
	}

	private static void tick(Minecraft minecraft) {
		while (KEY.consumeClick()) {
			Player player = minecraft.player;
			if (minecraft.gui.screen() != null || player == null) {
				continue;
			}
			Inventory inventory = player.getInventory();
			OptionalInt slot = SpeakerKeyTarget.choose(inventory.getSelectedSlot(), i -> stack(inventory, i).has(ModDataComponents.SPEAKER));
			if (slot.isPresent()) {
				ItemStack stack = stack(inventory, slot.getAsInt());
				SpeakerData data = stack.get(ModDataComponents.SPEAKER);
				minecraft.gui.setScreen(new com.ravecraft.mod.client.screen.SpeakerScreen(data.id(), stack.getHoverName()));
			}
		}
	}

	/** Core numbers the offhand 40; the inventory has its own constant for it. */
	private static ItemStack stack(Inventory inventory, int slot) {
		return inventory.getItem(slot == 40 ? Inventory.SLOT_OFFHAND : slot);
	}
}
