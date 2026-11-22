package com.ravecraft.mod.item;

import com.ravecraft.mod.block.SpeakerScreenOpener;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.registry.ModDataComponents;
import com.ravecraft.mod.world.audio.SpeakerHosts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/** The portable speaker as an item: places the block and keeps its data. */
public class PortableSpeakerItem extends BlockItem {
	/**
	 * The item that places {@code block}.
	 *
	 * @param block the block placed
	 * @param properties the item's properties
	 */
	public PortableSpeakerItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		SpeakerData data = stack.get(ModDataComponents.SPEAKER);
		if (!level.isClientSide()) {
			if (data == null) {
				stack.set(ModDataComponents.SPEAKER, SpeakerData.create());
			}
		} else if (data != null) {
			SpeakerScreenOpener.open(data.id(), stack.getHoverName());
		}
		return InteractionResult.SUCCESS;
	}

	/** A player carrying the speaker hosts it: reported every tick, so it keeps playing from them. */
	@Override
	public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
		super.inventoryTick(stack, level, owner, slot);
		if (owner instanceof ServerPlayer player) {
			SpeakerHosts.carried(player, stack, slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND);
		}
	}

	/** Burnt, blown up or otherwise destroyed on the ground: its session ends for good. */
	@Override
	public void onDestroyed(ItemEntity itemEntity) {
		super.onDestroyed(itemEntity);
		SpeakerData data = itemEntity.getItem().get(ModDataComponents.SPEAKER);
		if (data != null && itemEntity.level() instanceof ServerLevel level) {
			SpeakerHosts.destroyed(level.getServer(), data.id());
		}
	}
}
