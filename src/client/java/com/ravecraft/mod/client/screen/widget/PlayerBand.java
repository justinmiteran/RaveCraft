package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.client.screen.SpeakerStates;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import com.ravecraft.mod.network.payload.SpeakerStatePayload.StateEntry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The band at the top of the speaker screen, on the speaker's fabric tinted with its colour: the copper equaliser
 * logo, the current title and who added it, the elapsed time, the progress bar and the duration, and the power LED.
 * Dragging the knob only moves it here; one {@code SEEK} goes out on release. The knob is disabled when the
 * duration is unknown (-1) or the speaker is off.
 */
public final class PlayerBand extends AbstractWidget {
	public static final int HEIGHT = 44;
	/** The fabric is darkened so white text stays readable on any dye. */
	private static final float FABRIC_SHADE = 0.6F;
	private static final int TEXT_X = 42;
	private static final int TIME_W = 32;
	private static final int BAR_W = 6;
	private static final int BAR_GAP = 2;
	private static final int BAR_MAX = 30;
	/** Heights of the four bars when still, as a fraction of {@link #BAR_MAX}. */
	private static final float[] BARS = {0.35F, 0.75F, 1.0F, 0.5F};

	private final UUID speakerId;
	/** The speaker's fabric colour (opaque RGB), read each frame so a new dye shows at once. */
	private final IntSupplier fabricColour;
	private boolean seeking;
	private long seekMs;

	/**
	 * The band of speaker {@code speakerId}.
	 *
	 * @param x the left edge
	 * @param y the top
	 * @param width the width
	 * @param speakerId the speaker
	 * @param fabricColour gives the fabric's RGB colour
	 */
	public PlayerBand(int x, int y, int width, UUID speakerId, IntSupplier fabricColour) {
		super(x, y, width, HEIGHT, Component.empty());
		this.speakerId = speakerId;
		this.fabricColour = fabricColour;
	}

	// --- Layout -----------------------------------------------------------------------------------------------

	private int trackX() {
		return getX() + TEXT_X + TIME_W;
	}

	private int trackWidth() {
		return width - TEXT_X - 2 * TIME_W - 4;
	}

	private int trackY() {
		return getY() + 32;
	}

	private boolean overTrack(double mouseX, double mouseY) {
		return mouseX >= trackX() - 2 && mouseX < trackX() + trackWidth() + 2
				&& mouseY >= trackY() - 3 && mouseY < trackY() + SpeakerSprites.BAR_H + 3;
	}

	// --- State ------------------------------------------------------------------------------------------------

	private Optional<SpeakerStatePayload> state() {
		return SpeakerStates.get(speakerId);
	}

	private static Optional<StateEntry> current(SpeakerStatePayload state) {
		if (state.current().isEmpty()) {
			return Optional.empty();
		}
		UUID id = state.current().get();
		return state.entries().stream().filter(e -> e.entryId().equals(id)).findFirst();
	}

	/** Duration of the current track in ms, or -1 when unknown or nothing is current. */
	private long duration() {
		return state().flatMap(PlayerBand::current).map(StateEntry::durationMs).orElse(-1L);
	}

	private boolean seekable() {
		return duration() > 0 && state().map(SpeakerStatePayload::powered).orElse(false);
	}

	private long msAt(double mouseX) {
		double t = (mouseX - trackX()) / trackWidth();
		return Math.round(Mth.clamp(t, 0, 1) * duration());
	}

