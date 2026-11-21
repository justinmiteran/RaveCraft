package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.component.SpeakerData;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** A live view of a speaker wherever it is hosted, resolved from its id by {@link SpeakerHosts#handle}. */
public interface SpeakerHandle {
	UUID id();

	ServerLevel level();

	SpeakerHost host();

	SpeakerData data();

	/** Writes the data where it lives, then marks the speaker's open screens dirty. */
	void setData(SpeakerData data);

	default boolean powered() {
		return data().settings().powered();
	}

	/** Where the sound comes from: block centre, carrier eyes (in hand) or chest, entity position. */
	Vec3 position();

	boolean canControl(ServerPlayer player);

	/** Players who should hear it: chunk trackers (block) or entity trackers plus the carrier. */
	Collection<ServerPlayer> trackers();
}
