package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.registry.ModDataComponents;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** A speaker item in a player's inventory: {@code stack} is the stack itself, found by its id. */
record CarrierHandle(ServerPlayer player, ItemStack stack, boolean inHand) implements SpeakerHandle {
	/** Height fraction of the carrier where a speaker that is not in hand sounds from (chest). */
	static final double CHEST_HEIGHT = 0.6;

	@Override
	public UUID id() {
		return data().id();
	}

	@Override
	public ServerLevel level() {
		return player.level();
	}

	@Override
	public SpeakerHost host() {
		return new SpeakerHost.Carrier(player.getUUID(), player.level().dimension(), inHand);
	}

	@Override
	public SpeakerData data() {
		return Objects.requireNonNull(stack.get(ModDataComponents.SPEAKER), "carried speaker without data");
	}

	@Override
	public void setData(SpeakerData data) {
		stack.set(ModDataComponents.SPEAKER, data);
		SpeakerHosts.stored(data.id(), data.queue());
		SpeakerWatchers.markDirty(data.id());
	}

	@Override
	public Vec3 position() {
		return inHand ? player.getEyePosition() : player.position().add(0, CHEST_HEIGHT * player.getBbHeight(), 0);
	}

	/** Its carrier always can, wherever it stands; others must be within reach of the carrier. */
	@Override
	public boolean canControl(ServerPlayer other) {
		return other.getUUID().equals(player.getUUID())
				|| other.level() == player.level() && other.isWithinEntityInteractionRange(player, 1.0);
	}

	@Override
	public Collection<ServerPlayer> trackers() {
		List<ServerPlayer> players = new ArrayList<>(PlayerLookup.tracking(player));
		if (!players.contains(player)) {
			players.add(player);
		}
		return players;
	}
}
