package com.ravecraft.mod.client;

import com.ravecraft.mod.client.audio.render.AcousticSettingsClient;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.Propagation;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.registry.ModBlocks;
import com.ravecraft.mod.registry.ModDataComponents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
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
			lines.add(Component.translatable("tooltip.ravecraft.speaker.specs", number(profile.powerWatts()),
					number(profile.sensitivityDb())).withStyle(ChatFormatting.GRAY));
			lines.add(Component.translatable("tooltip.ravecraft.speaker.range", range, volume)
					.withStyle(ChatFormatting.GRAY));
		});
	}

	private static String number(double value) {
		return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.1f", value);
	}
}
