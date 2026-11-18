package com.ravecraft.mod.client.options;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/** How many voxels the occlusion tracer may visit per client tick. */
public enum OcclusionQuality implements StringRepresentable {
	LOW(5_000),
	MEDIUM(20_000),
	HIGH(60_000);

	public static final Codec<OcclusionQuality> CODEC = StringRepresentable.fromEnum(OcclusionQuality::values);

	private final int voxelsPerTick;
	private final String serializedName;
	private final Component caption;

	OcclusionQuality(int voxelsPerTick) {
		this.voxelsPerTick = voxelsPerTick;
		this.serializedName = name().toLowerCase(Locale.ROOT);
		this.caption = Component.translatable("options.ravecraft.occlusionQuality." + serializedName);
	}

	public int voxelsPerTick() {
		return voxelsPerTick;
	}

	public Component caption() {
		return caption;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
