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
	/**
	 * The speaker's id.
	 *
	 * @return the id
	 */
	UUID id();

	/**
	 * The level its host is in.
	 *
	 * @return the level
	 */
	ServerLevel level();

	/**
	 * Where it lives.
	 *
	 * @return the host
	 */
	SpeakerHost host();

	/**
	 * Its data, read where it lives.
	 *
	 * @return the data
	 */
	SpeakerData data();

	/**
	 * Writes the data where it lives, then marks the speaker's open screens dirty.
	 *
	 * @param data the new data
	 */
	void setData(SpeakerData data);

	/**
	 * Which kind of speaker it is, which picks its acoustic profile: from its block, or from its item.
	 *
	 * @return the speaker kind
	 */
	SpeakerKind kind();

	/**
	 * Whether it is switched on.
	 *
	 * @return true when switched on
	 */
	default boolean powered() {
		return data().settings().powered();
	}

	/**
	 * Where the sound comes from: block centre, carrier eyes (in hand) or chest, entity position.
	 *
	 * @return the position of the sound
	 */
	Vec3 position();

	/**
	 * Whether {@code player} is close enough to the speaker to control it.
	 *
	 * @param player the player
	 * @return true when the player may control it
	 */
	boolean canControl(ServerPlayer player);

	/**
	 * Players who should hear it: chunk trackers (block) or entity trackers plus the carrier.
	 *
	 * @return the players to send to
	 */
	Collection<ServerPlayer> trackers();
}
