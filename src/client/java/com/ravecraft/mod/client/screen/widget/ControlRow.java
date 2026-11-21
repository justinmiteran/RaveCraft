package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.client.screen.SpeakerStates;
import com.ravecraft.mod.core.queue.RepeatMode;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The player's controls under the band: shuffle, previous, play/pause (copper), next and repeat; volume −, slider
 * and +; then power at the row's right end, the slider taking the room in between.
 */
public final class ControlRow {
	private static final String KEY = SpeakerSprites.KEY;
	/** Odd, so the 9 px icons sit exactly in the middle of the button's face. */
	private static final int BUTTON = 21;
	private static final int STEP = BUTTON + 2;
	private static final int VOLUME_X = 7 + 5 * STEP + 3;
	private static final int SMALL = 14;

	private final UUID speakerId;
	private final IconButton shuffle;
	private final IconButton previous;
	private final IconButton play;
	private final IconButton next;
	private final IconButton repeat;
	private final VolumeSlider volume;
	private final IconButton power;
	private @Nullable SpeakerStatePayload state;
	private boolean powered;

	/**
	 * Creates the controls with the row's top at {@code (left + 7, top)}, power ending 7 px before
	 * {@code left + width}, and hands each one to {@code add}.
	 */
	public ControlRow(int left, int top, int width, UUID speakerId, Consumer<AbstractWidget> add) {
		this.speakerId = speakerId;
		int smallY = top + (BUTTON - SMALL) / 2;
		shuffle = new IconButton(left + 7, top, BUTTON, SpeakerSprites.SHUFFLE, label("shuffle.off"), this::toggleShuffle);
		previous = new IconButton(left + 7 + STEP, top, BUTTON, SpeakerSprites.PREVIOUS, label("previous"), () -> send(Command.PREVIOUS));
		play = new IconButton(left + 7 + 2 * STEP, top, BUTTON, SpeakerSprites.PLAY, label("play"), this::togglePlay).copper();
		next = new IconButton(left + 7 + 3 * STEP, top, BUTTON, SpeakerSprites.NEXT, label("next"), () -> send(Command.NEXT));
		repeat = new IconButton(left + 7 + 4 * STEP, top, BUTTON, SpeakerSprites.REPEAT_ALL, label("repeat.all"), this::cycleRepeat);
		// The speaker icon sits in the slider's left margin; the track fills the rest up to the power button.
		volume = new VolumeSlider(left + VOLUME_X, smallY, width - 7 - BUTTON - 4 - VOLUME_X, SMALL, speakerId);
		power = new IconButton(left + width - 7 - BUTTON, top, BUTTON, SpeakerSprites.POWER_OFF, label("power.on"), this::togglePower);
		for (AbstractWidget widget : new AbstractWidget[] {shuffle, previous, play, next, repeat, volume, power}) {
			add.accept(widget);
		}
		update(null, false);
	}

	private static Component label(String key) {
		return Component.translatable(KEY + key);
	}

	/** Refreshes icons, labels and which controls are usable from the latest state and the block's power. */
	public void update(@Nullable SpeakerStatePayload state, boolean powered) {
		this.state = state;
		this.powered = powered;
		boolean known = state != null;
		// Off, only the volume and the power button work; play says the speaker is off.
		boolean on = known && powered;
		boolean shuffled = known && state.shuffle();
		shuffle.show(shuffled ? SpeakerSprites.SHUFFLE_ON : SpeakerSprites.SHUFFLE, label(shuffled ? "shuffle.on" : "shuffle.off"));
		shuffle.active = on;
		updateTransport(state, on);
		updateRepeat(known ? state.repeat() : RepeatMode.OFF, on);
		volume.active = known;
		if (known) {
			volume.sync(state.volume());
		}
		power.show(powered ? SpeakerSprites.POWER_ON : SpeakerSprites.POWER_OFF, label(powered ? "power.off" : "power.on"));
		power.active = known;
	}

	/** Previous, play and next, from the queue of {@code state}. */
	private void updateTransport(@Nullable SpeakerStatePayload state, boolean on) {
		boolean known = state != null;
		boolean queued = known && !state.entries().isEmpty();
		boolean playing = known && state.playing();
		previous.active = on && queued;
		play.show(playing ? SpeakerSprites.PAUSE : SpeakerSprites.PLAY, playLabel(known, playing));
		play.active = on && queued;
		next.active = on && state.current().isPresent();
	}

	private Component playLabel(boolean known, boolean playing) {
		if (known && !powered) {
			return label("off");
		}
		return label(playing ? "pause" : "play");
	}

	private void updateRepeat(RepeatMode mode, boolean on) {
		repeat.show(switch (mode) {
			case OFF -> SpeakerSprites.REPEAT_OFF;
			case ALL -> SpeakerSprites.REPEAT_ALL;
			case ONE -> SpeakerSprites.REPEAT_ONE;
		}, label("repeat." + mode.name().toLowerCase(Locale.ROOT)));
		repeat.active = on;
	}

	/** The next repeat mode on a click: ALL → ONE → OFF → ALL. */
	static RepeatMode nextRepeat(RepeatMode mode) {
		return switch (mode) {
			case ALL -> RepeatMode.ONE;
			case ONE -> RepeatMode.OFF;
			case OFF -> RepeatMode.ALL;
		};
	}

	private void cycleRepeat() {
		if (state != null) {
			SpeakerStates.send(speakerId, Command.REPEAT, nextRepeat(state.repeat()).ordinal());
		}
	}

	private void toggleShuffle() {
		if (state != null) {
			SpeakerStates.send(speakerId, Command.SHUFFLE, state.shuffle() ? 0 : 1);
		}
	}

	private void togglePlay() {
		send(state != null && state.playing() ? Command.PAUSE : Command.PLAY);
	}

	private void togglePower() {
		SpeakerStates.send(speakerId, Command.POWER, powered ? 0 : 1);
	}

	private void send(Command command) {
		SpeakerStates.send(speakerId, command, Optional.empty());
	}
}
