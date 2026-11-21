package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.core.audio.tags.TrackInfo;
import com.ravecraft.mod.core.library.AudioFiles;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

/** The tooltip of a library track: its title and duration, and with Shift its path, tags and audio details. */
final class TrackTooltip {
	private static final String LOADING = "…";

	private TrackTooltip() {
	}

	/**
	 * @param info the track's tags, empty while they are being read
	 * @param folder the folder to show under the title, or null outside the flat view
	 */
	static List<Component> lines(Path track, Optional<TrackInfo> info, boolean shift, @Nullable String folder) {
		List<Component> lines = new ArrayList<>();
		if (shift) {
			lines.add(Component.literal(track.toString()));
			if (info.isEmpty()) {
				lines.add(gray(LOADING));
			} else {
				details(lines, info.get());
			}
			return lines;
		}
		lines.add(Component.literal(info.flatMap(TrackInfo::sharedTitle).orElseGet(() -> AudioFiles.baseName(track))));
		if (info.isEmpty()) {
			lines.add(gray(LOADING));
		} else if (info.get().durationMs() > 0) {
			lines.add(PlayerBand.time(info.get().durationMs()).copy().withStyle(ChatFormatting.GRAY));
		}
		if (folder != null && !folder.isEmpty()) {
			lines.add(Component.translatable(SpeakerSprites.KEY + "in_folder", folder).withStyle(ChatFormatting.GRAY));
		}
		lines.add(Component.translatable("tooltip.ravecraft.speaker.shift_hint").withStyle(ChatFormatting.DARK_GRAY));
		return lines;
	}

	private static void details(List<Component> lines, TrackInfo info) {
		text(lines, "track.album", info.album());
		text(lines, "track.year", info.year());
		text(lines, "track.genre", info.genre());
		NumberFormat numbers = NumberFormat.getInstance(
				Locale.forLanguageTag(Minecraft.getInstance().getLanguageManager().getSelected().replace('_', '-')));
		List<Component> tech = new ArrayList<>();
		if (!info.format().isEmpty()) {
			tech.add(Component.literal(info.format()));
		}
		if (info.bitrateKbps() > 0) {
			tech.add(Component.translatable(SpeakerSprites.KEY + "track.kbps", numbers.format(info.bitrateKbps())));
		}
		if (info.sampleRateHz() > 0) {
			NumberFormat khz = (NumberFormat) numbers.clone();
			khz.setMaximumFractionDigits(1);
			tech.add(Component.translatable(SpeakerSprites.KEY + "track.khz", khz.format(info.sampleRateHz() / 1000.0)));
		}
		if (info.channels() > 0) {
			tech.add(switch (info.channels()) {
				case 1 -> Component.translatable(SpeakerSprites.KEY + "track.channels.mono");
				case 2 -> Component.translatable(SpeakerSprites.KEY + "track.channels.stereo");
				default -> Component.translatable(SpeakerSprites.KEY + "track.channels.many", info.channels());
			});
		}
		if (!tech.isEmpty()) {
			MutableComponent line = Component.empty();
			for (int i = 0; i < tech.size(); i++) {
				line.append(i == 0 ? tech.get(i) : Component.literal(" · ").append(tech.get(i)));
			}
			lines.add(line.withStyle(ChatFormatting.GRAY));
		}
	}

	private static void text(List<Component> lines, String key, String value) {
		if (!value.isEmpty()) {
			lines.add(Component.translatable(SpeakerSprites.KEY + key, value).withStyle(ChatFormatting.GRAY));
		}
	}

	private static Component gray(String text) {
		return Component.literal(text).withStyle(ChatFormatting.GRAY);
	}
}
