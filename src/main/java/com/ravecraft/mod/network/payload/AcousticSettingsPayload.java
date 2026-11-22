package com.ravecraft.mod.network.payload;

import com.ravecraft.mod.RaveCraft;
import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.DeviceProfiles;
import com.ravecraft.mod.core.acoustics.Directivity;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.reverb.EchoSettings;
import com.ravecraft.mod.core.acoustics.reverb.FluidReverb;
import com.ravecraft.mod.core.acoustics.reverb.ReverbSettings;
import io.netty.buffer.ByteBuf;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S→C on join: the server's acoustic model. The portable profile is always omnidirectional, so it is not sent. The occlusion lists are capped at their
 * maximum sizes, so only settings clamped by the server may be encoded. Decoded
 * values are brought into their bounds, so a broken server cannot feed NaN gains to the renderer.
 */
public record AcousticSettingsPayload(AcousticSettings settings) implements CustomPacketPayload {
	public static final Type<AcousticSettingsPayload> TYPE = new Type<>(RaveCraft.id("acoustic_settings"));
	private static final StreamCodec<ByteBuf, DeviceProfile> PROFILE = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, DeviceProfile::powerWatts,
			ByteBufCodecs.DOUBLE, DeviceProfile::sensitivityDb,
			ByteBufCodecs.DOUBLE, DeviceProfile::bassCutDb,
			ByteBufCodecs.DOUBLE, DeviceProfile::bassCutHz,
			ByteBufCodecs.DOUBLE, p -> p.directivity().halfAngleDeg(),
			ByteBufCodecs.DOUBLE, p -> p.directivity().rearGainDb(),
			(power, sensitivity, bassDb, bassHz, halfAngle, rearGain) -> new DeviceProfile(power, sensitivity, bassDb,
					bassHz, new Directivity(halfAngle, rearGain)));
	private static final StreamCodec<ByteBuf, DeviceProfiles> PROFILES = StreamCodec.composite(
			PROFILE, DeviceProfiles::portable,
			PROFILE, DeviceProfiles::party,
			DeviceProfiles::new);
	private static final StreamCodec<ByteBuf, AcousticMaterial> MATERIAL = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(OcclusionSettings.MAX_NAME), AcousticMaterial::name,
			ByteBufCodecs.DOUBLE, AcousticMaterial::lossDb,
			ByteBufCodecs.DOUBLE, AcousticMaterial::highLossDb,
			ByteBufCodecs.DOUBLE, AcousticMaterial::absorption,
			AcousticMaterial::new);
	private static final StreamCodec<ByteBuf, AcousticMedium> MEDIUM = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(OcclusionSettings.MAX_NAME), AcousticMedium::name,
			ByteBufCodecs.DOUBLE, AcousticMedium::impedance,
			ByteBufCodecs.DOUBLE, AcousticMedium::lossDbPerBlock,
			ByteBufCodecs.DOUBLE, AcousticMedium::highLossDbPerBlock,
			ByteBufCodecs.DOUBLE, AcousticMedium::listenerLossDb,
			ByteBufCodecs.DOUBLE, AcousticMedium::listenerHighLossDb,
			ByteBufCodecs.DOUBLE, AcousticMedium::listenerBlur,
			ByteBufCodecs.DOUBLE, AcousticMedium::surfaceAbsorption,
			AcousticMedium::new);
	private static final StreamCodec<ByteBuf, Map.Entry<String, String>> DIMENSION = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(OcclusionSettings.MAX_DIMENSION_ID), Map.Entry::getKey,
			ByteBufCodecs.stringUtf8(OcclusionSettings.MAX_NAME), Map.Entry::getValue,
			Map::entry);
	/** The lists are capped by {@code OcclusionSettings.clamped}, which the server applies on load: encode only clamped settings. */
	private static final StreamCodec<ByteBuf, OcclusionSettings> OCCLUSION = StreamCodec.composite(
			ByteBufCodecs.BOOL, OcclusionSettings::enabled,
			ByteBufCodecs.DOUBLE, OcclusionSettings::maxLossDb,
			MEDIUM.apply(ByteBufCodecs.list(OcclusionSettings.MAX_MEDIA)), OcclusionSettings::media,
			ByteBufCodecs.DOUBLE, OcclusionSettings::interfaceScale,
			ByteBufCodecs.DOUBLE, OcclusionSettings::interfaceHighRatio,
			DIMENSION.apply(ByteBufCodecs.list(OcclusionSettings.MAX_DIMENSIONS)), o -> List.copyOf(o.dimensions().entrySet()),
			MATERIAL.apply(ByteBufCodecs.list(OcclusionSettings.MAX_MATERIALS)), OcclusionSettings::materials,
			ByteBufCodecs.BOOL, OcclusionSettings::detours,
			ByteBufCodecs.DOUBLE, OcclusionSettings::bendLossDb,
			ByteBufCodecs.DOUBLE, OcclusionSettings::bendHighLossDb,
			(enabled, maxLoss, media, scale, highRatio, dimensions, materials, detours, bend, bendHigh) -> {
				Map<String, String> byId = new LinkedHashMap<>();
				dimensions.forEach(e -> byId.put(e.getKey(), e.getValue()));
				return new OcclusionSettings(enabled, maxLoss, media, scale, highRatio, byId, materials, detours, bend,
						bendHigh);
			});
	private static final StreamCodec<ByteBuf, EchoSettings> ECHO = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, EchoSettings::minDistance,
			ByteBufCodecs.DOUBLE, EchoSettings::maxDistance,
			ByteBufCodecs.VAR_INT, EchoSettings::minRays,
			ByteBufCodecs.DOUBLE, EchoSettings::maxAbsorption,
			ByteBufCodecs.DOUBLE, EchoSettings::gain,
			ByteBufCodecs.DOUBLE, EchoSettings::feedback,
			ByteBufCodecs.DOUBLE, EchoSettings::damping,
			EchoSettings::new);
	private static final StreamCodec<ByteBuf, FluidReverb> FLUID = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(OcclusionSettings.MAX_NAME), FluidReverb::medium,
			ByteBufCodecs.DOUBLE, FluidReverb::decaySeconds,
			ByteBufCodecs.DOUBLE, FluidReverb::density,
			ByteBufCodecs.DOUBLE, FluidReverb::diffusion,
			ByteBufCodecs.DOUBLE, FluidReverb::gain,
			ByteBufCodecs.DOUBLE, FluidReverb::decayHfRatio,
			ByteBufCodecs.BOOL, FluidReverb::chorus,
			ByteBufCodecs.DOUBLE, FluidReverb::chorusRateHz,
			ByteBufCodecs.DOUBLE, FluidReverb::chorusDepthMs,
			FluidReverb::new);
	/** The list is capped by {@code ReverbSettings.clamped}, which the server applies on load: encode only clamped settings. */
	private static final StreamCodec<ByteBuf, ReverbSettings> REVERB = StreamCodec.composite(
			ByteBufCodecs.BOOL, ReverbSettings::enabled,
			ByteBufCodecs.DOUBLE, ReverbSettings::rayLength,
			ByteBufCodecs.DOUBLE, ReverbSettings::maxDecaySeconds,
			ByteBufCodecs.DOUBLE, ReverbSettings::maxWetGain,
			ByteBufCodecs.DOUBLE, ReverbSettings::sameSpaceLossDb,
			ECHO, ReverbSettings::echo,
			FLUID.apply(ByteBufCodecs.list(ReverbSettings.MAX_MEDIA)), ReverbSettings::media,
			ReverbSettings::new);
	public static final StreamCodec<ByteBuf, AcousticSettingsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.DOUBLE, p -> p.settings().rolloff(),
			ByteBufCodecs.DOUBLE, p -> p.settings().fadeFraction(),
			ByteBufCodecs.DOUBLE, p -> p.settings().audibilityThresholdDb(),
			ByteBufCodecs.DOUBLE, p -> p.settings().maxRange(),
			PROFILES, p -> p.settings().profiles(),
			OCCLUSION, p -> p.settings().occlusion(),
			REVERB, p -> p.settings().reverb(),
			(rolloff, fade, threshold, maxRange, profiles, occlusion, reverb) -> new AcousticSettingsPayload(
					new AcousticSettings(rolloff, fade, threshold, maxRange, profiles, occlusion, reverb)
							.clamped(RaveCraft.LOGGER::warn)));

	@Override
	public Type<AcousticSettingsPayload> type() {
		return TYPE;
	}
}
