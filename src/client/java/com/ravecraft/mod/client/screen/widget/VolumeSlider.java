package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.client.screen.SpeakerStates;
import com.ravecraft.mod.core.speaker.SpeakerSettings;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

/**
 * The speaker's volume, 0–100, as a speaker icon followed by a copper bar with a knob. While dragging it sends at most one {@code VOLUME} per
 * {@link #SEND_INTERVAL_MS}, plus one on release; for a moment after a local change it ignores the server's older
 * value so the knob does not jump back.
 */
public final class VolumeSlider extends AbstractWidget {
	private static final long SEND_INTERVAL_MS = 250;
	private static final long HOLD_LOCAL_MS = 1000;
	/** The track starts after the speaker icon and a small gap. */
	private static final int TRACK_X = SpeakerSprites.ICON + 5;

	private final BlockPos pos;
	private int volume = SpeakerSettings.DEFAULT_VOLUME;
	private int sentVolume = -1;
	private boolean dragging;
	private long lastSendMs;
	private long localUntilMs;

	/** The volume slider of the speaker at {@code pos}. */
	public VolumeSlider(int x, int y, int width, int height, BlockPos pos) {
		super(x, y, width, height, Component.empty());
		this.pos = pos;
		updateLabel();
	}

	/** The volume the server reports, shown unless the player has just moved the slider. */
	public void sync(int serverVolume) {
		if (!dragging && Util.getMillis() >= localUntilMs && serverVolume != volume) {
			volume = serverVolume;
			sentVolume = serverVolume;
			updateLabel();
		}
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		dragging = true;
		set(valueAt(event.x()));
		sendThrottled();
	}

	@Override
	protected void onDrag(MouseButtonEvent event, double dx, double dy) {
		if (dragging) {
			set(valueAt(event.x()));
			sendThrottled();
		}
	}

	@Override
	public void onRelease(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			send();
		}
	}

	private int valueAt(double mouseX) {
		double t = (mouseX - (trackX() + SpeakerSprites.KNOB_W / 2.0)) / (trackWidth() - SpeakerSprites.KNOB_W);
		return (int) Math.round(Mth.clamp(t, 0, 1) * SpeakerSettings.MAX_VOLUME);
	}

	private void set(int value) {
		int clamped = Mth.clamp(value, SpeakerSettings.MIN_VOLUME, SpeakerSettings.MAX_VOLUME);
		localUntilMs = Util.getMillis() + HOLD_LOCAL_MS;
		if (clamped != volume) {
			volume = clamped;
			updateLabel();
		}
	}

	private void sendThrottled() {
		if (Util.getMillis() - lastSendMs >= SEND_INTERVAL_MS) {
			send();
		}
	}

	private void send() {
		localUntilMs = Util.getMillis() + HOLD_LOCAL_MS;
		if (volume != sentVolume) {
			sentVolume = volume;
			lastSendMs = Util.getMillis();
			SpeakerStates.send(pos, Command.VOLUME, volume);
		}
	}

	private void updateLabel() {
		Component label = Component.translatable(SpeakerSprites.KEY + "volume", volume);
		setMessage(label);
		setTooltip(Tooltip.create(label));
	}

	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SpeakerSprites.VOLUME, getX(),
				getY() + (height - SpeakerSprites.ICON) / 2, SpeakerSprites.ICON, SpeakerSprites.ICON, active ? 1.0F : 0.4F);
		SpeakerSprites.copperBar(graphics, trackX(), getY() + (height - SpeakerSprites.BAR_H) / 2, trackWidth(),
				new SpeakerSprites.Bar(volume / (float) SpeakerSettings.MAX_VOLUME, true, 1.0F));
	}

	private int trackX() {
		return getX() + TRACK_X;
	}

	private int trackWidth() {
		return width - TRACK_X;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
