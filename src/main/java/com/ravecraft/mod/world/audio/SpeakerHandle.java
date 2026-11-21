package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** A live view of a speaker wherever it is hosted, resolved from its id by {@link SpeakerHosts#handle}. */
public interface SpeakerHandle {
	/** The speaker's id. */
	UUID id();

	/** The level its host is in. */
	ServerLevel level();

	/** Where it lives. */
	SpeakerHost host();

	/** Its data, read where it lives. */
	SpeakerData data();

	/** Writes the data where it lives, then marks the speaker's open screens dirty. */
	void setData(SpeakerData data);

	/**
	 * Which kind of speaker it is, which picks its acoustic profile. Every speaker is a portable one until the party
	 * speaker's block and item exist: this is the one place that tells.
	 */
	default SpeakerKind kind() {
		return SpeakerKind.PORTABLE;
	}

	/** Whether it is switched on. */
	default boolean powered() {
		return data().settings().powered();
	}

	/** Where the sound comes from: block centre, carrier eyes (in hand) or chest, entity position. */
	Vec3 position();

	/** Whether {@code player} is close enough to the speaker to control it. */
	boolean canControl(ServerPlayer player);

	/** Players who should hear it: chunk trackers (block) or entity trackers plus the carrier. */
	Collection<ServerPlayer> trackers();
}
