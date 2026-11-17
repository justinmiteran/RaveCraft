package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.Directivity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** S→C on join: the server's acoustic model. The portable profile is always omnidirectional, so it is not sent. */
public record AcousticSettingsPayload(AcousticSettings settings) implements CustomPacketPayload {
	public static final Type<AcousticSettingsPayload> TYPE = new Type<>(RaveCraft.id("acoustic_settings"));
	public static final StreamCodec<ByteBuf, AcousticSettingsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, p -> p.settings().rolloff(),
			ByteBufCodecs.DOUBLE, p -> p.settings().fadeFraction(),
			ByteBufCodecs.DOUBLE, p -> p.settings().airAbsorptionDbPerBlock(),
			ByteBufCodecs.DOUBLE, p -> p.settings().portable().range(),
			ByteBufCodecs.DOUBLE, p -> p.settings().portable().bassCutDb(),
			ByteBufCodecs.DOUBLE, p -> p.settings().portable().bassCutHz(),
			(rolloff, fade, absorption, range, bassDb, bassHz) -> new AcousticSettingsPayload(new AcousticSettings(
					rolloff, fade, absorption, new DeviceProfile(range, bassDb, bassHz, Directivity.OMNI))));

	@Override
	public Type<AcousticSettingsPayload> type() {
		return TYPE;
	}
}
