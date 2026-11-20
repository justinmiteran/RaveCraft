package com.ravecraft.mod.client.audio.stream;

import net.minecraft.client.color.ColorLerper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.DyeColor;

/**
 * The "Now playing" toast of a speaker: the look of the game's own music toast (its background and colour-cycling
 * notes, drawn from the game's sprites), with a title line and a "track — added by" line. One at a time: a new track
 * replaces the text of the one showing.
 */
public final class SpeakerTrackToast implements Toast {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("toast/now_playing");
	private static final Identifier NOTES = Identifier.withDefaultNamespace("icon/music_notes");
	private static final Object TOKEN = new Object();
	private static final long DISPLAY_MS = 5000;
	private static final long NOTE_COLOR_STEP_MS = 25;
	private static final int TEXT_X = 30;
	private static final int PADDING = 7;
	private static final int MAX_TEXT_WIDTH = 200;
	private static final int TITLE_COLOR = 0xFFFFFF00;
	private static final int MESSAGE_COLOR = DyeColor.LIGHT_GRAY.getTextColor();

	private final Font font;
	private FormattedCharSequence title;
	private FormattedCharSequence message;
	private int width;
	private boolean changed = true;
	private long shownSince;
	private Visibility wanted = Visibility.HIDE;

	private SpeakerTrackToast(Font font, Component title, Component message) {
		this.font = font;
		set(title, message);
	}

	/** Shows {@code title} / {@code message}, replacing the text of the toast already showing if there is one. */
	public static void show(ToastManager manager, Font font, Component title, Component message) {
		SpeakerTrackToast toast = manager.getToast(SpeakerTrackToast.class, TOKEN);
		if (toast == null) {
			manager.addToast(new SpeakerTrackToast(font, title, message));
		} else {
			toast.set(title, message);
			toast.changed = true;
		}
	}

	private void set(Component title, Component message) {
		this.title = firstLine(title);
		this.message = firstLine(message);
		width = TEXT_X + Math.max(font.width(this.title), font.width(this.message)) + PADDING;
	}

	/** The text cut to one line of at most {@link #MAX_TEXT_WIDTH} pixels. */
	private FormattedCharSequence firstLine(Component text) {
		return font.split(text, MAX_TEXT_WIDTH).stream().findFirst().orElse(FormattedCharSequence.EMPTY);
	}

	@Override
	public Object getToken() {
		return TOKEN;
	}

	@Override
	public int width() {
		return Math.max(DEFAULT_WIDTH, width);
	}

	@Override
	public Visibility getWantedVisibility() {
		return wanted;
	}

	@Override
	public void update(ToastManager manager, long fullyVisibleForMs) {
		if (changed) {
			shownSince = fullyVisibleForMs;
			changed = false;
		}
		boolean show = fullyVisibleForMs - shownSince < DISPLAY_MS * manager.getNotificationDisplayTimeMultiplier();
		wanted = show ? Visibility.SHOW : Visibility.HIDE;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND, 0, 0, width(), height());
		int noteColor = ColorLerper.getLerpedColor(ColorLerper.Type.MUSIC_NOTE, fullyVisibleForMs / NOTE_COLOR_STEP_MS);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, NOTES, PADDING, 8, 16, 16, noteColor);
		graphics.text(font, title, TEXT_X, 7, TITLE_COLOR, false);
		graphics.text(font, message, TEXT_X, 18, MESSAGE_COLOR, false);
	}
}
