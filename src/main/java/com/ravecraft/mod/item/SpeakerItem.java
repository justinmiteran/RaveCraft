package com.ravecraft.mod.item;

import com.ravecraft.mod.block.SpeakerBlock;
import com.ravecraft.mod.block.SpeakerScreenOpener;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.speaker.CarryPlace;
import com.ravecraft.mod.core.speaker.SpeakerKind;
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

/** A speaker as an item, of the kind of the block it places: places the block and keeps its data. */
public class SpeakerItem extends BlockItem {
	/** The item that places {@code block}. */
	public SpeakerItem(Block block, Properties properties) {
		super(block, properties);
	}

	/** The kind of speaker this item places, read from its block. */
	public SpeakerKind kind() {
		return getBlock() instanceof SpeakerBlock block ? block.kind() : SpeakerKind.PORTABLE;
	}

	/** The kind of the speaker in {@code stack}; a portable one if the stack is somehow not a speaker. */
	public static SpeakerKind kindOf(ItemStack stack) {
		return stack.getItem() instanceof SpeakerItem item ? item.kind() : SpeakerKind.PORTABLE;
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
			SpeakerHosts.carried(player, stack, placeOf(slot));
		}
	}

	/** Where a stack ticked in {@code slot} is carried: a hand, the back (chest slot), or the inventory. */
	static CarryPlace placeOf(@Nullable EquipmentSlot slot) {
		if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
			return CarryPlace.HAND;
		}
		return slot == EquipmentSlot.CHEST ? CarryPlace.BACK : CarryPlace.INVENTORY;
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