	// --- Input ------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		// Only the progress bar reacts, and only when the track has a known duration.
		return seekable() && overTrack(event.x(), event.y()) && super.mouseClicked(event, doubleClick);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		seeking = true;
		seekMs = msAt(event.x());
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		if (seeking) {
			seekMs = msAt(event.x());
		}
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		if (seeking) {
			seeking = false;
			if (seekable()) {
				SpeakerStates.send(speakerId, Command.SEEK, msAt(event.x()));
			}
		}
	}

	@Override
	public void playDownSound(SoundManager soundManager) {
	}

	// --- Drawing ----------------------------------------------------------------------------------------------

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		Minecraft minecraft = Minecraft.getInstance();
		Font font = minecraft.font;
		int x = getX();
		int y = getY();
		drawFabric(graphics);

		Optional<SpeakerStatePayload> state = state();
		boolean powered = state.map(SpeakerStatePayload::powered).orElse(false);
		boolean playing = state.map(SpeakerStatePayload::playing).orElse(false);
		drawLogo(graphics, x + 6, y + 38, playing);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, powered ? SpeakerSprites.LED_ON : SpeakerSprites.LED_OFF,
				x + width - 9, y + 4, 5, 5);

		Optional<StateEntry> current = state.flatMap(PlayerBand::current);
		drawLines(graphics, font, state.isPresent() && !powered, current);
		drawProgress(graphics, font, current.isPresent());
		if (duration() > 0 && (seeking || overTrack(mouseX, mouseY))) {
			long ms = seeking ? seekMs : msAt(mouseX);
			graphics.setTooltipForNextFrame(font,
					List.of(Component.translatable(SpeakerSprites.KEY + "time_of", time(ms), time(duration()))),
					Optional.empty(), mouseX, mouseY);
		}
	}

	/** The title of {@code current}, then who added it, or that the speaker is {@code off}. */
	private void drawLines(GuiGraphicsExtractor graphics, Font font, boolean off, Optional<StateEntry> current) {
		int y = getY();
		int textX = getX() + TEXT_X;
		int textW = width - TEXT_X - 14;
		if (current.isPresent()) {
			graphics.text(font, SpeakerSprites.fit(font, current.get().title(), textW), textX, y + 6, SpeakerSprites.TEXT);
		} else {
			String empty = Component.translatable(SpeakerSprites.KEY + "nothing_playing").getString();
			graphics.text(font, SpeakerSprites.fit(font, empty, textW), textX, y + 6, SpeakerSprites.MUTED);
		}
		// Second line: a switched-off speaker says so, otherwise who added the current track.
		if (off) {
			String text = Component.translatable(SpeakerSprites.KEY + "off").getString();
			graphics.text(font, SpeakerSprites.fit(font, text, textW), textX, y + 17, SpeakerSprites.WARNING);
		} else if (current.isPresent()) {
			String by = Component.translatable(SpeakerSprites.KEY + "added_by", current.get().ownerName()).getString();
			graphics.text(font, SpeakerSprites.fit(font, by, textW), textX, y + 17, SpeakerSprites.MUTED);
		}
	}

	private void drawFabric(GuiGraphicsExtractor graphics) {
		int colour = fabricColour.getAsInt();
		int x = getX();
		int y = getY();
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.FABRIC, x, y, width, height,
				ARGB.opaque(ARGB.scaleRGB(colour, FABRIC_SHADE)));
		// Recessed into the window, like the panels below.
		graphics.fill(x, y, x + width, y + 1, 0xFF373737);
		graphics.fill(x, y, x + 1, y + height, 0xFF373737);
		graphics.fill(x, y + height - 1, x + width, y + height, 0xFFFFFFFF);
		graphics.fill(x + width - 1, y + 1, x + width, y + height, 0xFFFFFFFF);
	}

	/**
	 * The four copper bars standing on {@code baseY}. No per-speaker sound level exists on the client, so while
	 * playing they move with a fixed pattern; paused, they stand still.
	 */
	private static void drawLogo(GuiGraphicsExtractor graphics, int x, int baseY, boolean playing) {
		double t = Util.getMillis() / 1000.0;
		for (int i = 0; i < BARS.length; i++) {
			float level = BARS[i];
			if (playing) {
				level *= 0.45F + 0.55F * (float) Math.abs(Math.sin(t * (4.0 + 1.3 * i) + i * 1.7));
			}
			int h = Math.max(2, Math.round(level * BAR_MAX));
			int bx = x + i * (BAR_W + BAR_GAP);
			int top = baseY - h;
			graphics.fill(bx, top, bx + BAR_W, baseY, SpeakerSprites.COPPER);
			graphics.fill(bx, top, bx + BAR_W, top + 1, SpeakerSprites.COPPER_LIGHT);
			graphics.fill(bx, top, bx + 1, baseY, SpeakerSprites.COPPER_LIGHT);
			graphics.fill(bx + BAR_W - 1, top + 1, bx + BAR_W, baseY, SpeakerSprites.COPPER_DARK);
		}
	}

	/** Draws the progress bar and the times, the bar filled while {@code hasCurrent}. */
	private void drawProgress(GuiGraphicsExtractor graphics, Font font, boolean hasCurrent) {
		long duration = duration();
		long position = seeking ? seekMs : SpeakerStates.positionMs(speakerId);
		int textY = trackY() - 2;
		if (hasCurrent) {
			graphics.text(font, time(position), getX() + TEXT_X, textY, SpeakerSprites.COPPER_LIGHT);
			if (duration >= 0) {
				graphics.text(font, time(duration), trackX() + trackWidth() + 4, textY, SpeakerSprites.MUTED);
			}
		}
		boolean known = duration > 0;
		float fraction = known ? Mth.clamp(position / (float) duration, 0, 1) : 0;
		SpeakerSprites.copperBar(graphics, trackX(), trackY(), trackWidth(),
				new SpeakerSprites.Bar(fraction, known, hasCurrent ? (seekable() ? 1.0F : 0.4F) : 0));
	}

	/** {@code m:ss}, through the translation so a language may write it its own way. */
	static Component time(long ms) {
		long seconds = Math.max(0, ms) / 1000;
		return Component.translatable(SpeakerSprites.KEY + "time", seconds / 60, String.format("%02d", seconds % 60));
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
	}
}
