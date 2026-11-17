package com.ravecraft.mod.item;

import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.registry.ModDataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class PortableSpeakerItem extends BlockItem {
	public PortableSpeakerItem(Block block, Properties properties) {
		super(block, properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		// Placeholder behaviour: the UI PR replaces this with "open the speaker screen".
		if (!level.isClientSide()) {
			ItemStack stack = player.getItemInHand(hand);
			SpeakerData data = stack.get(ModDataComponents.SPEAKER);
			if (data == null) {
				data = SpeakerData.create();
			}
			stack.set(ModDataComponents.SPEAKER, data.withSettings(data.settings().togglePower()));
		}
		return InteractionResult.SUCCESS;
	}
}
