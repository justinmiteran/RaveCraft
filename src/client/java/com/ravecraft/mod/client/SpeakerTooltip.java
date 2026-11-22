package com.ravecraft.mod.client;

import com.ravecraft.mod.client.audio.render.AcousticSettingsClient;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.Directivity;
import com.ravecraft.mod.core.acoustics.Propagation;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.item.SpeakerItem;
import com.ravecraft.mod.registry.ModDataComponents;
import java.util.List;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Shows the speaker's data sheet and the range it reaches at its stored volume, under the server's acoustic model. */
public final class SpeakerTooltip {
	private SpeakerTooltip() {
	}

	/** Adds the speaker lines to the tooltips of speaker items. */
	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (stack.getItem() instanceof SpeakerItem item) {
				addLines(stack, item.kind(), lines);
			}
		});
	}

	/** Adds the lines of the speaker item {@code stack}: its specs, with {@code Shift} its volume and track too. */
	private static void addLines(ItemStack stack, SpeakerKind kind, List<Component> lines) {
		AcousticSettings settings = AcousticSettingsClient.get();
		DeviceProfile profile = settings.profile(kind);
		SpeakerData data = stack.get(ModDataComponents.SPEAKER);
		int volume = data != null ? data.settings().volume() : SpeakerSettings.DEFAULT_VOLUME;
		long range = Math.round(Propagation.range(settings, profile, volume / (double) SpeakerSettings.MAX_VOLUME));
		SpeakerQueue queue = data != null ? data.queue() : SpeakerQueue.EMPTY;
		Component directivity = directivityText(profile.directivity());
		boolean shift = Minecraft.getInstance().hasShiftDown();
		if (shift) {
			lines.add(gray("tooltip.ravecraft.speaker.base_short", number(profile.powerWatts()), directivity));
			lines.add(gray("tooltip.ravecraft.speaker.sensitivity", number(profile.sensitivityDb())));
			lines.add(gray("tooltip.ravecraft.speaker.bass_cut", number(profile.bassCutDb()),
					number(profile.bassCutHz())));
			lines.add(gray("tooltip.ravecraft.speaker.volume_range", volume, range));
			queue.current().flatMap(queue::entry).ifPresent(entry ->
					lines.add(gray("tooltip.ravecraft.speaker.now_playing", entry.title())));
		} else {
			lines.add(gray("tooltip.ravecraft.speaker.base", number(profile.powerWatts()), range, directivity));
		}
		addKeyLine(lines);
		addQueueLine(lines, queue);
		if (!shift) {
			lines.add(Component.translatable("tooltip.ravecraft.speaker.shift_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** The directivity of a speaker: omnidirectional, or the cone it covers and how much quieter it is behind. */
	private static Component directivityText(Directivity directivity) {
		if (directivity.equals(Directivity.OMNI)) {
			return Component.translatable("tooltip.ravecraft.speaker.directivity.omni");
		}
		return Component.translatable("tooltip.ravecraft.speaker.directivity.cone",
				number(directivity.halfAngleDeg()), number(directivity.rearGainDb()));
	}

	/** Adds the key that opens the speaker from the inventory, unless it is unbound. */
	private static void addKeyLine(List<Component> lines) {
		if (!OpenSpeakerKey.key().isUnbound()) {
			lines.add(gray("tooltip.ravecraft.speaker.open_key", OpenSpeakerKey.key().getTranslatedKeyMessage()));
		}
	}

	/** Adds how many tracks {@code queue} holds, unless it is empty. */
	private static void addQueueLine(List<Component> lines, SpeakerQueue queue) {
		if (!queue.entries().isEmpty()) {
			int count = queue.entries().size();
			lines.add(gray(count == 1 ? "tooltip.ravecraft.speaker.queue.one" : "tooltip.ravecraft.speaker.queue",
					count));
		}
	}

	private static Component gray(String key, Object... args) {
		return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
	}

	private static String number(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.1f", value);
	}
}
