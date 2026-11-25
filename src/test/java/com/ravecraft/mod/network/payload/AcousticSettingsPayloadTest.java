package com.ravecraft.mod.network.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ravecraft.mod.core.acoustics.AcousticSettings;
import com.ravecraft.mod.core.acoustics.DeviceProfile;
import com.ravecraft.mod.core.acoustics.DeviceProfiles;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMaterial;
import com.ravecraft.mod.core.acoustics.occlusion.AcousticMedium;
import com.ravecraft.mod.core.acoustics.occlusion.OcclusionSettings;
import com.ravecraft.mod.core.acoustics.reverb.EchoSettings;
import com.ravecraft.mod.core.acoustics.reverb.FluidReverb;
import com.ravecraft.mod.core.acoustics.reverb.ReverbSettings;
import com.ravecraft.mod.core.speaker.SpeakerKind;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcousticSettingsPayloadTest {
	private static AcousticSettings with(OcclusionSettings occlusion) {
		AcousticSettings d = AcousticSettings.DEFAULT;
		return new AcousticSettings(d.rolloff(), d.fadeFraction(), d.audibilityThresholdDb(), d.maxRange(), d.profiles(),
				occlusion, d.reverb());
	}

	@Test
	void codecRoundTrip() {
		AcousticSettingsPayload payload = new AcousticSettingsPayload(
				new AcousticSettings(0.5, 0.3, 60, 128,
						new DeviceProfiles(new DeviceProfile(40, 90, -9, 120), DeviceProfile.PARTY),
						OcclusionSettings.DEFAULT, ReverbSettings.DEFAULT));
		ByteBuf buf = Unpooled.buffer();
		AcousticSettingsPayload.CODEC.encode(buf, payload);
		assertEquals(payload, AcousticSettingsPayload.CODEC.decode(buf));
		assertEquals(0, buf.readableBytes());
	}

	@Test
	void partyProfileRoundTrips() {
		DeviceProfile party = new DeviceProfile(200, 90, -3, 100);
		AcousticSettings d = AcousticSettings.DEFAULT;
		AcousticSettingsPayload payload = new AcousticSettingsPayload(new AcousticSettings(d.rolloff(),
				d.fadeFraction(), d.audibilityThresholdDb(), d.maxRange(),
				new DeviceProfiles(DeviceProfile.PORTABLE, party), d.occlusion(), d.reverb()));
		ByteBuf buf = Unpooled.buffer();
		AcousticSettingsPayload.CODEC.encode(buf, payload);
		AcousticSettingsPayload decoded = AcousticSettingsPayload.CODEC.decode(buf);
		assertEquals(party, decoded.settings().profile(SpeakerKind.PARTY));
		assertEquals(0, buf.readableBytes());
	}

	@Test
	void aProfileTakesFourValues() {
		ByteBuf buf = Unpooled.buffer();
		AcousticSettingsPayload.CODEC.encode(buf, new AcousticSettingsPayload(AcousticSettings.DEFAULT));
		int header = 4 * Double.BYTES;
		int partyPower = header + 4 * Double.BYTES;
		assertEquals(DeviceProfile.PARTY.powerWatts(), buf.getDouble(partyPower));
	}

	@Test
	void decodeBoundsValuesFromServer() {
		AcousticSettingsPayload payload = new AcousticSettingsPayload(
				new AcousticSettings(Double.NaN, 0.3, 68, 9999,
						new DeviceProfiles(new DeviceProfile(20, 85, -9, 30_000), DeviceProfile.PARTY),
						OcclusionSettings.DEFAULT, ReverbSettings.DEFAULT));
		ByteBuf buf = Unpooled.buffer();
		AcousticSettingsPayload.CODEC.encode(buf, payload);
		AcousticSettings decoded = AcousticSettingsPayload.CODEC.decode(buf).settings();
		assertEquals(0.25, decoded.rolloff());
		assertEquals(512, decoded.maxRange());
		assertEquals(500, decoded.profile(SpeakerKind.PORTABLE).bassCutHz());
	}

	@Test
	void roundTripsOcclusion() {
		List<AcousticMedium> media = new ArrayList<>(OcclusionSettings.DEFAULT.media());
		media.add(new AcousticMedium("slime", 2_000_000, 1.5, 2.5, 7, 9, 0.5, 0.3));
		List<AcousticMaterial> materials = new ArrayList<>(OcclusionSettings.DEFAULT.materials());
		materials.add(new AcousticMaterial("slime", 3, 4, 0.6));
		OcclusionSettings custom = new OcclusionSettings(false, 45, media, 0.5, 1.25,
				Map.of("ad_astra:moon", "vacuum"), materials, false, 3, 7);
		AcousticSettingsPayload payload = new AcousticSettingsPayload(with(custom));
		ByteBuf buf = Unpooled.buffer();
		AcousticSettingsPayload.CODEC.encode(buf, payload);
		assertEquals(payload, AcousticSettingsPayload.CODEC.decode(buf));
		assertEquals(0, buf.readableBytes());
	}

	@Test
	void roundTripsReverb() {
		List<FluidReverb> media = new ArrayList<>(ReverbSettings.DEFAULT.media());
		media.add(new FluidReverb("slime", 2.5, 0.5, 0.6, 0.2, 0.7, true, 0.8, 3));
		ReverbSettings custom = new ReverbSettings(false, 90, 8, 0.5, 12,
				new EchoSettings(20, 30, 6, 0.3, 0.4, 0.25, 0.7), media);
		AcousticSettings d = AcousticSettings.DEFAULT;
		AcousticSettingsPayload payload = new AcousticSettingsPayload(new AcousticSettings(d.rolloff(),
				d.fadeFraction(), d.audibilityThresholdDb(), d.maxRange(), d.profiles(), d.occlusion(), custom));
		ByteBuf buf = Unpooled.buffer();
		AcousticSettingsPayload.CODEC.encode(buf, payload);
		assertEquals(payload, AcousticSettingsPayload.CODEC.decode(buf));
		assertEquals(0, buf.readableBytes());
	}

	@Test
	void encodesClampedSettingsAtCaps() {
		OcclusionSettings d = OcclusionSettings.DEFAULT;
		List<AcousticMaterial> materials = new ArrayList<>(d.materials());
		for (int i = 0; materials.size() < 40; i++) {
			materials.add(new AcousticMaterial("extra" + i, 1, 1));
		}
		OcclusionSettings many = new OcclusionSettings(d.enabled(), d.maxLossDb(), d.media(), d.interfaceScale(),
				d.interfaceHighRatio(), d.dimensions(), materials, d.detours(), d.bendLossDb(), d.bendHighLossDb());
		AcousticSettings clamped = with(many).clamped(w -> { });
		ByteBuf buf = Unpooled.buffer();
		AcousticSettingsPayload.CODEC.encode(buf, new AcousticSettingsPayload(clamped));
		assertEquals(32, AcousticSettingsPayload.CODEC.decode(buf).settings().occlusion().materials().size());
	}

	@Test
	void decodeBoundsOcclusionValues() {
		OcclusionSettings d = OcclusionSettings.DEFAULT;
		OcclusionSettings loud = new OcclusionSettings(d.enabled(), 500, d.media(), d.interfaceScale(),
				d.interfaceHighRatio(), d.dimensions(), d.materials(), d.detours(), d.bendLossDb(), d.bendHighLossDb());
		ByteBuf buf = Unpooled.buffer();
		AcousticSettingsPayload.CODEC.encode(buf, new AcousticSettingsPayload(with(loud)));
		assertEquals(120, AcousticSettingsPayload.CODEC.decode(buf).settings().occlusion().maxLossDb());
	}
}
