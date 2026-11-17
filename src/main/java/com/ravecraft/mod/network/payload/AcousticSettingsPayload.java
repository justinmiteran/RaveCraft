package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.Directivity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S→C on join: the server's acoustic model. The portable profile is always omnidirectional, so it is not sent. Decoded
 * values are brought into their bounds, so a broken server cannot feed NaN gains to the renderer.
 */
public record AcousticSettingsPayload(AcousticSettings settings) implements CustomPacketPayload {
	public static final Type<AcousticSettingsPayload> TYPE = new Type<>(RaveCraft.id("acoustic_settings"));
	private static final StreamCodec<ByteBuf, DeviceProfile> PROFILE = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, DeviceProfile::powerWatts,
			ByteBufCodecs.DOUBLE, DeviceProfile::sensitivityDb,
			ByteBufCodecs.DOUBLE, DeviceProfile::bassCutDb,
			ByteBufCodecs.DOUBLE, DeviceProfile::bassCutHz,
			(power, sensitivity, bassDb, bassHz) -> new DeviceProfile(power, sensitivity, bassDb, bassHz, Directivity.OMNI));
	public static final StreamCodec<ByteBuf, AcousticSettingsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, p -> p.settings().rolloff(),
			ByteBufCodecs.DOUBLE, p -> p.settings().fadeFraction(),
			ByteBufCodecs.DOUBLE, p -> p.settings().airAbsorptionDbPerBlock(),
			ByteBufCodecs.DOUBLE, p -> p.settings().audibilityThresholdDb(),
			ByteBufCodecs.DOUBLE, p -> p.settings().maxRange(),
			PROFILE, p -> p.settings().portable(),
			(rolloff, fade, absorption, threshold, maxRange, portable) -> new AcousticSettingsPayload(
					new AcousticSettings(rolloff, fade, absorption, threshold, maxRange, portable)
							.clamped(RaveCraft.LOGGER::warn)));

	@Override
	public Type<AcousticSettingsPayload> type() {
		return TYPE;
	}
}
