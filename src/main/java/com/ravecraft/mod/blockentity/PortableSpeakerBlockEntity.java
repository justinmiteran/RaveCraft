package com.ravecraft.mod.blockentity;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.block.PortableSpeakerBlock;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.core.queue.SpeakerQueue;
import com.ravecraft.mod.registry.ModBlockEntities;
import com.ravecraft.mod.registry.ModDataComponents;
import com.ravecraft.mod.world.audio.SpeakerHosts;
import com.ravecraft.mod.world.audio.SpeakerPlayback;
import com.ravecraft.mod.world.audio.SpeakerWatchers;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** A placed portable speaker: its data (id, settings, queue), its fabric colour and its name. */
public class PortableSpeakerBlockEntity extends BlockEntity {
	public static final String KEY = "speaker";
	private static final String COLOR_KEY = "color";
	private static final String NAME_KEY = "CustomName";

	private SpeakerData data = SpeakerData.create();
	/** Dye applied to the fabric, or null when undyed ({@link PortableSpeakerBlock#DEFAULT_COLOR}). */
	private @Nullable DyedItemColor color;
	/** The name given to the item in an anvil, shown as the screen's title; null when unnamed. */
	private @Nullable Component customName;

	/**
	 * A speaker with fresh data, undyed and unnamed.
	 *
	 * @param pos where the block stands
	 * @param state the block's state
	 */
	public PortableSpeakerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlockEntities.PORTABLE_SPEAKER, pos, state);
	}

	/**
	 * The speaker's id, settings and queue.
	 *
	 * @return the speaker's data
	 */
	public SpeakerData data() {
		return data;
	}

	/**
	 * Replaces the speaker's data and marks the block entity changed.
	 *
	 * @param data the new data
	 */
	public void setData(SpeakerData data) {
		this.data = data;
		setChanged();
	}

	/**
	 * Sets the volume and sends it to everyone tracking the chunk: listeners read it from their own copy of the
	 * block entity (gain and range). Unlike queue changes, which only go to the screen's watchers.
	 *
	 * @param volume the new volume
	 */
	public void setVolume(int volume) {
		setData(data.withSettings(data.settings().withVolume(volume)));
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/**
	 * The dye on the fabric, or null when undyed.
	 *
	 * @return the dye, or null
	 */
	public @Nullable DyedItemColor dyedColor() {
		return color;
	}

	/**
	 * RGB colour of the fabric, as tinted by the client.
	 *
	 * @return the fabric's RGB colour
	 */
	public int fabricColor() {
		return color != null ? color.rgb() : PortableSpeakerBlock.DEFAULT_COLOR;
	}

	/**
	 * The name given in an anvil, or null when unnamed.
	 *
	 * @return the custom name, or null
	 */
	public @Nullable Component customName() {
		return customName;
	}

	/**
	 * Dyes the fabric (null: undyed) and sends it to the clients tracking the chunk.
	 *
	 * @param color the new dye, or null to undye
	 */
	public void setDyedColor(@Nullable DyedItemColor color) {
		this.color = color;
		setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	/** Flips the power state, keeping the block's POWERED property in sync. */
	public void togglePower() {
		data = data.withSettings(data.settings().togglePower());
		if (level != null) {
			level.setBlock(worldPosition, getBlockState().setValue(PortableSpeakerBlock.POWERED, data.settings().powered()), 3);
		}
		setChanged();
		if (level instanceof ServerLevel serverLevel) {
			if (!data.settings().powered()) {
				SpeakerPlayback.pauseAt(serverLevel.getServer(), data.id());
			}
			// Open screens show the power state, whoever toggled it (the POWER command or a sneak-click).
			SpeakerWatchers.markDirty(data.id());
		}
	}

	/**
	 * Called when the block is broken or its chunk unloads. The session carries on for a grace: a broken block's
	 * drop takes it over; otherwise {@link SpeakerHosts#tick} pauses it once the id is lost for good.
	 */
	@Override
	public void setRemoved() {
		super.setRemoved();
		if (level instanceof ServerLevel) {
			SpeakerHosts.removed(this);
			// Open screens close: the next flush finds the speaker gone.
			SpeakerWatchers.markDirty(data.id());
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		// A chunk is saved before its block entities are removed: while playing, save where the track is now.
		output.store(KEY, SpeakerData.CODEC, level instanceof ServerLevel ? data.withQueue(SpeakerPlayback.liveQueue(data.id(), data.queue())) : data);
		output.storeNullable(COLOR_KEY, DyedItemColor.CODEC, color);
		output.storeNullable(NAME_KEY, ComponentSerialization.CODEC, customName);
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
		DyedItemColor previous = color;
		color = input.read(COLOR_KEY, DyedItemColor.CODEC).orElse(null);
		customName = parseCustomNameSafe(input, NAME_KEY);
		// The fabric tint is baked into the chunk mesh: rebuild it when the server sends a new colour.
		if (level != null && level.isClientSide() && !Objects.equals(previous, color)) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		SpeakerData fromItem = components.get(ModDataComponents.SPEAKER);
		if (fromItem != null) {
			data = fromItem;
		}
		color = components.get(DataComponents.DYED_COLOR);
		customName = components.get(DataComponents.CUSTOM_NAME);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder components) {
		super.collectImplicitComponents(components);
		// While playing, the stored position is stale: the item gets where the track is now.
		components.set(ModDataComponents.SPEAKER, level instanceof ServerLevel ? data.withQueue(SpeakerPlayback.liveQueue(data.id(), data.queue())) : data);
		if (color != null) {
			components.set(DataComponents.DYED_COLOR, color);
		}
		if (customName != null) {
			components.set(DataComponents.CUSTOM_NAME, customName);
		}
	}

	@Override
	public void removeComponentsFromTag(ValueOutput output) {
		output.discard(KEY);
		output.discard(COLOR_KEY);
		output.discard(NAME_KEY);
	}

	/** The client needs the colour to tint the fabric. */
	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		// The queue only goes to players with the screen open (SpeakerState), not to everyone tracking the chunk.
		CompoundTag tag = saveCustomOnly(registries);
		tag.store(KEY, SpeakerData.CODEC, registries.createSerializationContext(NbtOps.INSTANCE), data.withQueue(SpeakerQueue.EMPTY));
		return tag;
	}
}
