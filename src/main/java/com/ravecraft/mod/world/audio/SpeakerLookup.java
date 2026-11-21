package com.ravecraft.mod.world.audio;

import com.ravecraft.mod.blockentity.PortableSpeakerBlockEntity;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.registry.ModDataComponents;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;

/** Finds a speaker where its host says it is: in a loaded block, a carrier's inventory or an item entity. */
final class SpeakerLookup {
	private SpeakerLookup() {
	}

	/** Speaker {@code id} at {@code host}; empty when that host is not loaded or no longer holds the id. */
	static Optional<SpeakerHandle> handle(MinecraftServer server, SpeakerHost host, UUID id) {
		return switch (host) {
			case SpeakerHost.Block block -> blockHandle(server, block, id);
			case SpeakerHost.Carrier carrier -> carrierHandle(server, carrier, id);
			case SpeakerHost.Dropped dropped -> droppedHandle(server, dropped, id);
		};
	}

	/** The placed block of {@code id}, when its chunk is loaded and it still holds the id. */
	private static Optional<SpeakerHandle> blockHandle(MinecraftServer server, SpeakerHost.Block block, UUID id) {
		ServerLevel level = server.getLevel(block.dimension());
		if (level == null || !level.isLoaded(block.pos())
				|| !(level.getBlockEntity(block.pos()) instanceof PortableSpeakerBlockEntity speaker)
				|| !speaker.data().id().equals(id)) {
			return Optional.empty();
		}
		return Optional.of(new BlockHandle(level, block.pos(), speaker));
	}

	/** Searches the carrier's inventory: only when a handle is asked for, never each tick. */
	private static Optional<SpeakerHandle> carrierHandle(MinecraftServer server, SpeakerHost.Carrier carrier, UUID id) {
		ServerPlayer player = server.getPlayerList().getPlayer(carrier.player());
		return player == null
				? Optional.empty()
				: findCarried(player, id).map(stack -> new CarrierHandle(player, stack, carrier.inHand()));
	}

	/** The stack of {@code id} the player carries, in its inventory or where nothing ticks it. */
	static Optional<ItemStack> findCarried(ServerPlayer player, UUID id) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (hasId(inventory.getItem(slot), id)) {
				return Optional.of(inventory.getItem(slot));
			}
		}
		return findUnticked(player, id);
	}

	/**
	 * The stack of {@code id} where the player holds it but vanilla never ticks it, so it does not report: on the
	 * mouse cursor of the open menu, or in the 2x2 crafting grid. The creative inventory's cursor exists only on the
	 * client: a speaker held there is not found, and pauses once its grace runs out.
	 */
	static Optional<ItemStack> findUnticked(ServerPlayer player, UUID id) {
		ItemStack cursor = player.containerMenu.getCarried();
		if (hasId(cursor, id)) {
			return Optional.of(cursor);
		}
		CraftingContainer grid = player.inventoryMenu.getCraftSlots();
		for (int slot = 0; slot < grid.getContainerSize(); slot++) {
			if (hasId(grid.getItem(slot), id)) {
				return Optional.of(grid.getItem(slot));
			}
		}
		return Optional.empty();
	}

	private static boolean hasId(ItemStack stack, UUID id) {
		SpeakerData data = stack.get(ModDataComponents.SPEAKER);
		return data != null && data.id().equals(id);
	}

	/** The item entity of {@code id}, when it is loaded and still holds the id. */
	private static Optional<SpeakerHandle> droppedHandle(MinecraftServer server, SpeakerHost.Dropped dropped, UUID id) {
		ServerLevel level = server.getLevel(dropped.dimension());
		if (level == null || !(level.getEntity(dropped.entity()) instanceof ItemEntity item) || item.isRemoved()) {
			return Optional.empty();
		}
		SpeakerData data = item.getItem().get(ModDataComponents.SPEAKER);
		return data != null && data.id().equals(id) ? Optional.of(new DroppedHandle(level, item)) : Optional.empty();
	}
}
