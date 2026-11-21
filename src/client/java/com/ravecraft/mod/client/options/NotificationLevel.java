package com.ravecraft.mod.client.options;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** Which RaveCraft toasts this player sees: every one, only the errors (a track removed), or none. */
public enum NotificationLevel implements StringRepresentable {
	ALL(true, true),
	ERRORS(false, true),
	NONE(false, false);

	public static final Codec<NotificationLevel> CODEC = StringRepresentable.fromEnum(NotificationLevel::values);

	private final boolean nowPlaying;
	private final boolean errors;
	private final String serializedName;
	private final Component caption;

	NotificationLevel(boolean nowPlaying, boolean errors) {
		this.nowPlaying = nowPlaying;
		this.errors = errors;
		this.serializedName = name().toLowerCase(Locale.ROOT);
		this.caption = Component.translatable("options.ravecraft.notifications." + serializedName);
	}

	/** Whether the "Now playing" toast shows. */
	public boolean nowPlaying() {
		return nowPlaying;
	}

	/** Whether error toasts show. */
	public boolean errors() {
		return errors;
	}

	/** The level's name in the options. */
	public Component caption() {
		return caption;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
