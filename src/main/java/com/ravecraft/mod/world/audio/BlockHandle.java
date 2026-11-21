package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.component.SpeakerData;
import java.util.Collection;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** A speaker placed as a block. */
record BlockHandle(ServerLevel level, BlockPos pos, PortableSpeakerBlockEntity speaker) implements SpeakerHandle {
	@Override
	public UUID id() {
		return speaker.data().id();
	}

	@Override
	public SpeakerHost host() {
		return new SpeakerHost.Block(level.dimension(), pos);
	}

	@Override
	public SpeakerData data() {
		return speaker.data();
	}

	@Override
	public void setData(SpeakerData data) {
		speaker.setData(data);
		SpeakerHosts.stored(data.id(), data.queue());
		SpeakerWatchers.markDirty(id());
	}

	@Override
	public Vec3 position() {
		return Vec3.atCenterOf(pos);
	}

	@Override
	public boolean canControl(ServerPlayer player) {
		return player.level() == level && player.isWithinBlockInteractionRange(pos, 1.0);
	}

	@Override
	public Collection<ServerPlayer> trackers() {
		return PlayerLookup.tracking(level, pos);
	}
}
