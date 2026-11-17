package com.ravecraft.mod.blockentity;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.registry.ModBlockEntities;
import com.ravecraft.mod.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class PortableSpeakerBlockEntity extends BlockEntity {
	private static final String KEY = "speaker";

	private SpeakerData data = SpeakerData.create();

	public PortableSpeakerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PORTABLE_SPEAKER, pos, state);
	}

	public SpeakerData data() {
		return data;
	}

	public void setData(SpeakerData data) {
		this.data = data;
		setChanged();
	}

	/** Flips the power state, keeping the block's POWERED property in sync. */
	public void togglePower() {
		data = data.withSettings(data.settings().togglePower());
		if (level != null) {
			level.setBlock(worldPosition, getBlockState().setValue(PortableSpeakerBlock.POWERED, data.settings().powered()), 3);
		}
		setChanged();
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store(KEY, SpeakerData.CODEC, data);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		input.read(KEY, SpeakerData.CODEC).ifPresentOrElse(read -> data = read, () -> {
			// Present but undecodable: keep the random id from construction, but do not fail silently.
			if (input.child(KEY).isPresent()) {
				RaveCraft.LOGGER.warn("Portable speaker at {} has undecodable data, using a fresh identity", worldPosition);
			}
		});
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		SpeakerData fromItem = components.get(ModDataComponents.SPEAKER);
		if (fromItem != null) {
			data = fromItem;
		}
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		components.set(ModDataComponents.SPEAKER, data);
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		output.discard(KEY);
	}
}
