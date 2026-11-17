package com.ravecraft.mod.client.item;

import com.mojang.serialization.MapCodec;
import com.ravecraft.mod.component.SpeakerData;
import com.ravecraft.mod.registry.ModDataComponents;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperties;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** Item model condition {@code ravecraft:speaker/powered}: true when the speaker item carries powered settings. */
public record SpeakerPowered() implements ConditionalItemModelProperty {
	public static final MapCodec<SpeakerPowered> MAP_CODEC = MapCodec.unit(new SpeakerPowered());
	public static final Identifier ID = Identifier.fromNamespaceAndPath("ravecraft", "speaker/powered");
	private static boolean registered;

	/** Registers the property; called by the client and by datagen (which encodes it), safe to call twice. */
	public static synchronized void register() {
		if (!registered) {
			ConditionalItemModelProperties.ID_MAPPER.put(ID, MAP_CODEC);
			registered = true;
		}
	}

	@Override
	public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed,
			ItemDisplayContext displayContext) {
		SpeakerData data = stack.get(ModDataComponents.SPEAKER);
		return data != null && data.settings().powered();
	}

	@Override
	public MapCodec<SpeakerPowered> type() {
		return MAP_CODEC;
	}
}
