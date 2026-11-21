package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.registry.ModDataComponents;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/** A speaker item lying on the ground. */
record DroppedHandle(ServerLevel level, ItemEntity entity) implements SpeakerHandle {
	@Override
	public UUID id() {
		return data().id();
	}

	@Override
	public SpeakerHost host() {
		return new SpeakerHost.Dropped(entity.getUUID(), level.dimension());
	}

	@Override
	public SpeakerData data() {
		return Objects.requireNonNull(entity.getItem().get(ModDataComponents.SPEAKER), "dropped speaker without data");
	}

	@Override
	public void setData(SpeakerData data) {
		entity.getItem().set(ModDataComponents.SPEAKER, data);
		SpeakerHosts.stored(data.id(), data.queue());
		SpeakerWatchers.markDirty(data.id());
	}

	@Override
	public Vec3 position() {
		return entity.position();
	}

	@Override
	public boolean canControl(ServerPlayer player) {
		return player.level() == level && player.isWithinEntityInteractionRange(entity, 1.0);
	}

	@Override
	public Collection<ServerPlayer> trackers() {
		return PlayerLookup.tracking(entity);
	}
}
