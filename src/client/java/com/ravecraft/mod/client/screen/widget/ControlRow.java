package com.ravecraft.mod.client.screen.widget;

import com.ravecraft.mod.client.screen.SpeakerStates;
import com.ravecraft.mod.core.queue.RepeatMode;
import com.ravecraft.mod.network.payload.SpeakerCommandPayload.Command;
import com.ravecraft.mod.network.payload.SpeakerStatePayload;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The player's controls under the band: shuffle, previous, play/pause (copper), next and repeat; volume −, slider
 * and +; then power. Positions are relative to the window's top-left corner.
 */
public final class ControlRow {
	private static final String KEY = SpeakerSprites.KEY;
	private static final int BUTTON = 20;
	private static final int SMALL = 14;

	private final BlockPos pos;
	private final IconButton shuffle;
	private final IconButton previous;
	private final IconButton play;
	private final IconButton next;
	private final IconButton repeat;
	private final Button volumeDown;
	private final VolumeSlider volume;
	private final Button volumeUp;
	private final IconButton power;
	private @Nullable SpeakerStatePayload state;
	private boolean powered;

	/** Creates the controls with the row's top at {@code (left + 7, top)} and hands each one to {@code add}. */
	public ControlRow(int left, int top, BlockPos pos, Consumer<AbstractWidget> add) {
		this.pos = pos;
		int smallY = top + (BUTTON - SMALL) / 2;
		shuffle = new IconButton(left + 7, top, BUTTON, SpeakerSprites.SHUFFLE, label("shuffle.off"), this::toggleShuffle);
		previous = new IconButton(left + 29, top, BUTTON, SpeakerSprites.PREVIOUS, label("previous"), () -> send(Command.PREVIOUS));
		play = new IconButton(left + 51, top, BUTTON, SpeakerSprites.PLAY, label("play"), this::togglePlay).copper();
		next = new IconButton(left + 73, top, BUTTON, SpeakerSprites.NEXT, label("next"), () -> send(Command.NEXT));
		repeat = new IconButton(left + 95, top, BUTTON, SpeakerSprites.REPEAT_ALL, label("repeat.all"), this::cycleRepeat);
		volume = new VolumeSlider(left + 137, smallY, 66, SMALL, pos);
		volumeDown = Button.builder(Component.literal("-"), b -> volume.nudge(-VolumeSlider.STEP))
				.bounds(left + 121, smallY, SMALL, SMALL).tooltip(Tooltip.create(label("volume_down"))).build();
		volumeUp = Button.builder(Component.literal("+"), b -> volume.nudge(VolumeSlider.STEP))
				.bounds(left + 205, smallY, SMALL, SMALL).tooltip(Tooltip.create(label("volume_up"))).build();
		power = new IconButton(left + 229, top, BUTTON, SpeakerSprites.POWER_OFF, label("power.on"), this::togglePower);
		for (AbstractWidget widget : new AbstractWidget[] {shuffle, previous, play, next, repeat, volumeDown, volume, volumeUp, power}) {
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
		boolean queued = known && !state.entries().isEmpty();
		boolean playing = known && state.playing();

		boolean shuffled = known && state.shuffle();
		shuffle.show(shuffled ? SpeakerSprites.SHUFFLE_ON : SpeakerSprites.SHUFFLE, label(shuffled ? "shuffle.on" : "shuffle.off"));
		shuffle.active = known;
		previous.active = queued;
		play.show(playing ? SpeakerSprites.PAUSE : SpeakerSprites.PLAY, label(playing ? "pause" : "play"));
		play.active = queued && (playing || powered);
		next.active = known && state.current().isPresent();
		RepeatMode mode = known ? state.repeat() : RepeatMode.OFF;
		repeat.show(switch (mode) {
			case OFF -> SpeakerSprites.REPEAT_OFF;
			case ALL -> SpeakerSprites.REPEAT_ALL;
			case ONE -> SpeakerSprites.REPEAT_ONE;
		}, label("repeat." + mode.name().toLowerCase(Locale.ROOT)));
		repeat.active = known;
		volumeDown.active = known;
		volume.active = known;
		volumeUp.active = known;
		if (known) {
			volume.sync(state.volume());
		}
		power.show(powered ? SpeakerSprites.POWER_ON : SpeakerSprites.POWER_OFF, label(powered ? "power.off" : "power.on"));
		power.active = known;
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
			SpeakerStates.send(pos, Command.REPEAT, nextRepeat(state.repeat()).ordinal());
		}
	}

	private void toggleShuffle() {
		if (state != null) {
			SpeakerStates.send(pos, Command.SHUFFLE, state.shuffle() ? 0 : 1);
		}
	}

	private void togglePlay() {
		send(state != null && state.playing() ? Command.PAUSE : Command.PLAY);
	}

	private void togglePower() {
		SpeakerStates.send(pos, Command.POWER, powered ? 0 : 1);
	}

	private void send(Command command) {
		SpeakerStates.send(pos, command, Optional.empty());
	}
}
