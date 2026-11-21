package com.ravecraft.mod.client.audio.stream;

import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.client.audio.render.AcousticRenderer;
import com.ravecraft.mod.client.options.RaveCraftOptions;
import com.ravecraft.mod.client.screen.SpeakerScreen;
import com.ravecraft.mod.core.notify.FollowedSource;
import com.ravecraft.mod.core.notify.FollowedSource.Heard;
import com.ravecraft.mod.network.payload.NowPlayingPayload;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * The "Now playing" toast: once a second, joins what the server said each session plays with the gain it is heard
 * at, and announces the tracks of the speaker heard loudest ({@link FollowedSource}). Client thread only.
 */
public final class NowPlayingToasts {
	private static final int CHECK_TICKS = 20;
	private static final int MAX_NOTICES = 64;

	/** What each session plays, oldest first, dropped when it ends. */
	private static final Map<Integer, NowPlayingPayload> NOTICES = new LinkedHashMap<>();
	private static FollowedSource followed = new FollowedSource();
	private static int ticks;

	private NowPlayingToasts() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(NowPlayingToasts::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			NOTICES.clear();
			followed = new FollowedSource();
		});
	}

	public static void onNotice(NowPlayingPayload notice) {
		NOTICES.remove(notice.sessionId());
		NOTICES.put(notice.sessionId(), notice);
		if (NOTICES.size() > MAX_NOTICES) {
			NOTICES.remove(NOTICES.keySet().iterator().next());
		}
	}

	public static void onSessionEnd(int sessionId) {
		NOTICES.remove(sessionId);
	}

	private static void tick(Minecraft client) {
		if (++ticks < CHECK_TICKS) {
			return;
		}
		ticks = 0;
		if (client.level == null || !RaveCraftOptions.notifications().get().nowPlaying()) {
			return;
		}
		Map<Integer, Double> gains = AcousticRenderer.heard();
		List<Heard> heard = new ArrayList<>();
		for (NowPlayingPayload notice : NOTICES.values()) {
			Double gain = gains.get(notice.sessionId());
			if (gain != null) {
				heard.add(new Heard(notice.pos().asLong(), notice.entryId(), gain));
			}
		}
		OptionalLong open = client.gui.screen() instanceof SpeakerScreen screen
				? noticePosOf(client.level, screen.speakerId())
				: OptionalLong.empty();
		Optional<Heard> announce = followed.update(Util.getMillis(), heard, open);
		announce.flatMap(NowPlayingToasts::noticeOf).ifPresent(notice -> SpeakerTrackToast.show(
				client.gui.toastManager(), client.font, Component.translatable("toast.ravecraft.now_playing.title"),
				Component.translatable("toast.ravecraft.now_playing.message", notice.title(), notice.ownerName())));
	}

	/**
	 * The position of the notice whose speaker block holds {@code speakerId}: notices still name speakers by their
	 * block while the screen names them by id.
	 */
	private static OptionalLong noticePosOf(ClientLevel level, UUID speakerId) {
		for (NowPlayingPayload notice : NOTICES.values()) {
			if (level.getBlockEntity(notice.pos()) instanceof PortableSpeakerBlockEntity speaker
					&& speaker.data().id().equals(speakerId)) {
				return OptionalLong.of(notice.pos().asLong());
			}
		}
		return OptionalLong.empty();
	}

	private static Optional<NowPlayingPayload> noticeOf(Heard heard) {
		return NOTICES.values().stream()
				.filter(n -> n.pos().asLong() == heard.speaker() && n.entryId().equals(heard.entryId()))
				.findFirst();
	}
}
