package com.ravecraft.mod.client.options;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * How many voxels the occlusion tracer may visit per client tick, and how finely the reverb scheduler measures the
 * spaces: rays per room probe and voxels per tick, a budget of its own.
 */
public enum OcclusionQuality implements StringRepresentable {
	LOW(5_000, 16, 1_500),
	MEDIUM(20_000, 32, 3_000),
	HIGH(60_000, 48, 6_000);

	public static final Codec<OcclusionQuality> CODEC = StringRepresentable.fromEnum(OcclusionQuality::values);

	private final int voxelsPerTick;
	private final int reverbRays;
	private final int reverbVoxelsPerTick;
	private final String serializedName;
	private final Component caption;

	OcclusionQuality(int voxelsPerTick, int reverbRays, int reverbVoxelsPerTick) {
		this.voxelsPerTick = voxelsPerTick;
		this.reverbRays = reverbRays;
		this.reverbVoxelsPerTick = reverbVoxelsPerTick;
		this.serializedName = name().toLowerCase(Locale.ROOT);
		this.caption = Component.translatable("options.ravecraft.occlusionQuality." + serializedName);
	}

	/**
	 * How many voxels the occlusion tracer may visit per tick.
	 *
	 * @return the voxel budget per tick
	 */
	public int voxelsPerTick() {
		return voxelsPerTick;
	}

	/**
	 * Rays cast by each room probe.
	 *
	 * @return the number of rays per probe
	 */
	public int reverbRays() {
		return reverbRays;
	}

	/**
	 * Voxels the room probes may visit per client tick, shared by the listener's and the speakers' spaces.
	 *
	 * @return the voxel budget per tick of the room probes
	 */
	public int reverbVoxelsPerTick() {
		return reverbVoxelsPerTick;
	}

	/**
	 * The quality's name in the options.
	 *
	 * @return the quality's caption
	 */
	public Component caption() {
		return caption;
	}

	@Override
	public String getSerializedName() {
		return serializedName;
	}
}
