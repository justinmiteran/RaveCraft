package com.ravecraft.mod.client;

import com.ravecraft.mod.client.audio.render.AcousticSettingsClient;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.Directivity;
import com.ravecraft.mod.core.acoustics.Propagation;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModDataComponents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Shows the speaker's data sheet and the range it reaches at its stored volume, under the server's acoustic model. */
public final class SpeakerTooltip {
	private SpeakerTooltip() {
	}

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (!stack.is(ModBlocks.PORTABLE_SPEAKER.asItem())) {
				return;
			}
			AcousticSettings settings = AcousticSettingsClient.get();
			DeviceProfile profile = settings.portable();
			SpeakerData data = stack.get(ModDataComponents.SPEAKER);
			int volume = data != null ? data.settings().volume() : SpeakerSettings.DEFAULT_VOLUME;
			long range = Math.round(Propagation.range(settings, profile, volume / (double) SpeakerSettings.MAX_VOLUME));
			SpeakerQueue queue = data != null ? data.queue() : SpeakerQueue.EMPTY;
			Component directivity = Component.translatable(profile.directivity().equals(Directivity.OMNI)
					? "tooltip.ravecraft.speaker.directivity.omni" : "tooltip.ravecraft.speaker.directivity.directional");
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
			if (!OpenSpeakerKey.key().isUnbound()) {
				lines.add(gray("tooltip.ravecraft.speaker.open_key", OpenSpeakerKey.key().getTranslatedKeyMessage()));
			}
			if (!queue.entries().isEmpty()) {
				int count = queue.entries().size();
				lines.add(gray(count == 1 ? "tooltip.ravecraft.speaker.queue.one" : "tooltip.ravecraft.speaker.queue",
						count));
			}
			if (!shift) {
				lines.add(Component.translatable("tooltip.ravecraft.speaker.shift_hint").withStyle(ChatFormatting.DARK_GRAY));
			}
		});
	}

	private static Component gray(String key, Object... args) {
		return Component.translatable(key, args).withStyle(ChatFormatting.GRAY);
	}

	private static String number(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.1f", value);
	}
}
